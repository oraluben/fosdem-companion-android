package be.digitalia.fosdem.db

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import be.digitalia.fosdem.db.converters.GlobalTypeConverters
import be.digitalia.fosdem.db.entities.Bookmark
import be.digitalia.fosdem.db.entities.EventEntity
import be.digitalia.fosdem.db.entities.EventTitles
import be.digitalia.fosdem.db.entities.EventToPerson
import be.digitalia.fosdem.model.Day
import be.digitalia.fosdem.model.Link
import be.digitalia.fosdem.model.Person
import be.digitalia.fosdem.model.Track
import be.digitalia.fosdem.utils.SingletonHolder

@Database(entities = [EventEntity::class, EventTitles::class, Person::class, EventToPerson::class,
    Link::class, Track::class, Day::class, Bookmark::class], version = 2, exportSchema = false)
@TypeConverters(GlobalTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    lateinit var sharedPreferences: SharedPreferences
        private set

    abstract val scheduleDao: ScheduleDao
    abstract val bookmarksDao: BookmarksDao

    companion object : SingletonHolder<AppDatabase, Context>({ context ->
        val DB_FILE = "fosdem.sqlite"
        val DB_PREFS_FILE = "database"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) = with(database) {
                // Events: make primary key and track_id not null
                execSQL("CREATE TABLE tmp_${EventEntity.TABLE_NAME} (id INTEGER PRIMARY KEY NOT NULL, day_index INTEGER NOT NULL, start_time INTEGER, end_time INTEGER, room_name TEXT, slug TEXT, track_id INTEGER NOT NULL, abstract TEXT, description TEXT)")
                execSQL("INSERT INTO tmp_${EventEntity.TABLE_NAME} SELECT * FROM ${EventEntity.TABLE_NAME}")
                execSQL("DROP TABLE ${EventEntity.TABLE_NAME}")
                execSQL("ALTER TABLE tmp_${EventEntity.TABLE_NAME} RENAME TO ${EventEntity.TABLE_NAME}")
                execSQL("CREATE INDEX event_day_index_idx ON ${EventEntity.TABLE_NAME} (day_index)")
                execSQL("CREATE INDEX event_start_time_idx ON ${EventEntity.TABLE_NAME} (start_time)")
                execSQL("CREATE INDEX event_end_time_idx ON ${EventEntity.TABLE_NAME} (end_time)")
                execSQL("CREATE INDEX event_track_id_idx ON ${EventEntity.TABLE_NAME} (track_id)")
                // Links: add explicit primary key
                execSQL("CREATE TABLE tmp_${Link.TABLE_NAME} (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, event_id INTEGER NOT NULL, url TEXT NOT NULL, description TEXT)")
                execSQL("INSERT INTO tmp_${Link.TABLE_NAME} SELECT `rowid` AS id, event_id, url, description FROM ${Link.TABLE_NAME}")
                execSQL("DROP TABLE ${Link.TABLE_NAME}")
                execSQL("ALTER TABLE tmp_${Link.TABLE_NAME} RENAME TO ${Link.TABLE_NAME}")
                execSQL("CREATE INDEX link_event_id_idx ON ${Link.TABLE_NAME} (event_id)")
                // Tracks: make primary key not null
                execSQL("CREATE TABLE tmp_${Track.TABLE_NAME} (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL)")
                execSQL("INSERT INTO tmp_${Track.TABLE_NAME} SELECT * FROM ${Track.TABLE_NAME}")
                execSQL("DROP TABLE ${Track.TABLE_NAME}")
                execSQL("ALTER TABLE tmp_${Track.TABLE_NAME} RENAME TO ${Track.TABLE_NAME}")
                execSQL("CREATE UNIQUE INDEX track_main_idx ON ${Track.TABLE_NAME} (name, type)")
                // Days: make primary key not null and rename _index to index
                execSQL("CREATE TABLE tmp_${Day.TABLE_NAME} (`index` INTEGER PRIMARY KEY NOT NULL, date INTEGER NOT NULL)")
                execSQL("INSERT INTO tmp_${Day.TABLE_NAME} SELECT _index as `index`, date FROM ${Day.TABLE_NAME}")
                execSQL("DROP TABLE ${Day.TABLE_NAME}")
                execSQL("ALTER TABLE tmp_${Day.TABLE_NAME} RENAME TO ${Day.TABLE_NAME}")
                // Bookmarks: make primary key not null
                execSQL("CREATE TABLE tmp_${Bookmark.TABLE_NAME} (event_id INTEGER PRIMARY KEY NOT NULL)")
                execSQL("INSERT INTO tmp_${Bookmark.TABLE_NAME} SELECT * FROM ${Bookmark.TABLE_NAME}")
                execSQL("DROP TABLE ${Bookmark.TABLE_NAME}")
                execSQL("ALTER TABLE tmp_${Bookmark.TABLE_NAME} RENAME TO ${Bookmark.TABLE_NAME}")
            }
        }

        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_FILE)
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(JournalMode.TRUNCATE)
                .build().apply {
                    sharedPreferences = context.applicationContext.getSharedPreferences(DB_PREFS_FILE, Context.MODE_PRIVATE)
                }
    })
}