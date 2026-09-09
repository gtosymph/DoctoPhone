package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.SleepStageSegmentEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les segments de stade de sommeil. Les plages temporelles portent sur le début du segment. */
@Dao
interface SleepStageSegmentDao {

    @Upsert
    suspend fun upsertAll(items: List<SleepStageSegmentEntity>)

    @Query("SELECT * FROM sleep_stage_segments WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<SleepStageSegmentEntity>>

    @Query("SELECT * FROM sleep_stage_segments WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<SleepStageSegmentEntity>

    /** Segments d'une nuit donnée, dans leur ordre chronologique. */
    @Query("SELECT * FROM sleep_stage_segments WHERE sleepId = :sleepId ORDER BY startEpochMillis ASC")
    fun observeForSleep(sleepId: String): Flow<List<SleepStageSegmentEntity>>

    @Query("SELECT COUNT(*) FROM sleep_stage_segments")
    suspend fun count(): Int

    @Query("DELETE FROM sleep_stage_segments")
    suspend fun deleteAll()
}
