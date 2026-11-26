package pl.szkola.radiowezel.data

import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.data.db.TrackEntity

/**
 * Szablon lekcji:
 * 1 07:30–08:15, 2 08:20–09:05, 3 09:15–10:00, 4 10:15–11:00,
 * 5 11:15–12:00, 6 12:15–13:00, 7 13:05–13:50, 8 13:55–14:40
 *
 * wiosna = start przerwy (koniec lekcji)
 * chopin = koniec przerwy (start lekcji)
 */
object SchoolSchedule {
    const val TEMPLATE_VERSION = 2
    const val DURATION_SECONDS = 60

    /** Koniec lekcji → rozpoczęcie przerwy */
    private val breakStarts = listOf(
        "08:15", "09:05", "10:00", "11:00", "12:00", "13:00", "13:50", "14:40"
    )

    /** Start lekcji → zakończenie przerwy */
    private val breakEnds = listOf(
        "07:30", "08:20", "09:15", "10:15", "11:15", "12:15", "13:05", "13:55"
    )

    fun buildEvents(tracks: List<TrackEntity>): List<BellEventEntity> {
        val startTrackId = findTrackId(tracks, "wiosna")
        val endTrackId = findTrackId(tracks, "chopin")
        val starts = breakStarts.map { label ->
            BellEventEntity(
                timeMinutes = parseMinutes(label),
                type = BellType.BREAK_START,
                trackId = startTrackId,
                durationSeconds = DURATION_SECONDS,
                enabled = true
            )
        }
        val ends = breakEnds.map { label ->
            BellEventEntity(
                timeMinutes = parseMinutes(label),
                type = BellType.BREAK_END,
                trackId = endTrackId,
                durationSeconds = DURATION_SECONDS,
                enabled = true
            )
        }
        return (starts + ends).sortedBy { it.timeMinutes }
    }

    fun findTrackId(tracks: List<TrackEntity>, needle: String): Long? {
        val n = needle.lowercase()
        return tracks.firstOrNull { it.name.lowercase().contains(n) }?.id
    }

    private fun parseMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
