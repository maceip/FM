package com.google.research.air.cosmo.ui.mainscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.google.research.air.cosmo.BuildConfig
import com.google.research.air.cosmo.controller.CosmoController
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.fulfillment.FulfillmentActionHandler
import com.google.research.air.cosmo.fulfillment.FulfillmentOrchestrator
import com.google.research.air.cosmo.fulfillment.requiresExecuteConfirmation
import com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorNavPayload
import com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorNavigationBridge
import com.google.research.air.cosmo.glasses.CosmoGlassManager
import com.google.research.air.cosmo.lib.permission.PermissionRationaleHelper
import com.google.research.air.cosmo.lib.permission.RuntimePermissionGrantState
import com.google.research.air.cosmo.lib.permission.RuntimePermissionState
import com.google.research.air.cosmo.notificationcapture.NotificationCaptureController
import com.google.research.air.cosmo.presence.BrowserTab
import com.google.research.air.cosmo.presence.CardAction
import com.google.research.air.cosmo.presence.CardActionType
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.CheckItem
import com.google.research.air.cosmo.presence.MessageChannel
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.presence.PresenceState
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.Recipient
import com.google.research.air.cosmo.presence.Source
import com.google.research.air.cosmo.presence.overlay.PresenceOverlayStarter
import com.google.research.air.cosmo.presence.surfaces.CardStackSurface
import com.google.research.air.cosmo.presence.surfaces.OrbSurface
import com.google.research.air.cosmo.presence.surfaces.TabSurface
import com.google.research.air.cosmo.presence.surfaces.GlassPanel
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.CosmoStateManager
import com.google.research.air.cosmo.ui.theme.CosmoBarItem
import com.google.research.air.cosmo.ui.theme.CosmoBackground
import com.google.research.air.cosmo.ui.theme.CosmoBottomBar
import com.google.research.air.cosmo.ui.theme.CosmoHazeHost
import com.google.research.air.cosmo.ui.theme.CosmoNavRail
import com.google.research.air.cosmo.ui.theme.CosmoSignalTag
import com.google.research.air.cosmo.ui.theme.CosmoStickerSurface
import com.google.research.air.cosmo.ui.theme.CosmoTokens
import com.google.research.air.cosmo.ui.theme.CosmoTopBar
import com.google.research.air.cosmo.ui.theme.LocalHazeState
import com.google.research.air.cosmo.ui.theme.cosmoHazeSource
import com.google.research.air.cosmo.ui.theme.setCosmoContent
import com.google.research.air.cosmo.ui.theme.verticalFadingEdges
import com.google.research.air.cosmo.voiceassistant.camera.DeviceCameraCaptureSurface
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val EXTRA_DEBUG_TIMER_FLOW = "com.google.research.air.cosmo.extra.DEBUG_TIMER_FLOW"
private const val DEBUG_TIMER_FLOW_QUERY = "set a timer for 10 minutes"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var cosmoController: CosmoController

    @Inject lateinit var settingStore: CosmoSettingStore

    @Inject lateinit var stateManager: CosmoStateManager

    @Inject lateinit var actionsRepository: ActionsRepositoryContract

    @Inject lateinit var actionHandler: FulfillmentActionHandler

    @Inject lateinit var fulfillmentOrchestrator: FulfillmentOrchestrator

    @Inject lateinit var notificationCaptureManager: NotificationCaptureController

    @Inject lateinit var glassManager: CosmoGlassManager

    @Inject lateinit var cameraViewComposable: DeviceCameraCaptureSurface

    @Inject lateinit var permissionRationaleHelper: PermissionRationaleHelper

    @Inject lateinit var taskMonitorNavigationBridge: TaskMonitorNavigationBridge

    @Inject lateinit var debugPanels: Set<@JvmSuppressWildcards CosmoDebugPanel>

    @Inject lateinit var presenceOverlayStarter: PresenceOverlayStarter

    @Inject lateinit var presenceController: PresenceController

    @Inject lateinit var specialtyCards: com.google.research.air.cosmo.presence.PresenceSpecialtyCards

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debugTimerFlowEnabled =
            BuildConfig.DEBUG_TIMER_FLOW_AUTOMATION && intent.getBooleanExtra(EXTRA_DEBUG_TIMER_FLOW, false)
        handleSettingsIntent(intent)
        cosmoController.init()
        setCosmoContent(wallpaperDriven = true) {
            CosmoGenesisApp(
                settingStore = settingStore,
                stateManager = stateManager,
                actionsRepository = actionsRepository,
                actionHandler = actionHandler,
                fulfillmentOrchestrator = fulfillmentOrchestrator,
                notificationCaptureManager = notificationCaptureManager,
                glassManager = glassManager,
                cameraViewComposable = cameraViewComposable,
                permissionRationaleHelper = permissionRationaleHelper,
                taskMonitorNavigationBridge = taskMonitorNavigationBridge,
                debugPanels = debugPanels,
                presenceController = presenceController,
                debugTimerFlowEnabled = debugTimerFlowEnabled
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSettingsIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        presenceOverlayStarter.start()
    }

    private fun handleSettingsIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val isSettingsTrigger =
            intent.action == android.content.Intent.ACTION_APPLICATION_PREFERENCES ||
            intent.getBooleanExtra(
                com.google.research.air.cosmo.ui.surfaces.CosmoQuickSettingsTileService.EXTRA_SHOW_SETTINGS,
                false
            )
        if (isSettingsTrigger) specialtyCards.showSettings()
    }
}

