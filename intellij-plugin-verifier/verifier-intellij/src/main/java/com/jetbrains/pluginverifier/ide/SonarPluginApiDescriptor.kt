/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.ide

import com.jetbrains.plugin.structure.base.utils.closeLogged
import com.jetbrains.plugin.structure.base.utils.closeOnException
import com.jetbrains.plugin.structure.base.utils.isDirectory
import com.jetbrains.plugin.structure.classes.resolvers.Resolver
import com.jetbrains.plugin.structure.ide.SonarPluginApi
import com.jetbrains.plugin.structure.ide.SonarPluginApiManager
import com.jetbrains.plugin.structure.ide.classes.IdeResolverCreator
import com.jetbrains.pluginverifier.jdk.JdkDescriptor
import com.jetbrains.pluginverifier.jdk.JdkDescriptorCreator
import com.jetbrains.pluginverifier.repository.files.FileLock
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Holds IDE objects necessary for verification.
 *
 * - [sonarPluginApi] - instance of this sonar-plugin-api
 * - [ideResolver] - accessor of IDE class files
 * - [jdkDescriptor] - JDK used to run the IDE: a bundled JDK if available or a specified default JDK
 * - [ideFileLock] - a lock to protect the IDE file from deletion.
 * It will be closed along with `this` descriptor.
 */
data class SonarPluginApiDescriptor(
  val sonarPluginApi: SonarPluginApi,
  val ideResolver: Resolver,
  val jdkDescriptor: JdkDescriptor,
  val ideFileLock: FileLock?
) : Closeable {

  val version get() = sonarPluginApi.version

  val jdkVersion get() = jdkDescriptor.jdkVersion

  override fun toString() = version.toString()

  override fun close() {
    ideResolver.closeLogged()
    jdkDescriptor.closeLogged()
    ideFileLock.closeLogged()
  }

  companion object {
    /**
     * Creates [SonarPluginApiDescriptor] for specified [sonarPluginApiFilePath].
     * [ideFileLock] will be released when this [SonarPluginApiDescriptor] is closed.
     */
    fun create(
      sonarPluginApiFilePath: Path,
      defaultJdkPath: Path?,
      ideFileLock: FileLock?
    ): SonarPluginApiDescriptor {
      val ide = SonarPluginApiManager.createManager().createSonarPluginApi(sonarPluginApiFilePath)
      val ideResolver = IdeResolverCreator.createIdeResolver(ide)
      ideResolver.closeOnException {
        val jdkDescriptor = JdkDescriptorCreator.createBundledJdkDescriptor(ide)
          ?: createDefaultJdkDescriptor(defaultJdkPath)
        return SonarPluginApiDescriptor(ide, ideResolver, jdkDescriptor, ideFileLock)
      }
    }

    private fun createDefaultJdkDescriptor(defaultJdkPath: Path?): JdkDescriptor {
      val jdkPath = defaultJdkPath ?: run {
        val javaHome = System.getenv("JAVA_HOME")
        requireNotNull(javaHome) { "JAVA_HOME is not specified" }
        println("Using Java from JAVA_HOME: $javaHome")
        Paths.get(javaHome)
      }
      require(jdkPath.isDirectory) { "Invalid JDK path: $jdkPath" }
      return JdkDescriptorCreator.createJdkDescriptor(jdkPath)
    }

  }

}