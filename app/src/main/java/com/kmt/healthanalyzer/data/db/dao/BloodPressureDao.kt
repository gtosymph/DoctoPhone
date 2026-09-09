package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.BloodPressureEntity

/** DAO Room pour les prises de tension artérielle. */
@Dao
interface BloodPressureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BloodPressureEntity>)

    @Query("SELECT * FROM blood_pressure_readings WHERE timeEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY timeEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<BloodPressureEntity>

    @Query("SELECT COUNT(*) FROM blood_pressure_readings")
    suspend fun count(): Int

    @Query("DELETE FROM blood_pressure_readings")
    suspend fun deleteAll()
}