@Serializable
internal sealed interface CosmoRoute : NavKey {
    @Serializable data object Actions : CosmoRoute
    @Serializable data object Onboarding : CosmoRoute
    @Serializable data object Capture : CosmoRoute
    @Serializable data object Camera : CosmoRoute
    @Serializable data object Settings : CosmoRoute
    @Serializable data object Debug : CosmoRoute
    @Serializable data object DebugSupport : CosmoRoute
    @Serializable data class ActionDetail(val actionId: String) : CosmoRoute
    @Serializable data class TaskMonitor(val title: String, val prompt: String, val response: String) : CosmoRoute
}

internal enum class MainTab(
    val route: CosmoRoute,
    val label: String,
    val icon: ImageVector
) {
    Actions(CosmoRoute.Actions, "Actions", Icons.Filled.PlayArrow),
    Onboarding(CosmoRoute.Onboarding, "Onboard", Icons.Filled.PlayArrow),
    Capture(CosmoRoute.Capture, "Capture", Icons.Filled.GraphicEq),
    Camera(CosmoRoute.Camera, "Camera", Icons.Filled.CameraAlt),
    Settings(CosmoRoute.Settings, "Settings", Icons.Filled.Settings),
    Debug(CosmoRoute.Debug, "Debug", Icons.Filled.BugReport)
}

internal val MainNavJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
private data class TaskMonitorPersist(val title: String, val prompt: String, val response: String)

@Serializable
internal data class SavedNavigationBundle(
    val selectedTabOrdinal: Int,
    val stacks: Map<String, List<String>>,
    val historyOrdinals: List<Int>
)

internal fun defaultSavedNavigation(): SavedNavigationBundle =
    SavedNavigationBundle(
        selectedTabOrdinal = MainTab.Actions.ordinal,
        stacks =
            MainTab.entries.associate { tab ->
                tab.name to listOf(cosmoRouteToToken(tab.route))
            },
        historyOrdinals = emptyList()
    )

internal fun cosmoRouteToToken(route: CosmoRoute): String =
    when (route) {
        CosmoRoute.Actions -> "actions"
        CosmoRoute.Onboarding -> "onboarding"
        CosmoRoute.Capture -> "capture"
        CosmoRoute.Camera -> "camera"
        CosmoRoute.Settings -> "settings"
        CosmoRoute.Debug -> "debug"
        CosmoRoute.DebugSupport -> "debug_support"
        is CosmoRoute.ActionDetail -> "ad:${route.actionId}"
        is CosmoRoute.TaskMonitor ->
            "tm:${MainNavJson.encodeToString(TaskMonitorPersist(route.title, route.prompt, route.response))}"
    }

internal fun cosmoRouteFromToken(token: String): CosmoRoute? =
    when {
        token == "actions" -> CosmoRoute.Actions
        token == "onboarding" -> CosmoRoute.Onboarding
        token == "capture" -> CosmoRoute.Capture
        token == "camera" -> CosmoRoute.Camera
        token == "settings" -> CosmoRoute.Settings
        token == "debug" -> CosmoRoute.Debug
        token == "debug_support" -> CosmoRoute.DebugSupport
        token.startsWith("ad:") -> CosmoRoute.ActionDetail(token.removePrefix("ad:"))
        token.startsWith("tm:") ->
            runCatching {
                MainNavJson.decodeFromString<TaskMonitorPersist>(token.removePrefix("tm:")).let {
                    CosmoRoute.TaskMonitor(it.title, it.prompt, it.response)
                }
            }.getOrNull()
        else -> null
    }

