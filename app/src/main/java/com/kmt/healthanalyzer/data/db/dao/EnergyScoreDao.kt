package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kmt.healthanalyzer.data.db.entity.EnergyScoreEntity
import kotlinx.coroutines.flow.Flow

/** DAO Room pour le score d'énergie quotidien. La clé de plage est la date ISO. */
@Dao
interface EnergyScoreDao {

    @Upsert
    suspend fun upsertAll(items: List<EnergyScoreEntity>)

    @Query("SELECT * FROM energy_scores WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: String, to: String): Flow<List<EnergyScoreEntity>>

    @Query("SELECT * FROM energy_scores WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetween(from: String, to: String): List<EnergyScoreEntity>

    @Query("SELECT COUNT(*) FROM energy_scores")
    suspend fun count(): Int

    @Query("DELETE FROM energy_scores")
    suspend fun deleteAll()

    /** Journée la plus récente enregistrée, ou `null` si la table est vide. */
    @Query("SELECT * FROM energy_scores ORDER BY date DESC LIMIT 1")
    suspend fun latest(): EnergyScoreEntity?
}
