package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.SpO2SampleEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les échantillons de saturation en oxygène (SpO2). */
@Dao
interface SpO2SampleDao {

    @Upsert
    suspend fun upsertAll(items: List<SpO2SampleEntity>)

    @Query("SELECT * FROM spo2_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<SpO2SampleEntity>>

    @Query("SELECT * FROM spo2_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<SpO2SampleEntity>

    @Query("SELECT COUNT(*) FROM spo2_samples")
    suspend fun count(): Int

    @Query("DELETE FROM spo2_samples")
    suspend fun deleteAll()
}
