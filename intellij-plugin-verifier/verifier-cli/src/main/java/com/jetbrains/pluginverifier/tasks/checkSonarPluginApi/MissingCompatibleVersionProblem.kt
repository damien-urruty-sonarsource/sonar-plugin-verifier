/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkSonarPluginApi

import com.jetbrains.plugin.structure.intellij.version.Version

/**
 * Represents a problem of type "No versions of plugin X compatible with IDE Y".
 *
 * Existence of a compatible plugin version may be important for JetBrains plugins
 * when the next IDE EAP is published: all the JetBrains plugins must
 * be published to the Plugin Repository to make the EAP useful.
 */
data class MissingCompatibleVersionProblem(
  val pluginId: String,
  val version: Version,
  private val details: String?
) {

  override fun toString() = "For plugin '$pluginId' there are no versions compatible with $version " +
    "in the Plugin Repository" + if (details != null) " $details" else ""
}