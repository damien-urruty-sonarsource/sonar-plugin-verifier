/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkSonarPluginApi

import com.jetbrains.pluginverifier.PluginVerifier
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.runSeveralVerifiers
import com.jetbrains.pluginverifier.tasks.Task
import com.jetbrains.pluginverifier.verifiers.filter.DynamicallyLoadedFilter

class CheckSonarPluginApiTask(private val parameters: CheckSonarPluginApiParams) : Task {

  override fun execute(
    reportage: PluginVerificationReportage,
    pluginDetailsCache: PluginDetailsCache
  ): CheckSonarPluginApiResult {
    with(parameters) {
      val verifiers = verificationDescriptors.map {
        PluginVerifier(
          it,
          problemsFilters,
          pluginDetailsCache,
          listOf(DynamicallyLoadedFilter()),
          excludeExternalBuildClassesSelector
        )
      }

      val results = runSeveralVerifiers(reportage, verifiers)

      return CheckSonarPluginApiResult(
        verificationTarget,
        results,
        missingCompatibleVersionsProblems
      )
    }
  }

}

