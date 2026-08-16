package org.fossify.musicplayer.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.fossify.musicplayer.R

/**
 * A track's cover art, which doubles as the playing indicator for the row it sits in.
 *
 * Ported from Auxio's `CoverView`. Selecting the view — which the row does for whichever track the
 * player is on — hands the artwork over to an equalizer that runs while playback is ongoing and
 * rests while it is paused. A row whose track has already been played is dimmed instead.
 *
 * The cover, the equalizer and the fallback all share one shaped background, so the view occupies
 * exactly the same space and reads the same shape in every one of those states.
 */
class CoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val image = ImageView(context)

    private val indicator = ImageView(context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        ImageViewCompat.setImageTintList(this, ContextCompat.getColorStateList(context, R.color.sel_on_cover_bg))
    }

    private val playingDrawable =
        ContextCompat.getDrawable(context, R.drawable.ic_playing_indicator_24) as AnimationDrawable
    private val pausedDrawable = ContextCompat.getDrawable(context, R.drawable.ic_paused_indicator_24)!!

    /** Stands in for a track with no artwork of its own, matching Auxio's styled album glyph. */
    private val fallback = CoverFallbackDrawable(context)

    private val cornerRadius = resources.getDimension(R.dimen.corner_radius_small)

    // AnimatedVectorDrawable cannot be placed in a StyledDrawable, we must replicate the
    // behavior with a matrix.
    private val indicatorMatrix = Matrix()
    private val indicatorMatrixSrc = RectF()
    private val indicatorMatrixDst = RectF()

    init {
        background = MaterialShapeDrawable().apply {
            fillColor = ContextCompat.getColorStateList(context, R.color.sel_cover_bg)
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(cornerRadius).build()
        }
        clipToOutline = true
        setPlaying(false)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // The indicator is added last so it sits above the artwork and can take its place.
        addView(image)
        addView(indicator)
    }

    /** Show [coverArt], or the fallback glyph when the track carries none. */
    fun bind(coverArt: Any?) {
        Glide.with(this)
            .load(coverArt)
            .apply(
                RequestOptions()
                    .error(fallback)
                    .transform(CenterCrop(), RoundedCorners(cornerRadius.toInt()))
            )
            .into(image)
    }

    /**
     * Set if the playback indicator should be indicated ongoing or paused playback.
     *
     * @param isPlaying Whether playback is ongoing or paused.
     */
    fun setPlaying(isPlaying: Boolean) {
        if (isPlaying) {
            playingDrawable.start()
            indicator.setImageDrawable(playingDrawable)
        } else {
            playingDrawable.stop()
            indicator.setImageDrawable(pausedDrawable)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val iconSize = measuredWidth / 2
        indicator.imageMatrix = indicatorMatrix.apply {
            reset()
            indicator.drawable?.let { drawable ->
                // First scale the icon up to the desired size.
                indicatorMatrixSrc.set(
                    0f,
                    0f,
                    drawable.intrinsicWidth.toFloat(),
                    drawable.intrinsicHeight.toFloat()
                )
                indicatorMatrixDst.set(0f, 0f, iconSize.toFloat(), iconSize.toFloat())
                indicatorMatrix.setRectToRect(indicatorMatrixSrc, indicatorMatrixDst, Matrix.ScaleToFit.CENTER)

                // Then actually center it into the icon.
                indicatorMatrix.postTranslate(
                    (measuredWidth - iconSize) / 2f,
                    (measuredHeight - iconSize) / 2f
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidateRootAlpha()
        invalidatePlaybackIndicatorAlpha()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        invalidateRootAlpha()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidateRootAlpha()
        invalidatePlaybackIndicatorAlpha()
    }

    private fun invalidateRootAlpha() {
        alpha = if (isEnabled || isSelected) 1f else 0.5f
    }

    private fun invalidatePlaybackIndicatorAlpha() {
        // Occasionally content can bleed through the rounded corners and result in a seam
        // on the playing indicator, prevent that from occurring by disabling the visibility of
        // all views below the playback indicator.
        for (child in children) {
            child.alpha = when (child) {
                indicator -> if (isSelected) 1f else 0f
                else -> if (isSelected) 0f else 1f
            }
        }
    }
}

/**
 * Draws [R.drawable.ic_album_24] centred at half the cover's size rather than stretched over it,
 * so a track with no artwork still reads as a cover. Ported from Auxio's `StyledDrawable`.
 */
internal class CoverFallbackDrawable(context: Context) : Drawable() {
    // The icons we use might actually be shared and in that case we want to duplicate and
    // tint rather than tinting a global drawable
    private val inner: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_album_24)!!
        .let { (it.constantState?.newDrawable() ?: it).mutate() }

    init {
        // Re-tint the drawable to use the analogous "on surface" color for the cover background.
        DrawableCompat.setTintList(inner, ContextCompat.getColorStateList(context, R.color.sel_on_cover_bg))
    }

    override fun draw(canvas: Canvas) {
        // Resize the drawable such that it's always 1/4 the size of the image and
        // centered in the middle of the canvas.
        val adj = bounds.width() / 4
        inner.bounds.set(adj, adj, bounds.width() - adj, bounds.height() - adj)
        inner.draw(canvas)
    }

    // Required drawable overrides. Just forward to the wrapped drawable.

    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable, but still required to be implemented.")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
