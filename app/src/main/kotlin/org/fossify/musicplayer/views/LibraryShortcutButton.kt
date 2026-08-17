package org.fossify.musicplayer.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.content.res.use
import androidx.core.graphics.drawable.DrawableCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.helpers.HIGHER_ALPHA
import org.fossify.musicplayer.R
import com.google.android.material.R as MR

/**
 * One tile in the library shortcut row: a leading icon over a label, optionally backed by artwork.
 *
 * Both of those are drawn here rather than handed to the button, because MaterialButton owns them
 * on terms this tile cannot use. Its background is what drives the shape, the ripple and the width
 * animation the button group runs on press, so artwork given to it as a background would make it
 * disown all of that; and an icon in its own slot is pinned to the horizontal centre, since it is
 * laid out as a compound drawable and only [android.widget.TextView] gravity moves the label. So
 * the artwork is drawn beneath the container and the icon above it, both clipped by the button's
 * outline, leaving every expressive behaviour intact and following each corner morph for free.
 */
class LibraryShortcutButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = MR.attr.materialButtonStyle
) : RippleFixMaterialButton(context, attrs, defStyleAttr) {
    private val iconSize = resources.getDimensionPixelSize(R.dimen.size_library_shortcut_icon)

    private val shortcutIcon = context.obtainStyledAttributes(attrs, R.styleable.LibraryShortcutButton)
        .use { it.getDrawable(R.styleable.LibraryShortcutButton_shortcutIcon) }
        ?.mutate()

    private var cover: Drawable? = null
    private var containerColor = Color.TRANSPARENT

    private val coverTarget = object : CustomViewTarget<LibraryShortcutButton, Drawable>(this) {
        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) = setCover(resource)

        override fun onLoadFailed(errorDrawable: Drawable?) = setCover(null)

        override fun onResourceCleared(placeholder: Drawable?) = setCover(null)
    }

    init {
        clipToOutline = true
    }

    /** Show the artwork at [coverArt] behind the container, or nothing at all when it is null. */
    fun bind(coverArt: Any?) {
        Glide.with(this)
            .load(coverArt)
            .centerCrop()
            .into(coverTarget)
    }

    /**
     * Paint the container [color], dimming it to a scrim while artwork is showing through it.
     *
     * The dimming lives here rather than at the call site so the container and the cover can never
     * disagree, whichever of the two lands first.
     */
    fun setContainerColor(color: Int) {
        containerColor = color
        updateContainerTint()
    }

    override fun setIconTint(tint: ColorStateList?) {
        super.setIconTint(tint)
        // Called from the MaterialButton constructor, before this tile has read its own icon.
        shortcutIcon?.let { DrawableCompat.setTintList(it, tint) }
    }

    override fun draw(canvas: Canvas) {
        cover?.let {
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
        }

        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The label is laid out along the bottom of the content box by gravity, which leaves the
        // top of it free for the icon, both starting at the same edge. Placed on each draw rather
        // than on layout, since the group shifts the padding around while the button is pressed.
        shortcutIcon?.let {
            it.setBounds(paddingStart, paddingTop, paddingStart + iconSize, paddingTop + iconSize)
            it.draw(canvas)
        }
    }

    private fun setCover(cover: Drawable?) {
        if (this.cover == cover) {
            return
        }

        this.cover = cover
        updateContainerTint()
        invalidate()
    }

    private fun updateContainerTint() {
        val tint = if (cover == null) containerColor else containerColor.adjustAlpha(HIGHER_ALPHA)
        backgroundTintList = ColorStateList.valueOf(tint)
    }
}
