package com.kmt.healthanalyzer.data.repository

import com.kmt.healthanalyzer.data.db.HealthDatabase
import com.kmt.healthanalyzer.data.db.mapper.toEntity
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Écrit les données importées dans la base locale.
 *
 * Toutes les écritures passent par `upsert` : réimporter la même archive met les
 * enregistrements à jour au lieu de les dupliquer, car chaque modèle porte son
 * identifiant Samsung d'origine.
 */
@Singleton
class RoomHealthDataSink @Inject constructor(
    private val database: HealthDatabase,
) : HealthDataSink {

    override suspend fun writeSleepNights(items: List<SleepNight>) =
        database.sleepNightDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeSleepStages(items: List<SleepStageSegment>) =
        database.sleepStageSegmentDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeHeartRates(items: List<HeartRateSample>) =
        database.heartRateSampleDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeStress(items: List<StressSample>) =
        database.stressSampleDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeHrv(items: List<HrvSample>) =
        database.hrvSampleDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeSpO2(items: List<SpO2Sample>) =
        database.spO2SampleDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeBodyCompositions(items: List<BodyComposition>) =
        database.bodyCompositionDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeExercises(items: List<ExerciseSession>) =
        database.exerciseSessionDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeDailySteps(items: List<DailySteps>) =
        database.dailyStepsDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeDailyActivities(items: List<DailyActivity>) =
        database.dailyActivityDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeEnergyScores(items: List<EnergyScore>) =
        database.energyScoreDao().upsertAll(items.map { it.toEntity() })

    override suspend fun writeBloodPressure(items: List<BloodPressureReading>) =
        database.bloodPressureDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeEcgRecords(items: List<EcgRecord>) =
        database.ecgRecordDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeSnoringEpisodes(items: List<SnoringEpisode>) =
        database.snoringEpisodeDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeRespiratoryRates(items: List<RespiratoryRateSample>) =
        database.respiratoryRateDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeSkinTemperatures(items: List<SkinTemperatureSample>) =
        database.skinTemperatureDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeSleepApnea(items: List<SleepApneaResult>) =
        database.sleepApneaDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeDailyFloors(items: List<DailyFloors>) =
        database.dailyFloorsDao().insertAll(items.map { it.toEntity() })

    override suspend fun writeStressAlerts(items: List<StressAlert>) =
        database.stressAlertDao().insertAll(items.map { it.toEntity() })
}
