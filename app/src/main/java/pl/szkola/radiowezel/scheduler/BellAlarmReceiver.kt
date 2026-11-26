package pl.szkola.radiowezel.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.player.BellPlayerService
import pl.szkola.radiowezel.util.TimeUtils
import java.time.LocalDate

class BellAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_BELL) return
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as RadiowezelApp
                if (pl.szkola.radiowezel.alarm.AlarmState.isActive) {
                    BellScheduler(context).rescheduleAll()
                    return@launch
                }
                val today = LocalDate.now()
                if (!TimeUtils.isSchoolDay(today) ||
                    app.database.holidayDao().isHoliday(TimeUtils.formatDate(today))
                ) {
                    BellScheduler(context).rescheduleAll()
                    return@launch
                }
                val event = app.database.bellEventDao().getById(eventId)
                if (event == null || !event.enabled) {
                    BellScheduler(context).rescheduleAll()
                    return@launch
                }
                val track = event.trackId?.let { app.database.trackDao().getById(it) }
                val typeLabel = when (event.type) {
                    BellType.BREAK_START -> "Start przerwy"
                    BellType.BREAK_END -> "Koniec przerwy"
                }
                BellPlayerService.play(
                    context = context,
                    trackPath = track?.filePath,
                    durationSec = event.durationSeconds,
                    title = "$typeLabel ${TimeUtils.minutesToLabel(event.timeMinutes)}"
                )
                BellScheduler(context).rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BELL = "pl.szkola.radiowezel.action.BELL_ALARM"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
