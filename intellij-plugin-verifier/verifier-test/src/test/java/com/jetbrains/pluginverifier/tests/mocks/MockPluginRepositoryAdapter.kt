package com.jetbrains.pluginverifier.tests.mocks

import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository

open class MockPluginRepositoryAdapter : PluginRepository {

  override fun getLastCompatiblePlugins(Version: Version): List<PluginInfo> = defaultAction()

  override fun getLastCompatibleVersionOfPlugin(Version: Version, pluginId: String): PluginInfo? = defaultAction()

  override fun getAllVersionsOfPlugin(pluginId: String): List<PluginInfo> = defaultAction()

  override fun getPluginsDeclaringModule(moduleId: String, Version: Version?): List<PluginInfo> = defaultAction()

  open fun defaultAction(): Nothing = throw AssertionError("Not required in tests")

  override val presentableName
    get() = "Mock repository"

  override fun toString() = presentableName
}

fun createMockPluginInfo(pluginId: String, version: String): PluginInfo =
  object : PluginInfo(pluginId, pluginId, version, null, null, null) {}
