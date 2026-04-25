package org.fossify.musicplayer.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import android.content.Context
import androidx.media3.session.MediaNotification
import org.fossify.musicplayer.R
import org.fossify.musicplayer.helpers.NotificationHelper

@UnstableApi
internal class MusicNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    init {
        setSmallIcon(R.drawable.ic_headset_small)
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(
            NotificationHelper.NOTIFICATION_CHANNEL,
            R.string.app_name.toString()
        )
    }
}
