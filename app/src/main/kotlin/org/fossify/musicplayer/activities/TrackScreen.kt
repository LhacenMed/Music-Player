package org.fossify.musicplayer.activities

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import org.fossify.musicplayer.R

private val White70 = Color.White.copy(alpha = 0.7f)
private val White40 = Color.White.copy(alpha = 0.4f)
// Reduced to ~40% opacity so blurred album art colors bleed through visibly
private val Scrim = Color(0x80000000)
private val PlayCircle = Color(0x55FFFFFF)
private val AddPill = Color(0x44FFFFFF)

/** Single horizontal padding used across all screen elements for visual alignment. */
private val HPad = 30.dp

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TrackScreen(
    state: TrackUiState,
    onBack: () -> Unit,
    onSwipeDown: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,          // seconds
    onShuffleToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlaybackSettingToggle: () -> Unit,
    onAddLyrics: () -> Unit,
    onNextTrackClick: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .swipeDownToClose(onSwipeDown)
    ) {
        // ── Blurred background ──────────────────────────────────────────────
        if (state.coverArt != null) {
            GlideImage(
                model = state.coverArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radiusX = 80.dp, radiusY = 80.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0x80000000)))
        }

        // Dark scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
        )

        // Subtle bottom gradient so next-track strip blends in
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1.0f to Color(0xCC000000),
                    )
                )
        )

        // ── Main content ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onBack = onBack, onSpeedClick = onSpeedClick)

            // Album art — square, aligned to the universal horizontal padding
            GlideImage(
                model = state.coverArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = HPad)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                it.placeholder(R.drawable.ic_headset).error(R.drawable.ic_headset)
            }

            Spacer(Modifier.height(10.dp))

            // Track info
            TrackInfo(
                title = state.title,
                artist = state.artist,
                isFavorite = state.isFavorite,
                onFavoriteToggle = onFavoriteToggle,
                onAddLyrics = onAddLyrics,
            )

            Spacer(Modifier.weight(1f))

            // Seek + timestamps
            SeekSection(
                progressSecs = state.progressSecs,
                durationSecs = state.durationSecs,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(4.dp))

            // Controls
            ControlsRow(
                isPlaying = state.isPlaying,
                isShuffleOn = state.isShuffleOn,
                playbackSetting = state.playbackSetting,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffleToggle = onShuffleToggle,
                onPlaybackSettingToggle = onPlaybackSettingToggle,
            )

            Spacer(Modifier.height(48.dp))

