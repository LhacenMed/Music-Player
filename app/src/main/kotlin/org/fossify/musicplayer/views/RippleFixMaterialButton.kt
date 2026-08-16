package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.R as MR

/**
 * Fixes an issue where double ripples appear on [MaterialButton] from AppCompat 1.5 onwards due to
 * a currently unfixed change.
 */
open class RippleFixMaterialButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = MR.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    init {
        // Cosmetic only, so a minified build that has renamed the field simply keeps the stock
        // (double-drawn) ripple rather than failing to inflate.
        runCatching {
            AppCompatButton::class.java.getDeclaredField("mBackgroundTintHelper").apply {
                isAccessible = true
                set(this@RippleFixMaterialButton, null)
            }
        }
    }
}
