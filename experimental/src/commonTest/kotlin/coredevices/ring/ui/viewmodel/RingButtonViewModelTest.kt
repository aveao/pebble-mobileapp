package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.compose.runtime.Composable
import com.russhwolf.settings.MapSettings
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginActionAgentIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionNoteIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionReminderIntegration
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.util.Platform
import coredevices.util.transcription.CactusModelPathProvider
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RingButtonViewModelTest {

    @Test
    fun setGestureRouteWritesTheRoute() = runTest {
        val fixture = Fixture(this)

        fixture.viewModel.setGestureRoute(RingGesture.Hold, GestureDestination.WebSearch)

        assertEquals(GestureDestination.WebSearch, fixture.routing.destinationFor(RingGesture.Hold))
        assertEquals(GestureDestination.WebSearch, fixture.viewModel.gestureRoutes.value[RingGesture.Hold])
    }

    @Test
    fun agentDestinationsTriggerLmModelExtractionOnTheAppScope() = runTest {
        for (destination in listOf(GestureDestination.LocalAgent, GestureDestination.IndexAgent)) {
            val fixture = Fixture(this)

            fixture.viewModel.setGestureRoute(RingGesture.Hold, destination)
            assertEquals(0, fixture.cactus.lmModelPathCalls, "$destination resolved the model synchronously")
            advanceUntilIdle()

            assertEquals(1, fixture.cactus.lmModelPathCalls, "$destination did not extract the LM model")
        }
    }

    @Test
    fun otherDestinationsDoNotTouchTheLmModel() = runTest {
        val fixture = Fixture(this)

        fixture.viewModel.setGestureRoute(RingGesture.Hold, GestureDestination.WebSearch)
        fixture.viewModel.setGestureRoute(RingGesture.Hold, GestureDestination.WebhookOnly)
        fixture.viewModel.setGestureRoute(RingGesture.Hold, GestureDestination.Nothing)
        fixture.viewModel.setGestureRoute(RingGesture.Click, GestureDestination.PlayPause)
        advanceUntilIdle()

        assertEquals(0, fixture.cactus.lmModelPathCalls)
    }

    @Test
    fun disableWebhookKeepsTheConfigButStopsSending() = runTest {
        val fixture = Fixture(this)
        fixture.webhooks.setConfig(
            RingGesture.Hold,
            IndexWebhookConfig(url = "https://example.com/hook", saved = true),
        )
        assertTrue(fixture.webhooks.configFor(RingGesture.Hold).isActive)

        fixture.viewModel.disableWebhook(RingGesture.Hold)

        val config = fixture.viewModel.webhookConfigs.value.getValue(RingGesture.Hold)
        assertFalse(config.isActive)
        assertEquals("https://example.com/hook", config.url)
    }

    @Test
    fun musicEnabledFollowsThePlatform() = runTest {
        assertTrue(Fixture(this, platformName = "Android 15").viewModel.musicEnabled)
        assertFalse(Fixture(this, platformName = "iOS 18").viewModel.musicEnabled)
    }

    @Test
    fun everyAgentPluginIsListedAndOnlyConnectedOnesArePickable() = withMain {
        val claude = FakeProvider("plugin:claude", "Claude")
        val codex = FakeProvider("plugin:codex", "Codex", authorized = false)
        val fixture = Fixture(this, providers = listOf(claude, codex))
        backgroundScope.launch { fixture.viewModel.agentProviders.collect {} }
        backgroundScope.launch { fixture.viewModel.connectedAgentProviders.collect {} }
        runCurrent()

        assertEquals(listOf<IntegrationProvider>(claude, codex), fixture.viewModel.agentProviders.value)
        assertEquals(listOf<IntegrationProvider>(claude), fixture.viewModel.connectedAgentProviders.value)

        codex.authorized = true
        fixture.viewModel.refreshAgentDestinations()
        runCurrent()

        assertEquals(listOf<IntegrationProvider>(claude, codex), fixture.viewModel.connectedAgentProviders.value)
    }

    @Test
    fun agentPluginRoutesDoNotTouchTheLmModel() = runTest {
        val fixture = Fixture(this)

        fixture.viewModel.setGestureRoute(RingGesture.Hold, GestureDestination.AgentPlugin("plugin:claude"))
        advanceUntilIdle()

        assertEquals(GestureDestination.AgentPlugin("plugin:claude"), fixture.routing.destinationFor(RingGesture.Hold))
        assertEquals(0, fixture.cactus.lmModelPathCalls)
    }

    private fun withMain(block: suspend TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun storeAgentPluginsAreTheUninstalledOnesThatTakeRecordings() = withMain {
        val claude = storePlugin("Claude", "00000000-0000-0000-0002-000000000001", IntegrationSupport.Agent)
        val codex = storePlugin("Codex", "00000000-0000-0000-0002-000000000002", IntegrationSupport.Agent)
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000003", IntegrationSupport.Note)
        val fixture = Fixture(this, storePlugins = listOf(claude, codex, things))
        backgroundScope.launch { fixture.viewModel.storeAgentPlugins.collect {} }

        fixture.lockerUuids.value = listOf(codex.pluginUuid)
        runCurrent()
        assertEquals(listOf(claude), fixture.viewModel.storeAgentPlugins.value)

        fixture.pluginsEnabled.value = false
        runCurrent()
        assertEquals(emptyList(), fixture.viewModel.storeAgentPlugins.value)
    }

    @Test
    fun installingAStorePluginGoesThroughTheStoreAndRefreshesTheProviders() = withMain {
        val claude = storePlugin("Claude", "00000000-0000-0000-0002-000000000001", IntegrationSupport.Agent)
        val codex = FakeProvider("plugin:codex", "Codex", authorized = false)
        val fixture = Fixture(this, providers = listOf(codex), storePlugins = listOf(claude))
        backgroundScope.launch { fixture.viewModel.connectedAgentProviders.collect {} }
        runCurrent()
        assertEquals(emptyList(), fixture.viewModel.connectedAgentProviders.value)

        codex.authorized = true
        assertTrue(fixture.viewModel.installStorePlugin(claude, mapOf("location" to true)))
        runCurrent()

        assertEquals(listOf(claude.pluginUuid to mapOf<String, Boolean?>("location" to true)), fixture.store.installed)
        assertEquals(listOf<IntegrationProvider>(codex), fixture.viewModel.connectedAgentProviders.value)
    }

    private fun storePlugin(name: String, uuid: String, support: IntegrationSupport) = StorePlugin(
        metadata = PluginMetadata(
            pluginUuid = Uuid.parse(uuid),
            name = name,
            description = "",
            sources = listOf(
                SourceDeclaration(
                    category = when (support) {
                        IntegrationSupport.Agent -> PblPluginActionAgentIntegration.CATEGORY
                        IntegrationSupport.Note -> PblPluginActionNoteIntegration.CATEGORY
                        IntegrationSupport.Reminder -> PblPluginActionReminderIntegration.CATEGORY
                    },
                    items = listOf(PblPluginActionIntegration.CONFIGURED_ITEM),
                ),
            ),
            actions = listOf(
                ActionDeclaration(
                    name = when (support) {
                        IntegrationSupport.Agent -> PblPluginActionAgentIntegration.ACTION_NAME
                        IntegrationSupport.Note -> PblPluginActionNoteIntegration.ACTION_NAME
                        IntegrationSupport.Reminder -> PblPluginActionReminderIntegration.ACTION_NAME
                    },
                ),
            ),
        ),
        configurable = false,
    )

    private class Fixture(
        scope: TestScope,
        platformName: String = "Android 15",
        providers: List<IntegrationProvider> = emptyList(),
        storePlugins: List<StorePlugin> = emptyList(),
    ) {
        val routing = GestureRoutingPreferences(MapSettings(), PreferencesImpl(MapSettings()))
        val webhooks = IndexWebhookPreferences(MapSettings())
        val cactus = FakeCactusModelPathProvider()
        val store = FakePluginStore(storePlugins)
        val pluginsEnabled = MutableStateFlow(true)
        val lockerUuids = MutableStateFlow<List<Uuid>>(emptyList())
        val viewModel = RingButtonViewModel(
            routing,
            webhooks,
            cactus,
            LibIndexCoroutineScope(StandardTestDispatcher(scope.testScheduler)),
            FakePlatform(platformName),
            IntegrationProviderRepository(
                preferences = PreferencesImpl(MapSettings()),
                plugins = FakePlugins(),
                platform = FakePlatform(platformName),
                delegatedItems = DelegatedIntegrationItems(ItemFactory(), ItemRepository(FakeCachedItemDao(), cancelReminder = {})),
                builtIn = listOf(FakeProvider("index", "Built-in", storesItemsLocally = true, supports = setOf(IntegrationSupport.Note))) + providers,
            ),
            pluginStore = store,
            pluginsEnabled = pluginsEnabled,
            lockerUuids = lockerUuids,
        )
    }

    private class FakePluginStore(private val plugins: List<StorePlugin>) : PluginStore {
        val installed = mutableListOf<Pair<Uuid, Map<String, Boolean?>>>()

        override suspend fun indexPlugins(): List<StorePlugin>? = plugins
        override suspend fun reviewInstall(pluginUuid: Uuid): InstallReview? = null
        override suspend fun install(pluginUuid: Uuid, granted: Map<String, Boolean?>): Boolean {
            installed += pluginUuid to granted
            return true
        }

        @Composable
        override fun ReviewInstallDialog(review: InstallReview, onAnswer: (Map<String, Boolean?>?) -> Unit) {
        }
    }

    private class FakeAgentIntegration : AgentIntegration {
        override suspend fun sendToAgent(message: String, chatId: String?, source: ItemSource?): String = "sent"
        override suspend fun signIn(uiContext: PlatformUiContext) = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized() = true
    }

    private class FakeProvider(
        override val id: String,
        override val title: String,
        override val storesItemsLocally: Boolean = false,
        var authorized: Boolean = true,
        override val supports: Set<IntegrationSupport> = setOf(IntegrationSupport.Agent),
    ) : IntegrationProvider {
        override val isExternalPlugin: Boolean = !storesItemsLocally
        override suspend fun isAuthorized() = authorized
        override fun agent(): AgentIntegration = FakeAgentIntegration()
    }

    private class FakeCachedItemDao : CachedItemDao {
        override suspend fun upsert(item: CachedItem) {}
        override suspend fun upsertAll(items: List<CachedItem>) {}
        override suspend fun getById(id: String): CachedItem? = null
        override fun getByIdFlow(id: String): Flow<CachedItem?> = flowOf(null)
        override fun getAllFlow(): Flow<List<CachedItem>> = flowOf(emptyList())
        override fun getAllForSyncFlow(): Flow<List<CachedItem>> = flowOf(emptyList())
        override fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByRecording(recordingId: String): List<CachedItem> = emptyList()
        override suspend fun getAllActive(): List<CachedItem> = emptyList()
        override suspend fun getAllIncludingDeleted(): List<CachedItem> = emptyList()
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) {}
        override suspend fun deleteAll() {}
        override suspend fun getAllIds(): List<String> = emptyList()
        override suspend fun countLocked(): Int = 0
    }

    private class FakePlatform(override val name: String) : Platform {
        override val deviceModelName = "test"
        override suspend fun openUrl(url: String) = Unit
        override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) = task()
    }

    private class FakeCactusModelPathProvider : CactusModelPathProvider {
        var lmModelPathCalls = 0

        override suspend fun getSTTModelPath(modelName: String, version: String): String = error("unused")

        override suspend fun getLMModelPath(): String {
            lmModelPathCalls++
            return "lm"
        }

        override fun isModelDownloaded(modelName: String): Boolean = false
        override fun getDownloadedModels(): List<String> = emptyList()
        override fun getIncompatibleModels(): List<String> = emptyList()
        override fun deleteModel(modelName: String) = Unit
        override fun getModelSizeBytes(modelName: String): Long = 0L
        override fun initTelemetry() = Unit
    }
}
