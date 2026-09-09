package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour un échantillon de variabilité de la fréquence cardiaque (HRV). */
@Entity(
    tableName = "hrv_samples",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class HrvSampleEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val sdnnMillis: Float?,
    val rmssdMillis: Float?,
)
