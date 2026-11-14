package com.lamuier.scheduletimeline.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleDatabaseMigrationTest {
    private lateinit var context: Context
    private val createdDbNames = mutableListOf<String>()
    private val openedDbs = mutableListOf<ScheduleDatabase>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        openedDbs.forEach { it.close() }
        createdDbNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun migrate2To6() = runBlocking {
        val dbName = newDbName("v2")
        createRawDatabase(dbName, version = 2) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `schedule_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `category` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                    "`startMinutes` INTEGER NOT NULL, `endMinutes` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL, `dayKey` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`name` TEXT NOT NULL, PRIMARY KEY(`name`))",
            )
            db.execSQL("INSERT INTO `categories` (`name`) VALUES ('StarDiary')")
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`) " +
                    "VALUES (1, 'StarDiary', '舞台演出', 'Main', 600, 620, '', '2026-06-01')",
            )
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`) " +
                    "VALUES (2, '银烁花火', '特典', 'Booth', 700, 760, '', '2026-06-01')",
            )
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`) " +
                    "VALUES (3, 'CustomTeam', '签名会', 'Hall', 800, 830, 'Note', '2026-06-01')",
            )
        }

        val migrated = openMigratedDatabase(dbName)
        assertEquals(6, migrated.openHelper.writableDatabase.version)

        val events = migrated.scheduleEventDao().getAll()
        assertEquals(3, events.size)

        assertEquals("StarDiary", events[0].team)
        assertEquals("", events[0].title)
        assertEquals(EventType.PERFORMANCE.storage, events[0].eventType)
        assertEquals("", events[0].tokutenKind)
        assertFalse(events[0].completed)

        assertEquals("银烁花火", events[1].team)
        assertEquals("", events[1].title)
        assertEquals(EventType.TOKUTEN.storage, events[1].eventType)
        assertEquals(TokutenKind.PARALLEL.storage, events[1].tokutenKind)
        assertFalse(events[1].completed)

        assertEquals("CustomTeam", events[2].team)
        assertEquals("", events[2].title)
        assertEquals(EventType.PERFORMANCE.storage, events[2].eventType)
        assertEquals("Note · 签名会", events[2].note)

        assertEquals(listOf("StarDiary"), migrated.categoryDao().observeAll().first().map { it.name })
        assertCompositeOnlyIndex(migrated.openHelper.writableDatabase)
    }

    @Test
    fun migrate3To6() = runBlocking {
        val dbName = newDbName("v3")
        createDatabaseFromSchemaJson(dbName, version = 3) { db ->
            db.execSQL("INSERT INTO `categories` (`name`) VALUES ('TeamV3')")
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`) " +
                    "VALUES (1, 'TeamV3', '特典', 'ZoneA', 600, 660, 'V3Note', '2026-07-01')",
            )
        }

        val migrated = openMigratedDatabase(dbName)
        val events = migrated.scheduleEventDao().getAll()
        assertEquals(1, events.size)
        assertEquals("TeamV3", events.single().team)
        assertEquals(EventType.TOKUTEN.storage, events.single().eventType)
        assertEquals(TokutenKind.PARALLEL.storage, events.single().tokutenKind)
        assertEquals("V3Note", events.single().note)
        assertFalse(events.single().completed)
        assertCompositeOnlyIndex(migrated.openHelper.writableDatabase)
    }

    @Test
    fun migrate4To6() = runBlocking {
        val dbName = newDbName("v4")
        createDatabaseFromSchemaJson(dbName, version = 4) { db ->
            db.execSQL("INSERT INTO `categories` (`name`) VALUES ('TeamV4')")
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`, `team`, `eventType`, `tokutenKind`, `linkedPerformanceId`) " +
                    "VALUES (1, '', '', 'Stage', 700, 720, '', '2026-08-01', 'TeamV4', 'PERFORMANCE', '', NULL)",
            )
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`, `team`, `eventType`, `tokutenKind`, `linkedPerformanceId`) " +
                    "VALUES (2, '午场', '', 'Booth', 730, 800, '', '2026-08-01', 'TeamV4', 'TOKUTEN', 'FINAL', 1)",
            )
        }

        val migrated = openMigratedDatabase(dbName)
        val events = migrated.scheduleEventDao().getAll()
        assertEquals(2, events.size)
        assertEquals(1L, events[1].linkedPerformanceId)
        assertEquals(TokutenKind.FINAL.storage, events[1].tokutenKind)
        assertFalse(events[1].completed)
        assertCompositeOnlyIndex(migrated.openHelper.writableDatabase)
    }

    @Test
    fun migrate5To6() = runBlocking {
        val dbName = newDbName("v5")
        createDatabaseFromSchemaJson(dbName, version = 5) { db ->
            db.execSQL("INSERT INTO `categories` (`name`) VALUES ('TeamV5')")
            db.execSQL(
                "INSERT INTO `schedule_events` " +
                    "(`id`, `title`, `category`, `location`, `startMinutes`, `endMinutes`, `note`, `dayKey`, `team`, `eventType`, `tokutenKind`, `linkedPerformanceId`, `completed`) " +
                    "VALUES (1, '', '', 'Hall', 900, 960, 'done', '2026-09-01', 'TeamV5', 'TOKUTEN', 'PRE', NULL, 1)",
            )
        }

        val migrated = openMigratedDatabase(dbName)
        val event = migrated.scheduleEventDao().getAll().single()
        assertTrue(event.completed)
        assertEquals("TeamV5", event.team)
        assertEquals(TokutenKind.PRE.storage, event.tokutenKind)

        val sqliteDb = migrated.openHelper.writableDatabase
        assertCompositeOnlyIndex(sqliteDb)

        sqliteDb.query(
            "EXPLAIN QUERY PLAN SELECT * FROM schedule_events WHERE dayKey = ? ORDER BY startMinutes ASC, endMinutes ASC",
            arrayOf("2026-09-01"),
        ).use { cursor ->
            val details = buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
                }
            }.joinToString("\n")
            assertTrue(details, details.contains("index_schedule_events_dayKey_startMinutes_endMinutes"))
            assertFalse(details, details.contains("TEMP B-TREE"))
        }
    }

    @Test
    fun exportedSchema6_matchesCompositeIndexDefinition() {
        val schema6 = readSchemaJson(6).getJSONObject("database")
        assertEquals(6, schema6.getInt("version"))
        val entities = schema6.getJSONArray("entities")
        val eventsEntity = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .single { it.getString("tableName") == "schedule_events" }
        val indices = eventsEntity.getJSONArray("indices")
        assertEquals(1, indices.length())
        val index = indices.getJSONObject(0)
        assertEquals("index_schedule_events_dayKey_startMinutes_endMinutes", index.getString("name"))
        val cols = index.getJSONArray("columnNames")
        assertEquals(listOf("dayKey", "startMinutes", "endMinutes"), List(cols.length()) { cols.getString(it) })
    }

    private fun newDbName(tag: String): String =
        "$tag.db".also {
            context.getDatabasePath(it).parentFile?.mkdirs()
            createdDbNames += it
        }

    private fun openMigratedDatabase(dbName: String): ScheduleDatabase {
        return Room.databaseBuilder(context, ScheduleDatabase::class.java, dbName)
            .addMigrations(*ScheduleDatabase.ALL_MIGRATIONS)
            .build()
            .also {
                openedDbs += it
                it.openHelper.writableDatabase
            }
    }

    private fun createRawDatabase(
        dbName: String,
        version: Int,
        seed: (SupportSQLiteDatabase) -> Unit,
    ) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    seed(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createDatabaseFromSchemaJson(
        dbName: String,
        version: Int,
        seed: (SupportSQLiteDatabase) -> Unit,
    ) {
        val databaseJson = readSchemaJson(version).getJSONObject("database")
        createRawDatabase(dbName, version) { db ->
            val entities = databaseJson.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j).getString("createSql")
                        db.execSQL(idxSql.replace("\${TABLE_NAME}", tableName))
                    }
                }
            }
            val setupQueries = databaseJson.getJSONArray("setupQueries")
            for (i in 0 until setupQueries.length()) {
                db.execSQL(setupQueries.getString(i))
            }
            seed(db)
        }
    }

    private fun readSchemaJson(version: Int): JSONObject {
        val schemasBase = listOf(File("schemas"), File("app/schemas")).firstOrNull { it.isDirectory }
            ?: error("Cannot locate Room schemas directory from ${File(".").absolutePath}")
        val file = File(schemasBase, "com.lamuier.scheduletimeline.data.ScheduleDatabase/$version.json")
        require(file.isFile) { "Missing Room schema file: ${file.absolutePath}" }
        return JSONObject(file.readText())
    }

    private fun assertCompositeOnlyIndex(db: SupportSQLiteDatabase) {
        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'schedule_events' AND name LIKE 'index_%' ORDER BY name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertEquals(listOf("index_schedule_events_dayKey_startMinutes_endMinutes"), indices)
    }
}
