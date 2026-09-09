package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour un échantillon de fréquence cardiaque. */
@Entity(
    tableName = "heart_rate_samples",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class HeartRateSampleEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val beatsPerMinute: Int,
    val min: Int?,
    val max: Int?,
    val origin: String,
)
