package org.fossify.musicplayer.update

import android.os.Handler
import android.os.Looper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

private const val MANIFEST_URL = "https://raw.githubusercontent.com/LhacenMed/Music-Player/main/version.json"
private const val TIMEOUT_MS = 15_000

/** Checks the repo's `version.json` for a release newer than what's installed. */
object UpdateChecker {
    /**
     * Checks in the background and reports back on the main thread. Also the one place that
     * drives [UpdateService.state] through [UpdateState.Checking] and [UpdateState.Available], so
     * every caller - the launch check and the Settings row alike - keeps that state consistent.
     */
    fun checkAsync(onResult: (found: AppUpdate?) -> Unit) {
        val currentState = UpdateService.state
        if (currentState is UpdateState.Downloading || currentState is UpdateState.Downloaded) {
            // A download already in hand outranks checking again - re-checking here would only
            // reset the state a dialog showing it is reading, out from under an active download.
            onResult((currentState as? UpdateState.Downloaded)?.update ?: (currentState as UpdateState.Downloading).update)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        UpdateService.setState(UpdateState.Checking)
        ensureBackgroundThread {
            val update = check()
            mainHandler.post {
                UpdateService.setState(update?.let(UpdateState::Available) ?: UpdateState.Idle)
                onResult(update)
            }
        }
    }

    /** Blocks on a network call; run off the main thread. Null on any failure or if already up to date. */
    private fun check(): AppUpdate? = runCatching {
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        AppUpdate.fromJson(json)?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }.getOrNull()
}
