package com.onhand.llm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onhand.llm.AppContainer
import com.onhand.llm.ui.chat.ChatScreen
import com.onhand.llm.ui.models.ModelsScreen
import com.onhand.llm.ui.server.ServerScreen
import com.onhand.llm.ui.settings.SettingsScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("chat", "チャット", Icons.AutoMirrored.Filled.Chat),
    Tab("models", "モデル", Icons.Filled.Storage),
    Tab("server", "サーバー", Icons.Filled.Dns),
    Tab("settings", "設定", Icons.Filled.Settings),
)

@Composable
fun OnHandRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("chat") {
                ChatScreen(container, onNavigateToModels = {
                    navController.navigate("models") { launchSingleTop = true }
                })
            }
            composable("models") { ModelsScreen(container) }
            composable("server") { ServerScreen(container) }
            composable("settings") { SettingsScreen(container) }
        }
    }
}
