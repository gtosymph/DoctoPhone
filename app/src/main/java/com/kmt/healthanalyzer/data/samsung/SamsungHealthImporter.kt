package com.kmt.healthanalyzer.data.samsung

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvReader
import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvRow
import com.kmt.healthanalyzer.data.samsung.mapper.BloodPressureMapper
import com.kmt.healthanalyzer.data.samsung.mapper.BodyCompositionMapper
import com.kmt.healthanalyzer.data.samsung.mapper.DailyActivityMapper
import com.kmt.healthanalyzer.data.samsung.mapper.DailyFloorsMapper
import com.kmt.healthanalyzer.data.samsung.mapper.DailyStepsMapper
import com.kmt.healthanalyzer.data.samsung.mapper.EcgMapper
import com.kmt.healthanalyzer.data.samsung.mapper.EnergyScoreMapper
import com.kmt.healthanalyzer.data.samsung.mapper.ExerciseMapper
import com.kmt.healthanalyzer.data.samsung.mapper.HeartRateMapper
import com.kmt.healthanalyzer.data.samsung.mapper.RespiratoryRateMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SkinTemperatureMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SleepApneaMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SleepMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SleepStageMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SnoringMapper
import com.kmt.healthanalyzer.data.samsung.mapper.SpO2Mapper
import com.kmt.healthanalyzer.data.samsung.mapper.StressAlertMapper
import com.kmt.healthanalyzer.data.samsung.mapper.StressMapper
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.repository.HealthDataSink
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Importe un export Samsung Health.
 *
 * L'export contient une soixantaine de fichiers CSV et plusieurs milliers de fichiers
 * JSON. L'importateur écrit dans [sink] par lots, au fil de sa lecture.
 *
 * Il accepte indifféremment un dossier — la forme que Samsung Health produit sur le
 * téléphone — ou une archive compressée de ce dossier, forme courante pour transférer
 * l'export vers un autre appareil. C'est [source] qui porte cette différence.
 *
 * Un fichier illisible n'arrête pas l'import : il produit un avertissement dans le bilan.
 */
