package org.fossify.musicplayer.playback.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import org.fossify.musicplayer.extensions.currentMediaItems
import org.fossify.musicplayer.extensions.maybeForceNext
import org.fossify.musicplayer.extensions.maybeForcePrevious
import org.fossify.musicplayer.extensions.move
import org.fossify.musicplayer.extensions.shuffledMediaItemsIndices
import org.fossify.musicplayer.extensions.indexOfFirstOrNull

private const val DEFAULT_SHUFFLE_ORDER_SEED = 42L

@UnstableApi
class SimpleMusicPlayer(private val exoPlayer: ExoPlayer) : ForwardingPlayer(exoPlayer) {

    /**
     * The default implementation only advertises the seek to next and previous item in the case
     * that it's not the first or last track. We manually advertise that these
     * are available to ensure next/previous buttons are always visible.
     */
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .addAll(
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }

    override fun seekToNext() {
        play()
        if (maybeForceNext()) return
        super.seekToNext()
    }

    override fun seekToPrevious() {
        play()
        if (maybeForcePrevious()) return
        super.seekToPrevious()
    }

    override fun seekToNextMediaItem() = seekToNext()

    override fun seekToPreviousMediaItem() = seekToPrevious()

    /**
     * ExoPlayer builds one shuffle order when the queue is set and keeps it for the queue's
     * lifetime, so turning shuffle off and on again would deal out the very same order every time.
     * Seeding a fresh one here, anchored on whatever is playing, is what makes each turn of the
     * shuffle button a genuinely new run through the queue.
     *
     * Every way of enabling shuffle passes through the session's player, so this covers the panel,
     * the notification and any external controller alike.
     */
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        super.setShuffleModeEnabled(shuffleModeEnabled)
        if (shuffleModeEnabled && mediaItemCount > 0) {
            @Suppress("DEPRECATION")
            setShuffleIndices(createShuffledIndices(mediaItemCount, currentMediaItemIndex))
        }
    }

    override fun getAudioSessionId() = exoPlayer.audioSessionId

    fun setSkipSilence(skipSilence: Boolean) {
        exoPlayer.skipSilenceEnabled = skipSilence
    }

    /**
     * This is done here only because the player interface doesn't yet support the shuffle order concept: https://github.com/androidx/media/issues/325
     * To ensure the correct item is played, we manually alter the shuffle order here.
     */
    @Deprecated("Should be rewritten when https://github.com/androidx/media/issues/325 is implemented.")
    fun setShuffleIndices(indices: IntArray) {
        val shuffleOrder = DefaultShuffleOrder(indices, DEFAULT_SHUFFLE_ORDER_SEED)
        exoPlayer.shuffleOrder = shuffleOrder
    }

    @Deprecated("Should be rewritten when https://github.com/androidx/media/issues/325 is implemented.")
    fun setNextMediaItem(mediaItem: MediaItem) {
        val currentIndex = currentMediaItems.indexOfFirstOrNull { it.mediaId == mediaItem.mediaId }
        if (currentIndex != null) {
            if (shuffleModeEnabled) {
                ensureItemPlaysNext(currentIndex)
            } else {
                moveMediaItem(currentIndex, nextMediaItemIndex)
            }
        } else {
            val newIndex = currentMediaItemIndex + 1
            addMediaItem(newIndex, mediaItem)
            ensureItemPlaysNext(newIndex)
        }
    }

    private fun ensureItemPlaysNext(itemIndex: Int) {
        val nextMediaItemIndex = nextMediaItemIndex
        if (itemIndex != nextMediaItemIndex && shuffleModeEnabled) {
            val shuffledIndices = shuffledMediaItemsIndices.toMutableList()
            val shuffledCurrentIndex = shuffledIndices.indexOf(itemIndex)
            val shuffledNewIndex = shuffledIndices.indexOf(nextMediaItemIndex)
            shuffledIndices.move(currentIndex = shuffledCurrentIndex, newIndex = shuffledNewIndex)
            exoPlayer.shuffleOrder = DefaultShuffleOrder(
                shuffledIndices.toIntArray(),
                DEFAULT_SHUFFLE_ORDER_SEED
            )
        }
    }
}

/**
 * Deal out a fresh play order over [length] tracks, bringing the track at [startIndex] to the
 * front so that shuffling never interrupts what is already playing.
 *
 * An inside-out Fisher-Yates shuffle, so every ordering is equally likely and the whole queue is
 * laid out in one pass. Ported from Auxio's `BetterShuffleOrder`.
 */
private fun createShuffledIndices(length: Int, startIndex: Int): IntArray {
    val shuffled = IntArray(length)
    for (i in 0 until length) {
        val swapIndex = (0..i).random()
        shuffled[i] = shuffled[swapIndex]
        shuffled[swapIndex] = i
    }

    val startIndexInShuffled = shuffled.indexOf(startIndex)
    if (startIndexInShuffled != -1) {
        shuffled[startIndexInShuffled] = shuffled[0]
        shuffled[0] = startIndex
    }

    return shuffled
}