internal fun durableRoutesFromSaved(saved: SavedNavigationBundle): List<CosmoRoute> {
    val tab = MainTab.entries[saved.selectedTabOrdinal.coerceIn(MainTab.entries.indices)]
    val decoded =
        saved.stacks[tab.name].orEmpty().mapNotNull { cosmoRouteFromToken(it) }.filterNot {
            it == CosmoRoute.DebugSupport
        }
    return decoded.takeIf { it.isNotEmpty() } ?: listOf(tab.route)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Suppress("CyclomaticComplexMethod", "SpreadOperator")
@Composable
private fun CosmoGenesisApp(
    settingStore: CosmoSettingStore,
    stateManager: CosmoStateManager,
    actionsRepository: ActionsRepositoryContract,
    actionHandler: FulfillmentActionHandler,
    fulfillmentOrchestrator: FulfillmentOrchestrator,
    notificationCaptureManager: NotificationCaptureController,
    glassManager: CosmoGlassManager,
    cameraViewComposable: DeviceCameraCaptureSurface,
    permissionRationaleHelper: PermissionRationaleHelper,
    taskMonitorNavigationBridge: TaskMonitorNavigationBridge,
    debugPanels: Set<CosmoDebugPanel>,
    presenceController: PresenceController,
    debugTimerFlowEnabled: Boolean
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val isSupportingPaneMode =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val directive =
        remember(windowAdaptiveInfo) {
            calculatePaneScaffoldDirective(windowAdaptiveInfo)
                .copy(horizontalPartitionSpacerSize = 0.dp, verticalPartitionSpacerSize = 0.dp)
        }
    val supportingPaneStrategy =
        rememberSupportingPaneSceneStrategy<NavKey>(
            backNavigationBehavior = BackNavigationBehavior.PopUntilCurrentDestinationChange,
            directive = directive
        )
    var savedNavJson by rememberSaveable {
        mutableStateOf(MainNavJson.encodeToString(defaultSavedNavigation()))
    }
    fun currentNav(): SavedNavigationBundle = MainNavJson.decodeFromString<SavedNavigationBundle>(savedNavJson)
    fun commitNav(transform: (SavedNavigationBundle) -> SavedNavigationBundle) {
        savedNavJson = MainNavJson.encodeToString(transform(currentNav()))
    }

    val selectedTab =
        MainTab.entries[currentNav().selectedTabOrdinal.coerceIn(MainTab.entries.indices)]

    fun durableFromBackStack(nav: NavBackStack<NavKey>): List<CosmoRoute> =
        nav.withoutAdaptiveSupport().mapNotNull { it as? CosmoRoute }.filterNot { it == CosmoRoute.DebugSupport }

    val initialDurable = durableRoutesFromSaved(currentNav())
    val backStack =
        rememberNavBackStack(
            *initialDurable.withAdaptiveSupport(isSupportingPaneMode).toTypedArray()
        )

    val activity = LocalActivity.current

    fun persistCurrentStackToSaved(nav: NavBackStack<NavKey>) {
        val durable = durableFromBackStack(nav)
        commitNav {
            it.copy(
                stacks =
                    it.stacks.toMutableMap().apply {
                        put(selectedTab.name, durable.map { r -> cosmoRouteToToken(r) })
                    }
            )
        }
    }

    fun pushRoute(route: CosmoRoute) {
        val durable = durableFromBackStack(backStack) + route
        commitNav {
            it.copy(
                stacks =
                    it.stacks.toMutableMap().apply {
                        put(selectedTab.name, durable.map { r -> cosmoRouteToToken(r) })
                    }
            )
        }
        backStack.replaceWith(durable.withAdaptiveSupport(isSupportingPaneMode))
    }

    fun restoreTab(
        tab: MainTab,
        recordHistory: Boolean
    ) {
        val currentTab =
            MainTab.entries[currentNav().selectedTabOrdinal.coerceIn(MainTab.entries.indices)]
        if (currentTab == tab) {
            val root = listOf(tab.route)
            commitNav {
                it.copy(
                    stacks =
                        it.stacks.toMutableMap().apply {
                            put(tab.name, root.map { r -> cosmoRouteToToken(r) })
                        }
                )
            }
            backStack.replaceWith(root.withAdaptiveSupport(isSupportingPaneMode))
            return
        }
        persistCurrentStackToSaved(backStack)
        val newHistory =
            if (recordHistory) {
                val h = currentNav().historyOrdinals.toMutableList()
                h.remove(tab.ordinal)
                h.add(currentTab.ordinal)
                h
            } else {
                currentNav().historyOrdinals
            }
        commitNav {
            it.copy(
                selectedTabOrdinal = tab.ordinal,
                historyOrdinals = newHistory
            )
        }
        val stacksNow = currentNav().stacks
        val nextDurable =
            stacksNow[tab.name]?.mapNotNull { cosmoRouteFromToken(it) }
                ?.filterNot { it == CosmoRoute.DebugSupport }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(tab.route)
        backStack.replaceWith(nextDurable.withAdaptiveSupport(isSupportingPaneMode))
    }

    fun handleBack() {
        val durableStack = durableFromBackStack(backStack)
        if (durableStack.size > 1) {
            val next = durableStack.dropLast(1)
            commitNav {
                it.copy(
                    stacks =
                        it.stacks.toMutableMap().apply {
                            put(selectedTab.name, next.map { r -> cosmoRouteToToken(r) })
                        }
                )
            }
            backStack.replaceWith(next.withAdaptiveSupport(isSupportingPaneMode))
            return
        }
        val hist = currentNav().historyOrdinals.toMutableList()
        val previousOrdinal =
            if (hist.isNotEmpty()) {
                hist.removeAt(hist.lastIndex)
            } else {
                null
            }
        if (previousOrdinal != null) {
            val prevTab = MainTab.entries[previousOrdinal.coerceIn(MainTab.entries.indices)]
            val nextDurable =
                currentNav().stacks[prevTab.name]?.mapNotNull { cosmoRouteFromToken(it) }
                    ?.filterNot { it == CosmoRoute.DebugSupport }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(prevTab.route)
            commitNav {
                it.copy(
                    selectedTabOrdinal = previousOrdinal,
                    historyOrdinals = hist
                )
            }
            backStack.replaceWith(nextDurable.withAdaptiveSupport(isSupportingPaneMode))
        } else {
            commitNav { it.copy(historyOrdinals = hist) }
            activity?.finish()
        }
    }

    var pendingMonitorPayload by remember { mutableStateOf<TaskMonitorNavPayload?>(null) }

    DisposableEffect(taskMonitorNavigationBridge) {
        taskMonitorNavigationBridge.registerInAppNavigator {
            pendingMonitorPayload = it
        }
        onDispose {
            taskMonitorNavigationBridge.registerInAppNavigator(null)
        }
    }

    LaunchedEffect(pendingMonitorPayload) {
        val payload = pendingMonitorPayload ?: return@LaunchedEffect
        pendingMonitorPayload = null
        pushRoute(CosmoRoute.TaskMonitor(payload.title, payload.prompt, payload.agentResponse))
    }

    LaunchedEffect(isSupportingPaneMode) {
        backStack.replaceWith(durableFromBackStack(backStack).withAdaptiveSupport(isSupportingPaneMode))
    }
    CosmoBackground(Modifier.fillMaxSize()) {
        CosmoHazeHost {
            val hazeState = LocalHazeState.current
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (selectedTab != MainTab.Actions) {
                        CosmoTopBar(title = "Cosmo", frosted = true)
                    }
                },
                bottomBar = {
                    if (!isSupportingPaneMode && selectedTab != MainTab.Actions) {
                        BottomNavigationChrome(selectedTab, frosted = true) {
                            restoreTab(it, recordHistory = true)
                        }
                    }
                }
            ) { padding ->
                Surface(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .cosmoHazeSource(hazeState),
                    color = Color.Transparent
                ) {
                    if (isSupportingPaneMode) {
                        Row(Modifier.fillMaxSize()) {
                            if (selectedTab != MainTab.Actions) {
                                NavigationRailChrome(selectedTab, frosted = true) {
                                    restoreTab(it, recordHistory = true)
                                }
                            }
                            CosmoNavDisplay(
                                backStack = backStack,
                                sceneStrategies = listOf<androidx.navigation3.scene.SceneStrategy<NavKey>>(supportingPaneStrategy),
                                settingStore = settingStore,
                                stateManager = stateManager,
                                actionsRepository = actionsRepository,
                                actionHandler = actionHandler,
                                fulfillmentOrchestrator = fulfillmentOrchestrator,
                                notificationCaptureManager = notificationCaptureManager,
                                glassManager = glassManager,
                                cameraViewComposable = cameraViewComposable,
                                permissionRationaleHelper = permissionRationaleHelper,
                                debugPanels = debugPanels,
                                presenceController = presenceController,
                                debugTimerFlowEnabled = debugTimerFlowEnabled,
                                onNavigate = ::pushRoute,
                                onBack = ::handleBack,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        CosmoNavDisplay(
                            backStack = backStack,
                            sceneStrategies = emptyList(),
                            settingStore = settingStore,
                            stateManager = stateManager,
                            actionsRepository = actionsRepository,
                            actionHandler = actionHandler,
                            fulfillmentOrchestrator = fulfillmentOrchestrator,
                            notificationCaptureManager = notificationCaptureManager,
                            glassManager = glassManager,
                            cameraViewComposable = cameraViewComposable,
                            permissionRationaleHelper = permissionRationaleHelper,
                            debugPanels = debugPanels,
                            presenceController = presenceController,
                            debugTimerFlowEnabled = debugTimerFlowEnabled,
                            onNavigate = ::pushRoute,
                            onBack = ::handleBack,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun mainTabBarItems(): List<CosmoBarItem> =
    MainTab.entries.map { CosmoBarItem(label = it.label, icon = it.icon) }

@Composable
private fun BottomNavigationChrome(
    selectedTab: MainTab,
    frosted: Boolean = false,
    onSelected: (MainTab) -> Unit
) {
    CosmoBottomBar(
        items = mainTabBarItems(),
        selectedIndex = selectedTab.ordinal,
        onSelected = { onSelected(MainTab.entries[it]) },
        frosted = frosted
    )
}

@Composable
private fun NavigationRailChrome(
    selectedTab: MainTab,
    frosted: Boolean = false,
    onSelected: (MainTab) -> Unit
) {
    CosmoNavRail(
        items = mainTabBarItems(),
        selectedIndex = selectedTab.ordinal,
        onSelected = { onSelected(MainTab.entries[it]) },
        frosted = frosted
    )
}

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun CosmoNavDisplay(
    backStack: NavBackStack<NavKey>,
    sceneStrategies: List<androidx.navigation3.scene.SceneStrategy<NavKey>>,
    settingStore: CosmoSettingStore,
    stateManager: CosmoStateManager,
    actionsRepository: ActionsRepositoryContract,
    actionHandler: FulfillmentActionHandler,
    fulfillmentOrchestrator: FulfillmentOrchestrator,
    notificationCaptureManager: NotificationCaptureController,
    glassManager: CosmoGlassManager,
    cameraViewComposable: DeviceCameraCaptureSurface,
    permissionRationaleHelper: PermissionRationaleHelper,
    debugPanels: Set<CosmoDebugPanel>,
    presenceController: PresenceController,
    debugTimerFlowEnabled: Boolean,
    onNavigate: (CosmoRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        sceneStrategies = sceneStrategies,
        entryProvider =
            entryProvider {
                entry<CosmoRoute.Actions>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    ActionsScreen(actionsRepository, actionHandler, fulfillmentOrchestrator, presenceController, debugTimerFlowEnabled, onNavigate)
                }
                entry<CosmoRoute.Onboarding>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    OnboardingStackScreen(onNavigate = onNavigate)
                }
                entry<CosmoRoute.Capture>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    CaptureScreen(settingStore, stateManager, notificationCaptureManager, glassManager, permissionRationaleHelper)
                }
                entry<CosmoRoute.Camera>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    CameraScreen(settingStore, cameraViewComposable)
                }
                entry<CosmoRoute.Settings>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    SettingsScreen(settingStore, stateManager, permissionRationaleHelper)
                }
                entry<CosmoRoute.Debug>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    DebugScreen(debugPanels)
                }
                entry<CosmoRoute.DebugSupport>(metadata = SupportingPaneSceneStrategy.supportingPane()) {
                    DebugScreen(debugPanels)
                }
                entry<CosmoRoute.ActionDetail>(metadata = SupportingPaneSceneStrategy.supportingPane()) { route ->
                    ActionDetailScreen(route.actionId, actionsRepository, actionHandler, onNavigate)
                }
                entry<CosmoRoute.TaskMonitor>(metadata = SupportingPaneSceneStrategy.supportingPane()) { route ->
                    TaskMonitorRouteScreen(route.title, route.prompt, route.response, actionsRepository)
                }
            }
    )
}

private fun NavBackStack<NavKey>.replaceWith(entries: List<NavKey>) {
    clear()
    addAll(entries)
}

internal fun List<NavKey>.withAdaptiveSupport(supportingPaneMode: Boolean): List<NavKey> {
    val durable = filterNot { it == CosmoRoute.DebugSupport }
    val root = durable.firstOrNull()
    return if (supportingPaneMode && root != CosmoRoute.Debug) {
        durable + CosmoRoute.DebugSupport
    } else {
        durable
    }
}

internal fun NavBackStack<NavKey>.withoutAdaptiveSupport(): List<NavKey> =
    filterNot { it == CosmoRoute.DebugSupport }

@Composable
private fun ActionsScreen(
    actionsRepository: ActionsRepositoryContract,
    actionHandler: FulfillmentActionHandler,
    orchestrator: FulfillmentOrchestrator,
    presenceController: PresenceController,
    debugTimerFlowEnabled: Boolean,
    onNavigate: (CosmoRoute) -> Unit
) {
    val actions by actionsRepository.userFacingActions.collectAsState()
    val executedActions by actionsRepository.executedActions.collectAsState()
    val presenceState by presenceController.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingConfirmation by remember { mutableStateOf<FulfillmentAction?>(null) }
    var askQuery by rememberSaveable { mutableStateOf("") }
    fun submitAsk() {
        val submitted = askQuery.trim()
        if (submitted.isEmpty()) return
        askQuery = ""
        scope.launch { orchestrator.handleUserQuery(submitted, "") }
    }
    DebugTimerFlowAutomation(
        enabled = debugTimerFlowEnabled,
        actions = actions,
        onAskQueryChange = { askQuery = it },
        onSubmit = ::submitAsk,
        onShowConfirmation = { pendingConfirmation = it },
        onConfirm = { action ->
            pendingConfirmation = null
            scope.launch { actionHandler.execute(action) }
        }
    )
    pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Run action") },
            text = { Text(pending.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirmation = null
                        scope.launch { actionHandler.execute(pending) }
                    }
                ) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    PresenceLaunchDeck(
        presenceState = presenceState,
        pendingCount = actions.size,
        executedCount = executedActions.size,
        onBatchRun = { scope.launch { orchestrator.handleBatchRun() } },
        onOpenOnboarding = { onNavigate(CosmoRoute.Onboarding) },
        onCardAction = { cardId, actionId ->
            val action = actions.firstOrNull { it.id == cardId } ?: return@PresenceLaunchDeck
            when {
                actionId.startsWith("review:") -> onNavigate(CosmoRoute.ActionDetail(action.id))
                actionId == "open" -> onNavigate(CosmoRoute.ActionDetail(action.id))
                actionId == "dismiss" -> actionsRepository.markActionAsDismissed(action.id)
                actionId == "run" -> {
                    if (action.requiresExecuteConfirmation()) {
                        pendingConfirmation = action
                    } else {
                        scope.launch { actionHandler.execute(action) }
                    }
                }
            }
        }
    )
}

@Composable
private fun PresenceLaunchDeck(
    presenceState: PresenceState,
    pendingCount: Int,
    executedCount: Int,
    onBatchRun: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onCardAction: (cardId: String, actionId: String) -> Unit
) {
    val ready = presenceState as? PresenceState.Ready
    val cards = ready?.cards?.takeIf { it.isNotEmpty() } ?: launchPreviewCards()
    val orbActivity =
        when (presenceState) {
            is PresenceState.Parked -> presenceState.activity
            is PresenceState.NeedsInput,
            is PresenceState.Ready,
            is PresenceState.Working -> OrbActivity.LISTENING
            is PresenceState.Dismissed,
            PresenceState.Hidden -> OrbActivity.IDLE
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(PresenceTokens.Palette.Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OrbSurface(activity = orbActivity)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "COSMO PRESENCE",
                        color = PresenceTokens.Palette.CyanDim,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Orb / tab / card stack surface is live",
                        color = PresenceTokens.Palette.OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            TabSurface(
                expanded = true,
                modifier = Modifier.align(Alignment.End)
            )

            PresenceDeckActions(
                pendingCount = pendingCount,
                executedCount = executedCount,
                onBatchRun = onBatchRun,
                onOpenOnboarding = onOpenOnboarding
            )

            CardStackSurface(
                cards = cards,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .widthIn(max = PresenceTokens.Size.cardStackMaxWidth)
                        .weight(1f),
                onAction = onCardAction
            )
        }
    }
}

@Composable
private fun PresenceDeckActions(
    pendingCount: Int,
    executedCount: Int,
    onBatchRun: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "PENDING $pendingCount  /  EXECUTED $executedCount",
            color = PresenceTokens.Palette.OnSurfaceDim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        TacticalTextButton("Batch", onBatchRun)
        TacticalTextButton("Onboarding", onOpenOnboarding)
    }
}

@Composable
private fun TacticalTextButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PresenceTokens.Palette.SurfaceGlassHigh)
            .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            color = PresenceTokens.Palette.OnSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun launchPreviewCards(): List<PresenceCard> =
    listOf(
        PresenceCard(
            id = "preview-browser",
            payload =
                CardPayload.Browser(
                    query = "Review launch telemetry and summarize the risky deltas",
                    activeTab = BrowserTab.SOURCE_REVIEW,
                    sources =
                        listOf(
                            Source("Overlay lifecycle", "WindowManager host", 94, "OL"),
                            Source("Permission gate", "SYSTEM_ALERT_WINDOW", 89, "PG")
                        )
                )
        ),
        PresenceCard(
            id = "preview-message",
            payload =
                CardPayload.Messaging(
                    body = "I can take the next action once the card stack is confirmed.",
                    recipients = listOf(Recipient("Design Review", "review@cosmo", "DR")),
                    channel = MessageChannel.CLIPBOARD
                ),
            actions = listOf(CardAction("open", "Open", type = CardActionType.PRIMARY))
        ),
        PresenceCard(
            id = "preview-list",
            payload =
                CardPayload.ListTasks(
                    title = "Surface readiness",
                    items =
                        listOf(
                            CheckItem("Orb visible on launch", checked = true),
                            CheckItem("Tab renders high precision dock", checked = true),
                            CheckItem("Card stack replaces sticker deck")
                        )
                )
        )
    )

