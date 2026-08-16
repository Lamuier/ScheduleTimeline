package com.lamuier.scheduletimeline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleEventDao {
    @Query("SELECT * FROM schedule_events WHERE dayKey = :dayKey ORDER BY startMinutes ASC, endMinutes ASC")
    fun observeByDay(dayKey: String): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ScheduleEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<ScheduleEvent>)

    @Update
    suspend fun update(event: ScheduleEvent)

    @Update
    suspend fun updateAll(events: List<ScheduleEvent>)

    @Delete
    suspend fun delete(event: ScheduleEvent)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE schedule_events SET category = '' WHERE category = :name")
    suspend fun clearCategory(name: String)

    @Query("UPDATE schedule_events SET team = '' WHERE team = :name")
    suspend fun clearTeam(name: String)

    @Query("UPDATE schedule_events SET linkedPerformanceId = NULL WHERE linkedPerformanceId = :id")
    suspend fun clearLinkedPerformance(id: Long)

    @Query("UPDATE schedule_events SET linkedPerformanceId = NULL WHERE linkedPerformanceId IN (:ids)")
    suspend fun clearLinkedPerformances(ids: List<Long>)

    @Query("DELETE FROM schedule_events WHERE dayKey = :dayKey")
    suspend fun deleteByDay(dayKey: String)

    @Query("UPDATE schedule_events SET dayKey = :newKey WHERE dayKey = :oldKey")
    suspend fun reassignDayKey(oldKey: String, newKey: String)

    @Query("SELECT COUNT(*) FROM schedule_events")
    suspend fun count(): Int

    @Query("DELETE FROM schedule_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM schedule_events WHERE dayKey = :dayKey ORDER BY startMinutes ASC, endMinutes ASC")
    suspend fun getByDay(dayKey: String): List<ScheduleEvent>

    @Query("SELECT * FROM schedule_events ORDER BY dayKey ASC, startMinutes ASC, endMinutes ASC")
    suspend fun getAll(): List<ScheduleEvent>

    @Query("SELECT DISTINCT dayKey FROM schedule_events ORDER BY dayKey ASC")
    suspend fun distinctDayKeys(): List<String>

    @Query(
        "SELECT * FROM schedule_events WHERE linkedPerformanceId = :performanceId " +
            "ORDER BY startMinutes ASC, endMinutes ASC",
    )
    suspend fun getByLinkedPerformance(performanceId: Long): List<ScheduleEvent>
}
