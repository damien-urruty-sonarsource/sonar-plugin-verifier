package com.jetbrains.pluginverifier.resources

import com.jetbrains.plugin.structure.base.utils.writeText
import com.jetbrains.pluginverifier.repository.downloader.DownloadResult
import com.jetbrains.pluginverifier.repository.downloader.Downloader
import java.nio.file.Path

class SimulationDownloader(private val timeConsumer: () -> Unit = {}) : Downloader<Int> {
  override fun download(key: Int, tempDirectory: Path): DownloadResult {
    timeConsumer()
    val file = tempDirectory.resolve(key.toString())
    file.writeText(key.toString())
    return DownloadResult.Downloaded(file, "", false)
  }

  override fun downloadFile(key: Int, targetPath: Path): DownloadResult {
    TODO("Not yet implemented")
  }
}