//            // Next track strip
//            if (state.nextTrack != null) {
//                NextTrackStrip(
//                    next = state.nextTrack,
//                    onClick = onNextTrackClick,
//                )
//            }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onSpeedClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Icon buttons have built-in 12.dp padding; subtract to align icon edges with HPad
            .padding(horizontal = HPad - 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left_vector),
                contentDescription = stringResource(org.fossify.commons.R.string.back),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSpeedClick) {
            Icon(
                painter = painterResource(R.drawable.ic_playback_speed_vector),
                contentDescription = null,
                tint = White70,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// ── Track info ───────────────────────────────────────────────────────────────

@Composable
private fun TrackInfo(
    title: String,
    artist: String,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onAddLyrics: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Title + star — icon button has 12.dp built-in padding on its right edge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HPad, end = HPad - 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.ic_star_vector
                        else R.drawable.ic_star_outline_vector
                    ),
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFFFC107) else White70,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        // Artist
        Text(
            text = artist,
            color = White70,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HPad),
        )

        Spacer(Modifier.height(10.dp))

        // Lyrics row
        Row(
            modifier = Modifier.padding(horizontal = HPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.no_lyrics),
                color = White40,
                fontSize = 15.sp,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onAddLyrics,
                shape = RoundedCornerShape(50),
                color = AddPill,
            ) {
                Text(
                    text = stringResource(R.string.add),
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ── Seek bar + timestamps ────────────────────────────────────────────────────

@Composable
private fun SeekSection(
    progressSecs: Int,
    durationSecs: Int,
    onSeek: (Int) -> Unit,
) {
    // Slider has ~10.dp built-in horizontal padding on thumb edges; subtract to align track with HPad
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = HPad)) {
        Slider(
            value = if (durationSecs > 0) progressSecs.toFloat() / durationSecs else 0f,
            onValueChange = { onSeek((it * durationSecs).toInt()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = White40,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Text(text = progressSecs.formatDuration(), color = White70, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(text = durationSecs.formatDuration(), color = White70, fontSize = 12.sp)
        }
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

@Composable
private fun ControlsRow(
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    playbackSetting: PlaybackSettingUi,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onPlaybackSettingToggle: () -> Unit,
) {
    // Icon buttons have 12.dp built-in padding; subtract to keep outer icons flush with HPad
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HPad - 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shuffle
        val shuffleAlpha by animateFloatAsState(if (isShuffleOn) 1f else 0.5f, label = "shuffle")
        IconButton(onClick = onShuffleToggle) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle_vector),
                contentDescription = stringResource(
                    if (isShuffleOn) R.string.disable_shuffle else R.string.enable_shuffle
                ),
                tint = Color.White.copy(alpha = shuffleAlpha),
                modifier = Modifier.size(30.dp),
            )
        }

        // Previous
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_previous_vector),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }

        // Play / Pause — Lottie animated, matching activity_track.xml
        LottiePlayPause(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            modifier = Modifier.size(60.dp),
        )

        // Next
        IconButton(onClick = onNext) {
            Icon(
                painter = painterResource(R.drawable.ic_next_vector),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }

        // Repeat / playback setting
        val repeatTint = when (playbackSetting) {
            PlaybackSettingUi.REPEAT_OFF -> White40
            else -> Color.White
        }
        IconButton(onClick = onPlaybackSettingToggle) {
            Icon(
                painter = painterResource(playbackSetting.iconRes()),
                contentDescription = stringResource(R.string.repeat_song),
                tint = repeatTint,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// ── Lottie play/pause button ─────────────────────────────────────────────────

/**
 * Wraps the same [LottieAnimationView] and @raw/playpause asset used in activity_track.xml.
 *
 * Key design decision: [prevPlaying] uses a plain (non-Compose-state) holder so that
 * writing to it inside [AndroidView]'s update block does NOT trigger recomposition.
 * A mutableStateOf would cause an immediate re-run of update → prev == isPlaying →
 * animation skipped. The plain holder breaks that cycle entirely.
 *
 * Animation direction:
 *   forward  (speed +1): play-icon → pause-icon  (music started)
 *   backward (speed -1): pause-icon → play-icon  (music stopped)
 */
@Composable
private fun LottiePlayPause(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Non-state holder: writes are invisible to Compose, so update is called exactly once per
    // isPlaying change — which is what we need to trigger the animation exactly once.
    val prevPlaying = remember { NonStateRef<Boolean?>(null) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(PlayCircle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                LottieAnimationView(ctx).apply {
                    setAnimation(R.raw.playpause)
                    repeatCount = 0          // play once per trigger, same as lottie_loop="false"
                    isClickable = false
                    isFocusable = false
                }
            },
            update = { lottie ->
                val prev = prevPlaying.value
                when {
                    prev == null -> {
                        // First bind — jump to the correct frame with no animation
                        lottie.frame = if (isPlaying) lottie.maxFrame.toInt() else lottie.minFrame.toInt()
                    }
                    prev != isPlaying -> {
                        // State transition — animate forward (→ pause icon) or backward (→ play icon)
                        lottie.speed = if (isPlaying) 4f else -4f
                        lottie.playAnimation()
                    }
                    // prev == isPlaying: unrelated recomposition, do nothing
                }
                prevPlaying.value = isPlaying
            },
            modifier = Modifier.fillMaxSize(0.45f),
        )
    }
}

/** Mutable box whose writes never schedule a recomposition. */
private class NonStateRef<T>(var value: T)

// ── Next track strip ─────────────────────────────────────────────────────────

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun NextTrackStrip(next: NextTrackUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xCC000000),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlideImage(
                    model = next.coverArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    it.placeholder(R.drawable.ic_headset).error(R.drawable.ic_headset)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = next.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Drag down ≥ 100px with downward velocity triggers close. */
private fun Modifier.swipeDownToClose(onSwipeDown: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        var startY = 0f
        detectVerticalDragGestures(
            onDragStart = { startY = it.y },
            onDragEnd = {},
            onVerticalDrag = { change, _ ->
                if (change.position.y - startY > 100f) {
                    onSwipeDown()
                }
            },
        )
    }

private fun Int.formatDuration(): String {
    val m = this / 60
    val s = this % 60
    return "%02d:%02d".format(m, s)
}

private fun PlaybackSettingUi.iconRes(): Int = when (this) {
    PlaybackSettingUi.REPEAT_OFF -> R.drawable.ic_repeat_playlist_vector
    PlaybackSettingUi.REPEAT_ALL -> R.drawable.ic_repeat_playlist_vector
    PlaybackSettingUi.REPEAT_ONE -> R.drawable.ic_repeat_one_song_vector
    PlaybackSettingUi.STOP_AFTER_CURRENT -> R.drawable.ic_play_one_song_vector
}
