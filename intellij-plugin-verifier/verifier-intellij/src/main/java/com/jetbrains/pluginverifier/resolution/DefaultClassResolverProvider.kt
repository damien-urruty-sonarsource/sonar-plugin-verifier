/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.resolution

import com.jetbrains.plugin.structure.base.utils.closeOnException
import com.jetbrains.plugin.structure.base.utils.rethrowIfInterrupted
import com.jetbrains.plugin.structure.classes.resolvers.CompositeResolver
import com.jetbrains.plugin.structure.classes.resolvers.Resolver
import com.jetbrains.pluginverifier.createPluginResolver
import com.jetbrains.pluginverifier.dependencies.DependenciesGraphBuilder
import com.jetbrains.pluginverifier.dependencies.resolution.DependencyFinder
import com.jetbrains.pluginverifier.ide.SonarPluginApiDescriptor
import com.jetbrains.pluginverifier.plugin.PluginDetails
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.verifiers.packages.PackageFilter
import com.jetbrains.pluginverifier.verifiers.resolution.caching
import java.io.Closeable

class DefaultClassResolverProvider(
  private val dependencyFinder: DependencyFinder,
  private val ideDescriptor: SonarPluginApiDescriptor,
  private val externalClassesPackageFilter: PackageFilter
) : ClassResolverProvider {

  override fun provide(checkedPluginDetails: PluginDetails): ClassResolverProvider.Result {
    val closeableResources = arrayListOf<Closeable>()
    closeableResources.closeOnException {
      val pluginResolver = checkedPluginDetails.pluginClassesLocations.createPluginResolver()

      val (dependenciesGraph, dependenciesResults) =
        DependenciesGraphBuilder(dependencyFinder).buildDependenciesGraph(checkedPluginDetails.idePlugin, ideDescriptor.sonarPluginApi)

      closeableResources += dependenciesResults

      val dependenciesClassResolver = createDependenciesResolver(dependenciesResults)

      val resolver = CompositeResolver.create(
        pluginResolver,
        ideDescriptor.jdkDescriptor.jdkResolver,
        ideDescriptor.ideResolver,
        dependenciesClassResolver
      ).caching()
      return ClassResolverProvider.Result(pluginResolver, resolver, dependenciesGraph, closeableResources)
    }
  }

  override fun provideExternalClassesPackageFilter() = externalClassesPackageFilter

  private fun createDependenciesResolver(results: List<DependencyFinder.Result>): Resolver {
    val resolvers = arrayListOf<Resolver>()
    resolvers.closeOnException {
      for (result in results) {
        if (result is DependencyFinder.Result.DetailsProvided) {
          val cacheResult = result.pluginDetailsCacheResult
          if (cacheResult is PluginDetailsCache.Result.Provided) {
            val resolver = try {
              cacheResult.pluginDetails.pluginClassesLocations.createPluginResolver()
            } catch (e: Exception) {
              e.rethrowIfInterrupted()
              continue
            }
            resolvers.add(resolver)
          }
        }
      }
      return CompositeResolver.create(resolvers)
    }
  }
}