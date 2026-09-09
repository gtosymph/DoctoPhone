package com.kmt.healthanalyzer.data.healthconnect

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.DataOrigin
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStage
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

private const val TAG = "HealthConnectReader"

/** Fenêtre de proximité temporelle acceptée pour rattacher une taille/masse grasse à une pesée. */
private val BODY_COMPOSITION_PROXIMITY_WINDOW: Duration = Duration.ofHours(24)

/**
 * Lit les données de santé depuis Health Connect et les convertit vers le modèle de domaine.
 *
 * L'app est en LECTURE SEULE : cette classe n'écrit jamais dans Health Connect. Toutes les
 * méthodes tournent sur [Dispatchers.IO] et rendent une liste vide (plutôt que de lever une
 * exception) quand la permission de lecture correspondante n'a pas été accordée : chaque
 * appelant Health Connect est protégé par son propre `catch (SecurityException)`, journalisé
 * via [Log.w]. Toute autre exception n'est PAS interceptée et remonte à l'appelant.
 *
 * Le [HealthConnectClient] est injecté via un `Provider` (voir [HealthConnectModule]) : il
 * n'est résolu qu'au moment de chaque lecture, jamais construit à l'injection de ce lecteur.
 */
@Singleton
class HealthConnectReader @Inject constructor(
    private val clientProvider: Provider<HealthConnectClient>,
) {

    /**
     * Interroge Health Connect pour savoir quelles permissions sont réellement accordées.
     *
     * Contrairement aux méthodes `read*` ci-dessous, qui avalent un refus en rendant une liste
     * vide, cette photographie permet à l'appelant de distinguer un refus de permission d'une
     * simple absence de donnée sur la période demandée.
     */
    suspend fun permissionState(): HealthConnectPermissionState = withContext(Dispatchers.IO) {
        val granted = clientProvider.get().permissionController.getGrantedPermissions()
        HealthConnectPermissionState(
            grantedDataPermissions = granted.intersect(HealthConnectPermissions.READ_PERMISSIONS),
            hasHistoryPermission = HealthConnectPermissions.READ_HEALTH_DATA_HISTORY in granted,
        )
    }

    /** Lit les nuits de sommeil sur la plage donnée. Voir [readSleepStages] pour le détail par stade. */
    suspend fun readSleep(from: Instant, to: Instant): List<SleepNight> = withContext(Dispatchers.IO) {
        try {
            readSleepSessions(from, to).map { it.toSleepNight() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission Health Connect manquante pour lire le sommeil", e)
            emptyList()
        }
    }

    /**
     * Lit le détail des stades de sommeil sur la plage donnée, un [SleepStageSegment] par stade.
     * Relit les mêmes sessions que [readSleep] (le [SleepNight.id] et [SleepStageSegment.sleepId]
     * partagent la même valeur : l'identifiant de record Health Connect), au prix d'une seconde
     * lecture réseau/DB — choix privilégiant la simplicité de l'API publique.
     */
    suspend fun readSleepStages(from: Instant, to: Instant): List<SleepStageSegment> =
        withContext(Dispatchers.IO) {
            try {
                readSleepSessions(from, to).flatMap { it.toStageSegments() }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission Health Connect manquante pour lire les stades de sommeil", e)
                emptyList()
            }
        }

    /** Lit les échantillons de fréquence cardiaque, un [HeartRateSample] par échantillon brut. */
    suspend fun readHeartRate(from: Instant, to: Instant): List<HeartRateSample> =
        withContext(Dispatchers.IO) {
            try {
                readAllPaged(HeartRateRecord::class, from, to).flatMap { record ->
                    record.samples.map { sample ->
                        HeartRateSample(
                            id = "${record.metadata.id}-${sample.time.toEpochMilli()}",
                            time = sample.time,
                            beatsPerMinute = sample.beatsPerMinute.toInt(),
                            origin = DataOrigin.HEALTH_CONNECT,
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission Health Connect manquante pour lire la fréquence cardiaque", e)
                emptyList()
            }
        }

    /**
     * Lit le total de pas par jour via l'AGRÉGATION Health Connect ([StepsRecord.COUNT_TOTAL]
     * groupé par [Period.ofDays]) plutôt qu'en sommant les records bruts : l'agrégation dédoublonne
     * automatiquement les sources qui se chevauchent (ex. téléphone + montre), ce qu'une somme
     * naïve des records ferait compter en double.
     */
    suspend fun readDailySteps(from: Instant, to: Instant): List<DailySteps> = withContext(Dispatchers.IO) {
        try {
            val zone = ZoneId.systemDefault()
            val response = clientProvider.get().aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atZone(zone).toLocalDateTime(),
                        to.atZone(zone).toLocalDateTime(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            response.mapNotNull { grouped ->
                val total = grouped.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                DailySteps(
                    date = grouped.startTime.toLocalDate(),
                    steps = total.toInt(),
                    origin = DataOrigin.HEALTH_CONNECT,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission Health Connect manquante pour lire les pas", e)
            emptyList()
        }
    }

    /**
     * Lit les séances d'exercice. [ExerciseSession.samsungTypeCode] vaut toujours `null` (source
     * Health Connect, pas Samsung). Calories/distance/FC sont enrichies via une agrégation
     * secondaire par séance ; si l'une de ces permissions manque, l'enrichissement est omis
     * (valeurs `null`) sans faire échouer la séance elle-même.
     */
    suspend fun readExercise(from: Instant, to: Instant): List<ExerciseSession> = withContext(Dispatchers.IO) {
        try {
            val client = clientProvider.get()
            readAllPaged(ExerciseSessionRecord::class, from, to).map { it.toExerciseSession(client) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission Health Connect manquante pour lire les séances d'exercice", e)
            emptyList()
        }
    }

    /**
     * Lit la composition corporelle depuis [WeightRecord], enrichie par la taille et la masse
     * grasse les plus proches dans le temps (fenêtre de [BODY_COMPOSITION_PROXIMITY_WINDOW]).
     * Le manque de permission taille/masse grasse dégrade l'enrichissement, pas la lecture du poids.
     */
    suspend fun readBodyComposition(from: Instant, to: Instant): List<BodyComposition> =
        withContext(Dispatchers.IO) {
            try {
                val weights = readAllPaged(WeightRecord::class, from, to)
                val heights = try {
                    readAllPaged(HeightRecord::class, from, to)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission Health Connect manquante pour la taille", e)
                    emptyList()
                }
                val bodyFats = try {
                    readAllPaged(BodyFatRecord::class, from, to)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission Health Connect manquante pour la masse grasse", e)
                    emptyList()
                }
                weights.map { it.toBodyComposition(heights, bodyFats) }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission Health Connect manquante pour lire le poids", e)
                emptyList()
            }
        }

    /** Lit les échantillons de saturation en oxygène (SpO2). */
    suspend fun readOxygenSaturation(from: Instant, to: Instant): List<SpO2Sample> =
        withContext(Dispatchers.IO) {
            try {
                readAllPaged(OxygenSaturationRecord::class, from, to).map { record ->
                    SpO2Sample(
                        id = record.metadata.id,
                        time = record.time,
                        percent = record.percentage.value.toFloat(),
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission Health Connect manquante pour lire la saturation en oxygène", e)
                emptyList()
            }
        }

    /** Lit la variabilité de fréquence cardiaque (RMSSD). [HrvSample.sdnnMillis] reste `null`. */
    suspend fun readHrv(from: Instant, to: Instant): List<HrvSample> = withContext(Dispatchers.IO) {
        try {
            readAllPaged(HeartRateVariabilityRmssdRecord::class, from, to).map { record ->
                HrvSample(
                    id = record.metadata.id,
                    time = record.time,
                    sdnnMillis = null,
                    rmssdMillis = record.heartRateVariabilityMillis.toFloat(),
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission Health Connect manquante pour lire la VFC", e)
            emptyList()
        }
    }

    private suspend fun readSleepSessions(from: Instant, to: Instant): List<SleepSessionRecord> =
        readAllPaged(SleepSessionRecord::class, from, to)

    /** Lit tous les records d'un type donné sur la plage, en paginant jusqu'à épuisement. */
    private suspend fun <T : Record> readAllPaged(
        recordType: KClass<T>,
        from: Instant,
        to: Instant,
    ): List<T> {
        val client = clientProvider.get()
        val results = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                ),
            )
            results += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return results
    }

    private fun SleepSessionRecord.toSleepNight(): SleepNight {
        val zone = ZoneId.systemDefault()
        val stageMinutes = stages
            .groupBy { HealthConnectConverters.toSleepStage(it.stage) }
            .mapValues { (_, group) ->
                group.sumOf { HealthConnectConverters.durationMinutes(it.startTime, it.endTime) }
            }

        return SleepNight(
            id = metadata.id,
            date = HealthConnectConverters.nightDate(endTime, zone),
            bedTime = startTime,
            wakeTime = endTime,
            durationMinutes = HealthConnectConverters.durationMinutes(startTime, endTime),
            remMinutes = stageMinutes[SleepStage.REM],
            lightMinutes = stageMinutes[SleepStage.LIGHT],
            deepMinutes = stageMinutes[SleepStage.DEEP],
            awakeMinutes = stageMinutes[SleepStage.AWAKE],
            localBedTime = startTime.atZone(zone).toLocalTime(),
            origin = DataOrigin.HEALTH_CONNECT,
        )
    }

    private fun SleepSessionRecord.toStageSegments(): List<SleepStageSegment> = stages.map { stage ->
        SleepStageSegment(
            id = "${metadata.id}-${stage.startTime.toEpochMilli()}",
            sleepId = metadata.id,
            stage = HealthConnectConverters.toSleepStage(stage.stage),
            start = stage.startTime,
            end = stage.endTime,
            durationMinutes = HealthConnectConverters.durationMinutes(stage.startTime, stage.endTime),
        )
    }

    private suspend fun ExerciseSessionRecord.toExerciseSession(client: HealthConnectClient): ExerciseSession {
        val aggregate = try {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MAX,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                ),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission Health Connect manquante pour enrichir la séance ${metadata.id}", e)
            null
        }

        return ExerciseSession(
            id = metadata.id,
            kind = HealthConnectConverters.toExerciseKind(exerciseType),
            samsungTypeCode = null,
            start = startTime,
            end = endTime,
            durationMinutes = HealthConnectConverters.durationMinutes(startTime, endTime),
            calories = aggregate?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.toFloat(),
            distanceMeters = aggregate?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.toFloat(),
            meanHeartRate = aggregate?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            maxHeartRate = aggregate?.get(HeartRateRecord.BPM_MAX)?.toInt(),
            origin = DataOrigin.HEALTH_CONNECT,
        )
    }

    private fun WeightRecord.toBodyComposition(
        heights: List<HeightRecord>,
        bodyFats: List<BodyFatRecord>,
    ): BodyComposition {
        val weightKg = weight.inKilograms.toFloat()
        val closestHeight = heights.closestTo(time, BODY_COMPOSITION_PROXIMITY_WINDOW) { it.time }
        val closestBodyFat = bodyFats.closestTo(time, BODY_COMPOSITION_PROXIMITY_WINDOW) { it.time }
        val heightCm = closestHeight?.height?.inMeters?.times(100)?.toFloat()

        return BodyComposition(
            id = metadata.id,
            time = time,
            weightKg = weightKg,
            heightCm = heightCm,
            bodyMassIndex = HealthConnectConverters.bodyMassIndex(weightKg, heightCm),
            bodyFatPercent = closestBodyFat?.percentage?.value?.toFloat(),
            origin = DataOrigin.HEALTH_CONNECT,
        )
    }

    /** Rend l'élément le plus proche de [target] dans la [window] donnée, ou `null` si aucun. */
    private fun <T> List<T>.closestTo(target: Instant, window: Duration, timeOf: (T) -> Instant): T? =
        this.filter { Duration.between(timeOf(it), target).abs() <= window }
            .minByOrNull { Duration.between(timeOf(it), target).abs() }
}
