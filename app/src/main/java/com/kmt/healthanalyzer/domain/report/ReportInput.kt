package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailyFloors
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressAlert
import com.kmt.healthanalyzer.domain.model.StressSample
import java.time.LocalDate

/**
 * Toutes les mesures brutes dont [ReportBuilder] a besoin pour une période.
 *
 * Un seul objet plutôt qu'une longue liste de paramètres : chaque section du rapport ne
 * lit que les listes qui la concernent, et l'ajout d'une nouvelle mesure ne casse pas
 * les appels existants grâce aux valeurs par défaut.
 */
data class ReportInput(
    val range: ClosedRange<LocalDate>,
    /** Objectif de sommeil, en heures. Repère usuel pour un adulte : 7 h 30. */
    val sleepTargetHours: Double = 7.5,
    val sleepNights: List<SleepNight> = emptyList(),
    /**
     * Segments de stades, seule source du sommeil profond et de l'éveil.
     *
     * Le fichier de nuits Samsung ne porte que `total_rem_duration` et
     * `total_light_duration` : sans ces segments, la répartition des stades ramène le
     * léger et le paradoxal à 100 % et montre de fausses proportions.
     */
    val sleepStages: List<SleepStageSegment> = emptyList(),
    val heartRates: List<HeartRateSample> = emptyList(),
    val hrv: List<HrvSample> = emptyList(),
    val stress: List<StressSample> = emptyList(),
    val dailySteps: List<DailySteps> = emptyList(),
    val dailyActivities: List<DailyActivity> = emptyList(),
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    val bodyCompositions: List<BodyComposition> = emptyList(),
    val spO2: List<SpO2Sample> = emptyList(),
    val bloodPressure: List<BloodPressureReading> = emptyList(),
    val ecgRecords: List<EcgRecord> = emptyList(),
    val snoringEpisodes: List<SnoringEpisode> = emptyList(),
    val respiratoryRates: List<RespiratoryRateSample> = emptyList(),
    val skinTemperatures: List<SkinTemperatureSample> = emptyList(),
    val sleepApneaResults: List<SleepApneaResult> = emptyList(),
    val dailyFloors: List<DailyFloors> = emptyList(),
    val stressAlerts: List<StressAlert> = emptyList(),
    val energyScores: List<EnergyScore> = emptyList(),
    val profile: ReportProfile? = null,
)
