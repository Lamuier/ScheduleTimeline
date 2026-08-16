package com.lamuier.scheduletimeline.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScheduleEvent::class, Category::class],
    version = 4,
    exportSchema = true,
)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleEventDao(): ScheduleEventDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var instance: ScheduleDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_events_dayKey` " +
                        "ON `schedule_events` (`dayKey`)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `schedule_events` ADD COLUMN `team` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `schedule_events` ADD COLUMN `eventType` TEXT NOT NULL DEFAULT 'PERFORMANCE'",
                )
                db.execSQL(
                    "ALTER TABLE `schedule_events` ADD COLUMN `tokutenKind` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `schedule_events` ADD COLUMN `linkedPerformanceId` INTEGER DEFAULT NULL",
                )

                db.execSQL("UPDATE `schedule_events` SET `team` = `title`")
                db.execSQL("UPDATE `schedule_events` SET `title` = ''")

                db.execSQL(
                    """
                    UPDATE `schedule_events`
                    SET `eventType` = 'TOKUTEN', `tokutenKind` = 'PARALLEL'
                    WHERE `category` = '特典'
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE `schedule_events`
                    SET `eventType` = 'PERFORMANCE', `tokutenKind` = ''
                    WHERE `category` = '舞台演出' OR `category` = ''
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE `schedule_events`
                    SET
                      `eventType` = 'PERFORMANCE',
                      `tokutenKind` = '',
                      `note` = CASE
                        WHEN `note` = '' THEN `category`
                        ELSE `note` || ' · ' || `category`
                      END
                    WHERE `category` NOT IN ('舞台演出', '特典', '')
                    """.trimIndent(),
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_events_linkedPerformanceId` " +
                        "ON `schedule_events` (`linkedPerformanceId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_events_team` " +
                        "ON `schedule_events` (`team`)",
                )
            }
        }

        fun get(context: Context): ScheduleDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "schedule_timeline.db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // v1 schema predates categories; wipe only that ancient version
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
