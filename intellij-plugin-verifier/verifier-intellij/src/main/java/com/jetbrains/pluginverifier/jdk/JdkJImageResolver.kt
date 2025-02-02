/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.jdk

import com.jetbrains.plugin.structure.base.utils.closeAll
import com.jetbrains.plugin.structure.base.utils.closeLogged
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.rethrowIfInterrupted
import com.jetbrains.plugin.structure.classes.resolvers.*
import com.jetbrains.plugin.structure.classes.utils.AsmUtil
import org.objectweb.asm.tree.ClassNode
import java.io.Closeable
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.*
import java.util.*
import java.util.stream.Collectors

/**
 * [Resolver] that reads class files from JImage corresponding to `<JDK>/lib/modules` file of JDK 9 and later.
 */
class JdkJImageResolver(jdkPath: Path, override val readMode: ReadMode) : Resolver() {
  private companion object {

    val JRT_SCHEME_URI: URI = URI.create("jrt:/")
  }

  private val fileOrigin: FileOrigin = JdkFileOrigin(jdkPath)

  private val classNameToModuleName: Map<String, String>

  private val packageSet = PackageSet()

  private val nameSeparator: String

  private val modulesPath: Path

  private val closeableResources = arrayListOf<Closeable>()

  init {
    val fileSystem = try {
      getOrCreateJrtFileSystem(jdkPath)
    } catch (e: Exception) {
      throw RuntimeException("Unable to read content from jrt:/ file system.", e)
    }

    nameSeparator = fileSystem.separator

    modulesPath = fileSystem.getPath("/modules")

    classNameToModuleName = Files.walk(modulesPath).use { stream ->
      stream
        .filter { p -> p.fileName.toString().endsWith(".class") }
        .collect(
          Collectors.toMap(
            { p -> getClassName(p) },
            { p -> getModuleName(p) },
            { one, _ -> one }
          )
        )
    }

    for (className in classNameToModuleName.keys) {
      packageSet.addPackagesOfClass(className)
    }
  }

  private fun getOrCreateJrtFileSystem(jdkPath: Path): FileSystem {
    val javaVersion = System.getProperty("java.version")?.substringBefore(".")?.toIntOrNull()
    val jrtFsJars = listOf(
      jdkPath.resolve("lib").resolve("jrt-fs.jar"),
      jdkPath.resolve("Contents").resolve("Home").resolve("lib").resolve("jrt-fs.jar")
    )
    val jrtJar = jrtFsJars.find { it.exists() }

    requireNotNull(jrtJar) { "Invalid JDK. Neither of .jars exist: " + jrtFsJars.joinToString() }

    val classLoader = URLClassLoader(arrayOf(jrtJar.toUri().toURL()))

    return if (javaVersion == null || javaVersion < 17) {
      try {
        FileSystems.getFileSystem(JRT_SCHEME_URI)
      } catch (e: Exception) {
        try {
          val fileSystem = FileSystems.newFileSystem(JRT_SCHEME_URI, hashMapOf("java.home" to jdkPath.toString()), classLoader)
          closeableResources += classLoader
          fileSystem
        } catch (e: FileSystemAlreadyExistsException) {
          classLoader.closeLogged()

          //File system might be already created concurrently. Try to get existing file system again.
          FileSystems.getFileSystem(JRT_SCHEME_URI)
        }
      }
    }
    else {
      FileSystems.newFileSystem(JRT_SCHEME_URI, hashMapOf("java.home" to jdkPath.toString()), classLoader)
    }
  }

  private fun getModuleName(classPath: Path): String =
    modulesPath.relativize(classPath).first().toString()

  private fun getClassName(classPath: Path): String {
    val relative = modulesPath.relativize(classPath)
    return relative
      .subpath(1, relative.nameCount).toString()
      .substringBeforeLast(".class").replace(nameSeparator, "/")
  }

  override val allClasses
    get() = classNameToModuleName.keys

  override val allPackages
    get() = packageSet.getAllPackages()

  override val allBundleNameSet
    get() = ResourceBundleNameSet(emptyMap())

  override fun resolveClass(className: String): ResolutionResult<ClassNode> {
    val moduleName = classNameToModuleName[className]
    if (moduleName != null) {
      val classPath = modulesPath.resolve(moduleName).resolve(className.replace("/", nameSeparator) + ".class")
      return readClass(className, classPath)
    }
    return ResolutionResult.NotFound
  }

  private fun readClass(className: String, classPath: Path): ResolutionResult<ClassNode> =
    try {
      val classNode = readClassNode(className, classPath)
      ResolutionResult.Found(classNode, fileOrigin)
    } catch (e: InvalidClassFileException) {
      ResolutionResult.Invalid(e.message)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      ResolutionResult.FailedToRead(e.localizedMessage ?: e.javaClass.name)
    }

  override fun resolveExactPropertyResourceBundle(baseName: String, locale: Locale) = ResolutionResult.NotFound

  private fun readClassNode(className: String, classFilePath: Path): ClassNode =
    Files.newInputStream(classFilePath, StandardOpenOption.READ).use { inputStream ->
      AsmUtil.readClassNode(className, inputStream, readMode == ReadMode.FULL)
    }

  override fun containsClass(className: String) = className in classNameToModuleName

  override fun containsPackage(packageName: String) = packageSet.containsPackage(packageName)

  override fun processAllClasses(processor: (ResolutionResult<ClassNode>) -> Boolean): Boolean {
    Files.walk(modulesPath).use { stream ->
      for (classPath in stream.filter { it.fileName.toString().endsWith(".class") }) {
        val className = getClassName(classPath)
        val result = readClass(className, classPath)
        if (!processor(result)) {
          return false
        }
      }
    }
    return true
  }

  override fun close() {
    closeableResources.closeAll()
  }
}