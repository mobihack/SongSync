@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

/**
 * Top bar for screens opened from Settings: back arrow and a title that collapses on scroll.
 * The caller connects [scrollBehavior] to its list with `Modifier.nestedScroll`.
 */
@Composable
fun SubScreenTopBar(title: String, scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MediumFlexibleTopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
    )
}
