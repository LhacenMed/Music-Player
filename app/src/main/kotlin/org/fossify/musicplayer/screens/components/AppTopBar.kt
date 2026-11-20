package org.fossify.musicplayer.screens.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's own top bar, small or large, in place of Commons' XML `MaterialToolbar` - so a Compose
 * screen's bar is part of the same composition as its content instead of a separately painted View
 * next to it (see [org.fossify.musicplayer.theme.AppTheme]'s doc for why that mismatch mattered).
 *
 * Both are thin wrappers around the Material3 composables of the same name, ported from Seal's
 * `ui/component/TopAppBar.kt`: a plain small bar and a large bar that collapses into it, sharing
 * one [TopAppBarScrollBehavior] with [Modifier.nestedScroll] on the screen's `Scaffold`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    androidx.compose.material3.TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    androidx.compose.material3.LargeTopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/** A plain back arrow that calls [onBack] - the default [SmallTopAppBar]/[LargeTopAppBar] icon. */
@Composable
fun BackNavigationIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    }
}

/**
 * A screen's top bar while a multi-select is active: title becomes the selected count, the
 * navigation icon becomes a close button that clears the selection, and [actions] take over the
 * trailing slot - modeled on Seal's `PlaylistSelectionPage.kt` (title/nav-icon swap driven by
 * whether anything is selected, rather than a separate contextual bar).
 *
 * Selection state itself (which keys are selected) is owned by the screen; this only renders the
 * bar for it, so any screen with a selectable list can share it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        modifier = modifier,
        title = { Text(selectedCount.toString()) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = null)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
