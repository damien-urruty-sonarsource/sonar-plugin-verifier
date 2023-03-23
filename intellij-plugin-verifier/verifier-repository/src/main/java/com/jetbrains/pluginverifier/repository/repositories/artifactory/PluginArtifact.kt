/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.artifactory

import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.Browseable
import com.jetbrains.pluginverifier.repository.Downloadable
import com.jetbrains.pluginverifier.repository.PluginInfo
import java.net.URL
import java.util.*

/**
 * Identifier of a plugin hosted on the public Artifactory Repository [ArtifactoryRepository].
 */
class PluginArtifact(
  pluginId: String,
  pluginName: String,
  version: String,
  sinceBuild: Version?,
  untilBuild: Version?,
  vendor: String,
  val sourceCodeUrl: URL?,
  override val downloadUrl: URL,
  override val browserUrl: URL,
  val tags: List<String>,
) : Downloadable, Browseable, PluginInfo(
  pluginId,
  pluginName,
  version,
  sinceBuild,
  untilBuild,
  vendor
) {

  override val presentableName
    get() = "$pluginId:$version"

  override fun equals(other: Any?) = other is PluginArtifact
    && pluginId == other.pluginId
    && version == other.version

  override fun hashCode() = Objects.hash(pluginId, version)
}