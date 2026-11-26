package pl.szkola.radiowezel.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pl.szkola.radiowezel.ui.theme.Amber
import pl.szkola.radiowezel.ui.theme.InkDim
import pl.szkola.radiowezel.ui.theme.PanelLine
import pl.szkola.radiowezel.ui.theme.PanelRaised
import pl.szkola.radiowezel.ui.theme.Signal
import pl.szkola.radiowezel.ui.theme.Warn
import pl.szkola.radiowezel.util.BatteryHelper
import pl.szkola.radiowezel.util.KioskHelper
import pl.szkola.radiowezel.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun ScreenChrome(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = Amber)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkDim)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = PanelLine)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PanelRaised,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, PanelLine)
    ) {
        Column(Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
private fun StatusDot(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Signal else Warn)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatusScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val nextTime = state.nextBellLabel?.substringBefore(" ") ?: "--:--"
    val nextDetail = state.nextBellLabel ?: "Brak zaplanowanych dzwonków"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("RADIOWĘZEŁ", style = MaterialTheme.typography.labelLarge, color = Amber)
        Text("Konsola dzwonka", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))

        Panel {
            Text("NASTĘPNY", style = MaterialTheme.typography.labelSmall, color = InkDim)
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.nextBellLabel != null) nextTime else "--:--",
                style = MaterialTheme.typography.displayLarge,
                color = Amber
            )
            Text(nextDetail, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = PanelLine)
            Spacer(Modifier.height(12.dp))
            StatusDot(state.isDeviceOwner, if (state.isDeviceOwner) "Device Owner" else "Brak Device Owner")
            Spacer(Modifier.height(6.dp))
            StatusDot(
                state.batteryOptimizedIgnored,
                if (state.batteryOptimizedIgnored) "Bateria: bez optymalizacji"
                else "Bateria: optymalizacja WŁĄCZONA"
            )
            Spacer(Modifier.height(6.dp))
            StatusDot(state.autoTimeEnabled, if (state.autoTimeEnabled) "Czas automatyczny" else "Czas ręczny — ryzyko")
            Spacer(Modifier.height(6.dp))
            Text(
                "OTA  ${state.settings.lastOtaMessage.ifBlank { "brak sprawdzenia" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim
            )
            Text(
                "v${BuildConfig.VERSION_NAME}  ·  ${state.tracks.size} utw.  ·  ${state.events.count { it.enabled }} dzw.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.ringNow(null) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200))
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("DZWOŃ TERAZ", style = MaterialTheme.typography.labelLarge)
        }

        if (state.tracks.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Najpierw dodaj utwór w zakładce Utwory — inaczej usłyszysz tylko sygnał testowy.",
                style = MaterialTheme.typography.bodyMedium,
                color = Warn
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.refreshStatus() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) { Text("Odśwież") }

        if (!state.batteryOptimizedIgnored) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { BatteryHelper.openUnrestrictedBatterySettings(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Warn)
            ) { Text("Pozwól działać bez optymalizacji baterii") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(state: UiState, viewModel: AppViewModel) {
    var editing by remember { mutableStateOf<BellEventEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ScreenChrome(
            eyebrow = "HARMONOGRAM",
            title = "Plan dnia",
            subtitle = "Jeden schemat pon–pt. Soboty, niedziele i święta są pomijane."
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.events, key = { it.id }) { event ->
                    val trackName = state.tracks.find { it.id == event.trackId }?.name ?: "brak utworu"
                    Panel(
                        modifier = Modifier.clickable {
                            editing = event
                            showDialog = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    TimeUtils.minutesToLabel(event.timeMinutes),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Amber
                                )
                                Text(
                                    when (event.type) {
                                        BellType.BREAK_START -> "START PRZERWY"
                                        BellType.BREAK_END -> "KONIEC PRZERWY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkDim
                                )
                                Text("$trackName  ·  ${event.durationSeconds}s")
                            }
                            IconButton(onClick = { viewModel.deleteEvent(event) }) {
                                Icon(Icons.Default.Delete, "Usuń", tint = InkDim)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = null
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Amber,
            contentColor = Color(0xFF1A1200),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Add, "Dodaj")
        }
    }

    if (showDialog) {
        EventEditorDialog(
            initial = editing,
            tracks = state.tracks,
            onDismiss = { showDialog = false },
            onSave = { id, minutes, type, trackId, duration ->
                viewModel.saveEvent(id, minutes, type, trackId, duration)
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
    onSave: (Long?, Int, BellType, Long?, Int) -> Unit
) {
    val initialMinutes = initial?.timeMinutes ?: (8 * 60)
    var hour by remember { mutableIntStateOf(initialMinutes / 60) }
    var minute by remember { mutableIntStateOf(initialMinutes % 60) }
    var showTimePicker by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(initial?.type ?: BellType.BREAK_START) }
    var trackId by remember { mutableStateOf(initial?.trackId ?: tracks.firstOrNull()?.id) }
    var duration by remember { mutableIntStateOf(initial?.durationSeconds ?: 60) }
    var typeExpanded by remember { mutableStateOf(false) }
    var trackExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowy dzwonek" else "Edycja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Godzina  %02d:%02d".format(hour, minute), style = MaterialTheme.typography.headlineMedium)
                }
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
                        DropdownMenuItem(text = { Text("Start przerwy") }, onClick = {
                            type = BellType.BREAK_START
                            typeExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Koniec przerwy") }, onClick = {
                            type = BellType.BREAK_END
                            typeExpanded = false
                        })
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
                            DropdownMenuItem(text = { Text(track.name) }, onClick = {
                                trackId = track.id
                                trackExpanded = false
                            })
                        }
                    }
                }
                Text("Czas odtwarzania: ${duration}s", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = it.toInt() },
                    valueRange = 5f..120f,
                    steps = 22
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial?.id, hour * 60 + minute, type, trackId, duration)
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Wybierz godzinę") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Anuluj") }
            }
        )
    }
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
        ScreenChrome(
            eyebrow = "BIBLIOTEKA",
            title = "Utwory",
            subtitle = "Pliki kopiowane lokalnie — dzwonek nie potrzebuje Wi‑Fi."
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.tracks, key = { it.id }) { track ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(track.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "ID ${track.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkDim
                                )
                            }
                            IconButton(onClick = { viewModel.ringNow(track.id, 12) }) {
                                Icon(Icons.Default.PlayArrow, "Odtwórz", tint = Amber)
                            }
                            IconButton(onClick = { viewModel.deleteTrack(track) }) {
                                Icon(Icons.Default.Delete, "Usuń", tint = InkDim)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { picker.launch(arrayOf("audio/*", "audio/mpeg", "audio/mp4", "audio/wav")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Amber,
            contentColor = Color(0xFF1A1200),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Add, "Importuj")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(state: UiState, viewModel: AppViewModel) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var name by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    ScreenChrome(
        eyebrow = "KALENDARZ",
        title = "Święta",
        subtitle = "W te dni dzwonek milczy."
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Panel {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Data  ${date.format(dateFmt)}", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nazwa") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.addHoliday(date.format(dateFmt), name.trim())
                                name = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200))
                    ) { Text("Dodaj") }
                }
            }
            items(state.holidays, key = { it.id }) { holiday: HolidayEntity ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(holiday.date, style = MaterialTheme.typography.headlineMedium, color = Amber)
                            Text(holiday.name + if (holiday.builtIn) "  · wbudowane" else "")
                        }
                        IconButton(onClick = { viewModel.deleteHoliday(holiday) }) {
                            Icon(Icons.Default.Delete, "Usuń", tint = InkDim)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = pickerState)
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

    ScreenChrome(eyebrow = "SYSTEM", title = "Ustawienia") {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel {
                Text("AKTUALIZACJE", style = MaterialTheme.typography.labelSmall, color = InkDim)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otaUrl,
                    onValueChange = { otaUrl = it },
                    label = { Text("URL version.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setOtaUrl(otaUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Zapisz URL") }
                OutlinedButton(
                    onClick = { viewModel.checkUpdates(true) },
                    enabled = !state.otaBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) { Text(if (state.otaBusy) "Sprawdzanie…" else "Sprawdź aktualizację") }
            }

            Panel {
                Text("GŁOŚNOŚĆ (MUSIC / AUX)", style = MaterialTheme.typography.labelSmall, color = InkDim)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wymuszaj przy dzwonku")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = volumeEnabled, onCheckedChange = {
                        volumeEnabled = it
                        viewModel.setForceVolume(volume, it)
                    })
                }
                Text("$volume%", style = MaterialTheme.typography.headlineMedium, color = Amber)
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.toInt() },
                    onValueChangeFinished = { viewModel.setForceVolume(volume, volumeEnabled) },
                    valueRange = 10f..100f
                )
            }

            Panel {
                Text("HARMONOGRAM", style = MaterialTheme.typography.labelSmall, color = InkDim)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Wczytuje plan 8 lekcji: wiosna = start przerwy, chopin = koniec przerwy (60 s).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.applySchoolSchedule() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200))
                ) { Text("Wczytaj plan lekcji szkoły") }
            }

            Panel {
                Text("HUAWEI / KIOSK", style = MaterialTheme.typography.labelSmall, color = InkDim)
                Spacer(Modifier.height(8.dp))
                if (state.batteryOptimizedIgnored) {
                    Text("Optymalizacja baterii: wyłączona (OK)", color = Signal)
                } else {
                    Text(
                        "W dialogu wybierz Zezwól / Nie optymalizuj — aplikacja ma działać bez ograniczeń.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Warn
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { BatteryHelper.openUnrestrictedBatterySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Pozwól działać bez optymalizacji baterii") }
                }
                OutlinedButton(
                    onClick = {
                        if (!BatteryHelper.openHuaweiAutostart(context)) {
                            BatteryHelper.openAppDetails(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Autostart Huawei") }
                if (activity != null) {
                    Button(
                        onClick = { KioskHelper.startLockTask(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200))
                    ) { Text("Włącz kiosk") }
                    OutlinedButton(
                        onClick = { KioskHelper.stopLockTask(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Wyłącz kiosk") }
                }
                Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = InkDim)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
