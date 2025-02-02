/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkSonarPluginApi

import com.jetbrains.plugin.structure.base.utils.pluralizeWithNumber
import com.jetbrains.pluginverifier.PluginVerificationResult
import com.jetbrains.pluginverifier.output.OutputOptions
import com.jetbrains.pluginverifier.output.html.HtmlResultPrinter
import com.jetbrains.pluginverifier.output.stream.WriterResultPrinter
import com.jetbrains.pluginverifier.output.teamcity.TeamCityHistory
import com.jetbrains.pluginverifier.output.teamcity.TeamCityLog
import com.jetbrains.pluginverifier.output.teamcity.TeamCityResultPrinter
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.results.problems.CompatibilityProblem
import com.jetbrains.pluginverifier.tasks.TaskResult
import com.jetbrains.pluginverifier.tasks.TaskResultPrinter
import java.io.PrintWriter

class CheckSonarPluginApiResultPrinter(val pluginRepository: PluginRepository) : TaskResultPrinter {

  override fun printResults(taskResult: TaskResult, outputOptions: OutputOptions) {
    with(taskResult as CheckSonarPluginApiResult) {
      if (outputOptions.teamCityLog != null) {
        val teamCityHistory = printTcLog(outputOptions.teamCityGroupType, this, outputOptions.teamCityLog)
        outputOptions.postProcessTeamCityTests(teamCityHistory)
      } else {
        printOnStdOut(this)
      }

      HtmlResultPrinter(sonarPluginApi, outputOptions).printResults(results)
    }
  }

  private fun printTcLog(groupBy: TeamCityResultPrinter.GroupBy, checkIdeResult: CheckSonarPluginApiResult, tcLog: TeamCityLog): TeamCityHistory {
    with(checkIdeResult) {
      val resultPrinter = TeamCityResultPrinter(tcLog, groupBy, pluginRepository)
      val resultsHistory = resultPrinter.printResults(results)
      val versionsHistory = resultPrinter.printNoCompatibleVersionsProblems(missingCompatibleVersionsProblems)
      val problems = hashSetOf<CompatibilityProblem>()
      val brokenPlugins = hashSetOf<PluginInfo>()
      for (result in results) {
        if (result is PluginVerificationResult.Verified && (result.hasCompatibilityProblems || result.hasDirectMissingMandatoryDependencies)) {
          problems += result.compatibilityProblems
          brokenPlugins += result.plugin
        }
      }
      val problemsNumber = problems.distinctBy { it.shortDescription }.size
      if (problemsNumber > 0) {
        tcLog.buildStatusFailure("IDE ${sonarPluginApi.version} has " + "problem".pluralizeWithNumber(problemsNumber) + " affecting " + "plugin".pluralizeWithNumber(brokenPlugins.size))
      } else {
        tcLog.buildStatusSuccess("IDE ${sonarPluginApi.version} doesn't have broken API problems")
      }

      return TeamCityHistory(resultsHistory.tests + versionsHistory.tests)
    }
  }

  private fun printOnStdOut(checkIdeResult: CheckSonarPluginApiResult) {
    val printWriter = PrintWriter(System.out)
    val resultPrinter = WriterResultPrinter(printWriter)
    resultPrinter.printResults(checkIdeResult.results)
    printWriter.flush()
  }
}