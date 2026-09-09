package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.HeartRateSampleEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les échantillons de fréquence cardiaque. */
@Dao
interface HeartRateSampleDao {

    @Upsert
    suspend fun upsertAll(items: List<HeartRateSampleEntity>)

    @Query("SELECT * FROM heart_rate_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<HeartRateSampleEntity>>

    @Query("SELECT * FROM heart_rate_samples WHERE timeEpochMillis BETWEEN :from AND :to ORDER BY timeEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<HeartRateSampleEntity>

    @Query("SELECT COUNT(*) FROM heart_rate_samples")
    suspend fun count(): Int

    /** Échantillon le plus ancien enregistré, ou `null` si la table est vide. */
    @Query("SELECT * FROM heart_rate_samples ORDER BY timeEpochMillis ASC LIMIT 1")
    suspend fun earliest(): HeartRateSampleEntity?

    @Query("DELETE FROM heart_rate_samples")
    suspend fun deleteAll()
}
