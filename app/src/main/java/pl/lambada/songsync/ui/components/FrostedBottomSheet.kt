package pl.lambada.songsync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * A modal bottom sheet frosted like the floating controls ([frosted]): their tint over a blur of
 * what [hazeState] marks as its source (the screen behind). Without a [hazeState], a solid tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostedBottomSheet(
    onDismissRequest: () -> Unit,
    hazeState: HazeState?,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    // The frosting draws the sheet; its own fill and elevation tint would hide it
    containerColor = Color.Transparent,
    tonalElevation = 0.dp,
    // Drawn inside the frosted column below, so it sits on the frosting too
    dragHandle = null,
    // The frosting runs to the screen's bottom edge; each sheet pads its content above the
    // navigation bar itself
    contentWindowInsets = { WindowInsets(0) },
) {
    // Frosted here, not through the sheet's modifier: that one sits outside the offset that slides
    // the sheet up, so its clip and blur would stay where the sheet rests and hide the content
    Column(Modifier.fillMaxWidth().frosted(hazeState, BottomSheetDefaults.ExpandedShape)) {
        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
        content()
    }
}
