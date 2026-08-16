package org.fossify.musicplayer.extensions

import org.fossify.commons.extensions.getFormattedDuration

/**
 * Convert a deci-second (1/10th of a second) value into a string duration.
 *
 * @param isElapsed Whether this duration represents elapsed time. If false, `--:--` is returned
 *   when the value rounds down to zero seconds.
 */
fun Long.formatDurationDs(isElapsed: Boolean): String {
    val seconds = this / 10
    if (!isElapsed && seconds == 0L) {
        return "--:--"
    }

    return seconds.toInt().getFormattedDuration()
}
