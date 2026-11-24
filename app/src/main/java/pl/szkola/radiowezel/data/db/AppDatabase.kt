package pl.szkola.radiowezel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromBellType(value: BellType): String = value.name

    @TypeConverter
    fun toBellType(value: String): BellType = BellType.valueOf(value)
}

@Database(
    entities = [BellEventEntity::class, TrackEntity::class, HolidayEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bellEventDao(): BellEventDao
    abstract fun trackDao(): TrackDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "radiowezel.db"
                ).build().also { instance = it }
            }
        }
    }
}
