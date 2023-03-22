package com.jetbrains.pluginverifier.tests.mocks

import com.jetbrains.plugin.structure.base.plugin.PluginIcon
import com.jetbrains.plugin.structure.base.plugin.ThirdPartyDependency
import com.jetbrains.plugin.structure.intellij.plugin.*
import com.jetbrains.plugin.structure.intellij.version.Version
import org.jdom2.Document
import org.jdom2.Element
import java.nio.file.Path

data class MockIdePlugin(
  override val pluginId: String? = null,
  override val pluginName: String? = pluginId,
  override val pluginVersion: String? = null,
  override val description: String? = null,
  override val url: String? = null,
  override val vendor: String? = null,
  override val vendorEmail: String? = null,
  override val vendorUrl: String? = null,
  override val changeNotes: String? = null,
  override val icons: List<PluginIcon> = emptyList(),
  override val productDescriptor: ProductDescriptor? = null,
  override val dependencies: List<PluginDependency> = emptyList(),
  override val incompatibleModules: List<String> = emptyList(),
  override val underlyingDocument: Document = Document(Element("idea-plugin")),
  override val optionalDescriptors: List<OptionalPluginDescriptor> = emptyList(),
  override val extensions: Map<String, List<Element>> = hashMapOf(),
  override val sinceBuild: Version = Version.createIdeVersion("IU-163.1"),
  override val untilBuild: Version? = null,
  override val definedModules: Set<String> = emptySet(),
  override val originalFile: Path? = null,
  override val appContainerDescriptor: IdePluginContentDescriptor = MutableIdePluginContentDescriptor(),
  override val projectContainerDescriptor: IdePluginContentDescriptor = MutableIdePluginContentDescriptor(),
  override val moduleContainerDescriptor: IdePluginContentDescriptor = MutableIdePluginContentDescriptor(),
  override val thirdPartyDependencies: List<ThirdPartyDependency> = emptyList()
) : IdePlugin {

  override val useIdeClassLoader = false
  override val isImplementationDetail = false

  override val declaredThemes = emptyList<IdeTheme>()

  override fun isCompatibleWithIde(Version: Version) =
    sinceBuild <= Version && (untilBuild == null || Version <= untilBuild)
}