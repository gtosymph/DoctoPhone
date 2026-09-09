package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailyFloors
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStage
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressAlert
import com.kmt.healthanalyzer.domain.model.StressSample
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Lit `shared-fixtures/parity-input.json`, l'entrée commune au test de parité Kotlin
 * (`ReportParityTest`) et au script `web/test/parity.js`. Voir `shared-fixtures/README.md`.
 *
 * Les classes ci-dessous ne font que refléter la forme du JSON ; la conversion vers les
 * modèles du domaine se fait dans [ParityFixture.toReportInput].
 */
@Serializable
private data class ParityFixtureJson(
    val zone: String,
    val from: String,
    val to: String,
    val sleepTargetHours: Double = 7.5,
    val profile: FixtureProfile? = null,
    val sleepNights: List<FixtureSleepNight> = emptyList(),
    val sleepStages: List<FixtureSleepStage> = emptyList(),
    val heartRate: List<FixtureHeartRate> = emptyList(),
    val stress: List<FixtureStress> = emptyList(),
    val stressAlerts: List<FixtureStressAlert> = emptyList(),
    val hrv: List<FixtureHrv> = emptyList(),
    val spo2: List<FixtureSpo2> = emptyList(),
    val exercise: List<FixtureExercise> = emptyList(),
    val bodyComposition: List<FixtureBodyComposition> = emptyList(),
    val dailySteps: List<FixtureDailySteps> = emptyList(),
    val dailyActivity: List<FixtureDailyActivity> = emptyList(),
    val energyScores: List<FixtureEnergyScore> = emptyList(),
    val bloodPressure: List<FixtureBloodPressure> = emptyList(),
    val ecg: List<FixtureEcg> = emptyList(),
    val snoring: List<FixtureSnoring> = emptyList(),
    val respiratory: List<FixtureRespiratory> = emptyList(),
    val skinTemp: List<FixtureSkinTemp> = emptyList(),
    val sleepApnea: List<FixtureSleepApnea> = emptyList(),
    val dailyFloors: List<FixtureDailyFloors> = emptyList(),
)

@Serializable private data class FixtureProfile(val heightCm: Double? = null, val weightKg: Double? = null)

@Serializable
private data class FixtureSleepStage(
    val id: String,
    val sleepId: String,
    val stage: String,
    val start: Long,
    val end: Long,
    val durationMinutes: Int,
)

@Serializable
private data class FixtureSleepNight(
    val id: String,
    val date: String,
    val bedTime: Long,
    val wakeTime: Long,
    val durationMinutes: Int,
    val score: Int? = null,
    val efficiencyPercent: Double? = null,
    val latencyMinutes: Int? = null,
    val physicalRecovery: Int? = null,
    val mentalRecovery: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val localBedTime: String? = null,
)

@Serializable private data class FixtureHeartRate(val id: String, val time: Long, val beatsPerMinute: Int)
@Serializable private data class FixtureStress(val id: String, val start: Long, val end: Long, val score: Int)
@Serializable private data class FixtureStressAlert(val id: String, val start: Long, val end: Long)

@Serializable
private data class FixtureHrv(val id: String, val time: Long, val sdnnMillis: Double? = null, val rmssdMillis: Double? = null)

@Serializable private data class FixtureSpo2(val id: String, val time: Long, val percent: Double)

@Serializable
private data class FixtureExercise(
    val id: String,
    val kind: String,
    val samsungTypeCode: Int? = null,
    val start: Long,
    val end: Long,
    val durationMinutes: Int,
    val calories: Double? = null,
    val distanceMeters: Double? = null,
)

@Serializable
private data class FixtureBodyComposition(
    val id: String,
    val time: Long,
    val weightKg: Double,
    val heightCm: Double? = null,
    val bodyMassIndex: Double? = null,
    val bodyFatPercent: Double? = null,
    val bodyFatMassKg: Double? = null,
    val skeletalMuscleMassKg: Double? = null,
    val fatFreeMassKg: Double? = null,
    val totalBodyWaterKg: Double? = null,
    val basalMetabolicRate: Int? = null,
)

