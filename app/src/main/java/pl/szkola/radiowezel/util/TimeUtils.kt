package pl.szkola.radiowezel.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun minutesToLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    fun labelToMinutes(label: String): Int? {
        return try {
            val t = LocalTime.parse(label.trim(), timeFmt)
            t.hour * 60 + t.minute
        } catch (_: Exception) {
            null
        }
    }

    fun formatDate(date: LocalDate): String = date.format(dateFmt)

    fun isSchoolDay(date: LocalDate): Boolean {
        val dow = date.dayOfWeek
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
    }

    fun nextOccurrence(
        timeMinutes: Int,
        from: LocalDateTime = LocalDateTime.now(),
        isHoliday: (LocalDate) -> Boolean
    ): LocalDateTime? {
        var day = from.toLocalDate()
        val targetTime = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
        // Same day if still ahead and school day
        if (isSchoolDay(day) && !isHoliday(day) && from.toLocalTime().isBefore(targetTime)) {
            return LocalDateTime.of(day, targetTime)
        }
        // Search next 400 days
        for (i in 1..400) {
            day = from.toLocalDate().plusDays(i.toLong())
            if (isSchoolDay(day) && !isHoliday(day)) {
                return LocalDateTime.of(day, targetTime)
            }
        }
        return null
    }
}
