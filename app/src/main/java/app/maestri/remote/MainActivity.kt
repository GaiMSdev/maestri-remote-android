package app.maestri.remote

import androidx.fragment.app.FragmentActivity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.maestri.remote.presentation.canvas.CanvasScreen
import app.maestri.remote.presentation.common.MaestriTheme
import app.maestri.remote.presentation.common.OfflineBanner
import app.maestri.remote.presentation.notes.NoteDetailScreen
import app.maestri.remote.presentation.notes.NotesScreen
import app.maestri.remote.presentation.settings.SettingsScreen
import app.maestri.remote.presentation.settings.VoiceSettingsScreen
import app.maestri.remote.presentation.terminal.TerminalScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val connectionState by mainViewModel.connectionState.collectAsState()

            MaestriTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        OfflineBanner(
                            connectionState = connectionState,
                            onReconnectClick = { mainViewModel.reconnect() }
                        )
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "canvas") {
                            composable("canvas") {
                                CanvasScreen(
                                    onNodeClick = { nodeId, initialCommand ->
                                        val encodedCommand = initialCommand?.let { Uri.encode(it) }
                                        val route = if (encodedCommand != null) {
                                            "terminal/$nodeId?initialCommand=$encodedCommand"
                                        } else {
                                            "terminal/$nodeId"
                                        }
                                        navController.navigate(route)
                                    },
                                    onNextStepClick = { nodeId, command ->
                                        val encodedCommand = Uri.encode(command)
                                        navController.navigate("terminal/$nodeId?initialCommand=$encodedCommand")
                                    },
                                    onNotesClick = {
                                        navController.navigate("notes")
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    }
                                )
                            }
                            composable("notes") {
                                NotesScreen(
                                    onNoteClick = { noteId ->
                                        navController.navigate("note_detail/$noteId")
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "note_detail/{noteId}",
                                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                            ) {
                                NoteDetailScreen(onBackClick = { navController.popBackStack() })
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onVoiceSettingsClick = { navController.navigate("voice_settings") }
                                )
                            }
                            composable("voice_settings") {
                                VoiceSettingsScreen(onBackClick = { navController.popBackStack() })
                            }
                            composable(
                                route = "terminal/{nodeId}?initialCommand={initialCommand}",
                                arguments = listOf(
                                    navArgument("nodeId") { type = NavType.StringType },
                                    navArgument("initialCommand") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val nodeId = backStackEntry.arguments?.getString("nodeId") ?: ""
                                TerminalScreen(
                                    nodeId = nodeId,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
