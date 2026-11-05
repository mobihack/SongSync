@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.init.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitScreenTopBar() {
    LargeFlexibleTopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.init_screen_title)
            )
        }
    )
}