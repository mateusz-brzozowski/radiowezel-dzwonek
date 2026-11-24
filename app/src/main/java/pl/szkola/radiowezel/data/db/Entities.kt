package pl.szkola.radiowezel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BellType {
    BREAK_START,
    BREAK_END
}

@Entity(tableName = "bell_events")
data class BellEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Minutes from midnight, e.g. 8:00 = 480 */
    val timeMinutes: Int,
    val type: BellType,
    val trackId: Long?,
    /** Playback duration in seconds */
    val durationSeconds: Int = 30,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Absolute path inside app files dir */
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy-MM-dd */
    val date: String,
    val name: String,
    val builtIn: Boolean = false
)
