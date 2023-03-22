/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.local

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.repositories.VERSION_COMPARATOR

/**
 * [PluginRepository] consisting of [locally] [LocalPluginInfo] stored plugins.
 */
class LocalPluginRepository(private val plugins: MutableList<LocalPluginInfo> = arrayListOf()) : PluginRepository {

  fun addLocalPlugin(idePlugin: IdePlugin): LocalPluginInfo {
    val localPluginInfo = LocalPluginInfo(idePlugin)
    plugins.add(localPluginInfo)
    return localPluginInfo
  }

  override fun getLastCompatiblePlugins(Version: Version) =
    plugins.filter { it.isCompatibleWith(Version) }
      .groupBy { it.pluginId }
      .mapValues { it.value.maxWithOrNull(VERSION_COMPARATOR)!! }
      .values.toList()

  override fun getLastCompatibleVersionOfPlugin(Version: Version, pluginId: String) =
    getAllVersionsOfPlugin(pluginId).filter { it.isCompatibleWith(Version) }.maxWithOrNull(VERSION_COMPARATOR)

  override fun getAllVersionsOfPlugin(pluginId: String) =
    plugins.filter { it.pluginId == pluginId }

  override fun getPluginsDeclaringModule(moduleId: String, Version: Version?) =
    plugins.filter { moduleId in it.definedModules && (Version == null || it.isCompatibleWith(Version)) }

  override val presentableName
    get() = "Local Plugin Repository"

  override fun toString() = presentableName

}