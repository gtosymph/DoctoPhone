package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour une session d'exercice.
 *
 * L'index sur [startEpochMillis] sert aux requêtes de plage temporelle.
 */
@Entity(
    tableName = "exercise_sessions",
    indices = [Index(value = ["startEpochMillis"])],
)
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val samsungTypeCode: Int?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMinutes: Int,
    val calories: Float?,
    val distanceMeters: Float?,
    val meanHeartRate: Int?,
    val maxHeartRate: Int?,
    val origin: String,
)
