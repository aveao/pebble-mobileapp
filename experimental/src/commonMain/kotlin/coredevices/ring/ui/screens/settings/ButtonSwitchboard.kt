package coredevices.ring.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.theme.IndexTheme

private val DOT = 9.dp
private val TILE_WIDTH = 168.dp
private val LABEL_WIDTH = 106.dp
private val TILE_SHAPE = RoundedCornerShape(10.dp)

val RingGesture.gestureLabel: String
    get() = when (this) {
        RingGesture.Click -> "Click"
        RingGesture.DoubleClick -> "Double click"
        RingGesture.TripleClick -> "Triple click"
        RingGesture.Hold -> "Hold & Talk"
        RingGesture.ClickHold -> "Double click & hold"
    }

/** Press pattern drawn as one rounded segment per press: [DOT] wide is a click, wider is a hold. */
val RingGesture.glyph: List<Dp>
    get() = when (this) {
        RingGesture.Click -> listOf(DOT)
        RingGesture.DoubleClick -> listOf(DOT, DOT)
        RingGesture.TripleClick -> listOf(DOT, DOT, DOT)
        RingGesture.Hold -> listOf(38.dp)
        RingGesture.ClickHold -> listOf(DOT, 26.dp)
    }

/** An installed agent plugin offered as a recording destination; only a [connected] one can be picked,
 *  and a [configurable] one has a settings page to reopen once routed to. */
data class AgentPluginOption(
    val provider: IntegrationProvider,
    val connected: Boolean,
    val configurable: Boolean = false,
) {
    val destination: GestureDestination.AgentPlugin get() = GestureDestination.AgentPlugin(provider.id)
}

fun List<AgentPluginOption>.titles(): Map<String, String> = associate { it.provider.id to it.provider.title }

/** The plugin [destination] routes to, when it has settings to open. */
fun List<AgentPluginOption>.configurableFor(destination: GestureDestination): AgentPluginOption? =
    firstOrNull { it.configurable && it.destination == destination }

/** One row of the "Other agents" group in the destination sheet. */
sealed interface OtherAgentRow {
    /** An installed agent plugin; only a connected one can be picked, the rest offer Connect. */
    data class Installed(val option: AgentPluginOption) : OtherAgentRow
    /** The current route to a plugin that has since been uninstalled, kept so it can be changed. */
    data class Missing(val destination: GestureDestination.AgentPlugin) : OtherAgentRow
    /** A store plugin not installed yet; Connect installs it. */
    data class Store(val plugin: StorePlugin) : OtherAgentRow
}

/** Installed agent plugins first, then the store's, with [current] kept in between if its plugin is gone. */
fun otherAgentRows(
    current: GestureDestination,
    installed: List<AgentPluginOption>,
    store: List<StorePlugin>,
): List<OtherAgentRow> {
    val missing = (current as? GestureDestination.AgentPlugin)
        ?.takeIf { route -> installed.none { it.destination == route } }
    return installed.map { OtherAgentRow.Installed(it) } +
        listOfNotNull(missing?.let { OtherAgentRow.Missing(it) }) +
        store.map { OtherAgentRow.Store(it) }
}

/** Agent plugins are not listed here: the sheet groups them under "Other agents" after [GestureDestination.LocalAgent].
 *  [musicControl] is false where the platform can't control media; clicks can still go to a webhook or agent command. */
fun destinationsFor(
    kind: GestureKind,
    musicControl: Boolean = true,
): List<GestureDestination> = when (kind) {
    GestureKind.Music -> listOfNotNull(
        GestureDestination.PlayPause.takeIf { musicControl },
        GestureDestination.NextTrack.takeIf { musicControl },
        GestureDestination.PreviousTrack.takeIf { musicControl },
        GestureDestination.AgentCommand,
        GestureDestination.Webhook,
        GestureDestination.Nothing,
    )
    GestureKind.Recording -> listOf(
        GestureDestination.IndexAgent,
        GestureDestination.LocalAgent,
        GestureDestination.WebSearch,
        GestureDestination.WebhookOnly,
        GestureDestination.Nothing,
    )
}

