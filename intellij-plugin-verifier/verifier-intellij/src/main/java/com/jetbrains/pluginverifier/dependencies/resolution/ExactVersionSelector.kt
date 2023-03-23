/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.dependencies.resolution

import com.jetbrains.plugin.structure.ide.VersionComparatorUtil
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.repositories.artifactory.ArtifactoryRepository
import com.jetbrains.pluginverifier.repository.repositories.artifactory.PluginArtifact

/**
 * [PluginVersionSelector] that selects the _last_ version of the plugin from the repository.
 */
class ExactVersionSelector(private val version: Version) : PluginVersionSelector {
  override fun selectPluginVersion(pluginId: String, pluginRepository: PluginRepository): PluginVersionSelector.Result {
    val pluginInfo = pluginRepository.getPlugin(pluginId, version) ?: return PluginVersionSelector.Result.NotFound("Plugin $pluginId not found in the repository")
    return PluginVersionSelector.Result.Selected(pluginInfo)
  }

  override fun selectPluginByModuleId(moduleId: String, pluginRepository: PluginRepository): PluginVersionSelector.Result {
    val plugins = pluginRepository.getPluginsDeclaringModule(moduleId, null)
    return selectLastVersion(plugins, pluginRepository, "Plugin declaring module '$moduleId' is not found in $pluginRepository")
  }

  private fun selectLastVersion(
    allVersions: List<PluginInfo>,
    pluginRepository: PluginRepository,
    notFoundMessage: String
  ): PluginVersionSelector.Result {
    val lastVersion = if (pluginRepository is ArtifactoryRepository) {
      allVersions.maxByOrNull { it: PluginInfo -> 0 }
    } else {
      allVersions.maxWithOrNull(compareBy(VersionComparatorUtil.COMPARATOR) { it.version })
    }
    return if (lastVersion != null) {
      PluginVersionSelector.Result.Selected(lastVersion)
    } else {
      PluginVersionSelector.Result.NotFound(notFoundMessage)
    }
  }
}