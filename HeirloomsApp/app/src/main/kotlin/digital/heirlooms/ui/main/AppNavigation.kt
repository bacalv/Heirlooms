@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.ui.brand.OliveBranchIcon
import digital.heirlooms.ui.brand.WaxSealOliveIcon
import digital.heirlooms.ui.capsules.CapsuleCreateScreen
import digital.heirlooms.ui.capsules.CapsuleDetailScreen
import digital.heirlooms.ui.capsules.CapsulesScreen
import digital.heirlooms.ui.capsules.PhotoPickerScreen
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.LocalImageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import digital.heirlooms.app.UploadWorker
import digital.heirlooms.ui.explore.ExploreScreen
import digital.heirlooms.ui.garden.CompostHeapScreen
import digital.heirlooms.ui.share.UploadProgressScreen
import digital.heirlooms.ui.garden.GardenScreen
import digital.heirlooms.ui.garden.PhotoDetailScreen
import digital.heirlooms.ui.settings.SettingsScreen
import digital.heirlooms.ui.shared.SharedPlotsScreen
import digital.heirlooms.ui.social.FriendsScreen
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal object Routes {
    const val GARDEN = "garden"
    const val EXPLORE = "explore"
    const val SHARED = "shared"
    // Photo detail — neutral route serving garden/explore/compost flavours via ?from= param.
    const val PHOTO_DETAIL = "photo/{uploadId}?from={from}"
    const val COMPOST = "compost"
    const val CAPSULES = "capsules"
    const val CAPSULE_DETAIL = "capsules/{capsuleId}"
    const val CAPSULE_CREATE = "capsule_create"
    const val PHOTO_PICKER = "photo_picker"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val DEVICES_ACCESS = "devices_access"
    const val FRIENDS = "friends"
    const val PAIRING = "pairing"
    const val UPLOAD_PROGRESS = "upload_progress/{sessionTag}"
    const val FLOWS = "flows"
    const val STAGING = "staging/{flowId}/{plotId}"
    fun uploadProgress(sessionTag: String) = "upload_progress/$sessionTag"
    fun staging(flowId: String, plotId: String) = "staging/$flowId/$plotId"

    fun photoDetail(uploadId: String, from: String = "garden") = "photo/$uploadId?from=$from"
    fun capsuleDetail(capsuleId: String) = "capsules/$capsuleId"
    // Navigate to Explore with pre-applied filters (pushes on back stack, not a tab switch).
    fun exploreWithTags(tags: List<String>) = "explore?initial_tags=${tags.joinToString(",")}"
    fun exploreJustArrived() = "explore?just_arrived=true"
    fun exploreWithPlot(plotId: String) = "explore?initial_plot_id=$plotId"
}

// Compose Nav's currentRoute returns the full pattern including query placeholders.
// Match tab selection by prefix so "explore?initial_tags=..." still highlights Explore.
private fun String?.matchesTab(tabRoute: String) =
    this == tabRoute || (this?.startsWith("$tabRoute?") == true)

private enum class Tab(val route: String, val label: String) {
    Garden(Routes.GARDEN, "Garden"),
    Explore(Routes.EXPLORE, "Explore"),
    Capsules(Routes.CAPSULES, "Capsules"),
    Shared(Routes.SHARED, "Shared"),
    Burger("burger", "More"),
}

