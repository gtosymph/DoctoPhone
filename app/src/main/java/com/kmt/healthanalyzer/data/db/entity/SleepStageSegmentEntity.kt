package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour un segment de stade de sommeil à l'intérieur d'une nuit.
 *
 * L'index sur [sleepId] sert à retrouver les segments d'une nuit donnée.
 * L'index sur [startEpochMillis] sert aux requêtes de plage temporelle.
 */
@Entity(
    tableName = "sleep_stage_segments",
    indices = [Index(value = ["sleepId"]), Index(value = ["startEpochMillis"])],
)
data class SleepStageSegmentEntity(
    @PrimaryKey val id: String,
    val sleepId: String,
    val stage: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMinutes: Int,
)
