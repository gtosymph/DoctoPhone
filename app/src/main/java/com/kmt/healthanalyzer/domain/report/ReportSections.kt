package com.kmt.healthanalyzer.domain.report

import kotlinx.serialization.Serializable

/**
 * Les six sections thématiques du rapport.
 *
 * Chaque section porte ses séries et ses indicateurs. Le moteur de dessin lit ces
 * champs par leur nom : renommer un champ ici casse le graphique correspondant.
 * Voir [ReportModel] pour les conventions de format.
 */

// ---------------------------------------------------------------- sommeil

/**
 * Une nuit, rattachée au jour du réveil.
 *
 * [bedRel] et [wakeRel] sont des heures relatives à minuit. Un coucher à 23h vaut
 * `-1.0`, un coucher à 2h vaut `2.0`. Sans cette convention, l'axe se coupe à minuit et
 * la courbe saute d'un bord à l'autre du graphique.
 */
@Serializable
data class SleepNightPoint(
    val date: String,
    val hours: Double,
    val score: Int? = null,
    val bedRel: Double? = null,
    val wakeRel: Double? = null,
    val efficiencyPercent: Double? = null,
    val sessions: Int = 1,
)

@Serializable
data class SleepMonthStat(
    val month: String,
    val meanHours: Double,
    val medianHours: Double,
    val meanScore: Double? = null,
    val nights: Int,
)

/**
 * Répartition des stades sur un mois, en pourcentage.
 *
 * Le dénominateur est la **somme des quatre stades** du mois, pas la durée de sommeil.
 * Les quatre parts totalisent donc exactement 100, et la barre empilée du rapport se
 * remplit sans laisser de vide inexpliqué. Diviser par la durée laisserait un écart
 * quand une partie de la nuit n'a pas été classée.
 */
@Serializable
data class SleepStageMonth(
    val month: String,
    val deep: Double,
    val light: Double,
    val rem: Double,
    val awake: Double,
)

@Serializable
data class SleepSection(
    val nightly: List<SleepNightPoint> = emptyList(),
    val monthly: List<SleepMonthStat> = emptyList(),
    val dayOfWeek: List<LabelValue> = emptyList(),
    val distribution: List<LabelValue> = emptyList(),
    val stagesMonthly: List<SleepStageMonth> = emptyList(),
    val kpi: SleepKpi = SleepKpi(),
)

@Serializable
data class SleepKpi(
    val nights: Int = 0,
    val meanHours: Double? = null,
    val medianHours: Double? = null,
    val bedMedian: Double? = null,
    val wakeMedian: Double? = null,
    val bedSpreadHours: Double? = null,
    val pctAfterMidnight: Double? = null,
    val pctAfter2h: Double? = null,
    val pctUnder6h: Double? = null,
    val pctOver7h: Double? = null,
    val weekendCatchupHours: Double? = null,
    val debtHours: Double? = null,
    val targetHours: Double = 7.5,
    val efficiencyPercent: Double? = null,
    val latencyMinutes: Double? = null,
    val snoringNights: Int = 0,
    val snoringMeasuredNights: Int = 0,
    val snoringMedianMinutes: Double? = null,
    val apneaResult: String? = null,
)

// ---------------------------------------------------------------- coeur

@Serializable
data class HeartMonth(
    val month: String,
    val resting: Double? = null,
    val average: Double? = null,
)

@Serializable
data class BloodPressurePoint(
    val date: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
)

/** Un enregistrement d'ECG. [classificationLabel] traduit le code Samsung en texte lisible. */
@Serializable
data class EcgPoint(
    val date: String,
    val meanHeartRate: Int? = null,
    val classification: Int? = null,
    val classificationLabel: String,
)

@Serializable
data class HeartSection(
    val monthly: List<HeartMonth> = emptyList(),
    val restingDaily: List<DayValue> = emptyList(),
    val hourly: List<LabelValue> = emptyList(),
    val hrvMonthly: List<MonthValue> = emptyList(),
    val hrvDaily: List<DayValue> = emptyList(),
    val bloodPressure: List<BloodPressurePoint> = emptyList(),
    val ecg: List<EcgPoint> = emptyList(),
    val kpi: HeartKpi = HeartKpi(),
)

@Serializable
data class HeartKpi(
    val restingMean: Double? = null,
    val restingP10: Double? = null,
    val restingP90: Double? = null,
    val averageMean: Double? = null,
    val maxObserved: Int? = null,
    val hrvMedian: Double? = null,
    val measuredDays: Int = 0,
)