@Composable
fun MainNavigation(apiKey: String, onApiKeyReset: () -> Unit, store: digital.heirlooms.app.EndpointStore) {
    val context = LocalContext.current
    val api = remember(apiKey) { HeirloomsApi(apiKey = apiKey) }
    val imageLoader = remember(apiKey) {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(
                    callFactory = {
                        OkHttpClient.Builder()
                            .addInterceptor { chain ->
                                chain.proceed(
                                    chain.request().newBuilder()
                                        .header("X-Api-Key", apiKey)
                                        .build()
                                )
                            }
                            .build()
                    }
                ))
            }
            .build()
    }

    CompositionLocalProvider(
        LocalHeirloomsApi provides api,
        LocalImageLoader provides imageLoader,
    ) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        val burgerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        var showBurger by remember { mutableStateOf(false) }
        var displayName by remember { mutableStateOf(store.getDisplayName()) }

        // Back-fill display name for users already logged in before v0.45.9.
        LaunchedEffect(Unit) {
            if (displayName.isEmpty()) {
                try {
                    val me = api.authMe()
                    store.setDisplayName(me.displayName)
                    displayName = me.displayName
                } catch (_: Exception) {}
            }
        }

        // Observe active uploads so the Burger entry appears only when needed.
        val activeWorkInfos by WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(UploadWorker.TAG)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val uploadsActive = activeWorkInfos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        // Find a session tag from the first active job to link the Burger entry to the right screen.
        val activeSessionTag = if (uploadsActive) activeWorkInfos
            .firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            ?.tags?.firstOrNull { it.startsWith("session:") }
        else null

        if (showBurger) {
            BurgerPanel(
                sheetState = burgerSheetState,
                onDismiss = { showBurger = false },
                onSettingsTap = { navController.navigate(Routes.SETTINGS) },
                onCompostHeapTap = { navController.navigate(Routes.COMPOST) },
                uploadsInProgress = uploadsActive,
                onUploadsTap = {
                    activeSessionTag?.let { navController.navigate(Routes.uploadProgress(it)) }
                },
                onDiagnosticsTap = { navController.navigate(Routes.DIAGNOSTICS) },
                onDevicesAccessTap = { navController.navigate(Routes.DEVICES_ACCESS) },
                onFriendsTap = { navController.navigate(Routes.FRIENDS) },
                onFlowsTap = { navController.navigate(Routes.FLOWS) },
                displayName = displayName,
            )
        }

        Scaffold(
            containerColor = Parchment,
            bottomBar = {
                HeirloomsBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (tab == Tab.Burger) {
                            scope.launch {
                                showBurger = true
                                burgerSheetState.show()
                            }
                        } else {
                            scope.launch { burgerSheetState.hide() }
                            showBurger = false
                            navController.navigateToTab(tab.route)
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Parchment)
                    .padding(innerPadding)
            ) {
                AppNavHost(
                    navController = navController,
                    apiKey = apiKey,
                    onApiKeyReset = onApiKeyReset,
                    store = store,
                )
            }
        }
    }
}

