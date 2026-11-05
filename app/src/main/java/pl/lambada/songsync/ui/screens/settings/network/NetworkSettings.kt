@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.settings.network

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.ListGroup
import pl.lambada.songsync.ui.components.ListGroupMargin
import pl.lambada.songsync.ui.components.RadioItem
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.components.listGroupColors
import pl.lambada.songsync.ui.components.listGroupContainerColor
import pl.lambada.songsync.ui.components.listGroupItems
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.AppProxy
import pl.lambada.songsync.util.networking.ProxyMode
import pl.lambada.songsync.util.networking.ProxyScope
import pl.lambada.songsync.util.networking.ProxySettings
import pl.lambada.songsync.util.networking.ProxyType

private val PlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Sections come and go with the mode; fade them rather than letting the list jump. */
private fun Modifier.sectionItem(scope: LazyItemScope) = with(scope) {
    this@sectionItem.animateItem(fadeInSpec = tween(150), placementSpec = PlacementSpec, fadeOutSpec = tween(100))
}

/** The Network settings page: proxy mode, server, what it's used for, and a connection test. */
fun LazyListScope.network(settings: UserSettingsController) {
    val proxy = settings.proxySettings
    fun update(to: ProxySettings) = settings.updateProxySettings(to)
    // Typed text: applied once typing pauses, not on every keystroke
    fun updateTyped(to: ProxySettings) = settings.updateProxySettings(to, debounce = true)

    item(key = "proxyHeader") { Box(Modifier.sectionItem(this)) { SettingsHeadLabel(stringResource(R.string.proxy)) } }
    item(key = "proxyModes") {
        ListGroup(Modifier.sectionItem(this)) {
            row {
                RadioItem(
                    label = stringResource(R.string.proxy_off),
                    description = stringResource(R.string.proxy_off_summary),
                    selected = proxy.mode == ProxyMode.OFF,
                    shapes = it,
                    onClick = { update(proxy.copy(mode = ProxyMode.OFF)) },
                )
            }
            row {
                RadioItem(
                    label = stringResource(R.string.proxy_system),
                    description = stringResource(R.string.proxy_system_summary),
                    selected = proxy.mode == ProxyMode.SYSTEM,
                    shapes = it,
                    onClick = { update(proxy.copy(mode = ProxyMode.SYSTEM)) },
                )
            }
            row {
                RadioItem(
                    label = stringResource(R.string.proxy_manual),
                    description = stringResource(R.string.proxy_manual_summary),
                    selected = proxy.mode == ProxyMode.MANUAL,
                    shapes = it,
                    onClick = { update(proxy.copy(mode = ProxyMode.MANUAL)) },
                )
            }
        }
    }

    if (proxy.mode == ProxyMode.MANUAL) {
        item(key = "serverHeader") {
            Box(Modifier.sectionItem(this)) { SettingsHeadLabel(stringResource(R.string.proxy_server)) }
        }
        item(key = "server") { ProxyServerFields(proxy, ::update, ::updateTyped, Modifier.sectionItem(this)) }
    }

    if (proxy.mode == ProxyMode.OFF) return

    item(key = "test") { ProxyTest(proxy, Modifier.sectionItem(this)) }

    item(key = "scopeHeader") {
        Box(Modifier.sectionItem(this)) { SettingsHeadLabel(stringResource(R.string.proxy_use_for)) }
    }
    item(key = "scope") {
        ListGroup(Modifier.sectionItem(this)) {
            row {
                RadioItem(
                    label = stringResource(R.string.proxy_scope_all),
                    description = stringResource(R.string.proxy_scope_all_summary),
                    selected = proxy.scope == ProxyScope.ALL,
                    shapes = it,
                    onClick = { update(proxy.copy(scope = ProxyScope.ALL)) },
                )
            }
            row {
                RadioItem(
                    label = stringResource(R.string.proxy_scope_chosen),
                    description = stringResource(R.string.proxy_scope_chosen_summary),
                    selected = proxy.scope == ProxyScope.CHOSEN,
                    shapes = it,
                    onClick = { update(proxy.copy(scope = ProxyScope.CHOSEN)) },
                )
            }
        }
    }

    if (proxy.scope != ProxyScope.CHOSEN) return

    item(key = "providersHeader") {
        Box(Modifier.sectionItem(this)) { SettingsHeadLabel(stringResource(R.string.proxy_providers)) }
    }
    // Same providers and order as the provider menus
    listGroupItems(Providers.entries.filter { it.enabled }, key = { "proxy:${it.name}" }) { provider, shapes, padding ->
        val checked = provider in proxy.providers
        SegmentedListItem(
            checked = checked,
            onCheckedChange = {
                update(proxy.copy(providers = if (it) proxy.providers + provider else proxy.providers - provider))
            },
            shapes = shapes,
            colors = listGroupColors(),
            modifier = Modifier.sectionItem(this).padding(padding),
            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        ) {
            Text(provider.displayName)
        }
    }
}

