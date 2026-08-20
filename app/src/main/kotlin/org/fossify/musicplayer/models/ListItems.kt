package org.fossify.musicplayer.models

open class ListItem

data class AlbumHeader(val id: Long, val title: String, val coverArt: Any, val year: Int, val trackCnt: Int, val duration: Int, val artist: String) : ListItem()

data class AlbumSection(val title: String) : ListItem()
