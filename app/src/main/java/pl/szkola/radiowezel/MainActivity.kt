package pl.szkola.radiowezel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.szkola.radiowezel.ui.AppViewModel
import pl.szkola.radiowezel.ui.screens.HolidaysScreen
import pl.szkola.radiowezel.ui.screens.ScheduleScreen
import pl.szkola.radiowezel.ui.screens.SettingsScreen
import pl.szkola.radiowezel.ui.screens.StatusScreen
import pl.szkola.radiowezel.ui.screens.TracksScreen
import pl.szkola.radiowezel.ui.theme.RadiowezelTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadiowezelTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "status"

                LaunchedEffect(state.snackbar) {
                    state.snackbar?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearSnackbar()
                    }
                }

                if (state.settings.pendingForceUpdate) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Wymagana aktualizacja") },
                        text = {
                            Text(
                                state.settings.pendingChangelog.ifBlank {
                                    "Dostępna jest wymuszona aktualizacja aplikacji."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.installPendingUpdate() }) {
                                Text("Zainstaluj")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.checkUpdates(true) }) {
                                Text("Sprawdź ponownie")
                            }
                        }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = route == "status",
                                onClick = { navController.navigate("status") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Home, null) },
                                label = { Text("Status") }
                            )
                            NavigationBarItem(
                                selected = route == "schedule",
                                onClick = { navController.navigate("schedule") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.CalendarMonth, null) },
                                label = { Text("Plan") }
                            )
                            NavigationBarItem(
                                selected = route == "tracks",
                                onClick = { navController.navigate("tracks") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.LibraryMusic, null) },
                                label = { Text("Utwory") }
                            )
                            NavigationBarItem(
                                selected = route == "holidays",
                                onClick = { navController.navigate("holidays") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.EventBusy, null) },
                                label = { Text("Święta") }
                            )
                            NavigationBarItem(
                                selected = route == "settings",
                                onClick = { navController.navigate("settings") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, null) },
                                label = { Text("Ustawienia") }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        NavHost(navController, startDestination = "status") {
                            composable("status") {
                                StatusScreen(state, viewModel)
                            }
                            composable("schedule") {
                                ScheduleScreen(state, viewModel)
                            }
                            composable("tracks") {
                                TracksScreen(state, viewModel)
                            }
                            composable("holidays") {
                                HolidaysScreen(state, viewModel)
                            }
                            composable("settings") {
                                SettingsScreen(state, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }
}
