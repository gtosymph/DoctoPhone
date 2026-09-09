package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.SleepNightEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour les nuits de sommeil. Les plages temporelles portent sur l'heure de réveil. */
@Dao
interface SleepNightDao {

    @Upsert
    suspend fun upsertAll(items: List<SleepNightEntity>)

    @Query("SELECT * FROM sleep_nights WHERE wakeTimeEpochMillis BETWEEN :from AND :to ORDER BY wakeTimeEpochMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<SleepNightEntity>>

    @Query("SELECT * FROM sleep_nights WHERE wakeTimeEpochMillis BETWEEN :from AND :to ORDER BY wakeTimeEpochMillis ASC")
    suspend fun getBetween(from: Long, to: Long): List<SleepNightEntity>

    @Query("SELECT COUNT(*) FROM sleep_nights")
    suspend fun count(): Int

    /** Nuit la plus ancienne enregistrée (par heure de réveil), ou `null` si la table est vide. */
    @Query("SELECT * FROM sleep_nights ORDER BY wakeTimeEpochMillis ASC LIMIT 1")
    suspend fun earliest(): SleepNightEntity?

    @Query("DELETE FROM sleep_nights")
    suspend fun deleteAll()
}
