package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.HrvSampleEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les échantillons de variabilité de la fréquence cardiaque (HRV). */
@Dao
interface HrvSampleDao {

    @Upsert
    suspend fun upsertAll(items: List<HrvSampleEntity>)

    @Query("SELECT * FROM hrv_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<HrvSampleEntity>>

    @Query("SELECT * FROM hrv_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<HrvSampleEntity>

    @Query("SELECT COUNT(*) FROM hrv_samples")
    suspend fun count(): Int

    @Query("DELETE FROM hrv_samples")
    suspend fun deleteAll()
}
