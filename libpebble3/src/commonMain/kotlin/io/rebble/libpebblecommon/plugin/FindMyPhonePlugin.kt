package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Built-in find-my-phone plugin. The subscription lifecycle is the on/off switch: subscribing
 * to `phone_state/find_my_phone` starts the phone ringing, and the subscription ending (watch
 * app closed, unsubscribe, disconnect) stops it.
 */
class FindMyPhonePlugin(
    private val soundPlayer: PhoneSoundPlayer,
) : NativePlugin {
    override val pluginUuid: Uuid = BUILT_IN_FIND_MY_PHONE_UUID
    override val name: String = "Find My Phone"
    override val description: String = "Ring the phone from the watch to find it."

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_FIND_MY_PHONE),
            properties = mapOf(
                PROPERTY_RINGING to listOf(SourceShapeNames.BOOLEAN),
            ),
            supportsMultiple = false,
            callerPermissions = listOf(PluginPermission(PluginPermissions.FIND_MY_PHONE)),
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        return channelFlow {
            soundPlayer.startRinging()
            // Ring for at most RING_TIMEOUT; the subscription stays open (silent), and reopening
            // the watchapp re-subscribes and rings again.
            launch {
                delay(RING_TIMEOUT)
                soundPlayer.stopRinging()
            }
            soundPlayer.isRinging.collect { ringing -> send(toEnvelope(ringing)) }
        }.onCompletion { soundPlayer.stopRinging() }
    }

    private fun toEnvelope(ringing: Boolean): SourceEnvelope = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = listOf(
            SourceInstance(
                instanceId = INSTANCE_ID,
                properties = mapOf(
                    PROPERTY_RINGING to mapOf(
                        SourceShapeNames.BOOLEAN to encode(BooleanShape(ringing)),
                    ),
                ),
            ),
        ),
    )

    private inline fun <reified T> encode(value: T): JsonElement =
        Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "phone_state"
        const val ITEM_FIND_MY_PHONE = "find_my_phone"
        const val PROPERTY_RINGING = "ringing"
        private const val INSTANCE_ID = "phone"
        private val RING_TIMEOUT = 60.seconds

        val BUILT_IN_FIND_MY_PHONE_UUID: Uuid =
            Uuid.parse("00000000-0000-0000-0001-000000000008")
    }
}
