package org.fossify.musicplayer.update

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.MainActivity
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.playback.NotificationHelper
import org.greenrobot.eventbus.EventBus

/**
 * Downloads a release APK in the foreground, so the download survives the update dialog closing
 * or the app backgrounding.
 *
 * Holds [state] the same way [org.fossify.musicplayer.playback.PlaybackService] holds its own
 * playback state: a plain, synchronously-readable companion var, with [Events.UpdateStateChanged]
 * telling whatever's currently showing it to read the new value.
 */
class UpdateService : Service() {
    private lateinit var notificationHelper: NotificationHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper.createInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val update = intent?.getStringExtra(EXTRA_UPDATE)?.let(AppUpdate::fromJson)
        if (update == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(progress = 0, isDone = false))
        setState(UpdateState.Downloading(update, percent = 0))

        UpdateDownloader.download(
            context = this,
            update = update,
            onProgress = { percent ->
                setState(UpdateState.Downloading(update, percent))
                notificationHelper.notify(NOTIFICATION_ID, buildNotification(percent, isDone = false))
            },
            onComplete = { apkFile ->
                setState(UpdateState.Downloaded(update, apkFile))
                notificationHelper.notify(NOTIFICATION_ID, buildNotification(progress = 100, isDone = true))
                stopForegroundAndSelf()
            },
            onError = { message ->
                setState(UpdateState.Error(update, message))
                notificationHelper.cancel(NOTIFICATION_ID)
                stopForegroundAndSelf()
            }
        )

        return START_NOT_STICKY
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun buildNotification(progress: Int, isDone: Boolean) = notificationHelper.createUpdateNotification(
        contentText = if (isDone) getString(R.string.tap_to_install) else "$progress%",
        progress = progress,
        isDone = isDone,
        contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    companion object {
        private const val NOTIFICATION_ID = 44
        const val EXTRA_UPDATE = "EXTRA_UPDATE"

        var state: UpdateState = UpdateState.Idle
            private set

        fun setState(newState: UpdateState) {
            state = newState
            EventBus.getDefault().post(Events.UpdateStateChanged())
        }
    }
}
