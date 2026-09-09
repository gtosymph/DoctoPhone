package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.RespiratoryRateEntity

/** DAO Room pour les échantillons de fréquence respiratoire. */
@Dao
interface RespiratoryRateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RespiratoryRateEntity>)

    @Query("SELECT * FROM respiratory_rate_samples WHERE timeEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY timeEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<RespiratoryRateEntity>

    @Query("SELECT COUNT(*) FROM respiratory_rate_samples")
    suspend fun count(): Int

    @Query("DELETE FROM respiratory_rate_samples")
    suspend fun deleteAll()
}
