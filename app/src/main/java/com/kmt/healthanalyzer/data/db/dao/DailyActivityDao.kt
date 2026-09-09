package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.DailyActivityEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour le résumé d'activité quotidien. La clé de plage est la date ISO. */
@Dao
interface DailyActivityDao {

    @Upsert
    suspend fun upsertAll(items: List<DailyActivityEntity>)

    @Query("SELECT * FROM daily_activities WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: String, to: String): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activities WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetween(from: String, to: String): List<DailyActivityEntity>

    @Query("SELECT COUNT(*) FROM daily_activities")
    suspend fun count(): Int

    @Query("DELETE FROM daily_activities")
    suspend fun deleteAll()

    /** Journée la plus récente enregistrée, ou `null` si la table est vide. */
    @Query("SELECT * FROM daily_activities ORDER BY date DESC LIMIT 1")
    suspend fun latest(): DailyActivityEntity?
}
