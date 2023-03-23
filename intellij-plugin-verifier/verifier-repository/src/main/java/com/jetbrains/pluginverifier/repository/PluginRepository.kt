/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository

import com.jetbrains.plugin.structure.intellij.version.Version

/**
 * Represents API of the plugin repository.
 */
interface PluginRepository {

  /**
   * Name of the repository that can be shown to users.
   */
  val presentableName: String

  /**
   * Returns the latest plugins' versions compatible with [Version].
   */
  fun getPlugin(key: String, version: Version): PluginInfo? {
    return null
  }

  /**
   * Returns the latest plugins' versions compatible with [version].
   */
  fun getLastCompatiblePlugins(version: Version): List<PluginInfo>

  /**
   * Returns the last version of the plugin with ID equal to [pluginId]
   * compatible with [Version].
   */
  fun getLastCompatibleVersionOfPlugin(Version: Version, pluginId: String): PluginInfo?

  /**
   * Returns all versions of the plugin with ID equal to [pluginId].
   */
  fun getAllVersionsOfPlugin(pluginId: String): List<PluginInfo>

  /**
   * Returns all plugins declaring module [moduleId].
   * If [Version] is specified, only plugins compatible with this IDE are returned.
   */
  fun getPluginsDeclaringModule(moduleId: String, Version: Version?): List<PluginInfo>

}