package org.fossify.musicplayer.screens.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.musicplayer.theme.applyOpacity
import org.fossify.musicplayer.theme.preferenceTitle

private const val HORIZONTAL = 8
private const val VERTICAL = 16

/**
 * A settings row: a title, and under it the setting's current value or a sentence explaining it.
 *
 * The value belongs under the title rather than opposite it because it is what the title resolves
 * to, and a row reads as one thought that way whether the value is a word or a sentence.
 */
@Composable
fun PreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Surface(modifier = modifier.clickable(enabled = enabled, onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HORIZONTAL.dp, VERTICAL.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                PreferenceItemTitle(text = title, enabled = enabled)
                if (!description.isNullOrEmpty()) {
                    PreferenceItemDescription(text = description, enabled = enabled)
                }
            }
        }
    }
}

/** A settings row that is a toggle; the whole row is the target, not just the switch. */
@Composable
fun PreferenceSwitch(
    title: String,
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val thumbContent: (@Composable () -> Unit)? = remember(isChecked) {
        if (isChecked) {
            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.toggleable(
            value = isChecked,
            enabled = enabled,
            onValueChange = { onClick() },
            indication = LocalIndication.current,
            interactionSource = interactionSource,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HORIZONTAL.dp, VERTICAL.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PreferenceItemTitle(text = title, enabled = enabled)
                if (!description.isNullOrEmpty()) {
                    PreferenceItemDescription(text = description, enabled = enabled)
                }
            }
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                interactionSource = interactionSource,
                modifier = Modifier.padding(start = 20.dp, end = 6.dp),
                enabled = enabled,
                thumbContent = thumbContent,
            )
        }
    }
}

/** A section heading, in the accent colour - the same grouping the native settings screen used. */
@Composable
fun PreferenceSubtitle(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 24.dp, bottom = 12.dp),
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PreferenceItemTitle(text: String, enabled: Boolean) {
    Text(
        text = text,
        maxLines = 2,
        style = preferenceTitle,
        color = MaterialTheme.colorScheme.onBackground.applyOpacity(enabled),
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreferenceItemDescription(text: String, enabled: Boolean) {
    Text(
        modifier = Modifier.padding(top = 2.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(enabled),
        overflow = TextOverflow.Ellipsis,
    )
}
