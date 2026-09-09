package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour le résumé d'activité d'une journée.
 *
 * La clé primaire est la date (`String` ISO), un seul résumé existant par jour.
 */
@Entity(tableName = "daily_activities")
data class DailyActivityEntity(
    @PrimaryKey val date: String,
    val steps: Int?,
    val activeMinutes: Int?,
    val exerciseMinutes: Int?,
    val activeCalories: Int?,
    val distanceMeters: Float?,
    val floors: Int?,
)
