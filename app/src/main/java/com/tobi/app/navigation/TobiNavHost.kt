package com.tobi.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tobi.app.core.player.PlayerActivity
import com.tobi.app.core.player.PlayerRequest
import com.tobi.app.core.player.PlayerSessionStore
import com.tobi.app.di.AppContainer
import com.tobi.app.feature.connectors.ConnectorManagerScreen
import com.tobi.app.feature.connectors.ConnectorManagerViewModel
import com.tobi.app.feature.details.DetailsScreen
import com.tobi.app.feature.details.DetailsViewModel
import com.tobi.app.feature.downloads.DownloadsScreen
import com.tobi.app.feature.downloads.DownloadsViewModel
import com.tobi.app.feature.home.HomeScreen
import com.tobi.app.feature.home.HomeViewModel
import com.tobi.app.feature.library.LibraryScreen
import com.tobi.app.feature.library.LibraryViewModel
import com.tobi.app.feature.search.SearchScreen
import com.tobi.app.feature.search.SearchViewModel
import com.tobi.app.feature.settings.SettingsScreen
import com.tobi.app.feature.settings.SettingsViewModel

data class TopDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    TopDestination("home", "Home", Icons.Filled.Home),
    TopDestination("search", "Search", Icons.Filled.Search),
    TopDestination("library", "Library", Icons.Filled.VideoLibrary),
    TopDestination("downloads", "Downloads", Icons.Filled.Download),
    TopDestination("connectors", "Connectors", Icons.Filled.Extension),
    TopDestination("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun TobiNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val openPlayer: (PlayerRequest) -> Unit = { request ->
        PlayerSessionStore.set(request)
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
    Scaffold(
        bottomBar = {
            if (route?.startsWith("details/") != true) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = container.viewModelFactory)
                HomeScreen(
                    viewModel = vm,
                    onOpenEntry = { connector, entry -> navController.navigate("details/${Uri.encode(connector)}/${Uri.encode(entry.uid)}") },
                    onOpenCatalog = { navController.navigate("search") },
                    onOpenConnectors = { navController.navigate("connectors") }
                )
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(factory = container.viewModelFactory)
                SearchScreen(vm) { connector, entry -> navController.navigate("details/${Uri.encode(connector)}/${Uri.encode(entry.uid)}") }
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = container.viewModelFactory)
                LibraryScreen(vm, { connector, uid -> navController.navigate("details/${Uri.encode(connector)}/${Uri.encode(uid)}") }, openPlayer)
            }
            composable("downloads") {
                val vm: DownloadsViewModel = viewModel(factory = container.viewModelFactory)
                DownloadsScreen(vm)
            }
            composable("connectors") {
                val vm: ConnectorManagerViewModel = viewModel(factory = container.viewModelFactory)
                ConnectorManagerScreen(vm)
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel(factory = container.viewModelFactory)
                SettingsScreen(vm)
            }
            composable(
                route = "details/{connectorId}/{uid}",
                arguments = listOf(
                    navArgument("connectorId") { type = NavType.StringType },
                    navArgument("uid") { type = NavType.StringType }
                )
            ) { entry ->
                val vm: DetailsViewModel = viewModel(factory = container.viewModelFactory)
                DetailsScreen(
                    connectorId = entry.arguments?.getString("connectorId").orEmpty(),
                    uid = entry.arguments?.getString("uid").orEmpty(),
                    viewModel = vm,
                    onPlay = openPlayer
                )
            }
        }
    }
}
