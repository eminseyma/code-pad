package com.codepad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codepad.app.ui.AssistantScreen
import com.codepad.app.ui.CodePadTheme
import com.codepad.app.ui.EditorScreen
import com.codepad.app.ui.NotebookScreen
import com.codepad.app.ui.NotebooksScreen
import com.codepad.app.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodePadRoot() }
    }
}

@Composable
fun CodePadRoot(vm: AppViewModel = viewModel()) {
    CodePadTheme(vm.theme) { CodePadApp(vm) }
}

@Composable
fun CodePadApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        NavHost(nav, startDestination = "notebooks", modifier = Modifier.padding(padding)) {
            composable("notebooks") {
                NotebooksScreen(
                    vm,
                    onOpenNotebook = { nav.navigate("notebook/$it") },
                    onOpenNote = { nav.navigate("editor/$it") },
                    onNewNote = { scope.launch { nav.navigate("editor/${vm.createNote(null)}") } },
                    onAssistant = { nav.navigate("assistant") },
                    onSettings = { nav.navigate("settings") },
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
            composable("assistant") { AssistantScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
