package com.kmt.healthanalyzer.data.repository

import android.content.Context
import android.net.Uri
import com.kmt.healthanalyzer.data.db.HealthDatabase
import com.kmt.healthanalyzer.data.db.mapper.toDomain
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectReader
import com.kmt.healthanalyzer.data.samsung.ImportProgress
import com.kmt.healthanalyzer.data.samsung.DocumentTreeExportSource
import com.kmt.healthanalyzer.data.samsung.SamsungHealthImporter
import com.kmt.healthanalyzer.data.samsung.ZipExportSource
import com.kmt.healthanalyzer.domain.analysis.HealthAggregator
import com.kmt.healthanalyzer.domain.analysis.HealthSnapshot
import com.kmt.healthanalyzer.domain.report.ReportBuilder
import com.kmt.healthanalyzer.domain.report.ReportInput
import com.kmt.healthanalyzer.domain.report.ReportModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Marge d'un jour pour rattraper une nuit qui commence avant le début de période. */
private const val MILLIS_PER_DAY = 86_400_000L

/** Signale un import impossible, avec un message destiné à l'utilisateur. */
class ImportFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Point d'entrée unique sur les données de santé.
 *
 * Il réunit les deux sources — l'archive Samsung Health et Health Connect — et les
 * expose sous la forme d'un [HealthSnapshot] déjà agrégé, prêt pour l'écran d'accueil
 * comme pour l'analyse par le modèle de langage.
 */
