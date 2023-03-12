/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.plugin.api

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.repository.downloader.DownloadResult
import com.jetbrains.pluginverifier.repository.downloader.Downloader
import com.jetbrains.pluginverifier.repository.downloader.UrlDownloader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * [Downloader] of the sonar-plugin-api jar file.
 */
class PluginApiJarDownloader : Downloader<String> {

  private val urlDownloader = UrlDownloader<String> { URL(it) }

  @Throws(InterruptedException::class)
  override fun download(url: String, targetPath: Path): DownloadResult {
    return DownloadResult.NotFound("No-op")
  }

  override fun downloadFile(key: String, targetPath: Path): DownloadResult {
    return try {
      urlDownloader.downloadFile(key, targetPath)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      DownloadResult.FailedToDownload("Unable to download $key", e)
    }
  }
}