// ---------------------------------------------------------------- activite

/**
 * Le bilan d'exercice d'un mois.
 *
 * [minutes] et [calories] sont des **totaux** du mois, pas des moyennes par séance.
 * [sessions] compte les séances enregistrées.
 */
@Serializable
data class ExerciseMonth(
    val month: String,
    val sessions: Int,
    val minutes: Double,
    val calories: Double? = null,
)

/**
 * L'activité de la période.
 *
 * [stepsMonthly] et [floorsMonthly] sont des **moyennes quotidiennes** du mois, pas des
 * totaux : une moyenne se compare d'un mois à l'autre même quand les mois n'ont pas la
 * même longueur ni le même nombre de jours mesurés.
 */
@Serializable
data class ActivitySection(
    val stepsDaily: List<DayValue> = emptyList(),
    val stepsRolling7: List<DayValue> = emptyList(),
    val stepsMonthly: List<MonthValue> = emptyList(),
    val stepsDayOfWeek: List<LabelValue> = emptyList(),
    val exerciseMonthly: List<ExerciseMonth> = emptyList(),
    val exerciseByKind: List<LabelValue> = emptyList(),
    val floorsMonthly: List<MonthValue> = emptyList(),
    val kpi: ActivityKpi = ActivityKpi(),
)

@Serializable
data class ActivityKpi(
    val meanSteps: Double? = null,
    val meanSteps30: Double? = null,
    val meanSteps90: Double? = null,
    val bestSteps: Int? = null,
    val bestStepsDate: String? = null,
    val pctDaysUnder3000: Double? = null,
    val pctDaysOver8000: Double? = null,
    val totalExerciseMinutes: Double? = null,
    val exerciseSessions: Int = 0,
    val measuredDays: Int = 0,
)

// ---------------------------------------------------------------- corps

@Serializable
data class BodyPoint(
    val date: String,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val skeletalMuscleKg: Double? = null,
    val bodyMassIndex: Double? = null,
    val basalMetabolicRate: Int? = null,
)

@Serializable
data class BodySection(
    val daily: List<BodyPoint> = emptyList(),
    val kpi: BodyKpi = BodyKpi(),
)

@Serializable
data class BodyKpi(
    val firstWeightKg: Double? = null,
    val lastWeightKg: Double? = null,
    val deltaKg: Double? = null,
    val deltaMuscleKg: Double? = null,
    val deltaFatKg: Double? = null,
    val bodyMassIndex: Double? = null,
    val bodyFatPercent: Double? = null,
    val basalMetabolicRate: Int? = null,
    val lastMeasuredOn: String? = null,
    val daysSinceLastMeasure: Int? = null,
    val measures: Int = 0,
)

// ---------------------------------------------------------------- stress

@Serializable
data class StressMonth(
    val month: String,
    val mean: Double,
    val percentAbove60: Double,
)

@Serializable
data class StressSection(
    val monthly: List<StressMonth> = emptyList(),
    val hourly: List<LabelValue> = emptyList(),
    val dayOfWeek: List<LabelValue> = emptyList(),
    val daily: List<DayValue> = emptyList(),
    val vitalityDaily: List<DayValue> = emptyList(),
    val kpi: StressKpi = StressKpi(),
)

@Serializable
data class StressKpi(
    val mean: Double? = null,
    val percentAbove60: Double? = null,
    val alerts: Int = 0,
    val peakHour: Int? = null,
    val vitalityMean: Double? = null,
    val measuredDays: Int = 0,
)

// ---------------------------------------------------------------- respiration

@Serializable
data class Spo2Month(
    val month: String,
    val mean: Double,
    val min: Double,
)

@Serializable
data class BreathingSection(
    val spo2Monthly: List<Spo2Month> = emptyList(),
    val spo2Daily: List<DayValue> = emptyList(),
    val respiratoryDaily: List<DayValue> = emptyList(),
    val skinTempDaily: List<DayValue> = emptyList(),
    val kpi: BreathingKpi = BreathingKpi(),
)

@Serializable
data class BreathingKpi(
    val spo2Mean: Double? = null,
    val spo2Measures: Int = 0,
    val spo2Under90: Int = 0,
    val respiratoryMean: Double? = null,
    val skinTempMean: Double? = null,
    val skinTempStdDev: Double? = null,
)
