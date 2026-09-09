package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.SkinTemperatureEntity

/** DAO Room pour les échantillons de température cutanée. */
@Dao
interface SkinTemperatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SkinTemperatureEntity>)

    @Query("SELECT * FROM skin_temperature_samples WHERE timeEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY timeEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<SkinTemperatureEntity>

    @Query("SELECT COUNT(*) FROM skin_temperature_samples")
    suspend fun count(): Int

    @Query("DELETE FROM skin_temperature_samples")
    suspend fun deleteAll()
}
