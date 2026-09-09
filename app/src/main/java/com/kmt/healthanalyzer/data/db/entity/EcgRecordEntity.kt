package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour un enregistrement d'électrocardiogramme. */
@Entity(
    tableName = "ecg_records",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class EcgRecordEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val meanHeartRate: Int?,
    val minHeartRate: Int?,
    val maxHeartRate: Int?,
    val classification: Int?,
    val symptoms: String?,
)
