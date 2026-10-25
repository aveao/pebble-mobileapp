package coredevices.coreapp.backup

import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.builtin_servlets.messaging.ApprovedBeeperContact
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.SecondaryMode
import coredevices.util.CoreConfig
import io.rebble.libpebblecommon.LibPebbleConfig
import kotlinx.serialization.Serializable

@Serializable
data class SettingsExport(
    val coreConfig: CoreConfig? = null,
    val libPebbleConfig: LibPebbleConfig? = null,
    val ringPreferences: RingPreferencesExport? = null,
)

@Serializable
data class RingPreferencesExport(
    val useCactusTranscription: Boolean = true,
    val cactusMode: Int = 0,
    val ringPaired: String? = null,
    val musicControlMode: Int = MusicControlMode.DoubleClick.id,
    val lastSyncIndex: Int? = null,
    val debugDetailsEnabled: Boolean = false,
    val approvedBeeperContacts: List<ApprovedBeeperContact> = emptyList(),
    val secondaryMode: Int = SecondaryMode.Search.id,
    // Legacy int ids from before providers became integration providers
    val reminderProvider: Int? = null,
    val noteProvider: Int? = null,
    val reminderProviderId: String? = null,
    val noteProviderId: String? = null,
    val noteShortcut: String? = null,
    val autoDismissActionNotifications: Boolean = true,
    val phoneCalendarEnabled: Boolean = false,
    val platformSttDefaulted: Boolean = false,
    val backupEnabled: Boolean = true,
    val useEncryption: Boolean = false,
    val defaultCaptureType: Int = DefaultCaptureType.Note.id,
    val targetCalendar: Int? = null,
    val scanByPairedAddress: Boolean? = null,
    // Android only; null when exported on iOS where the preference doesn't exist
    val usePendingIntentScan: Boolean? = null,
)
