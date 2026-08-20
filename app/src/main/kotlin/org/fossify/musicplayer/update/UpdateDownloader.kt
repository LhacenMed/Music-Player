package org.fossify.musicplayer.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.helpers.ensureBackgroundThread
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 15_000
private const val MAX_REDIRECTS = 5
private const val BUFFER_SIZE = 64 * 1024

/** Downloads a release APK to a stable, reusable path under the cache dir. */
object UpdateDownloader {
    /** Runs off the main thread; every callback lands back on it. */
    fun download(
        context: Context,
        update: AppUpdate,
        onProgress: (percent: Int) -> Unit,
        onComplete: (file: File) -> Unit,
        onError: (message: String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val apkFile = apkFile(context)

        ensureBackgroundThread {
            try {
                val connection = openWithRedirects(update.apkUrl)
                val totalBytes = connection.contentLengthLong

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = 0L
                        var lastReportedPercent = -1
                        var readBytes: Int

                        while (input.read(buffer).also { readBytes = it } != -1) {
                            output.write(buffer, 0, readBytes)
                            downloadedBytes += readBytes

                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    mainHandler.post { onProgress(percent) }
                                }
                            }
                        }
                    }
                }

                mainHandler.post { onComplete(apkFile) }
            } catch (e: Exception) {
                apkFile.delete()
                mainHandler.post { onError(e.message ?: "Download failed") }
            }
        }
    }

    fun apkFile(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }.resolve("update.apk")

    /**
     * A GitHub release asset URL 302s to a different host, which [HttpURLConnection] does not
     * follow on its own once the host changes.
     */
    private fun openWithRedirects(urlString: String, redirectsLeft: Int = MAX_REDIRECTS): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.connect()

        val redirectLocation = connection.getHeaderField("Location")
        return when {
            connection.responseCode !in 300..399 || redirectLocation == null -> connection
            redirectsLeft <= 0 -> throw IllegalStateException("Too many redirects")
            else -> {
                connection.disconnect()
                openWithRedirects(redirectLocation, redirectsLeft - 1)
            }
        }
    }
}
