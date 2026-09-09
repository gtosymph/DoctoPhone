package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour le score d'énergie quotidien Samsung (Energy Score).
 *
 * La clé primaire est la date (`String` ISO), un seul score existant par jour.
 */
@Entity(tableName = "energy_scores")
data class EnergyScoreEntity(
    @PrimaryKey val date: String,
    val total: Int,
    val sleep: Int?,
    val activity: Int?,
    val nightHeartRate: Int?,
    val nightHeartRateVariability: Int?,
    val sleepDurationMinutes: Int?,
)
