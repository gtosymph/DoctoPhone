package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entité Room pour une prise de tension artérielle. */
@Entity(
    tableName = "blood_pressure_readings",
    indices = [Index(value = ["timeEpochMillis"])],
)
data class BloodPressureEntity(
    @PrimaryKey val id: String,
    val timeEpochMillis: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val mean: Int?,
)
