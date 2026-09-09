package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.SnoringEpisodeEntity

/** DAO Room pour les épisodes de ronflement. Les plages temporelles portent sur le début de l'épisode. */
@Dao
interface SnoringEpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SnoringEpisodeEntity>)

    @Query("SELECT * FROM snoring_episodes WHERE startEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY startEpochMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<SnoringEpisodeEntity>

    @Query("SELECT COUNT(*) FROM snoring_episodes")
    suspend fun count(): Int

    @Query("DELETE FROM snoring_episodes")
    suspend fun deleteAll()
}
