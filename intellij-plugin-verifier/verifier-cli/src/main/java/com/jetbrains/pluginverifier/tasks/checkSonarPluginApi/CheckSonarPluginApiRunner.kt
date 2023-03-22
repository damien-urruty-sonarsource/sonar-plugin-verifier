/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkSonarPluginApi

import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.tasks.CommandRunner

/**
 * Runner of the ['check-sonar-plugin-api'] [CheckSonarPluginApiTask] command.
 */
class CheckSonarPluginApiRunner : CommandRunner {
  override val commandName: String = "check-sonar-plugin-api"

  override fun getParametersBuilder(
    pluginRepository: PluginRepository,
    pluginDetailsCache: PluginDetailsCache,
    reportage: PluginVerificationReportage
  ) = CheckSonarPluginApiParamsBuilder(pluginRepository, pluginDetailsCache, reportage)

}