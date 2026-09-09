package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour une alerte de stress élevé émise par la montre. */
@Entity(
    tableName = "stress_alerts",
    indices = [Index(value = ["startEpochMillis"])],
)
data class StressAlertEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)
