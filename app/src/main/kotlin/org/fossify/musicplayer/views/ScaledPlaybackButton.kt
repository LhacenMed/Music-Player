package org.fossify.musicplayer.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.AttrRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.AbsoluteCornerSize
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearance
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.StateListShapeAppearanceModel
import org.fossify.musicplayer.extensions.lazyReflectedMethodOrNull
import java.lang.reflect.Method
import kotlin.math.max
import com.google.android.material.R as MR

/** Companion scalable button to [ScaledPlaybackButtonGroup]. */
@SuppressLint("RestrictedApi")
class ScaledPlaybackButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = MR.attr.materialButtonStyle
) : WidthFixMaterialButton(context, attrs, defStyleAttr) {
    private val baseMetrics: BaseMetrics
    private var lastScale: Float? = null

    init {
        // We need to capture the button's original state without any transforms so that our
        // scaling applies to the correct dimensions.
        val contentWidth = paddingStart + paddingEnd + iconSize
        val contentHeight = paddingTop + paddingBottom + iconSize
        baseMetrics = BaseMetrics(
            width = max(minimumWidth, contentWidth),
            height = max(minimumHeight, contentHeight),
            minimumWidth = minimumWidth,
            minimumHeight = minimumHeight,
            paddingStart = paddingStart,
            paddingTop = paddingTop,
            paddingEnd = paddingEnd,
            paddingBottom = paddingBottom,
            iconSize = iconSize,
            strokeWidth = strokeWidth,
            shapeAppearance = shapeAppearance
        )
    }

    val baseWidth: Int get() = baseMetrics.width

    val baseHeight: Int get() = baseMetrics.height

    fun applyPlaybackScale(scale: Float) {
        val scaledWidth = baseMetrics.width.scale(scale)
        val scaledHeight = baseMetrics.height.scale(scale)
        // MaterialButtonGroup mutates child widths while pressed so neighbors can shrink/grow.
        // Only rewrite the baseline when the scale itself changes.
        if (lastScale == scale) {
            return
        }
        lastScale = scale

        METHOD_RECOVER_ORIG_PARAMS?.invoke(this)
        val recoveredParams = layoutParams
        if (recoveredParams != null) {
            recoveredParams.width = scaledWidth
            recoveredParams.height = scaledHeight
        } else {
            layoutParams = ViewGroup.LayoutParams(scaledWidth, scaledHeight)
        }

        minimumWidth = minOf(baseMetrics.minimumWidth.scale(scale), scaledWidth)
        minimumHeight = minOf(baseMetrics.minimumHeight.scale(scale), scaledHeight)

        setPaddingRelative(
            baseMetrics.paddingStart.scale(scale),
            baseMetrics.paddingTop.scale(scale),
            baseMetrics.paddingEnd.scale(scale),
            baseMetrics.paddingBottom.scale(scale)
        )

        iconSize = baseMetrics.iconSize.scale(scale)

        // A bit inaccurate (strokes actually step from 3, 2, 1) but better to interpolate honestly.
        strokeWidth = if (baseMetrics.strokeWidth > 0) max(1, baseMetrics.strokeWidth.scale(scale)) else 0

        shapeAppearance = baseMetrics.shapeAppearance.scale(scale)
    }

    private fun Int.scale(scale: Float) = (this * scale).toInt()

    private fun ShapeAppearance.scale(scale: Float): ShapeAppearance = when (this) {
        is StateListShapeAppearanceModel -> withTransformedCornerSizes { it.scale(scale) }
        is ShapeAppearanceModel -> withTransformedCornerSizes { it.scale(scale) }
        else -> this
    }

    private fun CornerSize.scale(scale: Float): CornerSize = when (this) {
        is RelativeCornerSize -> RelativeCornerSize(relativePercent * scale)
        is AbsoluteCornerSize -> AbsoluteCornerSize(cornerSize * scale)
        else -> CornerSize { bounds -> getCornerSize(bounds) * scale }
    }

    private data class BaseMetrics(
        val width: Int,
        val height: Int,
        val minimumWidth: Int,
        val minimumHeight: Int,
        val paddingStart: Int,
        val paddingTop: Int,
        val paddingEnd: Int,
        val paddingBottom: Int,
        val iconSize: Int,
        val strokeWidth: Int,
        val shapeAppearance: ShapeAppearance
    )

    private companion object {
        val METHOD_RECOVER_ORIG_PARAMS: Method? by
        lazyReflectedMethodOrNull(MaterialButton::class, "recoverOriginalLayoutParams")
    }
}
