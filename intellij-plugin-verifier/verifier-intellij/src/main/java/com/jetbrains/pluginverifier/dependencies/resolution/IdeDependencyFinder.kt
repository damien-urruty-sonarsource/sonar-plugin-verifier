/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.dependencies.resolution

import com.jetbrains.plugin.structure.ide.SonarPluginApi
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.repository.PluginRepository

fun createIdeBundledOrPluginRepositoryDependencyFinder(
  ide: SonarPluginApi,
  pluginRepository: PluginRepository,
  pluginDetailsCache: PluginDetailsCache
): DependencyFinder {
  val repositoryDependencyFinder = RepositoryDependencyFinder(
    pluginRepository,
    LastCompatibleVersionSelector(ide.version),
    pluginDetailsCache
  )

  return CompositeDependencyFinder(listOf(repositoryDependencyFinder))
}