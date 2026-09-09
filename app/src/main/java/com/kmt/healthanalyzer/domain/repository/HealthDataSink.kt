package com.kmt.healthanalyzer.domain.repository

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

/**
 * Destination d'écriture des données importées.
 *
 * L'importateur écrit par lots au fil de sa lecture. Il ne connaît ni Room ni Android.
 * Chaque écriture doit être idempotente : le même export importé deux fois ne crée pas
 * de doublon, car les modèles portent l'identifiant Samsung d'origine.
 */
interface HealthDataSink {
    suspend fun writeSleepNights(items: List<SleepNight>)
    suspend fun writeSleepStages(items: List<SleepStageSegment>)
    suspend fun writeHeartRates(items: List<HeartRateSample>)
    suspend fun writeStress(items: List<StressSample>)
    suspend fun writeHrv(items: List<HrvSample>)
    suspend fun writeSpO2(items: List<SpO2Sample>)
    suspend fun writeBodyCompositions(items: List<BodyComposition>)
    suspend fun writeExercises(items: List<ExerciseSession>)
    suspend fun writeDailySteps(items: List<DailySteps>)
    suspend fun writeDailyActivities(items: List<DailyActivity>)
    suspend fun writeEnergyScores(items: List<EnergyScore>)
    suspend fun writeBloodPressure(items: List<BloodPressureReading>)
    suspend fun writeEcgRecords(items: List<EcgRecord>)
    suspend fun writeSnoringEpisodes(items: List<SnoringEpisode>)
    suspend fun writeRespiratoryRates(items: List<RespiratoryRateSample>)
    suspend fun writeSkinTemperatures(items: List<SkinTemperatureSample>)
    suspend fun writeSleepApnea(items: List<SleepApneaResult>)
    suspend fun writeDailyFloors(items: List<DailyFloors>)
    suspend fun writeStressAlerts(items: List<StressAlert>)
}
