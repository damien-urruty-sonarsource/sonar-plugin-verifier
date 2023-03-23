/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkSonarPluginApi

import com.jetbrains.plugin.structure.base.utils.closeOnException
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.PluginVerificationDescriptor
import com.jetbrains.pluginverifier.PluginVerificationTarget
import com.jetbrains.pluginverifier.dependencies.resolution.createIdeBundledOrPluginRepositoryDependencyFinder
import com.jetbrains.pluginverifier.ide.SonarPluginApiDescriptor
import com.jetbrains.pluginverifier.options.CmdOpts
import com.jetbrains.pluginverifier.options.OptionsParser
import com.jetbrains.pluginverifier.options.PluginsParsing
import com.jetbrains.pluginverifier.options.PluginsSet
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.resolution.DefaultClassResolverProvider
import com.jetbrains.pluginverifier.tasks.TaskParametersBuilder

class CheckSonarPluginApiParamsBuilder(
  val pluginRepository: PluginRepository,
  val pluginDetailsCache: PluginDetailsCache,
  val reportage: PluginVerificationReportage
) : TaskParametersBuilder {

  override fun build(opts: CmdOpts, freeArgs: List<String>): CheckSonarPluginApiParams {
    require(freeArgs.isNotEmpty()) { "You have to specify the plugin-api to check. Usage:\n" +
        "   \"java -jar verifier.jar check-sonar-plugin-api ~/plugin-api/build/libs/sonar-plugin-api-9.15-SNAPSHOT.jar\"\n"+
        "   \"java -jar verifier.jar check-sonar-plugin-api 9.14.0.375\"\n" +
        "\n" +
        "By default all open-source SonarSource plugins will be checked against the given sonar-plugin-api file or version from Repox"
    }
    OptionsParser.createSonarPluginApiDescriptor(freeArgs[0], opts).closeOnException { ideDescriptor: SonarPluginApiDescriptor ->
      val externalClassesPackageFilter = OptionsParser.getExternalClassesPackageFilter(opts)
      val problemsFilters = OptionsParser.getProblemsFilters(opts)

      val pluginsSet = PluginsSet()
      PluginsParsing(pluginRepository, reportage, pluginsSet).addPluginsFromCmdOpts(opts, ideDescriptor.version)

      val missingCompatibleVersionsProblems = findMissingCompatibleVersionsProblems(ideDescriptor.version, pluginsSet)

      val dependencyFinder = createIdeBundledOrPluginRepositoryDependencyFinder(ideDescriptor.sonarPluginApi, pluginRepository, pluginDetailsCache)

      val classResolverProvider = DefaultClassResolverProvider(
        dependencyFinder,
        ideDescriptor,
        externalClassesPackageFilter
      )

      val verificationDescriptors = pluginsSet.pluginsToCheck.map {
        PluginVerificationDescriptor.IDE(ideDescriptor, classResolverProvider, it)
      }

      val verificationTarget = PluginVerificationTarget.SonarPluginApi(ideDescriptor.version, ideDescriptor.jdkVersion)
      pluginsSet.ignoredPlugins.forEach { (plugin, reason) ->
        reportage.logPluginVerificationIgnored(plugin, verificationTarget, reason)
      }

      return CheckSonarPluginApiParams(
        verificationTarget,
        verificationDescriptors,
        problemsFilters,
        missingCompatibleVersionsProblems,
        ideDescriptor,
        opts.excludeExternalBuildClassesSelector
      )
    }
  }

  /**
   * For all unique plugins' IDs to be verified determines
   * whether there are versions of these plugins
   * available in the Plugin Repository that are compatible
   * with [version], and returns [MissingCompatibleVersionProblem]s
   * for plugins IDs that don't have ones.
   */
  private fun findMissingCompatibleVersionsProblems(version: Version, pluginsSet: PluginsSet): List<MissingCompatibleVersionProblem> {
    val pluginIds = pluginsSet.pluginsToCheck.map { it.pluginId }.distinct()
    val existingPluginIds = runCatching {
      pluginRepository.getLastCompatiblePlugins(version).map { it.pluginId }
    }.getOrDefault(emptyList())

    return (pluginIds - existingPluginIds)
      .map {
          MissingCompatibleVersionProblem(it, version, null)
        }
  }

}