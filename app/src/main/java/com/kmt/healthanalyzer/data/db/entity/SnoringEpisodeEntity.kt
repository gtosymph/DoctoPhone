package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour un épisode de ronflement détecté pendant une nuit. */
@Entity(
    tableName = "snoring_episodes",
    indices = [Index(value = ["startEpochMillis"])],
)
data class SnoringEpisodeEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMinutes: Int,
)
