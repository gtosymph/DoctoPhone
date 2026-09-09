package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour une nuit de sommeil.
 *
 * L'index sur [wakeTimeEpochMillis] sert aux requêtes de plage temporelle
 * (l'app classe une nuit sous le jour de son réveil).
 */
@Entity(
    tableName = "sleep_nights",
    indices = [Index(value = ["wakeTimeEpochMillis"]), Index(value = ["date"])],
)
data class SleepNightEntity(
    @PrimaryKey val id: String,
    val date: String,
    val bedTimeEpochMillis: Long,
    val wakeTimeEpochMillis: Long,
    val durationMinutes: Int,
    val score: Int?,
    val efficiencyPercent: Float?,
    val latencyMinutes: Int?,
    val physicalRecovery: Int?,
    val mentalRecovery: Int?,
    val remMinutes: Int?,
    val lightMinutes: Int?,
    val deepMinutes: Int?,
    val awakeMinutes: Int?,
    val localBedTime: String?,
    val origin: String,
)
