package com.google.research.air.cosmo.ui.mainscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                debugPanels = debugPanels
            )
        }
    }
}

@Serializable
internal sealed interface CosmoRoute : NavKey {
    @Serializable data object Actions : CosmoRoute
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
    debugPanels: Set<CosmoDebugPanel>
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
                topBar = { CosmoTopBar(title = "Cosmo Genesis", frosted = true) },
                bottomBar = {
                    if (!isSupportingPaneMode) {
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
                            NavigationRailChrome(selectedTab, frosted = true) {
                                restoreTab(it, recordHistory = true)
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
                    ActionsScreen(actionsRepository, actionHandler, fulfillmentOrchestrator, onNavigate)
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
    onNavigate: (CosmoRoute) -> Unit
) {
    val actions by actionsRepository.userFacingActions.collectAsState()
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
    Column(
        Modifier.fillMaxSize().padding(CosmoTokens.Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ACTION DECK", style = MaterialTheme.typography.headlineSmall)
            CosmoSignalTag(
                text = "SYSTEM ONLINE",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                rotationDegrees = 5f
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { orchestrator.handleUserQuery("summarize what I should do next", "") } }) {
                Text("Ask")
            }
            Button(onClick = { scope.launch { orchestrator.handleBatchRun() } }) {
                Text("Batch")
            }
        }
        LazyColumn(
            modifier = Modifier.verticalFadingEdges(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(actions, key = { it.id }) { action ->
                ActionCard(
                    action,
                    onOpen = { onNavigate(CosmoRoute.ActionDetail(action.id)) },
                    onMonitor = action.monitorRouteOrNull()?.let { route -> { onNavigate(route) } },
                    onExecute = {
                        if (action.requiresExecuteConfirmation()) {
                            pendingConfirmation = action
                        } else {
                            scope.launch { actionHandler.execute(action) }
                        }
                    },
                    onDismiss = { actionsRepository.markActionAsDismissed(action.id) }
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: FulfillmentAction,
    onOpen: () -> Unit,
    onMonitor: (() -> Unit)?,
    onExecute: () -> Unit,
    onDismiss: () -> Unit
) {
    val rotation = if (action.id.hashCode() % 2 == 0) -1f else 1f
    CosmoStickerSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        containerColor = CosmoTokens.Color.SurfaceHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
        rotationDegrees = rotation
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CosmoSignalTag(
                text = action::class.simpleName.orEmpty().ifBlank { "ACTION" },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                rotationDegrees = -4f
            )
            Text(action.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpen, label = { Text("Details") })
                if (onMonitor != null) {
                    AssistChip(onClick = onMonitor, label = { Text("Monitor") })
                }
                Button(onClick = onExecute) { Text("Run") }
                AssistChip(onClick = onDismiss, label = { Text("Dismiss") })
            }
        }
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
    Column(
        Modifier.fillMaxSize().padding(CosmoTokens.Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToggleRow("Cosmo", setting.globalCosmoEnabled) {
            if (it) stateManager.enableCosmo() else stateManager.disableCosmo()
        }
        ToggleRow("Analytics", setting.analyticsEnabled) { settingStore.launchUpdate { analyticsEnabled = it } }
        if (missingPermissions.isNotEmpty()) {
            PermissionRationaleCard(
                permissionStates = missingPermissions,
                onRequest = {
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
                },
                onOpenSettings = { context.startActivity(permissionRationaleHelper.appSettingsIntent()) }
            )
        }
        ToggleRow(
            "Raw audio gatekeeper",
            setting.rawAudioGatekeeperEnabled
        ) { settingStore.launchUpdate { rawAudioGatekeeperEnabled = it } }
        ToggleRow("Voice match", setting.voiceMatchEnabled) { settingStore.launchUpdate { voiceMatchEnabled = it } }
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
