package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour le résultat d'un dépistage d'apnée du sommeil. */
@Entity(
    tableName = "sleep_apnea_results",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class SleepApneaEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val result: Int,
    val averageBreathingDisturbance: Float?,
)