@Serializable private data class FixtureDailySteps(val date: String, val steps: Int)

@Serializable
private data class FixtureDailyActivity(
    val date: String,
    val steps: Int? = null,
    val activeMinutes: Int? = null,
    val activeCalories: Int? = null,
)

@Serializable private data class FixtureEnergyScore(val date: String, val total: Int)

@Serializable
private data class FixtureBloodPressure(
    val id: String,
    val time: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val mean: Int? = null,
)

@Serializable
private data class FixtureEcg(
    val id: String,
    val time: Long,
    val meanHeartRate: Int? = null,
    val classification: Int? = null,
)

@Serializable private data class FixtureSnoring(val id: String, val start: Long, val end: Long, val durationMinutes: Int)
@Serializable private data class FixtureRespiratory(val id: String, val time: Long, val breathsPerMinute: Double)

@Serializable
private data class FixtureSkinTemp(val id: String, val time: Long, val celsius: Double, val baseline: Double? = null)

@Serializable
private data class FixtureSleepApnea(
    val id: String,
    val time: Long,
    val result: Int,
    val averageBreathingDisturbance: Double? = null,
)

@Serializable private data class FixtureDailyFloors(val date: String, val floors: Int)

/** Le résultat du chargement : les entrées prêtes pour [ReportBuilder], plus les options du fichier. */
internal data class ParityFixture(
    val zone: ZoneId,
    val range: ClosedRange<LocalDate>,
    val sleepTargetHours: Double,
    val input: ReportInput,
)

/** Cherche `shared-fixtures/parity-input.json` en remontant depuis le répertoire courant du test. */
internal fun findParityFixtureFile(): File? {
    var dir: File? = File(".").absoluteFile
    repeat(6) {
        val candidate = File(dir, "shared-fixtures/parity-input.json")
        if (candidate.isFile) return candidate
        dir = dir?.parentFile
    }
    return null
}