class SamsungHealthImporter(
    private val sink: HealthDataSink,
    private val source: SamsungExportSource,
) {
    private val reader = SamsungCsvReader()

    fun import(): Flow<ImportProgress> = flow {
        val counts = LinkedHashMap<String, Int>()
        val warnings = mutableListOf<String>()
        var firstDay: LocalDate? = null
        var lastDay: LocalDate? = null

        fun observeDay(day: LocalDate?) {
            if (day == null) return
            firstDay = firstDay?.let { if (day < it) day else it } ?: day
            lastDay = lastDay?.let { if (day > it) day else it } ?: day
        }

        source.use { export ->
            val entries = export.entries().filter { isCsv(it.path) || isHrvBinning(it.path) }

            var filesDone = 0
            // Les fichiers HRV sont des milliers de tout petits JSON. Les écrire un par un
            // ferait autant de transactions dans la base ; on les regroupe par lots.
            val hrvBatch = mutableListOf<HrvSample>()

            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                if (filesDone % PROGRESS_EVERY == 0 || isCsv(entry.path)) {
                    emit(ImportProgress.Reading(entry.fileName, filesDone, entries.size))
                }

                if (isCsv(entry.path)) {
                    readCsvEntry(entry, counts, warnings, ::observeDay)
                } else {
                    readHrvEntry(entry, hrvBatch, counts, warnings, ::observeDay)
                    if (hrvBatch.size >= HRV_BATCH_SIZE) {
                        sink.writeHrv(hrvBatch.toList())
                        hrvBatch.clear()
                    }
                }
                filesDone += 1
            }
            if (hrvBatch.isNotEmpty()) sink.writeHrv(hrvBatch.toList())
            emit(ImportProgress.Reading("", entries.size, entries.size))
        }

        emit(
            ImportProgress.Finished(
                ImportSummary(
                    recordsByType = counts.toMap(),
                    firstDay = firstDay,
                    lastDay = lastDay,
                    warnings = warnings.toList(),
                )
            )
        )
    }

    /** Lit un CSV de l'archive et écrit ses lignes converties. */
    private suspend fun readCsvEntry(
        entry: SamsungExportEntry,
        counts: MutableMap<String, Int>,
        warnings: MutableList<String>,
        observeDay: (LocalDate?) -> Unit,
    ) {
        val dataType = dataTypeOf(entry.path) ?: return
        val handler = HANDLERS[dataType] ?: return

        try {
            val written = entry.open().use { stream ->
                handler.consume(reader, stream, sink, observeDay)
            }
            if (written > 0) counts[dataType] = (counts[dataType] ?: 0) + written
        } catch (failure: Exception) {
            warnings += "Le fichier $dataType est illisible : ${failure.message ?: failure::class.simpleName}"
        }
    }

    /**
     * Lit un fichier de mesures HRV haute résolution et le résume en un échantillon.
     *
     * Samsung écrit un fichier par fenêtre d'environ une heure, avec une mesure toutes
     * les 30 secondes. L'app garde la médiane de la fenêtre : elle résiste aux artefacts
     * de mouvement bien mieux que la moyenne, et suffit à suivre la tendance.
     */
    private fun readHrvEntry(
        entry: SamsungExportEntry,
        batch: MutableList<HrvSample>,
        counts: MutableMap<String, Int>,
        warnings: MutableList<String>,
        observeDay: (LocalDate?) -> Unit,
    ) {
        try {
            val body = entry.open().use { it.readBytes().decodeToString() }
            val sample = HrvBinningParser.parse(id = binningId(entry.path), json = body) ?: return
            batch += sample
            counts[HRV_TYPE] = (counts[HRV_TYPE] ?: 0) + 1
            observeDay(sample.time.atOffset(ZoneOffset.UTC).toLocalDate())
        } catch (failure: Exception) {
            warnings += "Un fichier HRV est illisible : ${failure.message ?: failure::class.simpleName}"
        }
    }

    private fun isCsv(name: String) = name.endsWith(CSV_SUFFIX) && !name.contains(JSON_DIRECTORY)

    private fun isHrvBinning(name: String) =
        name.contains("$JSON_DIRECTORY$HRV_TYPE/") && name.endsWith(BINNING_SUFFIX)

    private fun fileName(path: String) = path.substringAfterLast('/')

    private fun binningId(path: String) = fileName(path).removeSuffix(BINNING_SUFFIX)

    /**
     * Extrait le type Samsung du nom de fichier.
     * `.../com.samsung.shealth.sleep.20260908101800.csv` donne `com.samsung.shealth.sleep`.
     */
    private fun dataTypeOf(path: String): String? {
        val file = fileName(path).removeSuffix(CSV_SUFFIX)
        val separator = file.lastIndexOf('.')
        if (separator <= 0) return null
        return file.substring(0, separator).takeIf { it.startsWith(SAMSUNG_PREFIX) }
    }

    private companion object {
        const val CSV_SUFFIX = ".csv"
        const val BINNING_SUFFIX = ".binning_data.json"
        const val JSON_DIRECTORY = "jsons/"
        const val SAMSUNG_PREFIX = "com.samsung"
        const val HRV_TYPE = "com.samsung.health.hrv"

        /** Taille de lot pour l'écriture des échantillons HRV. */
        const val HRV_BATCH_SIZE = 250

        /** Un événement d'avancement tous les N fichiers : sinon l'UI recompose sans arrêt. */
        const val PROGRESS_EVERY = 25

        /** Table de routage : un type Samsung vers le gestionnaire qui sait le lire. */
        val HANDLERS: Map<String, CsvHandler> = mapOf(
            "com.samsung.shealth.sleep" to csvHandler(SleepMapper::map) { sink, items, day ->
                sink.writeSleepNights(items); items.forEach { day(it.date) }
            },
            "com.samsung.health.sleep_stage" to csvHandler(SleepStageMapper::map) { sink, items, _ ->
                sink.writeSleepStages(items)
            },
            "com.samsung.shealth.tracker.heart_rate" to csvHandler(HeartRateMapper::map) { sink, items, _ ->
                sink.writeHeartRates(items)
            },
            "com.samsung.shealth.stress" to csvHandler(StressMapper::map) { sink, items, _ ->
                sink.writeStress(items)
            },
            "com.samsung.shealth.tracker.oxygen_saturation" to csvHandler(SpO2Mapper::map) { sink, items, _ ->
                sink.writeSpO2(items)
            },
            "com.samsung.health.weight" to csvHandler(BodyCompositionMapper::map) { sink, items, _ ->
                sink.writeBodyCompositions(items)
            },
            "com.samsung.shealth.exercise" to csvHandler(ExerciseMapper::map) { sink, items, _ ->
                sink.writeExercises(items)
            },
            "com.samsung.shealth.step_daily_trend" to csvHandler(DailyStepsMapper::map) { sink, items, day ->
                sink.writeDailySteps(items); items.forEach { day(it.date) }
            },
            "com.samsung.shealth.activity.day_summary" to csvHandler(DailyActivityMapper::map) { sink, items, day ->
                sink.writeDailyActivities(items); items.forEach { day(it.date) }
            },
            "com.samsung.shealth.vitality_score" to csvHandler(EnergyScoreMapper::map) { sink, items, day ->
                sink.writeEnergyScores(items); items.forEach { day(it.date) }
            },
            "com.samsung.shealth.blood_pressure" to csvHandler(BloodPressureMapper::map) { sink, items, _ ->
                sink.writeBloodPressure(items)
            },
            "com.samsung.health.ecg" to csvHandler(EcgMapper::map) { sink, items, _ ->
                sink.writeEcgRecords(items)
            },
            "com.samsung.shealth.sleep_snoring" to csvHandler(SnoringMapper::map) { sink, items, _ ->
                sink.writeSnoringEpisodes(items)
            },
            "com.samsung.health.respiratory_rate" to csvHandler(RespiratoryRateMapper::map) { sink, items, _ ->
                sink.writeRespiratoryRates(items)
            },
            "com.samsung.health.skin_temperature" to csvHandler(SkinTemperatureMapper::map) { sink, items, _ ->
                sink.writeSkinTemperatures(items)
            },
            "com.samsung.health.sleep_apnea" to csvHandler(SleepApneaMapper::map) { sink, items, _ ->
                sink.writeSleepApnea(items)
            },
            "com.samsung.shealth.tracker.floors_day_summary" to csvHandler(DailyFloorsMapper::map) { sink, items, day ->
                sink.writeDailyFloors(items); items.forEach { day(it.date) }
            },
            "com.samsung.shealth.alerted_stress" to csvHandler(StressAlertMapper::map) { sink, items, _ ->
                sink.writeStressAlerts(items)
            },
        )
    }
}