@Composable
private fun DebugTimerFlowAutomation(
    enabled: Boolean,
    actions: List<FulfillmentAction>,
    onAskQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowConfirmation: (FulfillmentAction) -> Unit,
    onConfirm: (FulfillmentAction) -> Unit
) {
    var submitted by rememberSaveable(enabled) { mutableStateOf(false) }
    var confirmedActionId by rememberSaveable(enabled) { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled || submitted) return@LaunchedEffect
        submitted = true
        DEBUG_TIMER_FLOW_QUERY.indices.forEach { index ->
            onAskQueryChange(DEBUG_TIMER_FLOW_QUERY.take(index + 1))
            delay(45)
        }
        delay(300)
        onSubmit()
    }
    LaunchedEffect(enabled, actions) {
        if (!enabled) return@LaunchedEffect
        val timer = actions.filterIsInstance<FulfillmentAction.AddTimer>().firstOrNull()
        if (timer == null || timer.id == confirmedActionId) return@LaunchedEffect
        confirmedActionId = timer.id
        delay(700)
        onShowConfirmation(timer)
        delay(800)
        onConfirm(timer)
    }
}

private fun FulfillmentAction.monitorRouteOrNull(): CosmoRoute.TaskMonitor? =
    when (this) {
        is FulfillmentAction.BrowsingAgent -> CosmoRoute.TaskMonitor(title, prompt, "")
        is FulfillmentAction.InvokeAgentQ -> CosmoRoute.TaskMonitor(title, prompt, "")
        is FulfillmentAction.ProvideEventContext -> CosmoRoute.TaskMonitor(title, answer, answer)
        is FulfillmentAction.ProvideInsight -> CosmoRoute.TaskMonitor(title, answer, answer)
        is FulfillmentAction.ProvidePersonContext -> CosmoRoute.TaskMonitor(title, answer, answer)
        is FulfillmentAction.ProvideRecall -> CosmoRoute.TaskMonitor(title, answer, answer)
        else -> null
    }