/** True while the sheet still shows something to configure below the choice — the webhook row.
 *  Otherwise picking a destination is the whole interaction and the sheet closes itself.
 *  Mirrors what [GestureDestinationSheet] renders. */
internal fun gestureSheetStaysOpenFor(
    gesture: RingGesture,
    destination: GestureDestination,
): Boolean = destination == GestureDestination.Webhook ||
    destination == GestureDestination.AgentCommand ||
    (gesture.kind == GestureKind.Recording && destination != GestureDestination.Nothing)

/** [agentTitles] names agent plugins by provider id; a route to one no longer installed reads as unavailable. */
fun GestureDestination.tileLabel(agentTitles: Map<String, String>): String = when (this) {
    GestureDestination.PlayPause -> "Play/Pause"
    GestureDestination.NextTrack -> "Next track"
    GestureDestination.PreviousTrack -> "Previous track"
    GestureDestination.IndexAgent -> "Index agent"
    GestureDestination.LocalAgent -> "Local agent"
    GestureDestination.WebSearch -> "Web search"
    GestureDestination.WebhookOnly -> "Webhook only"
    GestureDestination.Webhook -> "Webhook"
    GestureDestination.AgentCommand -> "Agent command"
    is GestureDestination.AgentPlugin -> agentTitles[providerId] ?: "Unavailable agent"
    GestureDestination.Nothing -> "Nothing"
}

val GestureDestination.tileLabel: String get() = tileLabel(emptyMap())

private fun GestureDestination.optionLabel(agentTitles: Map<String, String>): String = when (this) {
    GestureDestination.PlayPause -> "Play or pause music"
    GestureDestination.NextTrack -> "Skip to the next track"
    GestureDestination.Webhook -> "Send to webhook"
    GestureDestination.AgentCommand -> "Run an Index agent command"
    GestureDestination.PreviousTrack -> "Go back to the last track"
    else -> tileLabel(agentTitles)
}

private val GestureDestination.optionSub: String?
    get() = when (this) {
        GestureDestination.IndexAgent -> "Takes actions for you in the cloud, on this phone when offline"
        GestureDestination.LocalAgent -> "Runs on this phone only. Experimental, supports fewer actions"
        GestureDestination.WebSearch -> "Answer lands in your feed"
        GestureDestination.WebhookOnly -> "Raw recording to your endpoint, nothing else"
        GestureDestination.Webhook -> "Click count and your payload to your endpoint"
        GestureDestination.AgentCommand -> "Sends a command, eg \"Unlock front door\""
        else -> null
    }

private val GestureDestination.icon: ImageVector
    get() = when (this) {
        GestureDestination.PlayPause -> Icons.Default.PlayArrow
        GestureDestination.NextTrack -> Icons.Default.SkipNext
        GestureDestination.PreviousTrack -> Icons.Default.SkipPrevious
        GestureDestination.IndexAgent -> Icons.Default.GraphicEq
        GestureDestination.LocalAgent -> Icons.Default.Smartphone
        GestureDestination.WebSearch -> Icons.Default.Search
        GestureDestination.WebhookOnly, GestureDestination.Webhook -> Icons.Default.Webhook
        GestureDestination.AgentCommand -> Icons.Default.Bolt
        is GestureDestination.AgentPlugin -> Icons.AutoMirrored.Filled.Send
        GestureDestination.Nothing -> Icons.Default.Block
    }

