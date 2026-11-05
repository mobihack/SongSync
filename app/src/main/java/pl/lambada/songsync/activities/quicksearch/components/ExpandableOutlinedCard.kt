@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.activities.quicksearch.components

import androidx.compose.material3.Card
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R

@Composable
fun ExpandableOutlinedCard(
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(isExpanded) }

    val animatedDegree = animateFloatAsState(
        targetValue = if (expanded) 0f else -180f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Button Rotation",
    )

    Card(
        modifier = modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.weight(0.1f),
                imageVector = icon,
                contentDescription = stringResource(R.string.song_lyrics),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
            FilledTonalIconButton(
                onClick = { expanded = !expanded },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.rotate(animatedDegree.value)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
private fun ExpandableElevatedCardPreview() {
    ExpandableOutlinedCard(
        title = "Title", subtitle = "Subtitle", content = {
            Text(text = "Content")
        }, icon = Icons.Outlined.PermDeviceInformation
    )
}