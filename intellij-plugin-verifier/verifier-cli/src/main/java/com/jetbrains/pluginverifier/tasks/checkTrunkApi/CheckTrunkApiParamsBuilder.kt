/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkTrunkApi

import com.jetbrains.plugin.structure.base.utils.closeOnException
import com.jetbrains.plugin.structure.base.utils.isDirectory
import com.jetbrains.plugin.structure.base.utils.listPresentationInColumns
import com.jetbrains.plugin.structure.ide.SonarPluginApi
import com.jetbrains.pluginverifier.PluginVerificationDescriptor
import com.jetbrains.pluginverifier.PluginVerificationTarget
import com.jetbrains.pluginverifier.dependencies.resolution.*
import com.jetbrains.pluginverifier.ide.SonarPluginApiDescriptor
import com.jetbrains.pluginverifier.misc.retry
import com.jetbrains.pluginverifier.options.CmdOpts
import com.jetbrains.pluginverifier.options.OptionsParser
import com.jetbrains.pluginverifier.options.PluginsSet
import com.jetbrains.pluginverifier.options.filter.PluginFilter
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginInfo
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.files.FileLock
import com.jetbrains.pluginverifier.repository.files.IdleFileLock
import com.jetbrains.pluginverifier.repository.repositories.artifactory.PluginArtifact
import com.jetbrains.pluginverifier.repository.repositories.empty.EmptyPluginRepository
import com.jetbrains.pluginverifier.repository.repositories.local.LocalPluginRepositoryFactory
import com.jetbrains.pluginverifier.resolution.DefaultClassResolverProvider
import com.jetbrains.pluginverifier.tasks.TaskParametersBuilder
import com.sampullara.cli.Args
import com.sampullara.cli.Argument
import java.nio.file.Paths

