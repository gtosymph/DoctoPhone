package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour un échantillon de stress.
 *
 * L'index sur [startEpochMillis] sert aux requêtes de plage temporelle.
 */
@Entity(
    tableName = "stress_samples",
    indices = [Index(value = ["startEpochMillis"])],
)
data class StressSampleEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val score: Int,
    val min: Int?,
    val max: Int?,
)
