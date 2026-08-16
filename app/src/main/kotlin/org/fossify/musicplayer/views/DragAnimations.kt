package org.fossify.musicplayer.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.motion.MotionUtils
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.R as MR

private const val MIN_VISIBLE_CHANGE_DRAWABLE_ALPHA = 1f

/** The Material spring used for movement, such as the lift a queue item gets while being dragged. */
class Spatial private constructor(@AttrRes val attr: Int, @StyleRes val defaultStyle: Int) {
    private fun resolve(context: Context) = MotionUtils.resolveThemeSpringForce(context, attr, defaultStyle)

    fun translateZ(view: View, to: Float): SpringAnimation {
        val from = view.translationZ
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = resolve(view.context)
            setStartValue(from)
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
            addUpdateListener { _, value, _ -> view.translationZ = value }
            addEndListener { _, canceled, value, _ -> view.translationZ = if (!canceled) to else value }
            animateToFinalPosition(to)
        }
    }

    fun elevation(context: Context, drawable: MaterialShapeDrawable, to: Float): SpringAnimation {
        val from = drawable.elevation
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = resolve(context)
            setStartValue(from)
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
            addUpdateListener { _, value, _ -> drawable.elevation = value }
            addEndListener { _, canceled, value, _ -> drawable.elevation = if (!canceled) to else value }
            animateToFinalPosition(to)
        }
    }

    fun corners(context: Context, drawable: MaterialShapeDrawable, to: Float): SpringAnimation {
        val from = drawable.topRightCornerResolvedSize
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = resolve(context)
            setStartValue(from)
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
            addUpdateListener { _, value, _ -> drawable.setCornerSize(value) }
            addEndListener { _, canceled, value, _ -> drawable.setCornerSize(if (!canceled) to else value) }
            animateToFinalPosition(to)
        }
    }

    companion object {
        val DEFAULT = Spatial(
            MR.attr.motionSpringDefaultSpatial,
            MR.style.Motion_Material3_Spring_Standard_Default_Spatial
        )
    }
}

/** The Material spring used for fades, such as the lift scrim behind a dragged queue item. */
class Effect private constructor(@AttrRes val attr: Int, @StyleRes val defaultStyle: Int) {
    private fun resolve(context: Context) = MotionUtils.resolveThemeSpringForce(context, attr, defaultStyle)

    fun alpha(context: Context, drawable: Drawable, to: Int): SpringAnimation {
        val from = drawable.alpha.toFloat()
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = resolve(context)
            setStartValue(from)
            setMinimumVisibleChange(MIN_VISIBLE_CHANGE_DRAWABLE_ALPHA)
            addUpdateListener { _, value, _ -> drawable.alpha = value.toInt() }
            addEndListener { _, canceled, value, _ ->
                drawable.alpha = if (!canceled) to else value.toInt()
            }
            animateToFinalPosition(to.toFloat())
        }
    }

    companion object {
        val DEFAULT = Effect(
            MR.attr.motionSpringDefaultEffects,
            MR.style.Motion_Material3_Spring_Standard_Default_Effects
        )
    }
}