class CheckTrunkApiParamsBuilder(
  private val pluginRepository: PluginRepository,
  private val reportage: PluginVerificationReportage,
  private val pluginDetailsCache: PluginDetailsCache
) : TaskParametersBuilder {

  override fun build(opts: CmdOpts, freeArgs: List<String>): CheckTrunkApiParams {
    val apiOpts = CheckTrunkApiOpts()
    val args = Args.parse(apiOpts, freeArgs.toTypedArray(), false)
    require(args.isNotEmpty()) { "The IDE to be checked is not specified" }

    reportage.logVerificationStage("Preparing the trunk IDE ${args[0]}")
    val trunkIdeDescriptor = OptionsParser.createSonarPluginApiDescriptor(args[0], opts)
    return trunkIdeDescriptor.closeOnException {
      buildParameters(opts, apiOpts, trunkIdeDescriptor)
    }
  }

  private fun buildParameters(opts: CmdOpts, apiOpts: CheckTrunkApiOpts, trunkIdeDescriptor: SonarPluginApiDescriptor): CheckTrunkApiParams {
    val releaseIdeFileLock: FileLock

    val majorIdePath = requireNotNull(apiOpts.majorIdePath) { "-mip --major-ide-path option is not specified" }
    val majorPath = Paths.get(majorIdePath)
    require(majorPath.isDirectory) { "The specified major IDE doesn't exist: $majorPath" }
    releaseIdeFileLock = IdleFileLock(majorPath)

    reportage.logVerificationStage("Reading classes of the release IDE ${releaseIdeFileLock.file}")
    val releaseIdeDescriptor = OptionsParser.createSonarPluginApiDescriptor(releaseIdeFileLock.file, opts)
    return releaseIdeDescriptor.closeOnException {
      releaseIdeFileLock.closeOnException {
        buildParameters(opts, apiOpts, releaseIdeDescriptor, trunkIdeDescriptor, releaseIdeFileLock)
      }
    }
  }

  private fun buildParameters(
      opts: CmdOpts,
      apiOpts: CheckTrunkApiOpts,
      releaseIdeDescriptor: SonarPluginApiDescriptor,
      trunkIdeDescriptor: SonarPluginApiDescriptor,
      releaseIdeFileLock: FileLock
  ): CheckTrunkApiParams {
    val externalClassesPackageFilter = OptionsParser.getExternalClassesPackageFilter(opts)
    val problemsFilters = OptionsParser.getProblemsFilters(opts)

    val releaseLocalRepository = apiOpts.releaseLocalPluginRepositoryRoot
      ?.let { LocalPluginRepositoryFactory.createLocalPluginRepository(Paths.get(it)) }
      ?: EmptyPluginRepository

    val trunkLocalRepository = apiOpts.trunkLocalPluginRepositoryRoot
      ?.let { LocalPluginRepositoryFactory.createLocalPluginRepository(Paths.get(it)) }
      ?: EmptyPluginRepository

    val message = "Requesting a list of plugins compatible with the release IDE ${releaseIdeDescriptor.version}"
    reportage.logVerificationStage(message)
    val releaseCompatibleVersions = pluginRepository.retry(message) {
      getLastCompatiblePlugins(releaseIdeDescriptor.version)
    }

    val releaseIgnoreInLocalRepositoryFilter = IgnorePluginsAvailableInOtherRepositoryFilter(releaseLocalRepository)
    val releaseBundledFilter = IgnoreBundledPluginsFilter(releaseIdeDescriptor.sonarPluginApi)

    val releasePluginsSet = PluginsSet()
    releasePluginsSet.addPluginFilter(releaseIgnoreInLocalRepositoryFilter)
    releasePluginsSet.addPluginFilter(releaseBundledFilter)

    releasePluginsSet.schedulePlugins(releaseCompatibleVersions)

    val trunkIgnoreInLocalRepositoryFilter = IgnorePluginsAvailableInOtherRepositoryFilter(trunkLocalRepository)
    val trunkBundledFilter = IgnoreBundledPluginsFilter(trunkIdeDescriptor.sonarPluginApi)

    val trunkPluginsSet = PluginsSet()
    trunkPluginsSet.addPluginFilter(trunkIgnoreInLocalRepositoryFilter)
    trunkPluginsSet.addPluginFilter(trunkBundledFilter)

    //Verify the same plugin versions as for the release IDE.
    trunkPluginsSet.schedulePlugins(releaseCompatibleVersions)

    //For plugins that are not compatible with the trunk IDE verify their latest versions, too.
    //This is in order to check if found compatibility problems are also present in the latest version.
    val latestCompatibleVersions = arrayListOf<PluginInfo>()
    for (pluginInfo in releaseCompatibleVersions) {
      if (!pluginInfo.isCompatibleWith(trunkIdeDescriptor.version)) {
        val lastCompatibleVersion = runCatching {
          pluginRepository.getLastCompatibleVersionOfPlugin(trunkIdeDescriptor.version, pluginInfo.pluginId)
        }.getOrNull()
        if (lastCompatibleVersion != null && lastCompatibleVersion != pluginInfo) {
          latestCompatibleVersions += lastCompatibleVersion
        }
      }
    }
    trunkPluginsSet.schedulePlugins(latestCompatibleVersions)

    val releasePluginsToCheck = releasePluginsSet.pluginsToCheck.sortedBy { 0 }
    if (releasePluginsToCheck.isNotEmpty()) {
      reportage.logVerificationStage(
        "The following updates will be checked with both ${trunkIdeDescriptor.version} and #${releaseIdeDescriptor.version}:\n" +
          releasePluginsToCheck
            .listPresentationInColumns(4, 60)
      )
    }

    val trunkLatestPluginsToCheck = latestCompatibleVersions.filter { trunkPluginsSet.shouldVerifyPlugin(it) }
    if (trunkLatestPluginsToCheck.isNotEmpty()) {
      reportage.logVerificationStage(
        "The following updates will be checked with ${trunkIdeDescriptor.version} only for comparison with the release versions of the same plugins:\n" +
          trunkLatestPluginsToCheck.listPresentationInColumns(4, 60)
      )
    }

    val releaseFinder = createDependencyFinder(releaseIdeDescriptor.sonarPluginApi, releaseIdeDescriptor.sonarPluginApi, releaseLocalRepository, pluginDetailsCache)
    val releaseResolverProvider = DefaultClassResolverProvider(
      releaseFinder,
      releaseIdeDescriptor,
      externalClassesPackageFilter
    )
    val releaseVerificationDescriptors = releasePluginsSet.pluginsToCheck.map {
      PluginVerificationDescriptor.SonarPluginApi(releaseIdeDescriptor, releaseResolverProvider, it)
    }

    val trunkFinder = createDependencyFinder(trunkIdeDescriptor.sonarPluginApi, releaseIdeDescriptor.sonarPluginApi, trunkLocalRepository, pluginDetailsCache)
    val trunkResolverProvider = DefaultClassResolverProvider(
      trunkFinder,
      trunkIdeDescriptor,
      externalClassesPackageFilter
    )
    val trunkVerificationDescriptors = trunkPluginsSet.pluginsToCheck.map {
      PluginVerificationDescriptor.SonarPluginApi(trunkIdeDescriptor, trunkResolverProvider, it)
    }

    val releaseVerificationTarget = PluginVerificationTarget.SonarPluginApi(releaseIdeDescriptor.version, releaseIdeDescriptor.jdkVersion)
    for ((pluginInfo, ignoreReason) in releasePluginsSet.ignoredPlugins) {
      reportage.logPluginVerificationIgnored(pluginInfo, releaseVerificationTarget, ignoreReason)
    }

    val trunkVerificationTarget = PluginVerificationTarget.SonarPluginApi(trunkIdeDescriptor.version, trunkIdeDescriptor.jdkVersion)
    for ((pluginInfo, ignoreReason) in trunkPluginsSet.ignoredPlugins) {
      reportage.logPluginVerificationIgnored(pluginInfo, trunkVerificationTarget, ignoreReason)
    }

    return CheckTrunkApiParams(
      trunkIdeDescriptor,
      releaseIdeDescriptor,
      releaseIdeFileLock,
      problemsFilters,
      releaseVerificationDescriptors,
      trunkVerificationDescriptors,
      releaseVerificationTarget,
      trunkVerificationTarget,
      opts.excludeExternalBuildClassesSelector
    )
  }

  /**
   * Creates [DependencyFinder] that searches dependencies using the following order:
   * 1) Bundled with [releaseOrTrunkIde]
   * 2) Available in the local repository [localPluginRepository].
   * 3) Compatible with the **release** IDE
   */
  private fun createDependencyFinder(
    releaseOrTrunkIde: SonarPluginApi,
    releaseIde: SonarPluginApi,
    localPluginRepository: PluginRepository,
    pluginDetailsCache: PluginDetailsCache
  ): DependencyFinder {
    val localRepositoryDependencyFinder = RepositoryDependencyFinder(
      localPluginRepository,
      LastVersionSelector(),
      pluginDetailsCache
    )

    val releaseDependencyFinder = RepositoryDependencyFinder(
      pluginRepository,
      LastCompatibleVersionSelector(releaseIde.version),
      pluginDetailsCache
    )

    return CompositeDependencyFinder(
      listOf(
        localRepositoryDependencyFinder,
        releaseDependencyFinder
      )
    )
  }

  private class IgnorePluginsAvailableInOtherRepositoryFilter(val repository: PluginRepository) : PluginFilter {
    override fun shouldVerifyPlugin(pluginInfo: PluginInfo): PluginFilter.Result {
      if (repository.getAllVersionsOfPlugin(pluginInfo.pluginId).isNotEmpty()) {
        return PluginFilter.Result.Ignore("Plugin is available in $repository")
      }
      return PluginFilter.Result.Verify
    }
  }

  private class IgnoreBundledPluginsFilter(val ide: SonarPluginApi) : PluginFilter {
    override fun shouldVerifyPlugin(pluginInfo: PluginInfo): PluginFilter.Result {
      return PluginFilter.Result.Verify
    }
  }

}

