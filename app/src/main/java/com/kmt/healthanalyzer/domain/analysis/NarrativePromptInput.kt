package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.ActivityKpi
import com.kmt.healthanalyzer.domain.report.BodyKpi
import com.kmt.healthanalyzer.domain.report.BreathingKpi
import com.kmt.healthanalyzer.domain.report.CorrelationItem
import com.kmt.healthanalyzer.domain.report.ExerciseMonth
import com.kmt.healthanalyzer.domain.report.HeartKpi
import com.kmt.healthanalyzer.domain.report.HeartMonth
import com.kmt.healthanalyzer.domain.report.LabelValue
import com.kmt.healthanalyzer.domain.report.MonthValue
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.ReportProfile
import com.kmt.healthanalyzer.domain.report.ReportTile
import com.kmt.healthanalyzer.domain.report.SleepKpi
import com.kmt.healthanalyzer.domain.report.SleepMonthStat
import com.kmt.healthanalyzer.domain.report.SleepStageMonth
import com.kmt.healthanalyzer.domain.report.Spo2Month
import com.kmt.healthanalyzer.domain.report.StressKpi
import com.kmt.healthanalyzer.domain.report.StressMonth
import kotlinx.serialization.Serializable

/**
 * Le sous-ensemble de [ReportModel] envoyé au LLM pour écrire le récit du rapport.
 *
 * Frontière de confidentialité : chaque type ici ne porte QUE des champs agrégés
 * (moyennes hebdomadaires/mensuelles, profils par jour de semaine et par heure,
 * indicateurs `kpi`, coefficients de corrélation, contenu des tuiles). Aucune série
 * quotidienne (`nightly`, `stepsDaily`, `restingDaily`, `vitalityDaily`, `spo2Daily`...),
 * aucun identifiant, aucun horodatage précis, aucune mesure individuelle brute. Comme
 * [ReportModel] ne quitte jamais l'appareil, ce sous-ensemble-ci est la seule vue du
 * rapport qui a le droit de partir vers un tiers — construit en recopiant explicitement
 * les champs autorisés, jamais en repartant du modèle complet.
 *
 * `meta.generatedAt` (horodatage précis) et toutes les listes de points quotidiens ou de
 * mesures individuelles (`heart.bloodPressure`, `heart.ecg`, `body.daily`...) sont donc
 * volontairement absents des types ci-dessous.
 */
@Serializable
internal data class NarrativeInput(
    val period: NarrativePeriod,
    val tiles: List<ReportTile>,
    val sleep: NarrativeSleep,
    val heart: NarrativeHeart,
    val activity: NarrativeActivity,
    val body: NarrativeBody,
    val stress: NarrativeStress,
    val breathing: NarrativeBreathing,
    val correlations: List<CorrelationItem>,
)

@Serializable
internal data class NarrativePeriod(
    val periodLabel: String,
    val days: Int,
    val nights: Int,
    val activeDays: Int,
    val heartRateSamples: Int,
    val hrvWindows: Int,
    val profile: ReportProfile?,
)

@Serializable
internal data class NarrativeSleep(
    val monthly: List<SleepMonthStat>,
    val dayOfWeek: List<LabelValue>,
    val distribution: List<LabelValue>,
    val stagesMonthly: List<SleepStageMonth>,
    val kpi: SleepKpi,
)

@Serializable
internal data class NarrativeHeart(
    val monthly: List<HeartMonth>,
    val hourly: List<LabelValue>,
    val hrvMonthly: List<MonthValue>,
    val kpi: HeartKpi,
)

@Serializable
internal data class NarrativeActivity(
    val stepsMonthly: List<MonthValue>,
    val stepsDayOfWeek: List<LabelValue>,
    val exerciseMonthly: List<ExerciseMonth>,
    val exerciseByKind: List<LabelValue>,
    val floorsMonthly: List<MonthValue>,
    val kpi: ActivityKpi,
)

@Serializable
internal data class NarrativeBody(val kpi: BodyKpi)

@Serializable
internal data class NarrativeStress(
    val monthly: List<StressMonth>,
    val hourly: List<LabelValue>,
    val dayOfWeek: List<LabelValue>,
    val kpi: StressKpi,
)

@Serializable
internal data class NarrativeBreathing(
    val spo2Monthly: List<Spo2Month>,
    val kpi: BreathingKpi,
)

/**
 * Deux ans de mensuel suffisent à raconter une tendance ; au-delà, la table ne fait que
 * grossir le prompt sans rien apprendre de plus au modèle (comme `MAX_WEEKS` pour
 * l'ancien prompt). Les listes mensuelles sont déjà triées chronologiquement par leur
 * section : ne garder que les [MAX_MONTHS] derniers mois revient à garder les plus récents.
 */
private const val MAX_MONTHS = 24

private fun <T> List<T>.capMonths(): List<T> = takeLast(MAX_MONTHS)

/** Construit la vue agrégée de [report] autorisée à quitter l'appareil. */
internal fun narrativeInputOf(report: ReportModel): NarrativeInput = NarrativeInput(
    period = NarrativePeriod(
        periodLabel = report.meta.periodLabel,
        days = report.meta.days,
        nights = report.meta.nights,
        activeDays = report.meta.activeDays,
        heartRateSamples = report.meta.heartRateSamples,
        hrvWindows = report.meta.hrvWindows,
        profile = report.meta.profile,
    ),
    tiles = report.tiles,
    sleep = NarrativeSleep(
        monthly = report.sleep.monthly.capMonths(),
        dayOfWeek = report.sleep.dayOfWeek,
        distribution = report.sleep.distribution,
        stagesMonthly = report.sleep.stagesMonthly.capMonths(),
        kpi = report.sleep.kpi,
    ),
    heart = NarrativeHeart(
        monthly = report.heart.monthly.capMonths(),
        hourly = report.heart.hourly,
        hrvMonthly = report.heart.hrvMonthly.capMonths(),
        kpi = report.heart.kpi,
    ),
    activity = NarrativeActivity(
        stepsMonthly = report.activity.stepsMonthly.capMonths(),
        stepsDayOfWeek = report.activity.stepsDayOfWeek,
        exerciseMonthly = report.activity.exerciseMonthly.capMonths(),
        exerciseByKind = report.activity.exerciseByKind,
        floorsMonthly = report.activity.floorsMonthly.capMonths(),
        kpi = report.activity.kpi,
    ),
    body = NarrativeBody(kpi = report.body.kpi),
    stress = NarrativeStress(
        monthly = report.stress.monthly.capMonths(),
        hourly = report.stress.hourly,
        dayOfWeek = report.stress.dayOfWeek,
        kpi = report.stress.kpi,
    ),
    breathing = NarrativeBreathing(
        spo2Monthly = report.breathing.spo2Monthly.capMonths(),
        kpi = report.breathing.kpi,
    ),
    correlations = report.correlations,
)
