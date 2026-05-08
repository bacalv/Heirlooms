@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.ui.brand.OliveBranchIcon
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.brand.WaxSealOliveIcon
import digital.heirlooms.ui.capsules.CapsuleCreateScreen
import digital.heirlooms.ui.capsules.CapsuleDetailScreen
import digital.heirlooms.ui.capsules.CapsulesScreen
import digital.heirlooms.ui.capsules.PhotoPickerScreen
import digital.heirlooms.ui.common.LocalImageLoader
import digital.heirlooms.ui.garden.CompostHeapScreen
import digital.heirlooms.ui.garden.GardenScreen
import digital.heirlooms.ui.garden.PhotoDetailScreen
import digital.heirlooms.ui.settings.SettingsScreen
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Parchment
import okhttp3.OkHttpClient

// Route constants
internal object Routes {
    const val GARDEN = "garden"
    const val PHOTO_DETAIL = "garden/photo/{uploadId}"
    const val COMPOST = "compost"
    const val CAPSULES = "capsules"
    const val CAPSULE_DETAIL = "capsules/{capsuleId}"
    const val CAPSULE_CREATE = "capsule_create"
    const val PHOTO_PICKER = "photo_picker"
    const val SETTINGS = "settings"

    fun photoDetail(uploadId: String) = "garden/photo/$uploadId"
    fun capsuleDetail(capsuleId: String) = "capsules/$capsuleId"
}

private val topLevelRoutes = setOf(Routes.GARDEN, Routes.CAPSULES, Routes.SETTINGS)

// The three root tabs for the bottom nav
private enum class Tab(val route: String, val label: String) {
    Garden(Routes.GARDEN, "Garden"),
    Capsules(Routes.CAPSULES, "Capsules"),
    Settings(Routes.SETTINGS, "Settings"),
}

/**
 * Hosts the bottom-nav bar and the full navigation graph.
 * The bottom nav stays visible across all destinations (sub-screens are pushed
 * inside the same NavHost, not in separate activities).
 */
@Composable
fun MainNavigation(apiKey: String, onApiKeyReset: () -> Unit) {
    val context = LocalContext.current
    val api = remember(apiKey) { HeirloomsApi(apiKey = apiKey) }
    val imageLoader = remember(apiKey) {
        ImageLoader.Builder(context)
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

        Scaffold(
            containerColor = Parchment,
            bottomBar = {
                HeirloomsBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { tab -> navController.navigateToTab(tab.route) },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Parchment)
                    .padding(innerPadding)
            ) {
                AppNavHost(navController = navController, onApiKeyReset = onApiKeyReset)
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
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                label = { Text(tab.label) },
                icon = {
                    when (tab) {
                        Tab.Garden -> OliveBranchIcon(Modifier.size(24.dp))
                        Tab.Capsules -> WaxSealOliveIcon(Modifier.size(24.dp))
                        Tab.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
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
private fun AppNavHost(navController: NavController, onApiKeyReset: () -> Unit) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = Routes.GARDEN,
    ) {
        composable(Routes.GARDEN) {
            GardenScreen(
                onPhotoTap = { uploadId -> navController.navigate(Routes.photoDetail(uploadId)) },
                onCompostHeapTap = { navController.navigate(Routes.COMPOST) },
            )
        }
        composable(Routes.PHOTO_DETAIL) { backStack ->
            val uploadId = backStack.arguments?.getString("uploadId") ?: return@composable
            PhotoDetailScreen(
                uploadId = uploadId,
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
                onPhotoTap = { uploadId -> navController.navigate(Routes.photoDetail(uploadId)) },
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onApiKeyReset = {
                    onApiKeyReset()
                },
                onCompostHeapTap = { navController.navigate(Routes.COMPOST) },
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
