package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.DailyFloorsEntity
import java.time.LocalDate

/** DAO Room pour le total d'étages quotidien. La clé de plage est la date ISO. */
@Dao
interface DailyFloorsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DailyFloorsEntity>)

    @Query("SELECT * FROM daily_floors WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun between(from: LocalDate, to: LocalDate): List<DailyFloorsEntity>

    @Query("SELECT COUNT(*) FROM daily_floors")
    suspend fun count(): Int

    @Query("DELETE FROM daily_floors")
    suspend fun deleteAll()
}
