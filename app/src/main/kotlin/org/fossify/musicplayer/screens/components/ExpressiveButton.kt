package org.fossify.musicplayer.screens.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A press-morphing button in the Material 3 Expressive style: its corner radius eases toward a
 * near-pill shape while held, and settles back to [restCornerRadius] on release.
 *
 * Ported from ArchiveTune's `V9AnimatedPlaybackControls.kt` mechanism - a hand-rolled `Box` with
 * its own `clickable`, not `material3.Button`, since the shape morph needs to drive the clipped
 * shape directly. ArchiveTune drives the morph with `MotionScheme.expressive()`; this project's
 * current Material3 version doesn't expose that publicly (see [org.fossify.musicplayer.theme.AppTheme]'s
 * doc), so a hand-tuned low-stiffness [spring] stands in for it - swap this for the real expressive
 * motion spec once the app can move to the Material3 version that has it. Generalized here into
 * one reusable button (ArchiveTune's version is a row of three playback controls morphing each
 * other's width, which this app has no equivalent of yet).
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = contentColorFor(containerColor),
    restCornerRadius: Dp = 16.dp,
    pressedCornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) pressedCornerRadius else restCornerRadius,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cornerRadius",
    )
    val background by animateColorAsState(containerColor, label = "containerColor")

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
