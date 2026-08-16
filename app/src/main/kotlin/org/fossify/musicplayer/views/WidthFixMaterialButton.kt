package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.google.android.material.R as MR

/**
 * [RippleFixMaterialButton] that works around another bug where switching the icon during a press
 * breaks width expansion animations.
 */
open class WidthFixMaterialButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = MR.attr.materialButtonStyle
) : RippleFixMaterialButton(context, attrs, defStyleAttr) {
    private var pendingIconRes: Int? = null
    private var appliedIconRes: Int? = null

    private val applyPendingIconRunnable = object : Runnable {
        override fun run() {
            val iconRes = pendingIconRes ?: return
            if (isPressed) {
                postOnAnimation(this)
                return
            }
            if (appliedIconRes != iconRes) {
                super@WidthFixMaterialButton.setIconResource(iconRes)
                appliedIconRes = iconRes
            }
            pendingIconRes = null
        }
    }

    override fun setIconResource(@DrawableRes iconRes: Int) {
        super.setIconResource(iconRes)
        pendingIconRes = iconRes
        removeCallbacks(applyPendingIconRunnable)
        postOnAnimation(applyPendingIconRunnable)
    }

    fun clearPendingIcon() {
        removeCallbacks(applyPendingIconRunnable)
        pendingIconRes = null
        appliedIconRes = null
    }
}
