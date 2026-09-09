package com.kmt.healthanalyzer.data.samsung

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
import com.kmt.healthanalyzer.domain.repository.HealthDataSink

/** Collecteur en mémoire, pour vérifier ce que l'importateur écrit. */
class RecordingSink : HealthDataSink {
    val sleepNights = mutableListOf<SleepNight>()
    val sleepStages = mutableListOf<SleepStageSegment>()
    val heartRates = mutableListOf<HeartRateSample>()
    val stress = mutableListOf<StressSample>()
    val hrv = mutableListOf<HrvSample>()
    val spO2 = mutableListOf<SpO2Sample>()
    val bodyCompositions = mutableListOf<BodyComposition>()
    val exercises = mutableListOf<ExerciseSession>()
    val dailySteps = mutableListOf<DailySteps>()
    val dailyActivities = mutableListOf<DailyActivity>()
    val energyScores = mutableListOf<EnergyScore>()
    val bloodPressure = mutableListOf<BloodPressureReading>()
    val ecgRecords = mutableListOf<EcgRecord>()
    val snoringEpisodes = mutableListOf<SnoringEpisode>()
    val respiratoryRates = mutableListOf<RespiratoryRateSample>()
    val skinTemperatures = mutableListOf<SkinTemperatureSample>()
    val sleepApnea = mutableListOf<SleepApneaResult>()
    val dailyFloors = mutableListOf<DailyFloors>()
    val stressAlerts = mutableListOf<StressAlert>()

    fun isEmpty(): Boolean = listOf(
        sleepNights, sleepStages, heartRates, stress, hrv, spO2,
        bodyCompositions, exercises, dailySteps, dailyActivities, energyScores,
        bloodPressure, ecgRecords, snoringEpisodes, respiratoryRates, skinTemperatures,
        sleepApnea, dailyFloors, stressAlerts,
    ).all { it.isEmpty() }

    override suspend fun writeSleepNights(items: List<SleepNight>) { sleepNights += items }
    override suspend fun writeSleepStages(items: List<SleepStageSegment>) { sleepStages += items }
    override suspend fun writeHeartRates(items: List<HeartRateSample>) { heartRates += items }
    override suspend fun writeStress(items: List<StressSample>) { stress += items }
    override suspend fun writeHrv(items: List<HrvSample>) { hrv += items }
    override suspend fun writeSpO2(items: List<SpO2Sample>) { spO2 += items }
    override suspend fun writeBodyCompositions(items: List<BodyComposition>) { bodyCompositions += items }
    override suspend fun writeExercises(items: List<ExerciseSession>) { exercises += items }
    override suspend fun writeDailySteps(items: List<DailySteps>) { dailySteps += items }
    override suspend fun writeDailyActivities(items: List<DailyActivity>) { dailyActivities += items }
    override suspend fun writeEnergyScores(items: List<EnergyScore>) { energyScores += items }
    override suspend fun writeBloodPressure(items: List<BloodPressureReading>) { bloodPressure += items }
    override suspend fun writeEcgRecords(items: List<EcgRecord>) { ecgRecords += items }
    override suspend fun writeSnoringEpisodes(items: List<SnoringEpisode>) { snoringEpisodes += items }
    override suspend fun writeRespiratoryRates(items: List<RespiratoryRateSample>) { respiratoryRates += items }
    override suspend fun writeSkinTemperatures(items: List<SkinTemperatureSample>) { skinTemperatures += items }
    override suspend fun writeSleepApnea(items: List<SleepApneaResult>) { sleepApnea += items }
    override suspend fun writeDailyFloors(items: List<DailyFloors>) { dailyFloors += items }
    override suspend fun writeStressAlerts(items: List<StressAlert>) { stressAlerts += items }
}
