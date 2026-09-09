package com.kmt.healthanalyzer.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Les mesures ponctuelles et cliniques de l'export Samsung.
 *
 * Ces types sont séparés de [HealthModels] parce qu'ils ne décrivent pas une activité
 * continue mais un événement rare : une prise de tension, un enregistrement d'ECG, un
 * test d'apnée. Ils sont peu nombreux mais ils portent le poids clinique du rapport.
 */

/** Une prise de tension artérielle. Les valeurs au poignet restent indicatives. */
data class BloodPressureReading(
    val id: String,
    val time: Instant,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val mean: Int? = null,
)

/**
 * Un enregistrement d'électrocardiogramme.
 *
 * [classification] est le code Samsung du résultat. L'app ne l'interprète pas
 * médicalement : elle le traduit en libellé et renvoie l'utilisateur vers Samsung
 * Health Monitor pour le texte officiel.
 */
data class EcgRecord(
    val id: String,
    val time: Instant,
    val meanHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val classification: Int? = null,
    val symptoms: String? = null,
)

/** Un épisode de ronflement détecté pendant une nuit. */
data class SnoringEpisode(
    val id: String,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Int,
)

/** La fréquence respiratoire moyenne d'une nuit, en cycles par minute. */
data class RespiratoryRateSample(
    val id: String,
    val time: Instant,
    val breathsPerMinute: Float,
    val min: Float? = null,
    val max: Float? = null,
)

/**
 * La température cutanée nocturne, en degrés Celsius.
 *
 * [baseline] est la référence personnelle calculée par la montre. L'écart à cette
 * référence compte davantage que la valeur absolue.
 */
data class SkinTemperatureSample(
    val id: String,
    val time: Instant,
    val celsius: Float,
    val baseline: Float? = null,
    val min: Float? = null,
    val max: Float? = null,
)

/** Le résultat d'un dépistage d'apnée du sommeil. */
data class SleepApneaResult(
    val id: String,
    val time: Instant,
    val result: Int,
    val averageBreathingDisturbance: Float? = null,
)

/** Le nombre d'étages montés dans une journée. */
data class DailyFloors(
    val date: LocalDate,
    val floors: Int,
)

/** Une alerte de stress élevé émise par la montre. */
data class StressAlert(
    val id: String,
    val start: Instant,
    val end: Instant,
)
