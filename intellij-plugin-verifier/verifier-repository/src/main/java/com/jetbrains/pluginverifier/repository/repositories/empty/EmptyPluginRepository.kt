/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.empty

import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository

object EmptyPluginRepository : PluginRepository {
  override fun getLastCompatiblePlugins(Version: Version): List<PluginInfo> = emptyList()

  override fun getLastCompatibleVersionOfPlugin(Version: Version, pluginId: String): PluginInfo? = null

  override fun getAllVersionsOfPlugin(pluginId: String): List<PluginInfo> = emptyList()

  override fun getPluginsDeclaringModule(moduleId: String, Version: Version?): List<PluginInfo> = emptyList()

  override val presentableName
    get() = "Empty repository"

  override fun toString() = presentableName
}