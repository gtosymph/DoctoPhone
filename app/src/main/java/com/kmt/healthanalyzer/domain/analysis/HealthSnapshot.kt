package com.kmt.healthanalyzer.domain.analysis

import java.time.LocalDate
import java.time.LocalTime

/** Les mesures que l'app suit dans le temps. */
enum class HealthMetric(val label: String, val unit: String, val lowerIsBetter: Boolean) {
    STEPS("Pas", "pas/jour", false),
    ACTIVE_MINUTES("Temps actif", "min/jour", false),
    SLEEP_DURATION("Durée de sommeil", "min/nuit", false),
    SLEEP_SCORE("Score de sommeil", "/100", false),
    RESTING_HEART_RATE("Fréquence cardiaque au repos", "bpm", true),
    HRV("Variabilité cardiaque", "ms", false),
    STRESS("Stress", "/100", true),
    WEIGHT("Poids", "kg", true),
    ENERGY_SCORE("Score d'énergie", "/100", false),
    SPO2("Saturation en oxygène", "%", false),
}

enum class TrendDirection { UP, DOWN, STABLE }

/**
 * Comparaison d'une mesure entre la fenêtre récente et la fenêtre précédente.
 *
 * [isImprovement] tient compte du sens de la mesure : une fréquence cardiaque de repos
 * qui baisse est une amélioration, un nombre de pas qui baisse ne l'est pas.
 */
data class MetricTrend(
    val metric: HealthMetric,
    val recentAverage: Double,
    val previousAverage: Double?,
    val direction: TrendDirection,
    val changePercent: Double?,
) {
    val isImprovement: Boolean?
        get() = when (direction) {
            TrendDirection.STABLE -> null
            TrendDirection.UP -> !metric.lowerIsBetter
            TrendDirection.DOWN -> metric.lowerIsBetter
        }
}

/** Régularité et dette de sommeil sur la période observée. */
data class SleepRegularity(
    val averageBedtime: LocalTime?,
    /** Écart-type des heures de coucher, en heures. Au-delà de 1 h, le rythme est irrégulier. */
    val bedtimeSpreadHours: Double,
    val averageDurationMinutes: Int,
    val sleepDebtMinutes: Int,
    val targetMinutes: Int,
    val nightsMeasured: Int,
)

/** Une journée, toutes mesures réunies. */
data class DailySnapshot(
    val date: LocalDate,
    val steps: Int? = null,
    val activeMinutes: Int? = null,
    val activeCalories: Int? = null,
    val restingHeartRate: Int? = null,
    val averageHeartRate: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepScore: Int? = null,
    val bedTime: LocalTime? = null,
    val averageStress: Int? = null,
    val hrvRmssd: Float? = null,
    val spO2: Float? = null,
    val weightKg: Float? = null,
    val energyScore: Int? = null,
) {
    val hasData: Boolean
        get() = steps != null || sleepMinutes != null || restingHeartRate != null ||
            averageStress != null || hrvRmssd != null || energyScore != null ||
            activeMinutes != null || spO2 != null
}

/** Vue consolidée d'une période, prête pour l'écran d'accueil et pour l'analyse LLM. */
data class HealthSnapshot(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DailySnapshot>,
    val trends: List<MetricTrend>,
    val sleepRegularity: SleepRegularity?,
) {
    val daysWithData: Int get() = days.count { it.hasData }
}
