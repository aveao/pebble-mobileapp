package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.button.accepts
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ButtonSwitchboardTest {

    @Test
    fun everyOfferedDestinationIsValidForTheGesture() {
        RingGesture.entries.forEach { gesture ->
            destinationsFor(gesture.kind).forEach { destination ->
                assertTrue(gesture.accepts(destination), "$gesture rejects $destination")
            }
        }
    }

    @Test
    fun musicGesturesOfferPlayPauseNextPreviousAgentCommandWebhookAndNothing() {
        assertEquals(
            listOf(
                GestureDestination.PlayPause,
                GestureDestination.NextTrack,
                GestureDestination.PreviousTrack,
                GestureDestination.AgentCommand,
                GestureDestination.Webhook,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Music),
        )
    }

    @Test
    fun clicksStillReachTheAgentAndWebhookWithoutMusicControl() {
        assertEquals(
            listOf(GestureDestination.AgentCommand, GestureDestination.Webhook, GestureDestination.Nothing),
            destinationsFor(GestureKind.Music, musicControl = false),
        )
    }

    @Test
    fun recordingGesturesOfferEveryAgentRoute() {
        assertEquals(
            listOf(
                GestureDestination.IndexAgent,
                GestureDestination.LocalAgent,
                GestureDestination.WebSearch,
                GestureDestination.WebhookOnly,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Recording),
        )
    }

    @Test
    fun agentPluginRoutesAreValidForRecordingGesturesAndKeepTheSheetOpen() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")

        assertFalse(claude in destinationsFor(GestureKind.Recording))
        RingGesture.entries.filter { it.kind == GestureKind.Recording }.forEach {
            assertTrue(it.accepts(claude))
            assertTrue(gestureSheetStaysOpenFor(it, claude))
        }
    }

    @Test
    fun otherAgentsListInstalledPluginsThenTheStores() {
        val claude = AgentPluginOption(FakeAgentProvider("plugin:claude", "Claude"), connected = true)
        val codex = AgentPluginOption(FakeAgentProvider("plugin:codex", "Codex"), connected = false)
        val things = storePlugin("Things")

        assertEquals(
            listOf(OtherAgentRow.Installed(claude), OtherAgentRow.Installed(codex), OtherAgentRow.Store(things)),
            otherAgentRows(GestureDestination.IndexAgent, listOf(claude, codex), listOf(things)),
        )
        assertEquals(emptyList(), otherAgentRows(GestureDestination.IndexAgent, emptyList(), emptyList()))
    }

    @Test
    fun aRouteToAnUninstalledAgentStaysListedSoItCanBeChanged() {
        val claude = AgentPluginOption(FakeAgentProvider("plugin:claude", "Claude"), connected = true)
        val gone = GestureDestination.AgentPlugin("plugin:gone")

        assertEquals(
            listOf(OtherAgentRow.Installed(claude), OtherAgentRow.Missing(gone)),
            otherAgentRows(gone, listOf(claude), emptyList()),
        )
        assertEquals(
            listOf(OtherAgentRow.Installed(claude)),
            otherAgentRows(claude.destination, listOf(claude), emptyList()),
        )
    }

    @Test
    fun agentPluginOptionsCarryTheirProviderTitles() {
        val claude = FakeAgentProvider("plugin:claude", "Claude")
        val options = listOf(AgentPluginOption(claude, connected = false))

        assertEquals(GestureDestination.AgentPlugin("plugin:claude"), options.single().destination)
        assertEquals(mapOf("plugin:claude" to "Claude"), options.titles())
    }

    @Test
    fun agentPluginTilesAreLabelledByTheirProvider() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")

        assertEquals("Claude", claude.tileLabel(mapOf("plugin:claude" to "Claude")))
        assertEquals("Unavailable agent", claude.tileLabel(emptyMap()))
        assertEquals("Index agent", GestureDestination.IndexAgent.tileLabel(mapOf("plugin:claude" to "Claude")))
    }

    @Test
    fun copyRowFollowsAnActiveWebhookConfig() {
        assertFalse(IndexWebhookConfig().isActive)
        assertFalse(IndexWebhookConfig(url = "https://example.com/hook").isActive)
        assertFalse(IndexWebhookConfig(url = "", saved = true).isActive)
        assertTrue(IndexWebhookConfig(url = "https://example.com/hook", saved = true).isActive)
    }

    @Test
    fun rowOrderAndGlyphsMatchThePressPatterns() {
        assertEquals(
            listOf("Click", "Double click", "Triple click", "Hold & Talk", "Double click & hold"),
            RingGesture.entries.map { it.gestureLabel },
        )
        assertEquals(
            RingGesture.entries.map { it.sequence.size },
            RingGesture.entries.map { it.glyph.size },
        )
    }

    @Test
    fun everyRecordingRouteButNothingLeavesAWebhookRowToTap() {
        RingGesture.entries.filter { it.kind == GestureKind.Recording }.forEach { gesture ->
            destinationsFor(GestureKind.Recording)
                .filter { it != GestureDestination.Nothing }
                .forEach {
                    assertTrue(gestureSheetStaysOpenFor(gesture, it), "$gesture / $it closes early")
                }
            assertFalse(gestureSheetStaysOpenFor(gesture, GestureDestination.Nothing))
        }
    }

    @Test
    fun aMusicChoiceIsTheWholeInteractionExceptWebhookAndAgentCommand() {
        RingGesture.entries.filter { it.kind == GestureKind.Music }.forEach { gesture ->
            destinationsFor(GestureKind.Music).forEach {
                assertEquals(
                    it == GestureDestination.Webhook || it == GestureDestination.AgentCommand,
                    gestureSheetStaysOpenFor(gesture, it),
                    "$gesture / $it",
                )
            }
        }
    }

    @Test
    fun everyDestinationHasATileLabel() {
        GestureKind.entries
            .flatMap { destinationsFor(it) }
            .forEach { assertTrue(it.tileLabel.isNotBlank(), "$it has no tile label") }
    }

    private fun storePlugin(name: String) = StorePlugin(
        metadata = PluginMetadata(
            pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000001"),
            name = name,
            description = "",
            sources = emptyList(),
            actions = emptyList(),
        ),
        configurable = false,
    )

    private class FakeAgentProvider(override val id: String, override val title: String) : IntegrationProvider {
        override val supports = setOf(IntegrationSupport.Agent)
    }
}
