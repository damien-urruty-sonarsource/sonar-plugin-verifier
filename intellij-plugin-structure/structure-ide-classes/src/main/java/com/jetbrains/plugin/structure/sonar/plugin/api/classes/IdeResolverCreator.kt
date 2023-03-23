/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.sonar.plugin.api.classes

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.classes.resolvers.*
import com.jetbrains.plugin.structure.sonar.plugin.api.SonarPluginApi
import com.jetbrains.plugin.structure.sonar.plugin.api.SonarPluginApiManagerImpl
import com.jetbrains.plugin.structure.sonar.plugin.api.SonarPluginApiManagerImpl.Companion.isCompiledCommunity
import com.jetbrains.plugin.structure.sonar.plugin.api.SonarPluginApiManagerImpl.Companion.isCompiledUltimate
import com.jetbrains.plugin.structure.sonar.plugin.api.SonarPluginApiManagerImpl.Companion.isDistributionIde
import com.jetbrains.plugin.structure.sonar.plugin.api.InvalidSonarPluginApiException
import com.jetbrains.plugin.structure.sonar.plugin.api.getRepositoryLibrariesJars
import java.nio.file.Path

object IdeResolverCreator {

  @JvmStatic
  fun createIdeResolver(sonarPluginApi: SonarPluginApi): Resolver {
    val fileOrigin = JarOrZipFileOrigin(sonarPluginApi.idePath.simpleName, IdeFileOrigin.IdeLibDirectory(sonarPluginApi))
    return JarFileResolver(sonarPluginApi.idePath, Resolver.ReadMode.FULL, fileOrigin)
  }

  @JvmStatic
  fun createIdeResolver(readMode: Resolver.ReadMode, sonarPluginApi: SonarPluginApi): Resolver {
    val idePath = sonarPluginApi.idePath
    return when {
      isDistributionIde(idePath) -> getJarsResolver(idePath.resolve("lib"), readMode, IdeFileOrigin.IdeLibDirectory(sonarPluginApi))
      isCompiledCommunity(idePath) || isCompiledUltimate(idePath) -> getIdeResolverFromCompiledSources(idePath, readMode, sonarPluginApi)
      else -> throw InvalidSonarPluginApiException(idePath, "Invalid IDE $sonarPluginApi at $idePath")
    }
  }

  private fun getJarsResolver(
    libDirectory: Path,
    readMode: Resolver.ReadMode,
    parentOrigin: FileOrigin
  ): Resolver {
    if (!libDirectory.isDirectory) {
      return EmptyResolver
    }

    val jars = libDirectory.listFiles().filter { file -> file.isJar() }
    val antJars = libDirectory.resolve("ant").resolve("lib").listFiles().filter { it.isJar() }
    return CompositeResolver.create(buildJarOrZipFileResolvers(jars + antJars, readMode, parentOrigin))
  }

  //TODO: Resolver created this way contains all libraries declared in the project,
  // including those that don't go to IDE distribution. So such a created resolver may
  // resolve classes differently than they are resolved when running IDE.
  // IDE sources can generate so-called "project-structure-mapping.json", which contains mapping
  // between compiled modules and jar files to which these modules are packaged in the final distribution.
  // We can use this mapping to construct a true resolver without irrelevant libraries.
  private fun getIdeResolverFromCompiledSources(idePath: Path, readMode: Resolver.ReadMode, sonarPluginApi: SonarPluginApi): Resolver {
    val resolvers = arrayListOf<Resolver>()
    resolvers.closeOnException {
      resolvers += getJarsResolver(idePath.resolve("lib"), readMode, IdeFileOrigin.SourceLibDirectory(sonarPluginApi))
      resolvers += getRepositoryLibrariesResolver(idePath, readMode, sonarPluginApi)

      val compiledClassesRoot = SonarPluginApiManagerImpl.getCompiledClassesRoot(idePath)!!
      compiledClassesRoot.listFiles().forEach { moduleRoot ->
        val fileOrigin = IdeFileOrigin.CompiledModule(sonarPluginApi, moduleRoot.simpleName)
        resolvers += DirectoryResolver(moduleRoot, fileOrigin, readMode)
      }

      if (isCompiledUltimate(idePath)) {
        resolvers += getJarsResolver(idePath.resolve("community").resolve("lib"), readMode, IdeFileOrigin.SourceLibDirectory(sonarPluginApi))
      }
      return CompositeResolver.create(resolvers)
    }
  }

  private fun getRepositoryLibrariesResolver(idePath: Path, readMode: Resolver.ReadMode, sonarPluginApi: SonarPluginApi): Resolver {
    val jars = getRepositoryLibrariesJars(idePath)
    return CompositeResolver.create(buildJarOrZipFileResolvers(jars, readMode, IdeFileOrigin.RepositoryLibrary(sonarPluginApi)))
  }

}

