package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.DailyStepsEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour le total de pas quotidien. La clé de plage est la date ISO. */
@Dao
interface DailyStepsDao {

    @Upsert
    suspend fun upsertAll(items: List<DailyStepsEntity>)

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: String, to: String): Flow<List<DailyStepsEntity>>

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetween(from: String, to: String): List<DailyStepsEntity>

    @Query("SELECT COUNT(*) FROM daily_steps")
    suspend fun count(): Int

    @Query("DELETE FROM daily_steps")
    suspend fun deleteAll()

    /** Journée la plus récente enregistrée, ou `null` si la table est vide. */
    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT 1")
    suspend fun latest(): DailyStepsEntity?

    /** Journée la plus ancienne enregistrée, ou `null` si la table est vide. */
    @Query("SELECT * FROM daily_steps ORDER BY date ASC LIMIT 1")
    suspend fun earliest(): DailyStepsEntity?
}