@Composable
private fun ProxyServerFields(
    proxy: ProxySettings,
    update: (ProxySettings) -> Unit,
    updateTyped: (ProxySettings) -> Unit,
    modifier: Modifier,
) {
    // Typed text is kept as is; the settings get the parsed value, so "" and "0" both read as invalid
    var portText by remember { mutableStateOf(if (proxy.port == 0) "" else proxy.port.toString()) }
    var showPassword by remember { mutableStateOf(false) }
    val hostValid = ProxySettings.isValidHost(proxy.host)
    val portValid = ProxySettings.isValidPort(proxy.port)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ListGroupMargin)
            .clip(MaterialTheme.shapes.large)
            .background(listGroupContainerColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val types = listOf(ProxyType.HTTP to R.string.proxy_type_http, ProxyType.SOCKS to R.string.proxy_type_socks)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = proxy.type == type,
                    onClick = { update(proxy.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                ) { Text(stringResource(label)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = proxy.host,
                onValueChange = { updateTyped(proxy.copy(host = it.trim())) },
                label = { Text(stringResource(R.string.proxy_host)) },
                placeholder = { Text("127.0.0.1") },
                singleLine = true,
                isError = !hostValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { text ->
                    val digits = text.filter { it.isDigit() }.take(5)
                    portText = digits
                    updateTyped(proxy.copy(port = digits.toIntOrNull() ?: 0))
                },
                label = { Text(stringResource(R.string.proxy_port)) },
                singleLine = true,
                isError = !portValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(104.dp),
            )
        }
        when {
            !hostValid -> ErrorText(stringResource(R.string.proxy_host_invalid))
            !portValid -> ErrorText(stringResource(R.string.proxy_port_invalid))
        }
        // OkHttp can't authenticate to a SOCKS proxy, so credentials are HTTP only
        if (proxy.type == ProxyType.HTTP) {
            OutlinedTextField(
                value = proxy.username,
                onValueChange = { updateTyped(proxy.copy(username = it)) },
                label = { Text(stringResource(R.string.proxy_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (proxy.username.isNotBlank()) OutlinedTextField(
                value = proxy.password,
                onValueChange = { updateTyped(proxy.copy(password = it)) },
                label = { Text(stringResource(R.string.proxy_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (showPassword) R.string.proxy_hide_password else R.string.proxy_show_password
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!proxy.isManualValid) Text(
            text = stringResource(R.string.proxy_invalid_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorText(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
)

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val millis: Long) : TestState
    data class Failed(val reason: String) : TestState
}

@Composable
private fun ProxyTest(proxy: ProxySettings, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }
    // A result is about the settings it was run with
    var testedWith by remember { mutableStateOf<ProxySettings?>(null) }
    if (testedWith != null && testedWith != proxy && state !is TestState.Running) state = TestState.Idle

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ListGroupMargin, end = ListGroupMargin, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilledTonalButton(
            enabled = state !is TestState.Running && (proxy.mode != ProxyMode.MANUAL || proxy.isManualValid),
            onClick = {
                state = TestState.Running
                testedWith = proxy
                scope.launch {
                    val result = withContext(Dispatchers.IO) { AppProxy.test(proxy) }
                    state = result.fold(
                        onSuccess = { TestState.Ok(it) },
                        onFailure = { TestState.Failed(it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName) },
                    )
                }
            },
        ) { Text(stringResource(R.string.proxy_test)) }

        when (val s = state) {
            TestState.Idle -> Unit
            TestState.Running -> StatusText(stringResource(R.string.proxy_test_running))
            is TestState.Ok -> StatusText(stringResource(R.string.proxy_test_ok, s.millis.toInt()))
            is TestState.Failed -> StatusText(stringResource(R.string.proxy_test_failed, s.reason), error = true)
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
)

/** One line for the Settings hub: "Direct connection", "System proxy", "SOCKS5 · 127.0.0.1:1080". */
@Composable
fun networkSummary(settings: UserSettingsController): String {
    val proxy = settings.proxySettings
    val base = when (proxy.mode) {
        ProxyMode.OFF -> return stringResource(R.string.network_summary_off)
        ProxyMode.SYSTEM -> stringResource(R.string.network_summary_system)
        ProxyMode.MANUAL -> stringResource(
            R.string.network_summary_manual,
            stringResource(if (proxy.type == ProxyType.HTTP) R.string.proxy_type_http else R.string.proxy_type_socks),
            "${proxy.host}:${proxy.port}",
        )
    }
    if (proxy.scope == ProxyScope.ALL) return base
    val count = proxy.providers.count { it.enabled }
    return pluralStringResource(R.plurals.network_summary_chosen, count, base, count)
}
