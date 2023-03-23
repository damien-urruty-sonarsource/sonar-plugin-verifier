/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkPlugin

import com.jetbrains.pluginverifier.PluginVerificationDescriptor
import com.jetbrains.pluginverifier.PluginVerificationTarget
import com.jetbrains.pluginverifier.dependencies.resolution.*
import com.jetbrains.pluginverifier.ide.SonarPluginApiDescriptor
import com.jetbrains.pluginverifier.options.CmdOpts
import com.jetbrains.pluginverifier.options.OptionsParser
import com.jetbrains.pluginverifier.options.PluginsParsing
import com.jetbrains.pluginverifier.options.PluginsSet
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.repositories.local.LocalPluginRepository
import com.jetbrains.pluginverifier.resolution.DefaultClassResolverProvider
import com.jetbrains.pluginverifier.tasks.TaskParametersBuilder
import java.nio.file.Paths

class CheckPluginParamsBuilder(
  val pluginRepository: PluginRepository,
  val reportage: PluginVerificationReportage,
  val pluginDetailsCache: PluginDetailsCache
) : TaskParametersBuilder {

  override fun build(opts: CmdOpts, freeArgs: List<String>): CheckPluginParams {
    require(freeArgs.size > 1) {
      "You must provide 'path:[plugin path]' followed by [sonar-plugin-api version(s)], example:\n" +
        "java -jar verifier.jar check-plugin path:~/target/sonar-XXX-plugin-X.Y-SNAPSHOT.jar 9.14.0.375"
    }

    val sonarPluginApiDescriptors = freeArgs.drop(1).map {
      reportage.logVerificationStage("Reading sonar-plugin-api $it")
      OptionsParser.createSonarPluginApiDescriptor(it, opts)
    }

    val pluginApiVersions = sonarPluginApiDescriptors.map { it.version }
    val pluginsSet = PluginsSet()
    val pluginsParsing = PluginsParsing(pluginRepository, reportage, pluginsSet)

    val pluginToTestArg = freeArgs[0]
    when {
      pluginToTestArg.startsWith("@") -> {
        pluginsParsing.addPluginsListedInFile(
          Paths.get(pluginToTestArg.substringAfter("@")),
          pluginApiVersions
        )
      }
      else -> {
        pluginsParsing.addPluginBySpec(pluginToTestArg, Paths.get(""), pluginApiVersions)
      }
    }

    val externalClassesPackageFilter = OptionsParser.getExternalClassesPackageFilter(opts)
    val problemsFilters = OptionsParser.getProblemsFilters(opts)

    val verificationDescriptors = sonarPluginApiDescriptors.flatMap { sonarPluginApiDescriptor ->
      val dependencyFinder = createDependencyFinder(pluginsSet.localRepository, sonarPluginApiDescriptor, pluginDetailsCache)
      val classResolverProvider = DefaultClassResolverProvider(
        dependencyFinder,
        sonarPluginApiDescriptor,
        externalClassesPackageFilter
      )

      pluginsSet.pluginsToCheck.map {
        PluginVerificationDescriptor.IDE(sonarPluginApiDescriptor, classResolverProvider, it)
      }
    }

    val verificationTargets = sonarPluginApiDescriptors.map {
      PluginVerificationTarget.SonarPluginApi(it.version, it.jdkVersion)
    }

    pluginsSet.ignoredPlugins.forEach { (plugin, reason) ->
      verificationTargets.forEach { verificationTarget ->
        reportage.logPluginVerificationIgnored(plugin, verificationTarget, reason)
      }
    }

    return CheckPluginParams(
      sonarPluginApiDescriptors,
      problemsFilters,
      verificationDescriptors,
      pluginsSet.invalidPluginFiles,
      opts.excludeExternalBuildClassesSelector
    )
  }

  /**
   * Creates the [DependencyFinder] that firstly tries to resolve the dependency among the verified plugins.
   *
   * The 'check-plugin' task searches for dependencies among the verified plugins:
   * suppose plugins A and B are verified simultaneously and A depends on B.
   * Then B must be resolved to the local plugin when the A is verified.
   */
  private fun createDependencyFinder(localRepository: LocalPluginRepository, ideDescriptor: SonarPluginApiDescriptor, pluginDetailsCache: PluginDetailsCache): DependencyFinder {
    val localFinder = RepositoryDependencyFinder(localRepository, LastVersionSelector(), pluginDetailsCache)
    val ideDependencyFinder = createIdeBundledOrPluginRepositoryDependencyFinder(ideDescriptor.sonarPluginApi, pluginRepository, pluginDetailsCache)
    return CompositeDependencyFinder(listOf(localFinder, ideDependencyFinder))
  }


}