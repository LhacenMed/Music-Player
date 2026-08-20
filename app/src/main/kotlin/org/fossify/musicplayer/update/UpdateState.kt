package org.fossify.musicplayer.update

import java.io.File

/** Where the app update flow currently stands. Read from [UpdateService.state]. */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val update: AppUpdate) : UpdateState()
    data class Downloading(val update: AppUpdate, val percent: Int) : UpdateState()
    data class Downloaded(val update: AppUpdate, val apkFile: File) : UpdateState()
    data class Error(val update: AppUpdate, val message: String) : UpdateState()
}
