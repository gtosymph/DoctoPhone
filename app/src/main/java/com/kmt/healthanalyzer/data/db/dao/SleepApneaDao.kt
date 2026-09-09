package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.SleepApneaEntity

/** DAO Room pour les résultats de dépistage d'apnée du sommeil. */
@Dao
interface SleepApneaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SleepApneaEntity>)

    @Query("SELECT * FROM sleep_apnea_results WHERE timeEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY timeEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<SleepApneaEntity>

    @Query("SELECT COUNT(*) FROM sleep_apnea_results")
    suspend fun count(): Int

    @Query("DELETE FROM sleep_apnea_results")
    suspend fun deleteAll()
}