@Composable
internal fun ButtonSwitchboard(
    routes: Map<RingGesture, GestureDestination>,
    commands: Map<RingGesture, String>,
    musicEnabled: Boolean,
    webhookConfigFor: (RingGesture) -> IndexWebhookConfig,
    onSetRoute: (RingGesture, GestureDestination) -> Unit,
    onSetCommand: (RingGesture, String) -> Unit,
    onOpenWebhookSettings: (RingGesture) -> Unit,
    onDisableWebhook: (RingGesture) -> Unit,
    agentPlugins: List<AgentPluginOption> = emptyList(),
    storeAgentPlugins: List<StorePlugin> = emptyList(),
    onConnectAgentPlugin: (IntegrationProvider) -> Unit = {},
    onInstallAgentPlugin: (StorePlugin) -> Unit = {},
    onConfigureAgentPlugin: (IntegrationProvider) -> Unit = {},
) {
    var sheetGesture by remember { mutableStateOf<RingGesture?>(null) }
    val webhookCopies = { gesture: RingGesture -> webhookConfigFor(gesture).isActive }
    val agentTitles = agentPlugins.titles()

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RingGesture.entries.forEach { gesture ->
            val destination = routes[gesture] ?: GestureDestination.Nothing
            val inert = !musicEnabled && destination is GestureDestination.Music &&
                destination != GestureDestination.Webhook &&
                destination != GestureDestination.AgentCommand
            Column(modifier = Modifier.alpha(if (inert) 0.38f else 1f)) {
                GestureRow(
                    gesture = gesture,
                    destination = destination,
                    agentTitles = agentTitles,
                    command = commands[gesture].orEmpty(),
                    onEdit = { sheetGesture = gesture },
                    onConfigure = agentPlugins.configurableFor(destination)?.let { plugin ->
                        { onConfigureAgentPlugin(plugin.provider) }
                    },
                )
                if (gesture.kind == GestureKind.Recording &&
                    destination != GestureDestination.WebhookOnly &&
                    destination != GestureDestination.Nothing &&
                    webhookCopies(gesture)
                ) {
                    WebhookCopyRow(onClick = { onOpenWebhookSettings(gesture) })
                }
            }
        }
    }

    sheetGesture?.let { gesture ->
        GestureDestinationSheet(
            gesture = gesture,
            current = routes[gesture] ?: GestureDestination.Nothing,
            agentPlugins = agentPlugins,
            storeAgentPlugins = storeAgentPlugins,
            musicControl = musicEnabled,
            command = commands[gesture].orEmpty(),
            onSetCommand = { onSetCommand(gesture, it) },
            webhookUrl = webhookConfigFor(gesture).takeIf { it.isActive }?.url,
            webhookCopyOn = webhookCopies(gesture),
            onSelect = {
                onSetRoute(gesture, it)
                if (!gestureSheetStaysOpenFor(gesture, it)) sheetGesture = null
            },
            onConnectAgentPlugin = {
                sheetGesture = null
                onConnectAgentPlugin(it)
            },
            onInstallAgentPlugin = {
                sheetGesture = null
                onInstallAgentPlugin(it)
            },
            onConfigureAgentPlugin = {
                sheetGesture = null
                onConfigureAgentPlugin(it)
            },
            onOpenWebhookSettings = {
                sheetGesture = null
                onOpenWebhookSettings(gesture)
            },
            onSetWebhookCopy = { on ->
                if (on) {
                    sheetGesture = null
                    onOpenWebhookSettings(gesture)
                } else {
                    onDisableWebhook(gesture)
                }
            },
            onDismiss = { sheetGesture = null },
        )
    }
}