@Singleton
class HealthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HealthDatabase,
    private val sink: RoomHealthDataSink,
    private val healthConnectReader: Provider<HealthConnectReader>,
    private val zone: ZoneId,
) {
    private val aggregator = HealthAggregator(zone)

    /**
     * Importe le dossier d'export désigné par [treeUri].
     *
     * C'est le chemin normal : Samsung Health écrit un dossier sur le téléphone, et
     * l'utilisateur le désigne dans le sélecteur. Rien n'est copié ni décompressé.
     */
    fun importSamsungDirectory(treeUri: Uri): Flow<ImportProgress> = flow {
        SamsungHealthImporter(sink, DocumentTreeExportSource(context.contentResolver, treeUri))
            .import()
            .collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    /**
     * Importe une archive compressée de l'export, désignée par [uri].
     *
     * Utile quand l'export a transité par un ordinateur et a été compressé en chemin.
     * L'archive est d'abord copiée dans le cache : la lecture d'un ZIP demande un accès
     * aléatoire au fichier, que le fournisseur de documents Android ne garantit pas.
     * Le fichier temporaire est supprimé à la fin, même en cas d'échec.
     */
    fun importSamsungArchive(uri: Uri): Flow<ImportProgress> = flow {
        val cached = copyToCache(uri)
        try {
            SamsungHealthImporter(sink, ZipExportSource(cached)).import().collect { emit(it) }
        } finally {
            cached.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun copyToCache(uri: Uri): File {
        val target = File.createTempFile(CACHE_PREFIX, CACHE_SUFFIX, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw ImportFailure("Le fichier choisi est introuvable ou illisible.")
        } catch (failure: IOException) {
            target.delete()
            throw ImportFailure("La copie de l'archive a échoué.", failure)
        }
        return target
    }

    /**
     * Reprend dans Health Connect les mesures postérieures à [from].
     *
     * Health Connect ne porte pas les scores propriétaires de Samsung (score de sommeil,
     * score d'énergie, index de stress). Cette synchronisation complète donc l'archive ;
     * elle ne la remplace pas.
     *
     * Rend un [HealthConnectSyncResult] plutôt que de se taire : l'appelant doit pouvoir dire
     * à l'utilisateur si la synchronisation n'a rien donné faute de permission, faute d'historique
     * accordé, ou simplement faute de donnée sur la période.
     */
    suspend fun syncFromHealthConnect(from: Instant, to: Instant = Instant.now()): HealthConnectSyncResult =
        withContext(Dispatchers.IO) {
            val reader = healthConnectReader.get()
            val permissionState = reader.permissionState()

            val sleepNights = reader.readSleep(from, to)
            val heartRates = reader.readHeartRate(from, to)
            val dailySteps = reader.readDailySteps(from, to)
            val exercises = reader.readExercise(from, to)
            val bodyCompositions = reader.readBodyComposition(from, to)
            val spO2 = reader.readOxygenSaturation(from, to)
            val hrv = reader.readHrv(from, to)

            sink.writeSleepNights(sleepNights)
            sink.writeSleepStages(reader.readSleepStages(from, to))
            sink.writeHeartRates(heartRates)
            sink.writeDailySteps(dailySteps)
            sink.writeExercises(exercises)
            sink.writeBodyCompositions(bodyCompositions)
            sink.writeSpO2(spO2)
            sink.writeHrv(hrv)

            val receivedDates = buildList {
                sleepNights.forEach { add(it.date) }
                dailySteps.forEach { add(it.date) }
                heartRates.forEach { add(it.time.atZone(zone).toLocalDate()) }
                exercises.forEach {
                    add(it.start.atZone(zone).toLocalDate())
                    add(it.end.atZone(zone).toLocalDate())
                }
                bodyCompositions.forEach { add(it.time.atZone(zone).toLocalDate()) }
                spO2.forEach { add(it.time.atZone(zone).toLocalDate()) }
                hrv.forEach { add(it.time.atZone(zone).toLocalDate()) }
            }

            HealthConnectSyncResult(
                hasAnyDataPermission = permissionState.hasAnyDataPermission,
                missingDataPermissions = permissionState.missingDataPermissions,
                historyPermissionMissing = isHistoryPermissionMissing(permissionState.hasHistoryPermission, from),
                earliestDate = receivedDates.minOrNull(),
                latestDate = receivedDates.maxOrNull(),
            )
        }

    /** Construit la vue consolidée de la période demandée. */
    suspend fun snapshot(from: LocalDate, to: LocalDate): HealthSnapshot = withContext(Dispatchers.IO) {
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val fromDate = from.toString()
        val toDate = to.toString()

        aggregator.aggregate(
            range = from..to,
            dailySteps = database.dailyStepsDao().getBetween(fromDate, toDate).map { it.toDomain() },
            dailyActivities = database.dailyActivityDao().getBetween(fromDate, toDate).map { it.toDomain() },
            sleepNights = database.sleepNightDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            heartRates = database.heartRateSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            stress = database.stressSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            hrv = database.hrvSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            spO2 = database.spO2SampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            bodyCompositions = database.bodyCompositionDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            energyScores = database.energyScoreDao().getBetween(fromDate, toDate).map { it.toDomain() },
        )
    }

    /**
     * Construit le rapport complet de la période demandée.
     *
     * [ReportBuilder] est une classe pure : ce point d'entrée ne fait que lire les DAO et
     * lui passer les listes. Le résultat ne doit jamais partir vers un tiers — voir
     * `HealthPromptBuilder` pour la seule porte de sortie autorisée vers un LLM.
     */
    suspend fun buildReport(range: ClosedRange<LocalDate>, zone: ZoneId): ReportModel = withContext(Dispatchers.IO) {
        val from = range.start
        val to = range.endInclusive
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val fromDate = from.toString()
        val toDate = to.toString()

        val input = ReportInput(
            range = range,
            sleepNights = database.sleepNightDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            // Les segments de stades appartiennent à leur nuit, pas à leur propre horodatage :
            // ceux de la première nuit commencent la veille du début de période. La fenêtre
            // recule donc d'un jour ; le rattachement final se fait par identifiant de session,
            // donc aucun segment étranger n'entre.
            sleepStages = database.sleepStageSegmentDao()
                .getBetween(fromMillis - MILLIS_PER_DAY, toMillis).map { it.toDomain() },
            heartRates = database.heartRateSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            hrv = database.hrvSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            stress = database.stressSampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            dailySteps = database.dailyStepsDao().getBetween(fromDate, toDate).map { it.toDomain() },
            dailyActivities = database.dailyActivityDao().getBetween(fromDate, toDate).map { it.toDomain() },
            exerciseSessions = database.exerciseSessionDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            bodyCompositions = database.bodyCompositionDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            spO2 = database.spO2SampleDao().getBetween(fromMillis, toMillis).map { it.toDomain() },
            bloodPressure = database.bloodPressureDao().between(fromMillis, toMillis).map { it.toDomain() },
            ecgRecords = database.ecgRecordDao().between(fromMillis, toMillis).map { it.toDomain() },
            snoringEpisodes = database.snoringEpisodeDao().between(fromMillis, toMillis).map { it.toDomain() },
            respiratoryRates = database.respiratoryRateDao().between(fromMillis, toMillis).map { it.toDomain() },
            skinTemperatures = database.skinTemperatureDao().between(fromMillis, toMillis).map { it.toDomain() },
            sleepApneaResults = database.sleepApneaDao().between(fromMillis, toMillis).map { it.toDomain() },
            dailyFloors = database.dailyFloorsDao().between(from, to).map { it.toDomain() },
            stressAlerts = database.stressAlertDao().between(fromMillis, toMillis).map { it.toDomain() },
            energyScores = database.energyScoreDao().getBetween(fromDate, toDate).map { it.toDomain() },
        )

        ReportBuilder(zone).build(input)
    }

    /** Nombre total d'enregistrements en base, pour savoir si un import a déjà eu lieu. */
    suspend fun recordCount(): Int = withContext(Dispatchers.IO) {
        database.dailyStepsDao().count() +
            database.sleepNightDao().count() +
            database.heartRateSampleDao().count()
    }

    /** Dernier jour porteur de mesures, pour caler la synchronisation Health Connect. */
    suspend fun lastRecordedDay(): LocalDate? = withContext(Dispatchers.IO) {
        database.dailyStepsDao().latest()?.toDomain()?.date
    }

    /**
     * Premier jour porteur de mesures, tous types confondus.
     *
     * Sert à borner « tout l'historique » pour la conversation d'analyse (voir
     * [com.kmt.healthanalyzer.domain.usecase.ChatWithHealthUseCase]) : le plus ancien des
     * trois types qui alimentent aussi [recordCount], puisqu'une seule table peut démarrer
     * plus tard que les autres (par exemple des nuits de sommeil importées avant les
     * premiers pas quotidiens).
     */
    suspend fun firstRecordedDay(): LocalDate? = withContext(Dispatchers.IO) {
        listOfNotNull(
            database.dailyStepsDao().earliest()?.toDomain()?.date,
            database.sleepNightDao().earliest()?.toDomain()?.date,
            database.heartRateSampleDao().earliest()?.toDomain()?.time?.atZone(zone)?.toLocalDate(),
        ).minOrNull()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    private companion object {
        const val CACHE_PREFIX = "samsung-export"
        const val CACHE_SUFFIX = ".zip"
    }
}
