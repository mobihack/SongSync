package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.util.ext.BackPressHandler

@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onShowSearchChange: (Boolean) -> Unit,
    showingSearch: Boolean,
    onShowingSearchChange: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var willShowIme by remember { mutableStateOf(false) }

    @OptIn(ExperimentalLayoutApi::class)
    val showingIme = WindowInsets.isImeVisible
    // Where the keyboard is heading: drops to 0 as soon as it starts closing, while
    // isImeVisible stays true until it has finished sliding away
    @OptIn(ExperimentalLayoutApi::class)
    val imeHeading = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0

    if (!showingSearch && showSearch) {
        onShowingSearchChange(true)
    }

    // Collapse alongside the closing keyboard, not after it
    if (!showSearch && !willShowIme && showingSearch && !imeHeading && query.isEmpty()) {
        onShowingSearchChange(false)
    }

    if (willShowIme && imeHeading) {
        willShowIme = false
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Each tap on the search button focuses the field with the keyboard up. That includes the bar
    // already being open with a query after the keyboard was closed: the field can still hold
    // focus then, so focusing it alone wouldn't bring the keyboard back.
    LaunchedEffect(showSearch) {
        if (showSearch) {
            willShowIme = true // don't collapse an empty bar before the keyboard starts opening
            focusRequester.requestFocus()
            keyboard?.show()
            onShowSearchChange(false)
        }
    }

    BackPressHandler(
        enabled = showingSearch,
        onBackPressed = {
            onQueryChange("")
            onShowSearchChange(false)
            onShowingSearchChange(false)
        }
    )

    TextField(
        query,
        onValueChange = onQueryChange,
        // IconButtons: a 48dp touch target and a button role for TalkBack
        leadingIcon = {
            IconButton(onClick = {
                onShowSearchChange(false)
                onShowingSearchChange(false)
            }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(id = R.string.search))
            }
        },
        trailingIcon = {
            IconButton(onClick = {
                onQueryChange("")
                onShowSearchChange(false)
                onShowingSearchChange(false)
            }) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(id = R.string.clear))
            }
        },
        placeholder = { Text(stringResource(id = R.string.search)) },
        shape = ShapeDefaults.ExtraLarge,
        colors = TextFieldDefaults.colors(
            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            // Clear of the app bar, which turns frosted once the list scrolls
            .padding(top = 8.dp)
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused && !showingIme) willShowIme = true
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() }
        )
    )
}