/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.repositories.artifactory

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.PluginRepository
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.IntellijUpdateMetadata
import org.jetbrains.intellij.pluginRepository.model.PluginId
import org.jetbrains.intellij.pluginRepository.model.UpdateId
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

class ArtifactoryRepository(private val repositoryURL: URL = DEFAULT_URL) : PluginRepository {

  private val pluginRepositoryInstance = PluginRepositoryFactory.create(host = repositoryURL.toExternalForm())

  private val metadataCache: LoadingCache<Pair<PluginId, UpdateId>, Optional<PluginArtifact>> = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(object : CacheLoader<Pair<PluginId, UpdateId>, Optional<PluginArtifact>>() {
      override fun load(key: Pair<PluginId, UpdateId>): Optional<PluginArtifact> {
        //Loading is required => this key is outdated => will request in batch and put to the cache.
        return Optional.empty()
      }
    })

  override fun getPlugin(key: String, version: Version): PluginArtifact {
    val versionString = version.asString()

    val jarUrl =
      URL("${repositoryURL}download?repoKey=sonarsource-public-releases&path=org%252Fsonarsource%252F$key%252Fsonar-$key-plugin%252F$versionString%252Fsonar-$key-plugin-$versionString.jar")
    return PluginArtifact(key, key, version.asString(), null, null, "SonarSource", null, jarUrl, jarUrl, emptyList())
  }

  override fun getLastCompatiblePlugins(version: Version): List<PluginArtifact> =
    getLastCompatiblePlugins(version, "")

  fun getLastCompatiblePlugins(version: Version, channel: String): List<PluginArtifact> {
    val pluginManager = pluginRepositoryInstance.pluginManager
    @Suppress("DEPRECATION")
    val pluginsXmlIds = pluginManager.getCompatiblePluginsXmlIds(version.asString(), 10_000, 0)
    val updates = pluginManager.searchCompatibleUpdates(pluginsXmlIds, version.asString(), channel)
    val pluginIdAndUpdateIds = updates.map { it.pluginId to it.id }
    return getPluginInfosForManyPluginIdsAndUpdateIds(pluginIdAndUpdateIds).values.toList()
  }

  override fun getLastCompatibleVersionOfPlugin(version: Version, pluginId: String): PluginArtifact? {
    val compatibleUpdates = pluginRepositoryInstance.pluginManager.searchCompatibleUpdates(listOf(pluginId), version.asString())
    val compatibleUpdate = compatibleUpdates.firstOrNull() ?: return null
    return getOrRequestInfo(compatibleUpdate.pluginId, compatibleUpdate.id)
  }

  override fun getAllVersionsOfPlugin(pluginId: String): List<PluginArtifact> {
    val pluginBean = pluginRepositoryInstance.pluginManager.getPluginByXmlId(pluginId) ?: return emptyList()
    val pluginVersions = pluginRepositoryInstance.pluginManager.getPluginVersions(pluginBean.id)
    val pluginIdAndUpdateIds = pluginVersions.map { pluginBean.id to it.id }
    return getPluginInfosForManyPluginIdsAndUpdateIds(pluginIdAndUpdateIds).values.toList()
  }

  override fun getPluginsDeclaringModule(moduleId: String, version: Version?): List<PluginArtifact> {
    val plugins = pluginRepositoryInstance.pluginManager.searchCompatibleUpdates(
      module = moduleId, build = version?.asString().orEmpty()
    )
    val pluginIdAndUpdateIds = plugins.map { it.pluginId to it.id }
    return getPluginInfosForManyPluginIdsAndUpdateIds(pluginIdAndUpdateIds).values.toList()
  }

  private fun createAndCacheUpdateInfo(metadata: IntellijUpdateMetadata, pluginId: Int): PluginArtifact {
    val updateInfo = PluginArtifact(
      metadata.xmlId,
      metadata.name,
      metadata.version,
      metadata.since.prepareIdeVersion(),
      metadata.until.prepareIdeVersion(),
      metadata.vendor,
      parseSourceCodeUrl(metadata.sourceCodeUrl),
      getDownloadUrl(metadata.id),
      getBrowserUrl(pluginId),
      metadata.tags,
    )
//    metadataCache.put(updateInfo.pluginIntId to updateInfo.updateId, Optional.of(updateInfo))
    return updateInfo
  }

  private fun getCachedInfo(pluginId: Int, updateId: Int): PluginArtifact? {
    val optional = metadataCache[pluginId to updateId]
    if (optional.isPresent) {
      //Return up-to-date metadata.
      return optional.get()
    }
    return null
  }

  private fun getOrRequestInfo(pluginId: Int, updateId: Int): PluginArtifact? {
    val cachedInfo = getCachedInfo(pluginId, updateId)
    if (cachedInfo != null) {
      return cachedInfo
    }
    val updateMetadata = pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadata(pluginId, updateId)
      ?: return null
    return createAndCacheUpdateInfo(updateMetadata, pluginId)
  }

  fun getPluginInfosForManyPluginIdsAndUpdateIds(pluginAndUpdateIds: List<Pair<Int, Int>>): Map<Int, PluginArtifact> {
    val toRequest = arrayListOf<Pair<PluginId, UpdateId>>()
    val result = hashMapOf<UpdateId, PluginArtifact>()
    for ((pluginId, updateId) in pluginAndUpdateIds) {
      val cachedInfo = getCachedInfo(pluginId, updateId)
      if (cachedInfo != null) {
        result[updateId] = cachedInfo
      } else {
        toRequest += pluginId to updateId
      }
    }
    if (toRequest.isNotEmpty()) {
      val metadataBatch = pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadataBatch(toRequest)
      val updateIdToPluginId = toRequest.associateBy({ it.second }, { it.first })
      for ((updateId, metadata) in metadataBatch) {
        val pluginId = updateIdToPluginId.getValue(updateId)
        result[updateId] = createAndCacheUpdateInfo(metadata, pluginId)
      }
    }
    return result
  }

  private fun getBrowserUrl(pluginId: Int) =
    URL("${repositoryURL.toExternalForm().trimEnd('/')}/plugin/$pluginId")

  private fun getDownloadUrl(updateId: Int) =
    URL("${repositoryURL.toExternalForm().trimEnd('/')}/plugin/download/?noStatistic=true&updateId=$updateId")

  private fun String?.prepareIdeVersion(): Version? =
    if (this == null || this == "" || this == "0.0") {
      null
    } else {
      Version.createIdeVersionIfValid(this)
    }

  private fun parseSourceCodeUrl(url: String?): URL? {
    if (url.isNullOrBlank()) {
      return null
    }
    return try {
      URL(url)
    } catch (e: MalformedURLException) {
      null
    }
  }

  override val presentableName
    get() = "Repox ${repositoryURL.toExternalForm()}"

  override fun toString() = presentableName
  companion object {

    val DEFAULT_URL = URL("https://repox.jfrog.io/ui/api/v1/")
  }
}