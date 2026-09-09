package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour le nombre d'étages montés dans une journée.
 *
 * La clé primaire est la date (`String` ISO), comme pour [DailyStepsEntity].
 */
@Entity(tableName = "daily_floors")
data class DailyFloorsEntity(
    @PrimaryKey val date: String,
    val floors: Int,
)
