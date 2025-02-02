/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.downloader

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.pluginverifier.misc.createOkHttpClient
import com.jetbrains.pluginverifier.network.*
import okhttp3.ResponseBody
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * [Downloader] of files for URLs provided with [urlProvider].
 */
class UrlDownloader<in K>(private val urlProvider: (K) -> URL?) : Downloader<K> {

  private companion object {
    private const val FILENAME = "filename="
    private const val FILE_PROTOCOL = "file"
    private const val HTTP_PROTOCOL = "http"
    private const val HTTPS_PROTOCOL = "https"

    private val LOG = LoggerFactory.getLogger(UrlDownloader::class.java)

    private val urlPathExtensions = listOf("jar", "zip", "tar.gz", "tar.bz2", "txt", "html", "xml", "json")
  }

  private val downloadConnector: DownloadConnector = Retrofit.Builder()
    //the base repository is not used because all URLs provided by [urlProvider] are absolute
    .baseUrl("https://unnecessary.com")
    .client(createOkHttpClient(LOG.isDebugEnabled, 5, TimeUnit.MINUTES))
    .build()
    .create(DownloadConnector::class.java)

  private fun Response<ResponseBody>.guessExtension(): String {
    /**
     * Guess by Content-Disposition header.
     */
    val contentDisposition = headers().get("Content-Disposition")
    if (contentDisposition != null && contentDisposition.contains(FILENAME)) {
      val path = contentDisposition.substringAfter(FILENAME).substringBefore(";").removeSurrounding("\"")
      val extension = guessExtensionByPath(path)
      if (extension != null) {
        return extension
      }
    }

    /**
     * Guess by content type.
     */
    val contentType = body()!!.contentType()
    if (contentType == jarContentMediaType || contentType == xJarContentMediaType) {
      return "jar"
    }
    if (contentType == jsonMediaType) {
      return "json"
    }

    /**
     * Guess by URL path extension.
     */
    val path = raw().request.url.encodedPath
    val extension = guessExtensionByPath(path)
    if (extension != null) {
      return extension
    }

    //Fallback to zip, since it's the most popular one.
    return "zip"
  }

  private fun guessExtensionByPath(path: String): String? {
    for (extension in urlPathExtensions) {
      if (path.endsWith(".$extension")) {
        return extension
      }
    }
    return null
  }

  @Throws(InterruptedException::class)
  override fun download(key: K, tempDirectory: Path): DownloadResult {
    val downloadUrl = try {
      urlProvider(key)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      return DownloadResult.FailedToDownload("Invalid URL", e)
    } ?: return DownloadResult.NotFound("Unknown URL for $key")

    return downloadByUrl(key, downloadUrl, tempDirectory)
  }

  override fun downloadFile(key: K, targetPath: Path): DownloadResult {
    val downloadUrl = try {
      urlProvider(key)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      return DownloadResult.FailedToDownload("Invalid URL", e)
    } ?: return DownloadResult.NotFound("Unknown URL for $key")

    return downloadFileByUrl(key, downloadUrl, targetPath)
  }

  private fun downloadByUrl(key: K, downloadUrl: URL, tempDirectory: Path): DownloadResult {
    checkIfInterrupted()
    return try {
      doDownload(key, downloadUrl, tempDirectory)
    } catch (e: NotFound404ResponseException) {
      DownloadResult.NotFound("Resource is not found by $downloadUrl")
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      DownloadResult.FailedToDownload("Unable to download $key: ${e.message}", e)
    }
  }

  private fun downloadFileByUrl(key: K, downloadUrl: URL, targetPath: Path): DownloadResult {
    checkIfInterrupted()
    return try {
      doDownloadFile(key, downloadUrl, targetPath)
    } catch (e: NotFound404ResponseException) {
      DownloadResult.NotFound("Resource is not found by $downloadUrl")
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      DownloadResult.FailedToDownload("Unable to download $key: ${e.message}", e)
    }
  }

  private fun doDownload(key: K, downloadUrl: URL, tempDirectory: Path): DownloadResult {
    return when (val protocol = downloadUrl.protocol) {
      FILE_PROTOCOL -> copyFileOrDirectory(downloadUrl, tempDirectory)
      HTTP_PROTOCOL, HTTPS_PROTOCOL -> downloadFileOrDirectory(downloadUrl, tempDirectory, key)
      else -> throw IllegalArgumentException("Unknown protocol: $protocol of $downloadUrl")
    }
  }

  private fun doDownloadFile(key: K, downloadUrl: URL, targetPath: Path): DownloadResult {
    return when (val protocol = downloadUrl.protocol) {
      FILE_PROTOCOL -> copyToPath(downloadUrl, targetPath)
      HTTP_PROTOCOL, HTTPS_PROTOCOL -> downloadFile(downloadUrl, targetPath, key)
      else -> throw IllegalArgumentException("Unknown protocol: $protocol of $downloadUrl")
    }
  }

  private fun copyFileOrDirectory(downloadUrl: URL, tempDirectory: Path): DownloadResult.Downloaded {
    val original = FileUtils.toFile(downloadUrl).toPath()
    val destination = tempDirectory.resolve(original.simpleName)
    original.toFile().copyRecursively(destination.toFile())
    return DownloadResult.Downloaded(destination, destination.extension, destination.isDirectory)
  }

  private fun copyToPath(downloadUrl: URL, targetPath: Path): DownloadResult.Downloaded {
    val original = FileUtils.toFile(downloadUrl).toPath()
    original.toFile().copyRecursively(targetPath.toFile())
    return DownloadResult.Downloaded(targetPath, targetPath.extension, targetPath.isDirectory)
  }

  private fun downloadFileOrDirectory(downloadUrl: URL, tempDirectory: Path, key: K): DownloadResult {
    val response = downloadConnector.download(downloadUrl.toExternalForm()).executeSuccessfully()
    val extension = response.guessExtension()
    val downloadedTempFile = Files.createTempFile(tempDirectory, "", ".$extension")
    return try {
      LOG.debug("Downloading $key to $downloadedTempFile")
      copyResponseTo(response, downloadedTempFile)
      DownloadResult.Downloaded(downloadedTempFile, extension, false)
    } catch (e: Throwable) {
      downloadedTempFile.deleteLogged()
      throw e
    }
  }

  private fun downloadFile(downloadUrl: URL, targetPath: Path, key: K): DownloadResult {
    val response = downloadConnector.download(downloadUrl.toExternalForm()).executeSuccessfully()
    val extension = response.guessExtension()
    LOG.debug("Downloading $key to $targetPath")
    copyResponseTo(response, targetPath)
    return DownloadResult.Downloaded(targetPath, extension, false)
  }

  private fun copyResponseTo(response: Response<ResponseBody>, file: Path) {
    checkIfInterrupted()
    response.body().use { responseBody ->
      val expectedSize = responseBody!!.contentLength()
      copyInputStreamToFileWithProgress(responseBody.byteStream(), expectedSize, file) { }
    }
  }

  private interface DownloadConnector {
    @Streaming
    @GET
    fun download(@Url url: String): Call<ResponseBody>
  }

}