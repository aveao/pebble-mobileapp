package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.pebble.rememberLibPebble
import coredevices.util.CoreConfigHolder
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import org.koin.compose.koinInject

private const val HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY = "hasSeenPermissionsIntro"

fun Settings.hasSeenPermissionsIntro() = getBoolean(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, false)
fun Settings.setHasSeenPermissionsIntro(seen: Boolean) = set(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, seen)

fun PermissionPolicy.displayName(): String = when (this) {
    PermissionPolicy.AskIfExfiltrating -> "With network access"
    PermissionPolicy.AlwaysAsk -> "Always"
    PermissionPolicy.NeverAsk -> "Never"
}

fun PermissionPolicy.description(): String = when (this) {
    PermissionPolicy.AskIfExfiltrating ->
        "Apps ask before going online or changing anything, and before reading your data if they could send it off your phone."
    PermissionPolicy.AlwaysAsk -> "Apps ask before using any permission."
    PermissionPolicy.NeverAsk -> "Apps can use any permission you haven't turned off on the app's page."
}

/** What permissions are, and when apps ask; shown once, in a dialog or in onboarding. */
@Composable
fun PermissionsIntroContent(settings: SettingsItemsState?, modifier: Modifier = Modifier) {
    val libPebble = rememberLibPebble()
    val hasWatch = libPebble.watches.collectAsState().value.isNotEmpty()
    val indexEnabled = koinInject<CoreConfigHolder>().config.collectAsState().value.enableIndex
    val askers = when {
        hasWatch && indexEnabled ->
            "Watchfaces, watchapps and plugins (including the ones Index uses to save your notes and reminders)"
        indexEnabled -> "Plugins, including the ones Index uses to save your notes and reminders,"
        else -> "Watchfaces, watchapps and plugins"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$askers now ask before using your phone's features: going online, your location, " +
                "notifications, calendar, contacts, music, files, watch settings and timeline.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Change the permissions mode for all apps below (also in Settings/Apps), and configure " +
                    "each app's individual settings on its own screen",
            style = MaterialTheme.typography.bodySmall,
        )
        if (hasWatch) {
            Text(
                "Older apps which weren't built for the Permissions system are not affected " +
                        "by default, unless you choose to enforce permissions for them in Settings.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        settings.Show(SettingsIds.PermissionPolicy)
    }
}

@Composable
fun PermissionsIntroDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // The settings row needs about the width of the settings screen.
        modifier = Modifier.padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("App permissions") },
        text = {
            val settings = rememberSettingsItemsState(navBarNav = null, snackbarDisplay = {})
            PermissionsIntroContent(settings, Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("OK") } },
    )
}
