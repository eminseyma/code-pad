package com.codepad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codepad.app.ui.AssistantScreen
import com.codepad.app.ui.CodePadTheme
import com.codepad.app.ui.EditorScreen
import com.codepad.app.ui.Forest
import com.codepad.app.ui.Green
import com.codepad.app.ui.MintLight
import com.codepad.app.ui.Muted
import com.codepad.app.ui.NotebookScreen
import com.codepad.app.ui.NotebooksScreen
import com.codepad.app.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodePadTheme { CodePadApp() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("notebooks", "Defterler", Icons.Outlined.GridView),
    Tab("assistant", "Asistan", Icons.Outlined.AutoAwesome),
    Tab("settings", "Ayarlar", Icons.Outlined.Settings),
)

@Composable
fun CodePadApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (Tabs.any { it.route == route }) {
                NavigationBar(containerColor = Color.White) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("notebooks") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Forest,
                                selectedTextColor = Green,
                                indicatorColor = MintLight,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "notebooks", modifier = Modifier.padding(padding)) {
            composable("notebooks") {
                NotebooksScreen(
                    vm,
                    onOpenNotebook = { nav.navigate("notebook/$it") },
                    onOpenNote = { nav.navigate("editor/$it") },
                    onNewNote = { scope.launch { nav.navigate("editor/${vm.createNote(null)}") } },
                )
            }
            composable("notebook/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                NotebookScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onOpenNote = { nav.navigate("editor/$it") },
                    onNewNote = { scope.launch { nav.navigate("editor/${vm.createNote(id)}") } },
                )
            }
            composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                EditorScreen(vm, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable("assistant") { AssistantScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
