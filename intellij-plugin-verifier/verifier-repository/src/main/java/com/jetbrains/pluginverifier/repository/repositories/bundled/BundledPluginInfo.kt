/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.bundled

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginInfo
import java.io.ObjectInputStream
import java.util.*

/**
 * Identifier of a plugin bundled to IDE.
 */
class BundledPluginInfo(
  val Version: Version,
  val idePlugin: IdePlugin
) : PluginInfo(
  idePlugin.pluginId!!,
  idePlugin.pluginName ?: idePlugin.pluginId!!,
  idePlugin.pluginVersion ?: Version.asString(),
  idePlugin.sinceBuild,
  idePlugin.untilBuild,
  idePlugin.vendor
) {

  private fun writeReplace(): Any = throw UnsupportedOperationException("Bundled plugins cannot be serialized")

  @Suppress("UNUSED_PARAMETER")
  private fun readObject(stream: ObjectInputStream): Unit = throw UnsupportedOperationException("Bundled plugins cannot be deserialized")

  override fun equals(other: Any?) = other is BundledPluginInfo
    && Version == other.Version
    && idePlugin == other.idePlugin

  override fun hashCode() = Objects.hash(Version, idePlugin)

}