class CheckTrunkApiOpts {
  @set:Argument("major-ide-version", alias = "miv", description = "The IDE version with which to compare API problems. This IDE will be downloaded from the IDE builds repository: https://www.jetbrains.com/intellij-repository/releases/.")
  var majorIdeVersion: String? = null

  @set:Argument("save-major-ide-file", alias = "smif", description = "Whether to save a downloaded release IDE in cache directory for use in later verifications")
  var saveMajorIdeFile: Boolean = false

  @set:Argument("major-ide-path", alias = "mip", description = "The path to release (major) IDE build with which to compare API problems in trunk (master) IDE build.")
  var majorIdePath: String? = null

  @set:Argument(
    "release-jetbrains-plugins", alias = "rjbp", description = "The root of the local plugin repository containing JetBrains plugins compatible with the release IDE. " +
    "The local repository is a set of non-bundled JetBrains plugins built from the same sources (see Installers/<artifacts>/IU-plugins). " +
    "The Plugin Verifier will read the plugin descriptors from every plugin-like file under the specified directory." +
    "On the release IDE verification, the JetBrains plugins will be taken from the local repository if present, and from the public repository, otherwise."
  )
  var releaseLocalPluginRepositoryRoot: String? = null

  @set:Argument("trunk-jetbrains-plugins", alias = "tjbp", description = "The same as --release-local-repository but specifies the local repository of the trunk IDE.")
  var trunkLocalPluginRepositoryRoot: String? = null

}
