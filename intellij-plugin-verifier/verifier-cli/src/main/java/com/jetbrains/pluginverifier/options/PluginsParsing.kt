/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.options

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.readLines
import com.jetbrains.plugin.structure.intellij.plugin.SonarPluginManager
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.dependencies.resolution.ExactVersionSelector
import com.jetbrains.pluginverifier.dependencies.resolution.LastVersionSelector
import com.jetbrains.pluginverifier.dependencies.resolution.PluginVersionSelector
import com.jetbrains.pluginverifier.misc.retry
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.tasks.InvalidPluginFile
import com.jetbrains.pluginverifier.tasks.checkPluginApi.SonarSourcePlugin
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility class used to fill [pluginsSet] with a list of plugins to check.
 */
class PluginsParsing(
  private val pluginRepository: PluginRepository,
  private val reportage: PluginVerificationReportage,
  private val pluginsSet: PluginsSet
) {

  /**
   * Parses command line options and add specified plugins compatible with [Version].
   */
  fun addPluginsFromCmdOpts(opts: CmdOpts, version: Version) {
    for (pluginId in opts.pluginToCheckAllBuilds) {
      addAllCompatibleVersionsOfPlugin(pluginId, version)
    }

    for (pluginId in opts.pluginToCheckLastBuild) {
      addLastCompatibleVersionOfPlugin(pluginId, version)
    }

    val pluginsToCheckFile = opts.pluginsToCheckFile?.let { Paths.get(it) }
    if (pluginsToCheckFile != null) {
      addPluginsListedInFile(pluginsToCheckFile, listOf(version))
    }
    if (opts.pluginToCheckAllBuilds.isEmpty() && opts.pluginsToCheckFile.isNullOrEmpty() && opts.pluginsToCheckFile == null) {
      // not checking any specific plugin
      addSonarSourcePlugins()
    }
  }

  private fun addSonarSourcePlugins() {
    SonarSourcePlugin.values().forEach { plugin -> addSonarSourcePlugin(plugin) }
  }

  private fun addSonarSourcePlugin(plugin: SonarSourcePlugin) {
    val selector = ExactVersionSelector(plugin.version)
    val selectResult = selector.selectPluginVersion(plugin.key, pluginRepository)

    if (selectResult is PluginVersionSelector.Result.Selected) {
      pluginsSet.schedulePlugin(selectResult.pluginInfo)
    }
  }

  /**
   * Adds last version of [pluginId] to [pluginsSet].
   */
  fun addLastPluginVersion(pluginId: String) {
    val selector = LastVersionSelector()
    val selectResult = retry("Latest version of $pluginId") {
      selector.selectPluginVersion(pluginId, pluginRepository)
    }
    if (selectResult is PluginVersionSelector.Result.Selected) {
      pluginsSet.schedulePlugin(selectResult.pluginInfo)
    }
  }

  /**
   * Parses lines of [pluginsListFile] and adds specified plugins to the [pluginsSet].
   */
  fun addPluginsListedInFile(pluginsListFile: Path, ideVersions: List<Version>) {
    reportage.logVerificationStage("Reading plugins list to check from file ${pluginsListFile.toAbsolutePath()} against IDE versions ${ideVersions.joinToString { it.asString() }}")
    val specs = pluginsListFile.readLines()
      .map { it.trim() }
      .filterNot { it.isEmpty() }
      .filterNot { it.startsWith("//") }

    for (spec in specs) {
      addPluginBySpec(spec, pluginsListFile, ideVersions)
    }
  }

  /**
   * Adds all plugins that correspond to one of the following specs:
   *
   * ```
   * - id:<plugin-id>                    // all compatible version of <plugin-id>
   * - version:<plugin-id>:<version>     // all compatible version of <plugin-id>
   * - $id or id$                        // only the last version of the plugin compatible with IDEs will be checked
   * - #<update-id>                      // update #<update-id>
   * - path:<plugin-path>                // plugin from <plugin-path>, where <plugin-path> may be relative to base path.
   * - <other>                           // treated as a path: or id:
   * ```
   */
  fun addPluginBySpec(spec: String, basePath: Path, ideVersions: List<Version>) {
    if (spec.startsWith('$') || spec.endsWith('$')) {
      val pluginId = spec.trim('$').trim()
      ideVersions.forEach { addLastCompatibleVersionOfPlugin(pluginId, it) }
      return
    }

    if (spec.startsWith("id:")) {
      val pluginId = spec.substringAfter("id:")
      ideVersions.forEach { addAllCompatibleVersionsOfPlugin(pluginId, it) }
      return
    }

    if (spec.startsWith("version:")) {
      val idAndVersion = spec.substringAfter("version:")
      val id = idAndVersion.substringBefore(":").trim()
      val version = idAndVersion.substringAfter(":").trim()
      require(version.isNotEmpty()) { "Empty version specified for a plugin to be checked: {$spec}" }
      addPluginVersion(id, version)
      return
    }

    val pluginFile = if (spec.startsWith("path:")) {
      val linePath = spec.substringAfter("path:")
      tryFindPluginByPath(basePath, linePath) ?: throw IllegalArgumentException("Invalid path: $linePath")
    } else {
      tryFindPluginByPath(basePath, spec)
    }

    if (pluginFile != null) {
      addPluginFile(pluginFile, false)
    } else {
      ideVersions.forEach { addAllCompatibleVersionsOfPlugin(spec, it) }
    }
  }

  private fun tryFindPluginByPath(baseFilePath: Path, linePath: String): Path? {
    val path = try {
      Paths.get(linePath)
    } catch (e: Exception) {
      return null
    }

    if (path.exists()) {
      return path
    }

    val siblingPath = baseFilePath.resolveSibling(linePath)
    if (siblingPath.exists()) {
      return siblingPath
    }
    return null
  }

  /**
   * Adds all versions of the plugin with ID `pluginId` compatible with `Version`.
   */
  private fun addAllCompatibleVersionsOfPlugin(pluginId: String, version: Version) {
    val stepName = "All versions of plugin '$pluginId' compatible with $version"
    val compatibleVersions = pluginRepository.retry(stepName) {
      getAllVersionsOfPlugin(pluginId).filter { it.isCompatibleWith(version) }
    }
    reportage.logVerificationStage("$stepName: " + if (compatibleVersions.isEmpty()) "no compatible versions" else compatibleVersions.joinToString { it.presentableName })
    pluginsSet.schedulePlugins(compatibleVersions)
  }

  /**
   * Adds version [version] of plugin with XML ID [pluginId].
   */
  private fun addPluginVersion(pluginId: String, version: String) {
    val stepName = "Plugin '$pluginId' of version '$version'"
    val allVersionsOfPlugin = pluginRepository.retry(stepName) {
      getAllVersionsOfPlugin(pluginId)
    }
    val pluginInfo = allVersionsOfPlugin.find { it.version == version }
    reportage.logVerificationStage(
      stepName + ": " + (pluginInfo?.presentableName ?: "no version '$version' of plugin '$pluginId' available")
    )
    pluginInfo ?: return
    pluginsSet.schedulePlugin(pluginInfo)
  }

  /**
   * Adds the last version of plugin with ID `pluginId` compatible with `Version`.
   */
  private fun addLastCompatibleVersionOfPlugin(pluginId: String, version: Version) {
    val stepName = "Last version of plugin '$pluginId' compatible with $version"
    val lastVersion = pluginRepository.retry(stepName) {
      getLastCompatibleVersionOfPlugin(version, pluginId)
    }
    reportage.logVerificationStage("$stepName: ${lastVersion?.presentableName ?: "no compatible version"}")
    lastVersion ?: return
    pluginsSet.schedulePlugin(lastVersion)
  }

  /**
   * Adds plugin from [pluginFile].
   */
  fun addPluginFile(pluginFile: Path, validateDescriptor: Boolean) {
    if (!pluginFile.exists()) {
      throw RuntimeException("Plugin file '$pluginFile' with absolute path '${pluginFile.toAbsolutePath()}' doesn't exist")
    }

    reportage.logVerificationStage("Reading plugin to check from $pluginFile")
    val pluginCreationResult = SonarPluginManager.createManager().createPlugin(pluginFile, validateDescriptor)
    with(pluginCreationResult) {
      when (this) {
        is PluginCreationSuccess -> pluginsSet.scheduleLocalPlugin(plugin)
        is PluginCreationFail -> {
          reportage.logVerificationStage("Plugin is invalid in $pluginFile: ${errorsAndWarnings.joinToString()}")
          pluginsSet.invalidPluginFiles.add(InvalidPluginFile(pluginFile, errorsAndWarnings))
        }
      }
    }
  }

}