@Composable
private fun ActionDetailScreen(
    actionId: String,
    actionsRepository: ActionsRepositoryContract,
    actionHandler: FulfillmentActionHandler,
    onNavigate: (CosmoRoute) -> Unit
) {
    val scope = rememberCoroutineScope()
    var pendingConfirmation by remember { mutableStateOf<FulfillmentAction?>(null) }
    pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Run action") },
            text = { Text(pending.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirmation = null
                        scope.launch { actionHandler.execute(pending) }
                    }
                ) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    val current by actionsRepository.currentActions.collectAsState()
    val userFacing by actionsRepository.userFacingActions.collectAsState()
    val executed by actionsRepository.executedActions.collectAsState()
    val dismissed by actionsRepository.userDismissedActions.collectAsState()
    val removed by actionsRepository.cosmoRemovedActions.collectAsState()
    val action = (current + userFacing + executed + dismissed + removed).firstOrNull { it.id == actionId }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(CosmoTokens.Spacing.screenHorizontal)
            .verticalFadingEdges(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(action?.title ?: "Action not found", style = MaterialTheme.typography.headlineSmall)
        }
        if (action != null) {
            item {
                CosmoStickerSurface(
                    containerColor = CosmoTokens.Color.SurfaceHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CosmoSignalTag(
                            text = action::class.simpleName.orEmpty().ifBlank { "ACTION" },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            rotationDegrees = -4f
                        )
                        Text("Status: ${action.status}")
                        Text("ID: ${action.id}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (action.requiresExecuteConfirmation()) {
                                        pendingConfirmation = action
                                    } else {
                                        scope.launch { actionHandler.execute(action) }
                                    }
                                }
                            ) {
                                Text("Run")
                            }
                            AssistChip(onClick = { actionsRepository.markActionAsDismissed(action.id) }, label = { Text("Dismiss") })
                            action.monitorRouteOrNull()?.let { route ->
                                AssistChip(onClick = { onNavigate(route) }, label = { Text("Monitor") })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMonitorRouteScreen(
    title: String,
    prompt: String,
    response: String,
    actionsRepository: ActionsRepositoryContract
) {
    val history by actionsRepository.fulfillmentHistory.collectAsState()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(CosmoTokens.Spacing.screenHorizontal)
            .verticalFadingEdges(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(title.ifBlank { "Task monitor" }, style = MaterialTheme.typography.headlineSmall)
        }
        if (prompt.isNotBlank()) {
            item { MonitorBubble("User", prompt) }
        }
        if (response.isNotBlank()) {
            item { MonitorBubble("Agent", response) }
        }
        items(history) { item ->
            CosmoStickerSurface(
                containerColor = CosmoTokens.Color.SurfaceHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
                rotationDegrees = 1f
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.fulfillmentTriggerType.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.llmResult.take(280), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MonitorBubble(
    role: String,
    text: String
) {
    val agent = role == "Agent"
    CosmoStickerSurface(
        containerColor = if (agent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (agent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
        rotationDegrees = if (agent) 1.5f else -1.5f
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(role, style = MaterialTheme.typography.labelMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CaptureScreen(
    settingStore: CosmoSettingStore,
    stateManager: CosmoStateManager,
    notificationCaptureManager: NotificationCaptureController,
    glassManager: CosmoGlassManager,
    permissionRationaleHelper: PermissionRationaleHelper
) {
    val setting by settingStore.getCosmoSettingFlow().collectAsState()
    val cosmoState by stateManager.getCosmoState().collectAsState()
    val isGlassConnected by glassManager.isGlassConnected().collectAsState()
    val isGlassBound by glassManager.isGlassServiceBound().collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var refreshPermissions by remember { mutableIntStateOf(0) }
    var permissionStates by remember { mutableStateOf<List<RuntimePermissionState>>(emptyList()) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions += 1
        }
    LaunchedEffect(setting, refreshPermissions, activity) {
        permissionStates =
            activity
                ?.let { permissionRationaleHelper.states(it, permissionRationaleHelper.capturePermissions(setting)) }
                .orEmpty()
    }
    val needed = permissionStates.filterNot { it.grantState == RuntimePermissionGrantState.GRANTED }
    Column(
        Modifier.fillMaxSize().padding(CosmoTokens.Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(cosmoState.name, style = MaterialTheme.typography.headlineSmall)
        if (needed.isNotEmpty()) {
            PermissionRationaleCard(
                permissionStates = needed,
                onRequest = {
                    val requestable =
                        needed
                            .filterNot { it.grantState == RuntimePermissionGrantState.DENIED_PERMANENTLY }
                            .map { it.manifestPermission }
                    if (requestable.isNotEmpty()) {
                        scope.launch {
                            permissionRationaleHelper.markRequested(requestable)
                            permissions.launch(requestable.toTypedArray())
                        }
                    }
                },
                onOpenSettings = { context.startActivity(permissionRationaleHelper.appSettingsIntent()) }
            )
        }
        ToggleRow("Ambient audio", setting.ambientAudioEnabled) { settingStore.launchUpdate { ambientAudioEnabled = it } }
        ToggleRow("Screen text", setting.screenContextEnabled) { settingStore.launchUpdate { screenContextEnabled = it } }
        ToggleRow("Screen interaction", setting.screenInteractionCaptureEnabled) {
            settingStore.launchUpdate {
                screenInteractionCaptureEnabled =
                    it
            }
        }
        ToggleRow("Notifications", setting.notificationCaptureEnabled) { settingStore.launchUpdate { notificationCaptureEnabled = it } }
        ToggleRow("Gatekeeper", setting.gatekeeperEnabled) { settingStore.launchUpdate { gatekeeperEnabled = it } }
        ToggleRow("Buds audio", setting.budsAudioEnabled) { settingStore.launchUpdate { budsAudioEnabled = it } }
        ToggleRow("Watch audio", setting.watchAudioEnabled) { settingStore.launchUpdate { watchAudioEnabled = it } }
        ToggleRow("Glass connection", setting.connectToGlassEnabled) { settingStore.launchUpdate { connectToGlassEnabled = it } }
        ToggleRow("LAN meeting devices", setting.deviceControlEnabled) { settingStore.launchUpdate { deviceControlEnabled = it } }
        MetricRow("Glass connected", isGlassConnected.toString())
        MetricRow("Glass service bound", isGlassBound.toString())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { context.startActivity(notificationCaptureManager.getNotificationListenerSettingsIntent()) }
            ) { Text("Notifications") }
            Button(onClick = { glassManager.startProjectedCapture() }) { Text("Glass") }
        }
    }
}

@Composable
private fun PermissionRationaleCard(
    permissionStates: List<RuntimePermissionState>,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    CosmoStickerSurface(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
        rotationDegrees = -1f
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions required", style = MaterialTheme.typography.titleMedium)
            permissionStates.forEach { state ->
                Text(state.descriptor.title, style = MaterialTheme.typography.titleSmall)
                Text(state.descriptor.rationale, style = MaterialTheme.typography.bodySmall)
                if (state.grantState == RuntimePermissionGrantState.DENIED_PERMANENTLY) {
                    Text("Denied permanently. Open Android settings to re-enable it.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permissionStates.any { it.grantState != RuntimePermissionGrantState.DENIED_PERMANENTLY }) {
                    Button(onClick = onRequest) {
                        Text("Grant permissions")
                    }
                }
                if (permissionStates.any { it.grantState == RuntimePermissionGrantState.DENIED_PERMANENTLY }) {
                    Button(onClick = onOpenSettings) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScreen(
    settingStore: CosmoSettingStore,
    cameraViewComposable: DeviceCameraCaptureSurface
) {
    val setting by settingStore.getCosmoSettingFlow().collectAsState()
    Column(Modifier.fillMaxSize().padding(CosmoTokens.Spacing.screenHorizontal)) {
        ToggleRow("Device camera", setting.cameraEnabled) { settingStore.launchUpdate { cameraEnabled = it } }
        if (setting.cameraEnabled) {
            cameraViewComposable.CameraPreview(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SettingsScreen(
    settingStore: CosmoSettingStore,
    stateManager: CosmoStateManager,
    permissionRationaleHelper: PermissionRationaleHelper
) {
    val setting by settingStore.getCosmoSettingFlow().collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var refreshPermissions by remember { mutableIntStateOf(0) }
    var permissionStates by remember { mutableStateOf<List<RuntimePermissionState>>(emptyList()) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions += 1
        }
    LaunchedEffect(refreshPermissions, activity) {
        permissionStates = activity?.let { permissionRationaleHelper.states(it) }.orEmpty()
    }
    val missingPermissions = permissionStates.filterNot { it.grantState == RuntimePermissionGrantState.GRANTED }
    StackScreenScaffold(
        eyebrow = "CONTROL STACK",
        title = "Settings",
        summary = "Tune the presence runtime without leaving the tactical glass surface."
    ) {
        item {
            StackPanel(label = "CORE", title = "Runtime") {
                StackToggleRow("Cosmo", "Master agent availability", setting.globalCosmoEnabled) {
                    if (it) stateManager.enableCosmo() else stateManager.disableCosmo()
                }
                StackToggleRow("Analytics", "Local telemetry and diagnostic counters", setting.analyticsEnabled) {
                    settingStore.launchUpdate { analyticsEnabled = it }
                }
            }
        }
        if (missingPermissions.isNotEmpty()) {
            item {
                StackPanel(label = "PERMISSIONS", title = "Required Access") {
                    missingPermissions.forEach { state ->
                        Text(state.descriptor.title, color = PresenceTokens.Palette.OnSurface, style = MaterialTheme.typography.titleSmall)
                        Text(state.descriptor.rationale, color = PresenceTokens.Palette.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (missingPermissions.any { it.grantState != RuntimePermissionGrantState.DENIED_PERMANENTLY }) {
                            Button(
                                onClick = {
                                    val requestable =
                                        missingPermissions
                                            .filterNot { it.grantState == RuntimePermissionGrantState.DENIED_PERMANENTLY }
                                            .map { it.manifestPermission }
                                    if (requestable.isNotEmpty()) {
                                        scope.launch {
                                            permissionRationaleHelper.markRequested(requestable)
                                            permissions.launch(requestable.toTypedArray())
                                        }
                                    }
                                }
                            ) { Text("Grant") }
                        }
                        if (missingPermissions.any { it.grantState == RuntimePermissionGrantState.DENIED_PERMANENTLY }) {
                            Button(onClick = { context.startActivity(permissionRationaleHelper.appSettingsIntent()) }) {
                                Text("Open Settings")
                            }
                        }
                    }
                }
            }
        }
        item {
            StackPanel(label = "INTELLIGENCE", title = "Gatekeeping") {
                StackToggleRow("Raw audio gatekeeper", "Classify low-level audio frames", setting.rawAudioGatekeeperEnabled) {
                    settingStore.launchUpdate { rawAudioGatekeeperEnabled = it }
                }
                StackToggleRow("Voice match", "Prefer voice-authenticated triggers", setting.voiceMatchEnabled) {
                    settingStore.launchUpdate { voiceMatchEnabled = it }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStackScreen(onNavigate: (CosmoRoute) -> Unit) {
    StackScreenScaffold(
        eyebrow = "SETUP STACK",
        title = "Onboarding",
        summary = "Bring the overlay online in three checks: permission, capture, then live cards."
    ) {
        item {
            StackPanel(label = "01", title = "Enable Overlay") {
                Text(
                    "Grant draw-over-apps access so the orb, tab, and card stack can float above the active app.",
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                TacticalTextButton("Open Settings Stack") { onNavigate(CosmoRoute.Settings) }
            }
        }
        item {
            StackPanel(label = "02", title = "Choose Context Sources") {
                Text(
                    "Turn on the sensors you want Cosmo to use: screen context, notifications, audio, camera, watch, or glass.",
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                TacticalTextButton("Configure Capture") { onNavigate(CosmoRoute.Capture) }
            }
        }
        item {
            StackPanel(label = "03", title = "Verify Live Surface") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OrbSurface(activity = OrbActivity.IDLE)
                    Text(
                        "The idle orb should be visible immediately. When work arrives, this expands into the tab and card stack.",
                        color = PresenceTokens.Palette.OnSurfaceDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StackScreenScaffold(
    eyebrow: String,
    title: String,
    summary: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(PresenceTokens.Palette.Background)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalFadingEdges(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(PresenceTokens.Spacing.cardGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        eyebrow,
                        color = PresenceTokens.Palette.CyanDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        title,
                        color = PresenceTokens.Palette.OnSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        summary,
                        color = PresenceTokens.Palette.OnSurfaceDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StackPanel(
    label: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        fill = PresenceTokens.Palette.SurfaceGlassMedium,
        borderColor = PresenceTokens.Palette.BorderDim,
        showGlowEdge = true
    ) {
        Column(
            Modifier.padding(PresenceTokens.Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    label.uppercase(),
                    color = PresenceTokens.Palette.CyanDim,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    title.uppercase(),
                    color = PresenceTokens.Palette.OnSurface,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}

@Composable
private fun StackToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = PresenceTokens.Palette.OnSurface, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = PresenceTokens.Palette.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DebugScreen(
    panels: Set<CosmoDebugPanel>
) {
    val orderedPanels = remember(panels) { panels.sortedBy { it.order } }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(CosmoTokens.Spacing.screenHorizontal)
            .verticalFadingEdges(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Debug Surfaces (${orderedPanels.size})", style = MaterialTheme.typography.headlineSmall)
        }
        items(orderedPanels, key = { it.id }) { panel ->
            panel.Content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    CosmoStickerSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CosmoTokens.Color.SurfaceHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(CosmoTokens.Shape.pill),
        borderWidth = CosmoTokens.Stroke.medium,
        shadowOffset = CosmoTokens.Decal.tagShadowOffset
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