@Composable
private fun HeirloomsBottomNav(
    currentRoute: String?,
    onTabSelected: (Tab) -> Unit,
) {
    NavigationBar(containerColor = Parchment, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute.matchesTab(tab.route)
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                label = { Text(tab.label) },
                icon = {
                    when (tab) {
                        Tab.Garden -> OliveBranchIcon(Modifier.size(24.dp))
                        Tab.Explore -> Icon(Icons.Filled.Search, contentDescription = null)
                        Tab.Capsules -> WaxSealOliveIcon(Modifier.size(24.dp))
                        Tab.Shared -> Icon(Icons.Filled.PeopleAlt, contentDescription = null)
                        Tab.Burger -> Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Forest,
                    selectedTextColor = Forest,
                    indicatorColor = Forest15,
                    unselectedIconColor = Forest.copy(alpha = 0.5f),
                    unselectedTextColor = Forest.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavController, apiKey: String, onApiKeyReset: () -> Unit, store: digital.heirlooms.app.EndpointStore) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = Routes.GARDEN,
    ) {
        composable(Routes.GARDEN) {
            GardenScreen(
                onPhotoTap = { uploadId -> navController.navigate(Routes.photoDetail(uploadId, "garden")) },
                onNavigateToExplore = { plotId, justArrived ->
                    when {
                        justArrived -> navController.navigate(Routes.exploreJustArrived())
                        plotId != null -> navController.navigate(Routes.exploreWithPlot(plotId))
                        else -> navController.navigate(Routes.EXPLORE)
                    }
                },
            )
        }
        composable(
            route = "explore?initial_tags={initial_tags}&just_arrived={just_arrived}&initial_plot_id={initial_plot_id}",
            arguments = listOf(
                navArgument("initial_tags") { type = NavType.StringType; defaultValue = "" },
                navArgument("just_arrived") { type = NavType.BoolType; defaultValue = false },
                navArgument("initial_plot_id") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStack ->
            val initialTagsStr = backStack.arguments?.getString("initial_tags") ?: ""
            val initialTags = if (initialTagsStr.isNotEmpty()) initialTagsStr.split(",") else emptyList()
            val justArrived = backStack.arguments?.getBoolean("just_arrived") ?: false
            val initialPlotId = backStack.arguments?.getString("initial_plot_id")?.takeIf { it.isNotEmpty() }
            ExploreScreen(
                initialTags = initialTags,
                initialJustArrived = justArrived,
                initialPlotId = initialPlotId,
                onPhotoTap = { uploadId -> navController.navigate(Routes.photoDetail(uploadId, "explore")) },
            )
        }
        composable(
            route = Routes.PHOTO_DETAIL,
            arguments = listOf(
                navArgument("uploadId") { type = NavType.StringType },
                navArgument("from") { type = NavType.StringType; defaultValue = "garden" },
            ),
        ) { backStack ->
            val uploadId = backStack.arguments?.getString("uploadId") ?: return@composable
            val from = backStack.arguments?.getString("from") ?: "garden"
            PhotoDetailScreen(
                uploadId = uploadId,
                from = from,
                onBack = { navController.popBackStack() },
                onCapsuleTap = { capsuleId -> navController.navigate(Routes.capsuleDetail(capsuleId)) },
                onStartCapsuleWithPhoto = { id -> navController.navigate("${Routes.CAPSULE_CREATE}?preSelectedId=$id") },
            )
        }
        composable(Routes.COMPOST) {
            CompostHeapScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CAPSULES) {
            CapsulesScreen(
                onCapsuleTap = { capsuleId -> navController.navigate(Routes.capsuleDetail(capsuleId)) },
                onStartCapsule = { navController.navigate(Routes.CAPSULE_CREATE) },
            )
        }
        composable(Routes.CAPSULE_DETAIL) { backStack ->
            val capsuleId = backStack.arguments?.getString("capsuleId") ?: return@composable
            CapsuleDetailScreen(
                capsuleId = capsuleId,
                onBack = { navController.popBackStack() },
                onPhotoTap = { uploadId -> navController.navigate(Routes.photoDetail(uploadId, "garden")) },
            )
        }
        composable("${Routes.CAPSULE_CREATE}?preSelectedId={preSelectedId}") { backStack ->
            val preSelectedId = backStack.arguments?.getString("preSelectedId")
            CapsuleCreateScreen(
                preSelectedUploadId = preSelectedId,
                navController = navController as androidx.navigation.NavHostController,
                onBack = { navController.popBackStack() },
                onCreated = { capsuleId ->
                    navController.popBackStack()
                    navController.navigate(Routes.capsuleDetail(capsuleId))
                },
            )
        }
        composable(Routes.PHOTO_PICKER) {
            PhotoPickerScreen(
                navController = navController as androidx.navigation.NavHostController,
                onDismiss = { navController.popBackStack() },
            )
        }
        composable(Routes.SHARED) {
            SharedPlotsScreen()
        }
        composable(Routes.FRIENDS) {
            FriendsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FLOWS) {
            digital.heirlooms.ui.flows.FlowsScreen(
                onBack = { navController.popBackStack() },
                onStagingTap = { flowId, plotId -> navController.navigate(Routes.staging(flowId, plotId)) },
            )
        }
        composable(
            route = Routes.STAGING,
            arguments = listOf(
                navArgument("flowId") { type = NavType.StringType },
                navArgument("plotId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val flowId = backStack.arguments?.getString("flowId") ?: return@composable
            val plotId = backStack.arguments?.getString("plotId") ?: return@composable
            digital.heirlooms.ui.flows.StagingScreen(
                flowId = flowId,
                plotId = plotId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onApiKeyReset = onApiKeyReset, store = store)
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(apiKey = apiKey, onBack = { navController.popBackStack() })
        }
        composable(Routes.DEVICES_ACCESS) {
            DevicesAccessScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPairing = { navController.navigate(Routes.PAIRING) },
            )
        }
        composable(Routes.PAIRING) {
            PairingScreen(
                onBack = { navController.popBackStack() },
                onPaired = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.UPLOAD_PROGRESS,
            arguments = listOf(navArgument("sessionTag") { type = NavType.StringType }),
        ) { backStack ->
            val sessionTag = backStack.arguments?.getString("sessionTag") ?: return@composable
            UploadProgressScreen(
                sessionTag = sessionTag,
                fromShare = false,
                onGoToGarden = { navController.popBackStack() },
                onContinueInBackground = { navController.popBackStack() },
            )
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
