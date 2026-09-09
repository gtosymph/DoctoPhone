package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.EcgRecordEntity

/** DAO Room pour les enregistrements d'électrocardiogramme. */
@Dao
interface EcgRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EcgRecordEntity>)

    @Query("SELECT * FROM ecg_records WHERE timeEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY timeEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<EcgRecordEntity>

    @Query("SELECT COUNT(*) FROM ecg_records")
    suspend fun count(): Int

    @Query("DELETE FROM ecg_records")
    suspend fun deleteAll()
}
