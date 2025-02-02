/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository

import com.jetbrains.plugin.structure.intellij.version.Version

/**
 * Identifier of a plugin.
 */
abstract class PluginInfo(
  /**
   * Unique plugin ID, which may be equal
   * to name if ID is not specified.
   */
  val pluginId: String,

  /**
   * Plugin name.
   */
  val pluginName: String,

  /**
   * Plugin version.
   */
  val version: String,

  /**
   * "since" compatibility range.
   */
  val sinceBuild: Version?,

  /**
   * "until" compatibility range.
   */
  val untilBuild: Version?,

  /**
   * Vendor of the plugin.
   */
  val vendor: String?
) {

  /**
   * Checks whether this plugin is compatible with [Version].
   */
  fun isCompatibleWith(Version: Version) =
    (sinceBuild == null || sinceBuild <= Version) && (untilBuild == null || Version <= untilBuild)

  val presentableSinceUntilRange: String
    get() {
      val sinceCode = sinceBuild
      val untilCode = untilBuild
      if (sinceCode != null) {
        if (untilCode != null) {
          return "$sinceCode — $untilCode"
        }
        return "$sinceCode+"
      }
      if (untilCode != null) {
        return "1.0 — $untilCode"
      }
      return "all"
    }

  open val presentableName
    get() = "$pluginId $version"

  final override fun toString() = presentableName
}