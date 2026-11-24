package pl.szkola.radiowezel.util

import pl.szkola.radiowezel.data.db.HolidayEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PolishHolidays {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun fixedForYear(year: Int): List<HolidayEntity> {
        val fixed = listOf(
            LocalDate.of(year, 1, 1) to "Nowy Rok",
            LocalDate.of(year, 1, 6) to "Trzech Króli",
            LocalDate.of(year, 5, 1) to "Święto Pracy",
            LocalDate.of(year, 5, 3) to "Święto Konstytucji 3 Maja",
            LocalDate.of(year, 8, 15) to "Wniebowzięcie NMP",
            LocalDate.of(year, 11, 1) to "Wszystkich Świętych",
            LocalDate.of(year, 11, 11) to "Święto Niepodległości",
            LocalDate.of(year, 12, 25) to "Boże Narodzenie",
            LocalDate.of(year, 12, 26) to "Drugi dzień Bożego Narodzenia"
        )
        val easter = easterSunday(year)
        val movable = listOf(
            easter.plusDays(1) to "Poniedziałek Wielkanocny",
            easter.plusDays(60) to "Boże Ciało"
        )
        return (fixed + movable).map { (date, name) ->
            HolidayEntity(date = date.format(fmt), name = name, builtIn = true)
        }
    }

    /** Anonymous Gregorian algorithm */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
