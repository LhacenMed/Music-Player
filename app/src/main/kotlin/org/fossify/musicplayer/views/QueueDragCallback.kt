package org.fossify.musicplayer.views

import androidx.recyclerview.widget.RecyclerView

/**
 * Applies the queue's drag and swipe gestures, updating the list immediately so the gesture reads as
 * continuous while [onMoveTrack] / [onRemoveTrack] carry the same change through to the player.
 */
class QueueDragCallback(
    private val adapter: QueueAdapter,
    private val onMoveTrack: (from: Int, to: Int) -> Unit,
    private val onRemoveTrack: (at: Int) -> Unit
) : MaterialDragCallback() {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        adapter.moveItems(from, to)
        onMoveTrack(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val at = viewHolder.bindingAdapterPosition
        adapter.removeItem(at)
        onRemoveTrack(at)
    }
}
