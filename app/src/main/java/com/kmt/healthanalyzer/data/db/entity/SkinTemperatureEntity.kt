package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour la température cutanée nocturne. */
@Entity(
    tableName = "skin_temperature_samples",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class SkinTemperatureEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val celsius: Float,
    val baseline: Float?,
    val min: Float?,
    val max: Float?,
)
