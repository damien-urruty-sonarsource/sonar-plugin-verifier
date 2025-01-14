package com.jetbrains.plugin.structure.dotnet.mock

import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.problems.PluginDescriptorIsNotFound
import com.jetbrains.plugin.structure.base.problems.PropertyNotSpecified
import com.jetbrains.plugin.structure.base.problems.UnexpectedDescriptorElements
import com.jetbrains.plugin.structure.base.utils.simpleName
import com.jetbrains.plugin.structure.dotnet.ReSharperPlugin
import com.jetbrains.plugin.structure.dotnet.ReSharperPluginManager
import com.jetbrains.plugin.structure.dotnet.problems.InvalidDependencyVersionError
import com.jetbrains.plugin.structure.dotnet.problems.NullIdDependencyError
import com.jetbrains.plugin.structure.dotnet.problems.createIncorrectDotNetPluginFileProblem
import com.jetbrains.plugin.structure.intellij.problems.TooLongPropertyValue
import com.jetbrains.plugin.structure.mocks.BasePluginManagerTest
import com.jetbrains.plugin.structure.rules.FileSystemType
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DotNetInvalidPluginTest(fileSystemType: FileSystemType) : BasePluginManagerTest<ReSharperPlugin, ReSharperPluginManager>(fileSystemType) {
  override fun createManager(extractDirectory: Path) =
    ReSharperPluginManager.createManager(extractDirectory)

  @Test
  fun `incorrect plugin file type`() {
    val incorrect = temporaryFolder.newFile("incorrect.txt")
    assertProblematicPlugin(incorrect, listOf(createIncorrectDotNetPluginFileProblem(incorrect.simpleName)))
  }

  @Test
  fun `plugin file does not exist`() {
    val nonExistentFile = Paths.get("non-existent-file")
    Assert.assertThrows("Plugin file non-existent-file does not exist", IllegalArgumentException::class.java) {
      createPluginSuccessfully(nonExistentFile)
    }
  }

  @Test
  fun `unable to extract plugin`() {
    val brokenZipArchive = temporaryFolder.newFile("broken.nupkg")
    assertProblematicPlugin(brokenZipArchive, listOf(PluginDescriptorIsNotFound("*.nuspec")))
  }

  @Test
  fun `no meta-inf plugin xml found`() {
    val file = temporaryFolder.newFile("withoutDescriptor.nupkg")
    ZipOutputStream(Files.newOutputStream(file)).use { it.putNextEntry(ZipEntry("randomEntry.txt")) }
    assertProblematicPlugin(file, listOf(PluginDescriptorIsNotFound("*.nuspec")))
  }

  @Test
  fun `completely invalid plugin descriptor`() {
    `test invalid plugin xml`(
      "abracadabra",
      listOf(UnexpectedDescriptorElements("unexpected element on line 1"))
    )
  }

  @Test
  fun `plugin id is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { id = "" },
      listOf(PropertyNotSpecified("id"))
    )
  }

  @Test
  fun `plugin version is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { version = "" },
      listOf(PropertyNotSpecified("version"))
    )
  }

  @Test
  fun `plugin license is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { licenseUrl = "" },
      listOf(PropertyNotSpecified("licenseUrl"))
    )
  }

  @Test
  fun `plugin long name`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { title = "<title>${"a".repeat(65)}</title>" },
      listOf(TooLongPropertyValue("", "title", 65, 64))
    )
  }

  @Test
  fun `plugin long releaseNotes`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { releaseNotes = "<releaseNotes>${"a".repeat(65550)}</releaseNotes>" },
      listOf(TooLongPropertyValue("", "releaseNotes", 65550, 65000))
    )
  }

  @Test
  fun `plugin authors is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { authors = "" },
      listOf(PropertyNotSpecified("authors"))
    )
  }

  @Test
  fun `plugin description is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { description = "" },
      listOf(PropertyNotSpecified("description"))
    )
  }

  @Test
  fun `dependency name is not specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency version=\"[8.0, 8.3)\"/></dependencies>" },
      listOf(NullIdDependencyError())
    )
  }

  @Test
  fun `dependency version is blank`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"ReSharper\" version=\"[8.0, 8.3)\" /><dependency id=\"Wave\" version=\"\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("", "Original range string is blank"))
    )
  }

  @Test
  fun `more than 2 values in dependency version`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"ReSharper\" version=\"[8.0, 8.3)\" /><dependency id=\"Wave\" version=\"[182.0.0, 183.0.0, 184.0.0)\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("[182.0.0, 183.0.0, 184.0.0)", "There shouldn't be more than 2 values in range"))
    )
  }

  @Test
  fun `wrong dependency version format`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"ReSharper\" version=\"[8.0)\" /><dependency id=\"Wave\" version=\"[182.0.0, 184.0.0)\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("[8.0)", "The formats (1.0.0], [1.0.0) and (1.0.0) are invalid"))
    )
  }

  @Test
  fun `upper and lower bounds were specified`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"Wave\" version=\"(, )\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("(, )", "Neither of upper nor lower bounds were specified"))
    )
  }

  @Test
  fun `lower bound is greater than upper one`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"Wave\" version=\"(184.0, 183.0)\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("(184.0, 183.0)", "maxVersion should be greater than minVersion"))
    )
  }

  @Test
  fun `wrong parentheses format=`() {
    `test invalid plugin xml`(
      perfectDotNetBuilder.modify { dependencies = "<dependencies><dependency id=\"Wave\" version=\"(183.0, 183.0)\"/></dependencies>" },
      listOf(InvalidDependencyVersionError("(183.0, 183.0)", "Wrong format. (1.0.0, 1.0.0], [1.0.0, 1.0.0) and (1.0.0, 1.0.0) are invalid"))
    )
  }

  private fun `test invalid plugin xml`(pluginXmlContent: String, expectedProblems: List<PluginProblem>) {
    val pluginFolder = getTempPluginArchive(pluginXmlContent)
    assertProblematicPlugin(pluginFolder, expectedProblems)
  }

  private fun getTempPluginArchive(pluginXmlContent: String): Path {
    val pluginFile = temporaryFolder.newFile("archive.nupkg")
    ZipOutputStream(Files.newOutputStream(pluginFile)).use {
      it.putNextEntry(ZipEntry("Vendor.PluginName.nuspec"))
      it.write(pluginXmlContent.toByteArray())
      it.closeEntry()
    }
    return pluginFile
  }
}