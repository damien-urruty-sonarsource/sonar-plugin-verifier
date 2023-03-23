package com.jetbrains.pluginverifier.repository

import com.jetbrains.plugin.structure.intellij.version.Version
import com.jetbrains.pluginverifier.repository.repositories.artifactory.ArtifactoryRepository
import com.jetbrains.pluginverifier.repository.repositories.artifactory.PluginArtifact
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class PublicPluginRepositoryTest : BaseRepositoryTest<ArtifactoryRepository>() {

  companion object {
    val repositoryURL = URL("https://plugins.jetbrains.com")
  }

  override fun createRepository() = ArtifactoryRepository(repositoryURL)

  @Test
  fun `browser url`() {
    val versions = repository.getAllVersionsOfPlugin("Mongo Plugin")
    assertTrue(versions.isNotEmpty())
    val updateInfo = versions.first()
    assertEquals(URL(repositoryURL, "/plugin/7141"), updateInfo.browserUrl)
  }

  @Test
  fun updatesOfExistentPlugin() {
    val updates = repository.getAllVersionsOfPlugin("Pythonid")
    assertNotNull(updates)
    assertFalse(updates.isEmpty())
    val update = updates[0]
    assertEquals("Pythonid", update.pluginId)
    assertEquals("JetBrains", update.vendor)

  }

  @Test
  fun updatesOfNonExistentPlugin() {
    val updates = repository.getAllVersionsOfPlugin("NON_EXISTENT_PLUGIN")
    assertEquals(emptyList<PluginArtifact>(), updates)
  }

}
