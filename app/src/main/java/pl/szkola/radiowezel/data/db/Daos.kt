package pl.szkola.radiowezel.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BellEventDao {
    @Query("SELECT * FROM bell_events ORDER BY timeMinutes ASC, id ASC")
    fun observeAll(): Flow<List<BellEventEntity>>

    @Query("SELECT * FROM bell_events WHERE enabled = 1 ORDER BY timeMinutes ASC")
    suspend fun getEnabled(): List<BellEventEntity>

    @Query("SELECT * FROM bell_events WHERE id = :id")
    suspend fun getById(id: Long): BellEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: BellEventEntity): Long

    @Update
    suspend fun update(event: BellEventEntity)

    @Delete
    suspend fun delete(event: BellEventEntity)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY name ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY name ASC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TrackEntity): Long

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("UPDATE bell_events SET trackId = NULL WHERE trackId = :trackId")
    suspend fun clearTrackRefs(trackId: Long)
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM holidays WHERE date = :date)")
    suspend fun isHoliday(date: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayEntity): Long

    @Delete
    suspend fun delete(holiday: HolidayEntity)

    @Query("SELECT COUNT(*) FROM holidays")
    suspend fun count(): Int
}
