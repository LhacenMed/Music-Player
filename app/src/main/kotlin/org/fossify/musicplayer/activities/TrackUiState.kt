package org.fossify.musicplayer.activities

import android.graphics.Bitmap

data class TrackUiState(
    val title: String = "",
    val artist: String = "",
    val coverArt: Any? = null,          // Glide model (Uri / File / null)
    val blurredBg: Bitmap? = null,      // pre-blurred bg bitmap
    val durationSecs: Int = 0,
    val progressSecs: Int = 0,
    val isPlaying: Boolean = false,
    val isShuffleOn: Boolean = false,
    val isFavorite: Boolean = false,
    val playbackSetting: PlaybackSettingUi = PlaybackSettingUi.REPEAT_OFF,
    val nextTrack: NextTrackUi? = null,
)

data class NextTrackUi(
    val label: String,
    val coverArt: Any?,
)

enum class PlaybackSettingUi { REPEAT_OFF, REPEAT_ALL, REPEAT_ONE, STOP_AFTER_CURRENT }
