package com.jetbrains.plugin.structure.intellij.classes.locator

import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.classes.resolvers.JarFileResolver
import com.jetbrains.plugin.structure.classes.resolvers.Resolver
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import java.io.File

class JarPluginLocator(private val readMode: Resolver.ReadMode) : ClassesLocator {
  override val locationKey: LocationKey = JarPluginKey

  override fun findClasses(idePlugin: IdePlugin, pluginFile: File): List<Resolver> {
    if (pluginFile.isJar()) {
      return listOf(JarFileResolver(pluginFile.toPath(), readMode, PluginFileOrigin.SingleJar(idePlugin)))
    }
    return emptyList()
  }
}

object JarPluginKey : LocationKey {
  override val name: String = "jar"

  override fun getLocator(readMode: Resolver.ReadMode) = JarPluginLocator(readMode)
}