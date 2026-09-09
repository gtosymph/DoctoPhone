package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour un échantillon de saturation en oxygène (SpO2). */
@Entity(
    tableName = "spo2_samples",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class SpO2SampleEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val percent: Float,
    val min: Float?,
    val max: Float?,
)
