package com.jetbrains.pluginverifier.tests.mocks

import com.jetbrains.plugin.structure.ide.SonarPluginApi
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.version.Version
import java.nio.file.Path
import java.nio.file.Paths

data class MockIde(
  private val version: Version,
  private val idePath: Path = Paths.get(""),
  private val bundledPlugins: List<IdePlugin> = emptyList()
) : SonarPluginApi() {

  override fun getIdePath() = idePath

  override fun getVersion() = version

}