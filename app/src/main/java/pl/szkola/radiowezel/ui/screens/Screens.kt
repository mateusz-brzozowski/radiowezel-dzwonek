package pl.szkola.radiowezel.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.szkola.radiowezel.BuildConfig
import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.data.db.HolidayEntity
import pl.szkola.radiowezel.data.db.TrackEntity
import pl.szkola.radiowezel.ui.AppViewModel
import pl.szkola.radiowezel.ui.UiState
import pl.szkola.radiowezel.util.BatteryHelper
import pl.szkola.radiowezel.util.KioskHelper
import pl.szkola.radiowezel.util.TimeUtils

@Composable
fun StatusScreen(
    state: UiState,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B3A4B), Color(0xFF2F5D73), Color(0xFFF2F0EA))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Radiowęzeł",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        Text(
            "Bezstresowy dzwonek",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Następny dzwonek", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    state.nextBellLabel ?: "Brak zaplanowanych dzwonków",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text("Wersja ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(
                    if (state.isDeviceOwner) "Device Owner: tak" else "Device Owner: nie",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (state.batteryOptimizedIgnored) "Optymalizacja baterii: wyłączona (OK)"
                    else "Optymalizacja baterii: WŁĄCZONA — ryzyko!",
                    color = if (state.batteryOptimizedIgnored) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.secondary
                )
                if (!state.autoTimeEnabled) {
                    Text(
                        "Uwaga: automatyczna data/czas jest wyłączona",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text("OTA: ${state.settings.lastOtaMessage.ifBlank { "jeszcze nie sprawdzano" }}")
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.ringNow(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Dzwoń teraz (test)")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.refreshStatus() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Odśwież status") }

        if (!state.batteryOptimizedIgnored) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { BatteryHelper.requestIgnoreBatteryOptimizations(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Wyłącz optymalizację baterii") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(state: UiState, viewModel: AppViewModel) {
    var editing by remember { mutableStateOf<BellEventEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Harmonogram dnia (pon–pt)", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Soboty, niedziele i święta są pomijane automatycznie.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.events, key = { it.id }) { event ->
                val trackName = state.tracks.find { it.id == event.trackId }?.name ?: "— brak utworu —"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editing = event
                            showDialog = true
                        }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                TimeUtils.minutesToLabel(event.timeMinutes),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                when (event.type) {
                                    BellType.BREAK_START -> "Start przerwy"
                                    BellType.BREAK_END -> "Koniec przerwy"
                                }
                            )
                            Text("$trackName • ${event.durationSeconds}s")
                            if (!event.enabled) Text("Wyłączony", color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { viewModel.deleteEvent(event) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Usuń")
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = null
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Dodaj")
        }
    }

    if (showDialog) {
        EventEditorDialog(
            initial = editing,
            tracks = state.tracks,
            onDismiss = { showDialog = false },
            onSave = { id, minutes, type, trackId, duration, enabled ->
                viewModel.saveEvent(id, minutes, type, trackId, duration, enabled)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    initial: BellEventEntity?,
    tracks: List<TrackEntity>,
    onDismiss: () -> Unit,
    onSave: (Long?, Int, BellType, Long?, Int, Boolean) -> Unit
) {
    var timeText by remember {
        mutableStateOf(initial?.let { TimeUtils.minutesToLabel(it.timeMinutes) } ?: "08:00")
    }
    var type by remember { mutableStateOf(initial?.type ?: BellType.BREAK_START) }
    var trackId by remember { mutableStateOf(initial?.trackId) }
    var duration by remember { mutableIntStateOf(initial?.durationSeconds ?: 30) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var typeExpanded by remember { mutableStateOf(false) }
    var trackExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowy dzwonek" else "Edycja dzwonka") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Godzina (HH:mm)") },
                    singleLine = true
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = if (type == BellType.BREAK_START) "Start przerwy" else "Koniec przerwy",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Typ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Start przerwy") },
                            onClick = {
                                type = BellType.BREAK_START
                                typeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Koniec przerwy") },
                            onClick = {
                                type = BellType.BREAK_END
                                typeExpanded = false
                            }
                        )
                    }
                }
                ExposedDropdownMenuBox(expanded = trackExpanded, onExpandedChange = { trackExpanded = it }) {
                    val label = tracks.find { it.id == trackId }?.name ?: "Wybierz utwór"
                    OutlinedTextField(
                        value = label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Utwór") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(trackExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = trackExpanded, onDismissRequest = { trackExpanded = false }) {
                        tracks.forEach { track ->
                            DropdownMenuItem(
                                text = { Text(track.name) },
                                onClick = {
                                    trackId = track.id
                                    trackExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = duration.toString(),
                    onValueChange = { duration = it.toIntOrNull() ?: duration },
                    label = { Text("Czas odtwarzania (s)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Włączony")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = TimeUtils.labelToMinutes(timeText) ?: return@TextButton
                onSave(initial?.id, minutes, type, trackId, duration, enabled)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
fun TracksScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        var name = "utwor.mp3"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        viewModel.importTrack(uri, name)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Biblioteka utworów", style = MaterialTheme.typography.headlineMedium)
                Text("Pliki są kopiowane lokalnie — dzwonek działa bez internetu.")
                Spacer(Modifier.height(8.dp))
            }
            items(state.tracks, key = { it.id }) { track ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.name, style = MaterialTheme.typography.titleLarge)
                            Text(track.filePath, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.ringNow(track.id, 10) }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Odtwórz")
                        }
                        IconButton(onClick = { viewModel.deleteTrack(track) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Usuń")
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { picker.launch(arrayOf("audio/*")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Importuj")
        }
    }
}

@Composable
fun HolidaysScreen(state: UiState, viewModel: AppViewModel) {
    var date by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Święta / dni wolne", style = MaterialTheme.typography.headlineMedium)
            Text("W te dni dzwonek nie gra (oprócz sobót i niedziel).")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Data (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (date.isNotBlank() && name.isNotBlank()) {
                        viewModel.addHoliday(date.trim(), name.trim())
                        date = ""
                        name = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Dodaj dzień wolny") }
            Spacer(Modifier.height(8.dp))
        }
        items(state.holidays, key = { it.id }) { holiday: HolidayEntity ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(holiday.date, style = MaterialTheme.typography.titleLarge)
                        Text(holiday.name + if (holiday.builtIn) " (wbudowane)" else "")
                    }
                    IconButton(onClick = { viewModel.deleteHoliday(holiday) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Usuń")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    var otaUrl by remember(state.settings.otaUrl) { mutableStateOf(state.settings.otaUrl) }
    var volume by remember(state.settings.forceVolumePercent) {
        mutableIntStateOf(state.settings.forceVolumePercent)
    }
    var volumeEnabled by remember(state.settings.forceVolumeEnabled) {
        mutableStateOf(state.settings.forceVolumeEnabled)
    }
    val activity = context as? android.app.Activity

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = otaUrl,
            onValueChange = { otaUrl = it },
            label = { Text("URL version.json (GitHub / HTTPS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(onClick = { viewModel.setOtaUrl(otaUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text("Zapisz URL OTA")
        }
        Button(
            onClick = { viewModel.checkUpdates(true) },
            enabled = !state.otaBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.otaBusy) "Sprawdzanie…" else "Sprawdź aktualizację")
        }
        if (state.settings.pendingForceUpdate) {
            Button(
                onClick = { viewModel.installPendingUpdate() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Zainstaluj wymuszoną aktualizację") }
            Text(state.settings.pendingChangelog)
        }

        Text("Głośność dzwonka (strumień ALARM)", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wymuszaj głośność")
            Spacer(Modifier.weight(1f))
            Switch(checked = volumeEnabled, onCheckedChange = {
                volumeEnabled = it
                viewModel.setForceVolume(volume, it)
            })
        }
        Text("$volume%")
        Slider(
            value = volume.toFloat(),
            onValueChange = { volume = it.toInt() },
            onValueChangeFinished = { viewModel.setForceVolume(volume, volumeEnabled) },
            valueRange = 10f..100f
        )

        Text("Niezawodność Huawei", style = MaterialTheme.typography.titleLarge)
        Button(
            onClick = { BatteryHelper.requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Wyłącz optymalizację baterii") }
        Button(
            onClick = {
                if (!BatteryHelper.openHuaweiAutostart(context)) {
                    BatteryHelper.openAppDetails(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Autostart / chronione aplikacje (Huawei)") }
        Button(
            onClick = { BatteryHelper.openAppDetails(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Szczegóły aplikacji w systemie") }

        if (activity != null) {
            OutlinedButton(
                onClick = { KioskHelper.startLockTask(activity) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Włącz tryb kiosku (Lock Task)") }
            OutlinedButton(
                onClick = { KioskHelper.stopLockTask(activity) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Wyłącz tryb kiosku") }
        }

        Text(
            "Device Owner (opcjonalnie, przez ADB po factory reset):\n" +
                "adb shell dpm set-device-owner pl.szkola.radiowezel/.util.AdminReceiver",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Wersja ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
