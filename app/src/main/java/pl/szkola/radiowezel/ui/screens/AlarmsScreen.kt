package pl.szkola.radiowezel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.szkola.radiowezel.alarm.AlarmType
import pl.szkola.radiowezel.ui.AppViewModel
import pl.szkola.radiowezel.ui.UiState
import pl.szkola.radiowezel.ui.theme.Amber
import pl.szkola.radiowezel.ui.theme.InkDim
import pl.szkola.radiowezel.ui.theme.PanelLine
import pl.szkola.radiowezel.ui.theme.Signal
import pl.szkola.radiowezel.ui.theme.Warn

@Composable
fun AlarmsScreen(state: UiState, viewModel: AppViewModel) {
    var drill by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<AlarmType?>(null) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("ALARMY", style = MaterialTheme.typography.labelLarge, color = Warn)
        Text("System alarmowy szkoły", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (state.assetsComplete) "Paczka dźwięków: kompletna"
            else "Paczka dźwięków: niekompletna — pobierz w Ustawieniach",
            color = if (state.assetsComplete) Signal else Warn,
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.activeAlarmLabel != null) {
            Spacer(Modifier.height(8.dp))
            Text("AKTYWNY: ${state.activeAlarmLabel}", color = Warn, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = PanelLine)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(if (drill) "Tryb: ĆWICZENIE" else "Tryb: PRAWDZIWY ALARM", color = Amber)
            Spacer(Modifier.weight(1f))
            Switch(checked = drill, onCheckedChange = { drill = it })
        }
        Text(
            if (drill) "Komunikaty ćwiczeniowe / bezpieczniejsze domyślne."
            else "Komunikaty rzeczywiste — używaj tylko przy zagrożeniu.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkDim
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.stopAlarm() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Warn, contentColor = Color.White)
        ) {
            Text("STOP ALARM", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))
        AlarmType.entries.forEach { type ->
            OutlinedButton(
                onClick = { pending = type; pin = ""; pinError = false },
                enabled = state.assetsComplete || type == AlarmType.TEST,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(type.label.uppercase())
            }
        }
    }

    pending?.let { type ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (drill) "Ćwiczenie: ${type.label}" else type.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Podaj PIN alarmowy, aby potwierdzić.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); pinError = false },
                        label = { Text("PIN") },
                        isError = pinError,
                        singleLine = true
                    )
                    if (pinError) Text("Nieprawidłowy PIN", color = Warn)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.confirmAndStartAlarm(type, drill, pin)) {
                        pending = null
                    } else {
                        pinError = true
                    }
                }) { Text("URUCHOM") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Anuluj") }
            }
        )
    }
}
