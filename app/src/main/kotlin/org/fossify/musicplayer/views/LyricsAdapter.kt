package org.fossify.musicplayer.views

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders lyric lines for [LyricsView] at the given [density]. Timing is not this adapter's
 * concern: it only knows which line is currently active, so plain and synced lyrics share the exact
 * same rendering path.
 */
internal class LyricsAdapter(private val density: LyricsDensity) :
    RecyclerView.Adapter<LyricsLineViewHolder>() {
    private var lines: List<String> = emptyList()
    private var activeIndex = NO_ACTIVE

    override fun getItemCount() = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LyricsLineViewHolder(
        LayoutInflater.from(parent.context).inflate(density.lineLayout, parent, false) as TextView
    )

    override fun onBindViewHolder(holder: LyricsLineViewHolder, position: Int) {
        holder.bind(lines[position], position == activeIndex)
    }

    override fun onBindViewHolder(holder: LyricsLineViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_ACTIVE)) {
            holder.setActive(position == activeIndex)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** Replace every line, clearing the active highlight. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newLines: List<String>) {
        lines = newLines
        activeIndex = NO_ACTIVE
        notifyDataSetChanged()
    }

    /**
     * Move the highlight to [index], or clear it with [NO_ACTIVE]. Only the two affected rows are
     * repainted, as this runs on every line change during playback.
     */
    fun setActiveIndex(index: Int) {
        if (index == activeIndex) return
        val previous = activeIndex
        activeIndex = index
        if (previous != NO_ACTIVE) notifyItemChanged(previous, PAYLOAD_ACTIVE)
        if (index != NO_ACTIVE) notifyItemChanged(index, PAYLOAD_ACTIVE)
    }

    companion object {
        /** Sentinel for "no line is active", used for plain lyrics and pre-first-line playback. */
        const val NO_ACTIVE = -1

        private val PAYLOAD_ACTIVE = Any()
    }
}

internal class LyricsLineViewHolder(private val line: TextView) : RecyclerView.ViewHolder(line) {
    fun bind(text: String, active: Boolean) {
        line.text = text
        setActive(active)
    }

    /**
     * Only the color changes with the active state. Weight and size are held constant so that a
     * line can never re-wrap and shift the rows around it.
     */
    fun setActive(active: Boolean) {
        line.isActivated = active
    }
}
