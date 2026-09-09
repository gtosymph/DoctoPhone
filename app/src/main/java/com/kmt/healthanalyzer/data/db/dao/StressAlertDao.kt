package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.StressAlertEntity

/** DAO Room pour les alertes de stress élevé. Les plages temporelles portent sur le début de l'alerte. */
@Dao
interface StressAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StressAlertEntity>)

    @Query("SELECT * FROM stress_alerts WHERE startEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY startEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<StressAlertEntity>

    @Query("SELECT COUNT(*) FROM stress_alerts")
    suspend fun count(): Int

    @Query("DELETE FROM stress_alerts")
    suspend fun deleteAll()
}
