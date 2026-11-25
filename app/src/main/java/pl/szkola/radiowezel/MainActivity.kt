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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import pl.szkola.radiowezel.ui.theme.Amber
import pl.szkola.radiowezel.ui.theme.InkDim
import pl.szkola.radiowezel.ui.theme.PanelBg
import pl.szkola.radiowezel.ui.theme.PanelLine
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
                    containerColor = PanelBg,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = PanelBg,
                            contentColor = Amber,
                            tonalElevation = 0.dp
                        ) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Amber,
                                selectedTextColor = Amber,
                                indicatorColor = PanelLine,
                                unselectedIconColor = InkDim,
                                unselectedTextColor = InkDim
                            )
                            data class Tab(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                            val tabs = listOf(
                                Tab("status", "Status", Icons.Default.Home),
                                Tab("schedule", "Plan", Icons.Default.CalendarMonth),
                                Tab("tracks", "Utwory", Icons.Default.LibraryMusic),
                                Tab("holidays", "Święta", Icons.Default.EventBusy),
                                Tab("settings", "Ustaw.", Icons.Default.Settings)
                            )
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.id,
                                    onClick = {
                                        navController.navigate(tab.id) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, null) },
                                    label = { Text(tab.label) },
                                    colors = colors
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        NavHost(navController, startDestination = "status") {
                            composable("status") { StatusScreen(state, viewModel) }
                            composable("schedule") { ScheduleScreen(state, viewModel) }
                            composable("tracks") { TracksScreen(state, viewModel) }
                            composable("holidays") { HolidaysScreen(state, viewModel) }
                            composable("settings") { SettingsScreen(state, viewModel) }
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
