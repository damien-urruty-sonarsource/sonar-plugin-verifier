/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.ide.classes

import com.jetbrains.plugin.structure.classes.resolvers.FileOrigin
import com.jetbrains.plugin.structure.ide.SonarPluginApi

sealed class IdeFileOrigin : FileOrigin {
  override val parent: FileOrigin? = null

  abstract val sonarPluginApi: SonarPluginApi

  data class IdeLibDirectory(override val sonarPluginApi: SonarPluginApi) : IdeFileOrigin()
  data class RepositoryLibrary(override val sonarPluginApi: SonarPluginApi) : IdeFileOrigin()
  data class SourceLibDirectory(override val sonarPluginApi: SonarPluginApi) : IdeFileOrigin()
  data class CompiledModule(override val sonarPluginApi: SonarPluginApi, val moduleName: String) : IdeFileOrigin()
}