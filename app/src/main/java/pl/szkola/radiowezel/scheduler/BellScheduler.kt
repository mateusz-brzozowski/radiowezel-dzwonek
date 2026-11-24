package pl.szkola.radiowezel.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.util.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BellScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val db get() = (context.applicationContext as RadiowezelApp).database

    suspend fun rescheduleAll() {
        cancelAll()
        val events = db.bellEventDao().getEnabled()
        val holidayDates = db.holidayDao().observeAll().first().map { it.date }.toSet()
        val isHoliday: (LocalDate) -> Boolean = { date ->
            holidayDates.contains(TimeUtils.formatDate(date))
        }

        var next: Pair<BellEventEntity, LocalDateTime>? = null
        val now = LocalDateTime.now()
        for (event in events) {
            val occurrence = TimeUtils.nextOccurrence(event.timeMinutes, now, isHoliday) ?: continue
            if (next == null || occurrence.isBefore(next!!.second)) {
                next = event to occurrence
            }
        }

        next?.let { (event, whenAt) ->
            scheduleExact(REQ_NEXT_BELL, whenAt, event.id)
        }

        scheduleWatchdog(LocalDateTime.now().plusMinutes(20))
    }

    suspend fun findNextBellLabel(): String? {
        val events = db.bellEventDao().getEnabled()
        val holidayDates = db.holidayDao().observeAll().first().map { it.date }.toSet()
        val isHoliday: (LocalDate) -> Boolean = { date ->
            holidayDates.contains(TimeUtils.formatDate(date))
        }
        val now = LocalDateTime.now()
        var best: LocalDateTime? = null
        var bestEvent: BellEventEntity? = null
        for (event in events) {
            val occurrence = TimeUtils.nextOccurrence(event.timeMinutes, now, isHoliday) ?: continue
            if (best == null || occurrence.isBefore(best)) {
                best = occurrence
                bestEvent = event
            }
        }
        return if (best != null && bestEvent != null) {
            val type = if (bestEvent.type.name == "BREAK_START") "start przerwy" else "koniec przerwy"
            "${TimeUtils.minutesToLabel(bestEvent.timeMinutes)} ($type) — ${best.toLocalDate()}"
        } else null
    }

    private fun scheduleExact(requestCode: Int, whenAt: LocalDateTime, eventId: Long) {
        val triggerAt = whenAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, pl.szkola.radiowezel.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, BellAlarmReceiver::class.java).apply {
                action = BellAlarmReceiver.ACTION_BELL
                putExtra(BellAlarmReceiver.EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
    }

    private fun scheduleWatchdog(whenAt: LocalDateTime) {
        val triggerAt = whenAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_WATCHDOG,
            Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelAll() {
        val bellPi = PendingIntent.getBroadcast(
            context,
            REQ_NEXT_BELL,
            Intent(context, BellAlarmReceiver::class.java).apply {
                action = BellAlarmReceiver.ACTION_BELL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(bellPi)

        val watchdogPi = PendingIntent.getBroadcast(
            context,
            REQ_WATCHDOG,
            Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(watchdogPi)
    }

    companion object {
        const val REQ_NEXT_BELL = 2001
        const val REQ_WATCHDOG = 2002
    }
}
