/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.sonar.plugin.api

data class PluginIdAndVersion(val pluginId: String, val version: String) {
  val presentableName: String
    get() = "$pluginId $version"
}