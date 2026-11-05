@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.lyricsFetch.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.CommonTextField


@Composable
fun NotSubmittedContent(
    querySong: String,
    onQuerySongChange: (String) -> Unit,
    queryArtist: String,
    onQueryArtistChange: (String) -> Unit,
    onGetLyricsRequest: () -> Unit,
    /** Name of the current provider choice, shown under the button. */
    providerLabel: String,
    providerMenuExpanded: Boolean,
    onProviderMenuRequest: () -> Unit,
    /** The provider menu, anchored to the button's trailing part. */
    providerMenu: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        CommonTextField(
            value = querySong,
            onValueChange = onQuerySongChange,
            label = stringResource(id = R.string.song_name_no_args),
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        CommonTextField(
            value = queryArtist,
            onValueChange = onQueryArtistChange,
            label = stringResource(R.string.artist_name_no_args),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        // Search with the current provider, or open the provider menu from the ▾
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onGetLyricsRequest,
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.heightIn(min = SplitButtonDefaults.MediumContainerHeight),
                ) {
                    Text(
                        text = stringResource(id = R.string.get_lyrics),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            },
            trailingButton = {
                Box {
                    providerMenu()
                    SplitButtonDefaults.TrailingButton(
                        checked = providerMenuExpanded,
                        onCheckedChange = { onProviderMenuRequest() },
                        shapes = SplitButtonDefaults.trailingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                        contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                        modifier = Modifier
                            .heightIn(min = SplitButtonDefaults.MediumContainerHeight)
                            .semantics { stateDescription = providerLabel },
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (providerMenuExpanded) 180f else 0f,
                            // No overshoot: the chevron shows state
                            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                            label = "providerChevron",
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.provider),
                            modifier = Modifier
                                .size(SplitButtonDefaults.MediumTrailingButtonIconSize)
                                .graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = providerLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
