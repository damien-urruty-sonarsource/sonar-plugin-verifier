/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.custom

import com.google.common.base.Suppliers
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.repositories.VERSION_COMPARATOR
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Base class for all repositories configured for special plugins not available in the Marketplace.
 */
abstract class CustomPluginRepository : PluginRepository {

  private val allPluginsCache = Suppliers.memoizeWithExpiration({ requestAllPlugins() }, 1, TimeUnit.MINUTES)

  protected abstract fun requestAllPlugins(): List<CustomPluginInfo>

  abstract val repositoryUrl: URL

  fun getAllPlugins(): List<CustomPluginInfo> = allPluginsCache.get()

  override fun getLastCompatiblePlugins(Version: Version) =
    getAllPlugins()

  override fun getLastCompatibleVersionOfPlugin(Version: Version, pluginId: String) =
    getAllPlugins().maxWithOrNull(VERSION_COMPARATOR)

  override fun getAllVersionsOfPlugin(pluginId: String) =
    getAllPlugins().filter { it.pluginId == pluginId }

  override fun getPluginsDeclaringModule(moduleId: String, Version: Version?): List<PluginInfo> = emptyList()

}