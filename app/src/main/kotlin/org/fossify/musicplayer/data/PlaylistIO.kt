package org.fossify.musicplayer.data

import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.writeLn
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.helpers.M3U_DURATION_SEPARATOR
import org.fossify.musicplayer.helpers.M3U_ENTRY
import org.fossify.musicplayer.helpers.M3U_HEADER
import org.fossify.musicplayer.models.Track
import java.io.File
import java.io.OutputStream

class M3uExporter(val activity: BaseSimpleActivity) {
    var failedEvents = 0
    var exportedEvents = 0

    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    fun exportPlaylist(
        outputStream: OutputStream?,
        tracks: ArrayList<Track>,
        callback: (result: ExportResult) -> Unit
    ) {
        if (outputStream == null) {
            callback(ExportResult.EXPORT_FAIL)
            return
        }

        activity.toast(org.fossify.commons.R.string.exporting)

        try {
            outputStream.bufferedWriter().use { out ->
                out.writeLn(M3U_HEADER)
                for (track in tracks) {
                    out.writeLn(M3U_ENTRY + track.duration + M3U_DURATION_SEPARATOR + track.artist + " - " + track.title)
                    out.writeLn(track.path)
                    exportedEvents++
                }
            }
        } catch (e: Exception) {
            failedEvents++
            activity.showErrorToast(e)
        } finally {
            outputStream.close()
        }

        callback(
            when {
                exportedEvents == 0 -> ExportResult.EXPORT_FAIL
                failedEvents > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            }
        )
    }
}

class M3uImporter(
    val activity: SimpleActivity,
    val callback: (result: ImportResult) -> Unit
) {
    var failedEvents = 0
    var exportedEvents = 0

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    fun importPlaylist(path: String, playListId: Int) {
        val inputStream = if (path.contains("/")) {
            File(path).inputStream()
        } else {
            activity.assets.open(path)
        }

        try {
            val m3uEntries: List<M3uEntry> = M3uParser.parse(inputStream.reader())

            val existingTracks = activity.audioHelper.getAllTracks()
                .filter { it.playListId == 0 }

            val playlistItems = mutableListOf<Track>()
            for (m3uEntry in m3uEntries) {
                for (track in existingTracks) {
                    if (m3uEntry.location.toString() == track.path || m3uEntry.title == track.title) {
                        playlistItems.add(track)
                    }
                }
            }

            activity.audioHelper.addTracksToPlaylist(playListId, playlistItems)
            exportedEvents = playlistItems.size
        } catch (e: Exception) {
            failedEvents++
            activity.showErrorToast(e)
        } finally {
            inputStream.close()
        }

        callback(
            when {
                exportedEvents == 0 -> ImportResult.IMPORT_FAIL
                failedEvents > 0 -> ImportResult.IMPORT_PARTIAL
                else -> ImportResult.IMPORT_OK
            }
        )
    }
}
