package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour le total de pas d'une journée.
 *
 * La clé primaire est la date (`String` ISO), car il n'existe qu'un seul
 * total de pas par jour dans le domaine.
 */
@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey val date: String,
    val steps: Int,
    val distanceMeters: Float?,
    val calories: Float?,
    val origin: String,
)