internal fun loadParityFixture(file: File): ParityFixture {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val fixture = json.decodeFromString(ParityFixtureJson.serializer(), file.readText())
    val zone = ZoneId.of(fixture.zone)
    val range = LocalDate.parse(fixture.from)..LocalDate.parse(fixture.to)

    val input = ReportInput(
        range = range,
        sleepTargetHours = fixture.sleepTargetHours,
        sleepStages = fixture.sleepStages.map {
            SleepStageSegment(
                id = it.id,
                sleepId = it.sleepId,
                stage = runCatching { SleepStage.valueOf(it.stage) }.getOrDefault(SleepStage.UNKNOWN),
                start = Instant.ofEpochMilli(it.start),
                end = Instant.ofEpochMilli(it.end),
                durationMinutes = it.durationMinutes,
            )
        },
        sleepNights = fixture.sleepNights.map {
            SleepNight(
                id = it.id,
                date = LocalDate.parse(it.date),
                bedTime = Instant.ofEpochMilli(it.bedTime),
                wakeTime = Instant.ofEpochMilli(it.wakeTime),
                durationMinutes = it.durationMinutes,
                score = it.score,
                efficiencyPercent = it.efficiencyPercent?.toFloat(),
                latencyMinutes = it.latencyMinutes,
                physicalRecovery = it.physicalRecovery,
                mentalRecovery = it.mentalRecovery,
                remMinutes = it.remMinutes,
                lightMinutes = it.lightMinutes,
                deepMinutes = it.deepMinutes,
                awakeMinutes = it.awakeMinutes,
                localBedTime = it.localBedTime?.let(LocalTime::parse),
            )
        },
        heartRates = fixture.heartRate.map { HeartRateSample(id = it.id, time = Instant.ofEpochMilli(it.time), beatsPerMinute = it.beatsPerMinute) },
        hrv = fixture.hrv.map {
            HrvSample(id = it.id, time = Instant.ofEpochMilli(it.time), sdnnMillis = it.sdnnMillis?.toFloat(), rmssdMillis = it.rmssdMillis?.toFloat())
        },
        stress = fixture.stress.map {
            StressSample(id = it.id, start = Instant.ofEpochMilli(it.start), end = Instant.ofEpochMilli(it.end), score = it.score)
        },
        dailySteps = fixture.dailySteps.map { DailySteps(date = LocalDate.parse(it.date), steps = it.steps) },
        dailyActivities = fixture.dailyActivity.map {
            DailyActivity(date = LocalDate.parse(it.date), steps = it.steps, activeMinutes = it.activeMinutes, activeCalories = it.activeCalories)
        },
        exerciseSessions = fixture.exercise.map {
            ExerciseSession(
                id = it.id,
                kind = ExerciseKind.valueOf(it.kind),
                samsungTypeCode = it.samsungTypeCode,
                start = Instant.ofEpochMilli(it.start),
                end = Instant.ofEpochMilli(it.end),
                durationMinutes = it.durationMinutes,
                calories = it.calories?.toFloat(),
                distanceMeters = it.distanceMeters?.toFloat(),
            )
        },
        bodyCompositions = fixture.bodyComposition.map {
            BodyComposition(
                id = it.id,
                time = Instant.ofEpochMilli(it.time),
                weightKg = it.weightKg.toFloat(),
                heightCm = it.heightCm?.toFloat(),
                bodyMassIndex = it.bodyMassIndex?.toFloat(),
                bodyFatPercent = it.bodyFatPercent?.toFloat(),
                bodyFatMassKg = it.bodyFatMassKg?.toFloat(),
                skeletalMuscleMassKg = it.skeletalMuscleMassKg?.toFloat(),
                fatFreeMassKg = it.fatFreeMassKg?.toFloat(),
                totalBodyWaterKg = it.totalBodyWaterKg?.toFloat(),
                basalMetabolicRate = it.basalMetabolicRate,
            )
        },
        spO2 = fixture.spo2.map { SpO2Sample(id = it.id, time = Instant.ofEpochMilli(it.time), percent = it.percent.toFloat()) },
        bloodPressure = fixture.bloodPressure.map {
            BloodPressureReading(id = it.id, time = Instant.ofEpochMilli(it.time), systolic = it.systolic, diastolic = it.diastolic, pulse = it.pulse, mean = it.mean)
        },
        ecgRecords = fixture.ecg.map {
            EcgRecord(id = it.id, time = Instant.ofEpochMilli(it.time), meanHeartRate = it.meanHeartRate, classification = it.classification)
        },
        snoringEpisodes = fixture.snoring.map {
            SnoringEpisode(id = it.id, start = Instant.ofEpochMilli(it.start), end = Instant.ofEpochMilli(it.end), durationMinutes = it.durationMinutes)
        },
        respiratoryRates = fixture.respiratory.map {
            RespiratoryRateSample(id = it.id, time = Instant.ofEpochMilli(it.time), breathsPerMinute = it.breathsPerMinute.toFloat())
        },
        skinTemperatures = fixture.skinTemp.map {
            SkinTemperatureSample(id = it.id, time = Instant.ofEpochMilli(it.time), celsius = it.celsius.toFloat(), baseline = it.baseline?.toFloat())
        },
        sleepApneaResults = fixture.sleepApnea.map {
            SleepApneaResult(id = it.id, time = Instant.ofEpochMilli(it.time), result = it.result, averageBreathingDisturbance = it.averageBreathingDisturbance?.toFloat())
        },
        dailyFloors = fixture.dailyFloors.map { DailyFloors(date = LocalDate.parse(it.date), floors = it.floors) },
        stressAlerts = fixture.stressAlerts.map { StressAlert(id = it.id, start = Instant.ofEpochMilli(it.start), end = Instant.ofEpochMilli(it.end)) },
        energyScores = fixture.energyScores.map { EnergyScore(date = LocalDate.parse(it.date), total = it.total) },
        profile = fixture.profile?.let { ReportProfile(heightCm = it.heightCm, weightKg = it.weightKg) },
    )

    return ParityFixture(zone = zone, range = range, sleepTargetHours = fixture.sleepTargetHours, input = input)
}
