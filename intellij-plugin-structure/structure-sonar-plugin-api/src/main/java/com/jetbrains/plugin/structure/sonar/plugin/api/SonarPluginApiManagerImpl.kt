/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.sonar.plugin.api

import com.jetbrains.plugin.structure.base.utils.isDirectory
import com.jetbrains.plugin.structure.intellij.plugin.JarFilesResourceResolver
import com.jetbrains.plugin.structure.intellij.resources.ResourceResolver
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.plugin.structure.intellij.version.VersionImpl
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class SonarPluginApiManagerImpl : SonarPluginApiManager() {

  override fun createSonarPluginApi(sonarPluginApiFilePath: Path) = createSonarPluginApi(sonarPluginApiFilePath, null)

  override fun createSonarPluginApi(sonarPluginApiFilePath: Path, version: Version?): SonarPluginApi {
    if (!Files.exists(sonarPluginApiFilePath)) {
      throw IOException("Specified path does not exist: $sonarPluginApiFilePath")
    }

    val ideVersion = version ?: readVersionFromJar(sonarPluginApiFilePath)
    return SonarPluginApiImpl(sonarPluginApiFilePath, ideVersion)
  }

  private fun readVersionFromJar(sonarPluginApiFilePath: Path): Version {
    return when (val fileLoadingResult =
      JarFilesResourceResolver(listOf(sonarPluginApiFilePath)).resolveResource("sonar-api-version.txt")) {
      is ResourceResolver.Result.NotFound -> throw InvalidSonarPluginApiException(
        sonarPluginApiFilePath,
        "Missing \"sonar-api-version.txt\" file in the sonar-plugin-api JAR file"
      )
      is ResourceResolver.Result.Failed -> throw InvalidSonarPluginApiException(sonarPluginApiFilePath, "Failed to read $sonarPluginApiFilePath")
      is ResourceResolver.Result.Found -> VersionImpl.fromString(fileLoadingResult.resourceStream.bufferedReader().use { it.readText().trim() })
    }
  }

  companion object {

    fun isCompiledUltimate(ideaDir: Path) = getCompiledClassesRoot(ideaDir) != null &&
      ideaDir.resolve(".idea").isDirectory &&
      ideaDir.resolve("community").resolve(".idea").isDirectory

    fun isCompiledCommunity(ideaDir: Path) = getCompiledClassesRoot(ideaDir) != null &&
      ideaDir.resolve(".idea").isDirectory &&
      !ideaDir.resolve("community").resolve(".idea").isDirectory

    fun isDistributionIde(ideaDir: Path) = ideaDir.resolve("lib").isDirectory &&
      !ideaDir.resolve(".idea").isDirectory

    fun getCompiledClassesRoot(ideaDir: Path): Path? =
      listOf(
        ideaDir.resolve("out").resolve("production"),
        ideaDir.resolve("out").resolve("classes").resolve("production"),
        ideaDir.resolve("out").resolve("compilation").resolve("classes").resolve("production")
      ).find { it.isDirectory }
  }

}