/**
 * Associe un mappeur de ligne à l'écriture correspondante dans le puits de données.
 *
 * Le lecteur CSV rend les lignes par un rappel non suspendu. Le gestionnaire convertit
 * donc chaque ligne au vol, puis écrit les modèles obtenus par lots de [BATCH_SIZE].
 * Seuls les modèles convertis restent en mémoire, jamais les lignes brutes.
 */
internal interface CsvHandler {
    suspend fun consume(
        reader: SamsungCsvReader,
        input: InputStream,
        sink: HealthDataSink,
        observeDay: (LocalDate?) -> Unit,
    ): Int
}

private const val BATCH_SIZE = 500

/** Crée le gestionnaire d'un type Samsung à partir de son mappeur et de son écriture. */
internal fun <T : Any> csvHandler(
    map: (SamsungCsvRow) -> T?,
    write: suspend (HealthDataSink, List<T>, (LocalDate?) -> Unit) -> Unit,
): CsvHandler = object : CsvHandler {
    override suspend fun consume(
        reader: SamsungCsvReader,
        input: InputStream,
        sink: HealthDataSink,
        observeDay: (LocalDate?) -> Unit,
    ): Int {
        val items = mutableListOf<T>()
        reader.forEachRow(input) { row -> map(row)?.let { items += it } }

        var written = 0
        for (batch in items.chunked(BATCH_SIZE)) {
            write(sink, batch, observeDay)
            written += batch.size
        }
        return written
    }
}
