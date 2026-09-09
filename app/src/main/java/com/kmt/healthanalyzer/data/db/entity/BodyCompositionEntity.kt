package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour une mesure de composition corporelle. */
@Entity(
    tableName = "body_compositions",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class BodyCompositionEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val weightKg: Float,
    val heightCm: Float?,
    val bodyMassIndex: Float?,
    val bodyFatPercent: Float?,
    val bodyFatMassKg: Float?,
    val skeletalMuscleMassKg: Float?,
    val fatFreeMassKg: Float?,
    val totalBodyWaterKg: Float?,
    val basalMetabolicRate: Int?,
    val origin: String,
)
