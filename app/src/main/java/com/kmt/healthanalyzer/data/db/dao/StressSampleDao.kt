package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.StressSampleEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les échantillons de stress. Les plages temporelles portent sur le début de l'échantillon. */
@Dao
interface StressSampleDao {

    @Upsert
    suspend fun upsertAll(items: List<StressSampleEntity>)

    @Query("SELECT * FROM stress_samples WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<StressSampleEntity>>

    @Query("SELECT * FROM stress_samples WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<StressSampleEntity>

    @Query("SELECT COUNT(*) FROM stress_samples")
    suspend fun count(): Int

    @Query("DELETE FROM stress_samples")
    suspend fun deleteAll()
}
