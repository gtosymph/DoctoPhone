package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour la fréquence respiratoire moyenne d'une nuit. */
@Entity(
    tableName = "respiratory_rate_samples",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class RespiratoryRateEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val breathsPerMinute: Float,
    val min: Float?,
    val max: Float?,
)