@Composable
private fun GestureRow(
    gesture: RingGesture,
    destination: GestureDestination,
    agentTitles: Map<String, String>,
    command: String,
    onEdit: () -> Unit,
    onConfigure: (() -> Unit)?,
) {
    val recording = gesture.kind == GestureKind.Recording
    val isIndexAgent = destination == GestureDestination.IndexAgent ||
        destination == GestureDestination.LocalAgent
    val colors = IndexTheme.colors
    val accent = colors.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(LABEL_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GestureGlyph(
                gesture = gesture,
                color = if (recording) accent else colors.onSurface,
            )
            Text(
                gesture.gestureLabel,
                fontSize = 12.sp,
                // The design's 12px label fits the 106dp column only without the
                // 0.5sp letter spacing bodyLarge would otherwise contribute.
                letterSpacing = 0.sp,
                color = colors.onSurfaceVariant,
            )
        }
        // The connector pairs with the tile rather than the whole row, so it meets the tile's
        // centre; the glyph column is taller and would otherwise pull the midpoint upwards.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (recording) accent else colors.outlineVariant)
            )
            Row(
                modifier = Modifier
                    .width(TILE_WIDTH)
                    .clip(TILE_SHAPE)
                    .background(if (isIndexAgent) colors.redSurface else Color.Transparent)
                    .border(
                        width = if (isIndexAgent) 1.5.dp else 1.dp,
                        color = if (isIndexAgent) accent else colors.outlineVariant,
                        shape = TILE_SHAPE,
                    )
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    destination.icon,
                    contentDescription = null,
                    tint = if (isIndexAgent) accent else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    command.trim().takeIf { destination == GestureDestination.AgentCommand && it.isNotEmpty() }
                        ?.let { "“$it”" }
                        ?: destination.tileLabel(agentTitles),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isIndexAgent) FontWeight.Medium else FontWeight.Normal,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (onConfigure != null) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configure",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onConfigure),
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureGlyph(gesture: RingGesture, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        gesture.glyph.forEach { segment ->
            Box(
                modifier = Modifier
                    .size(width = segment, height = 9.dp)
                    .background(color, if (segment == DOT) CircleShape else RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun WebhookCopyRow(onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(LABEL_WIDTH), contentAlignment = Alignment.CenterEnd) {
            Text(
                "also",
                fontSize = 11.sp,
                color = colors.outline,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .dashedLine(colors.outlineVariant)
        )
        Row(
            modifier = Modifier
                .width(TILE_WIDTH)
                .clip(TILE_SHAPE)
                .dashedBorder(colors.outline, 10.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Webhook,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "send to webhook",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GestureDestinationSheet(
    gesture: RingGesture,
    current: GestureDestination,
    agentPlugins: List<AgentPluginOption>,
    storeAgentPlugins: List<StorePlugin>,
    musicControl: Boolean,
    command: String,
    onSetCommand: (String) -> Unit,
    webhookUrl: String?,
    webhookCopyOn: Boolean,
    onSelect: (GestureDestination) -> Unit,
    onConnectAgentPlugin: (IntegrationProvider) -> Unit,
    onInstallAgentPlugin: (StorePlugin) -> Unit,
    onConfigureAgentPlugin: (IntegrationProvider) -> Unit,
    onOpenWebhookSettings: () -> Unit,
    onSetWebhookCopy: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    var commandDraft by remember(command) { mutableStateOf(command) }
    val commitCommand = { if (commandDraft != command) onSetCommand(commandDraft) }
    ModalBottomSheet(
        onDismissRequest = {
            commitCommand()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.sheetSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GestureGlyph(
                gesture = gesture,
                color = if (gesture.kind == GestureKind.Recording) colors.primary
                else colors.onSurface,
            )
            Text(
                gesture.gestureLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
        }
        val agentTitles = agentPlugins.titles()
        val otherAgents = otherAgentRows(current, agentPlugins, storeAgentPlugins)
        destinationsFor(gesture.kind, musicControl).forEach { option ->
            SheetChoiceRow(
                icon = option.icon,
                title = option.optionLabel(agentTitles),
                subtitle = option.optionSub,
                selected = option == current,
                onClick = {
                    commitCommand()
                    onSelect(option)
                },
            )
            if (option == GestureDestination.LocalAgent && otherAgents.isNotEmpty()) {
                OtherAgentsGroup(
                    rows = otherAgents,
                    current = current,
                    agentTitles = agentTitles,
                    onSelect = {
                        commitCommand()
                        onSelect(it)
                    },
                    onConnect = onConnectAgentPlugin,
                    onInstall = onInstallAgentPlugin,
                    onConfigure = onConfigureAgentPlugin,
                )
            }
        }
        if (current == GestureDestination.AgentCommand) {
            OutlinedTextField(
                value = commandDraft,
                onValueChange = { commandDraft = it },
                singleLine = true,
                label = { Text("Command") },
                placeholder = { Text("Unlock front door") },
                supportingText = {
                    Text(
                        "Runs as if you'd said it to Index, using your plugins and actions. " +
                            "For example: \"Unlock front door\", \"Turn off the living room lights\" " +
                            "or \"Start a 25 minute timer\""
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (gesture.kind == GestureKind.Recording &&
            current != GestureDestination.WebhookOnly &&
            current != GestureDestination.Nothing
        ) {
            HorizontalDivider(
                color = colors.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            WebhookSheetRow(
                icon = Icons.Default.Webhook,
                title = "Also send to webhook",
                webhookUrl = webhookUrl,
                onClick = onOpenWebhookSettings,
            ) {
                Switch(checked = webhookCopyOn, onCheckedChange = onSetWebhookCopy)
            }
        }
        if (current == GestureDestination.Webhook ||
            (gesture.kind == GestureKind.Recording &&
                (current == GestureDestination.WebhookOnly || webhookCopyOn))
        ) {
            WebhookSheetRow(
                icon = Icons.Default.SettingsEthernet,
                title = "Webhook settings",
                webhookUrl = webhookUrl,
                onClick = onOpenWebhookSettings,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Agent plugins folded into one row: installed ones, then the store's, each a tap away from connecting. */
@Composable
private fun OtherAgentsGroup(
    rows: List<OtherAgentRow>,
    current: GestureDestination,
    agentTitles: Map<String, String>,
    onSelect: (GestureDestination) -> Unit,
    onConnect: (IntegrationProvider) -> Unit,
    onInstall: (StorePlugin) -> Unit,
    onConfigure: (IntegrationProvider) -> Unit,
) {
    val colors = IndexTheme.colors
    val routed = current as? GestureDestination.AgentPlugin
    var expanded by remember { mutableStateOf(routed != null) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f)
    SheetChoiceRow(
        icon = Icons.AutoMirrored.Filled.Send,
        title = "Other agents",
        subtitle = routed?.tileLabel(agentTitles) ?: "What you say goes straight to the agent you pick",
        selected = routed != null && !expanded,
        onClick = { expanded = !expanded },
        trailing = {
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(chevron),
            )
        },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            rows.forEach { row ->
                when (row) {
                    is OtherAgentRow.Installed -> {
                        val option = row.option
                        val connect = { onConnect(option.provider) }
                        OtherAgentChoiceRow(
                            title = option.provider.title,
                            subtitle = when {
                                !option.connected -> "Not connected"
                                option.configurable -> "Hold to reconfigure"
                                else -> null
                            },
                            selected = option.destination == current,
                            onClick = { if (option.connected) onSelect(option.destination) else connect() },
                            onConnect = connect.takeUnless { option.connected },
                            onLongClick = if (option.configurable) { { onConfigure(option.provider) } } else null,
                        )
                    }
                    is OtherAgentRow.Missing -> OtherAgentChoiceRow(
                        title = row.destination.tileLabel(agentTitles),
                        subtitle = "No longer installed",
                        selected = true,
                        onClick = {},
                        onConnect = null,
                    )
                    is OtherAgentRow.Store -> {
                        val install = { onInstall(row.plugin) }
                        OtherAgentChoiceRow(
                            title = row.plugin.title,
                            subtitle = "Not connected",
                            selected = false,
                            onClick = install,
                            onConnect = install,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherAgentChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onConnect: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    SheetChoiceRow(
        icon = Icons.Default.Power,
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = if (onConnect == null) null else {
            { TextButton(onClick = onConnect) { Text("Connect") } }
        },
        modifier = Modifier.padding(start = 34.dp),
    )
}

@Composable
private fun SheetChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.redSurface else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.primary
            else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = colors.onSurface)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke()
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WebhookSheetRow(
    icon: ImageVector,
    title: String,
    webhookUrl: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = colors.onSurface)
            Text(
                webhookUrl?.takeIf { it.isNotBlank() } ?: "Not configured yet. Tap to set up",
                fontSize = 12.sp,
                color = if (webhookUrl.isNullOrBlank()) colors.primary
                else colors.onSurfaceVariant,
            )
        }
        trailing()
    }
}

private fun dashes(density: androidx.compose.ui.unit.Density) = with(density) {
    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
}

private fun Modifier.dashedLine(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 2.dp.toPx(),
        pathEffect = dashes(this),
    )
}

private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = dashes(this)),
    )
}
