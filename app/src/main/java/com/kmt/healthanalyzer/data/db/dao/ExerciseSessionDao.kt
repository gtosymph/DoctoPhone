package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les sessions d'exercice. Les plages temporelles portent sur le début de la session. */
@Dao
interface ExerciseSessionDao {

    @Upsert
    suspend fun upsertAll(items: List<ExerciseSessionEntity>)

    @Query("SELECT * FROM exercise_sessions WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_sessions WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<ExerciseSessionEntity>

    @Query("SELECT COUNT(*) FROM exercise_sessions")
    suspend fun count(): Int

    @Query("DELETE FROM exercise_sessions")
    suspend fun deleteAll()
}
