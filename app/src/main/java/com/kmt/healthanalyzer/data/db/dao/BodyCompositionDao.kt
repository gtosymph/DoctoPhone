package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.BodyCompositionEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les mesures de composition corporelle. */
@Dao
interface BodyCompositionDao {

    @Upsert
    suspend fun upsertAll(items: List<BodyCompositionEntity>)

    @Query("SELECT * FROM body_compositions WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<BodyCompositionEntity>>

    @Query("SELECT * FROM body_compositions WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<BodyCompositionEntity>

    @Query("SELECT COUNT(*) FROM body_compositions")
    suspend fun count(): Int

    @Query("DELETE FROM body_compositions")
    suspend fun deleteAll()
}
