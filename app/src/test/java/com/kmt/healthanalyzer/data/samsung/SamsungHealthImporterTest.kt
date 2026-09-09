package com.kmt.healthanalyzer.data.samsung

import com.kmt.healthanalyzer.domain.model.SleepStage
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SamsungHealthImporterTest {

    private val stamp = "20260908101800"
    private val root = "samsunghealth_test_$stamp"

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Écrit une archive de test sur le disque : l'importateur lit un fichier, pas un flux. */
    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = temporaryFolder.newFile("export-${'$'}{entries.hashCode()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, body) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun csv(dataType: String, header: String, vararg rows: String) =
        (listOf("$dataType,7006011,1", header) + rows).joinToString("\n")

    private fun importerFor(archive: File, sink: RecordingSink) =
        SamsungHealthImporter(sink, ZipExportSource(archive))

    /** Écrit les mêmes entrées dans un dossier, pour vérifier les deux sources à l'identique. */
    private fun directoryOf(vararg entries: Pair<String, String>): File {
        val root = temporaryFolder.newFolder("export-${'$'}{entries.hashCode()}")
        entries.forEach { (path, body) ->
            val file = File(root, path)
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
        return root
    }

    // -------------------------------------------------------------------

    @Test
    fun `imports sleep nights from the archive`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.sleep.$stamp.csv" to csv(
                "com.samsung.shealth.sleep",
                "sleep_score,sleep_duration,com.samsung.health.sleep.start_time," +
                    "com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset," +
                    "com.samsung.health.sleep.datauuid,",
                "71,401,2025-07-02 00:45:00.000,2025-07-02 07:26:00.000,UTC+0200,n-1,",
                "64,380,2025-07-03 01:00:00.000,2025-07-03 07:20:00.000,UTC+0200,n-2,",
            ),
        )
        val sink = RecordingSink()

        val summary = importerFor(archive, sink).import().last()

        assertEquals(2, sink.sleepNights.size)
        assertEquals(71, sink.sleepNights.first().score)
        assertTrue(summary is ImportProgress.Finished)
    }

    @Test
    fun `dispatches every supported data type to its own mapper`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.sleep.$stamp.csv" to csv(
                "com.samsung.shealth.sleep",
                "sleep_duration,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time," +
                    "com.samsung.health.sleep.time_offset,com.samsung.health.sleep.datauuid,",
                "401,2025-07-02 00:45:00.000,2025-07-02 07:26:00.000,UTC+0200,n-1,",
            ),
            "$root/com.samsung.health.sleep_stage.$stamp.csv" to csv(
                "com.samsung.health.sleep_stage",
                "start_time,end_time,stage,sleep_id,time_offset,datauuid,",
                "2025-07-02 00:45:00.000,2025-07-02 01:03:00.000,40003,n-1,UTC+0200,s-1,",
            ),
            "$root/com.samsung.shealth.tracker.heart_rate.$stamp.csv" to csv(
                "com.samsung.shealth.tracker.heart_rate",
                "com.samsung.health.heart_rate.heart_rate,com.samsung.health.heart_rate.start_time," +
                    "com.samsung.health.heart_rate.time_offset,com.samsung.health.heart_rate.datauuid,",
                "77.0,2025-07-01 20:15:32.766,UTC+0200,hr-1,",
            ),
            "$root/com.samsung.shealth.step_daily_trend.$stamp.csv" to csv(
                "com.samsung.shealth.step_daily_trend",
                "source_type,count,day_time,datauuid,",
                "-2,9715,2024-11-08 00:00:00.000,d-1,",
            ),
            "$root/com.samsung.health.weight.$stamp.csv" to csv(
                "com.samsung.health.weight",
                "weight,height,start_time,time_offset,datauuid,",
                "91.0,169.0,2025-07-01 20:49:34.277,UTC+0200,w-1,",
            ),
            "$root/com.samsung.shealth.stress.$stamp.csv" to csv(
                "com.samsung.shealth.stress",
                "score,start_time,end_time,time_offset,datauuid,",
                "19.0,2025-07-01 20:00:00.000,2025-07-01 20:59:59.999,UTC+0200,st-1,",
            ),
            "$root/com.samsung.shealth.vitality_score.$stamp.csv" to csv(
                "com.samsung.shealth.vitality_score",
                "total_score,sleep_score,day_time,datauuid,",
                "77.65,72.23,2025-07-02 00:00:00.000,v-1,",
            ),
        )
        val sink = RecordingSink()

        importerFor(archive, sink).import().last()

        assertEquals(1, sink.sleepNights.size)
        assertEquals(SleepStage.DEEP, sink.sleepStages.single().stage)
        assertEquals(77, sink.heartRates.single().beatsPerMinute)
        assertEquals(9715, sink.dailySteps.single().steps)
        assertEquals(91.0f, sink.bodyCompositions.single().weightKg, 0.01f)
        assertEquals(19, sink.stress.single().score)
        assertEquals(78, sink.energyScores.single().total)
    }

    @Test
    fun `dispatches the eight new clinical data types to their own mapper`() = runTest {
        val bpPrefix = "com.samsung.health.blood_pressure."
        val archive = zipOf(
            "$root/com.samsung.shealth.blood_pressure.$stamp.csv" to csv(
                "com.samsung.shealth.blood_pressure",
                "${bpPrefix}systolic,${bpPrefix}diastolic,${bpPrefix}start_time," +
                    "${bpPrefix}time_offset,${bpPrefix}datauuid,",
                "128,82,2026-03-01 08:15:00.000,UTC+0100,bp-1,",
            ),
            "$root/com.samsung.health.ecg.$stamp.csv" to csv(
                "com.samsung.health.ecg",
                "start_time,mean_heart_rate,time_offset,datauuid,",
                "2026-03-01 08:00:00.000,82.0,UTC+0100,ecg-1,",
            ),
            "$root/com.samsung.shealth.sleep_snoring.$stamp.csv" to csv(
                "com.samsung.shealth.sleep_snoring",
                "start_time,end_time,duration,time_offset,datauuid,",
                "2026-03-01 01:00:00.000,2026-03-01 01:02:00.000,120000,UTC+0100,sn-1,",
            ),
            "$root/com.samsung.health.respiratory_rate.$stamp.csv" to csv(
                "com.samsung.health.respiratory_rate",
                "start_time,average,time_offset,datauuid,",
                "2026-03-01 02:00:00.000,14.5,UTC+0100,rr-1,",
            ),
            "$root/com.samsung.health.skin_temperature.$stamp.csv" to csv(
                "com.samsung.health.skin_temperature",
                "start_time,temperature,time_offset,datauuid,",
                "2026-03-01 03:00:00.000,33.2,UTC+0100,st-1,",
            ),
            "$root/com.samsung.health.sleep_apnea.$stamp.csv" to csv(
                "com.samsung.health.sleep_apnea",
                "start_time,result,time_offset,datauuid,",
                "2026-03-01 00:00:00.000,1,UTC+0100,ap-1,",
            ),
            "$root/com.samsung.shealth.tracker.floors_day_summary.$stamp.csv" to csv(
                "com.samsung.shealth.tracker.floors_day_summary",
                "day_time,floor_count,datauuid,",
                "2026-03-01 00:00:00.000,9,fl-1,",
            ),
            "$root/com.samsung.shealth.alerted_stress.$stamp.csv" to csv(
                "com.samsung.shealth.alerted_stress",
                "start_time,end_time,time_offset,datauuid,",
                "2026-03-01 10:00:00.000,2026-03-01 10:12:00.000,UTC+0100,al-1,",
            ),
        )
        val sink = RecordingSink()

        importerFor(archive, sink).import().last()

        assertEquals(128, sink.bloodPressure.single().systolic)
        assertEquals(82, sink.ecgRecords.single().meanHeartRate)
        assertEquals(2, sink.snoringEpisodes.single().durationMinutes)
        assertEquals(14.5f, sink.respiratoryRates.single().breathsPerMinute, 0.01f)
        assertEquals(33.2f, sink.skinTemperatures.single().celsius, 0.01f)
        assertEquals(1, sink.sleepApnea.single().result)
        assertEquals(9, sink.dailyFloors.single().floors)
        assertEquals("al-1", sink.stressAlerts.single().id)
    }

    @Test
    fun `aggregates one hrv binning file into a single hourly sample`() = runTest {
        val archive = zipOf(
            "$root/jsons/com.samsung.health.hrv/9/abc.binning_data.json" to
                """[{"start_time":1751511522616,"end_time":1751511821616,"sdnn":50.0,"rmssd":60.0},
                    {"start_time":1751511552616,"end_time":1751511851616,"sdnn":60.0,"rmssd":70.0},
                    {"start_time":1751511582616,"end_time":1751511881616,"sdnn":70.0,"rmssd":80.0}]""",
        )
        val sink = RecordingSink()

        importerFor(archive, sink).import().last()

        val sample = sink.hrv.single()
        assertEquals(60.0f, sample.sdnnMillis!!, 0.01f) // médiane, robuste aux artefacts
        assertEquals(70.0f, sample.rmssdMillis!!, 0.01f)
        assertEquals("abc", sample.id)
    }

    @Test
    fun `ignores files that the app does not understand`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.badge.$stamp.csv" to csv(
                "com.samsung.shealth.badge", "a,", "1,",
            ),
            "$root/files/com.samsung.health.ecg/x.data.pdf" to "%PDF-1.4 fake",
            "$root/jsons/com.samsung.shealth.stress/a/x.binning_data.json" to """[{"score":98}]""",
        )
        val sink = RecordingSink()

        val summary = importerFor(archive, sink).import().last() as ImportProgress.Finished

        assertTrue(sink.isEmpty())
        assertEquals(0, summary.summary.totalRecords)
    }

    @Test
    fun `keeps going when one file is corrupt and reports it as a warning`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.sleep.$stamp.csv" to "ceci n'est pas un CSV Samsung",
            "$root/com.samsung.shealth.step_daily_trend.$stamp.csv" to csv(
                "com.samsung.shealth.step_daily_trend",
                "source_type,count,day_time,datauuid,",
                "-2,9715,2024-11-08 00:00:00.000,d-1,",
            ),
        )
        val sink = RecordingSink()

        val summary = importerFor(archive, sink).import().last() as ImportProgress.Finished

        assertEquals(1, sink.dailySteps.size)
        assertEquals(1, summary.summary.warnings.size)
        assertTrue(summary.summary.warnings.single().contains("sleep"))
    }

    @Test
    fun `reports the covered date range`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.step_daily_trend.$stamp.csv" to csv(
                "com.samsung.shealth.step_daily_trend",
                "source_type,count,day_time,datauuid,",
                "-2,100,2024-11-08 00:00:00.000,d-1,",
                "-2,200,2026-09-08 00:00:00.000,d-2,",
            ),
        )
        val sink = RecordingSink()

        val summary = importerFor(archive, sink).import().last() as ImportProgress.Finished

        assertEquals(LocalDate.of(2024, 11, 8), summary.summary.firstDay)
        assertEquals(LocalDate.of(2026, 9, 8), summary.summary.lastDay)
        assertEquals(2, summary.summary.totalRecords)
    }

    @Test
    fun `emits progress before the final summary`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.step_daily_trend.$stamp.csv" to csv(
                "com.samsung.shealth.step_daily_trend",
                "source_type,count,day_time,datauuid,",
                "-2,100,2024-11-08 00:00:00.000,d-1,",
            ),
        )
        val sink = RecordingSink()

        val steps = importerFor(archive, sink).import().toList()

        assertTrue(steps.size >= 2)
        assertNotNull(steps.filterIsInstance<ImportProgress.Reading>().firstOrNull())
        assertTrue(steps.last() is ImportProgress.Finished)
    }

    @Test
    fun `counts records per data type in the summary`() = runTest {
        val archive = zipOf(
            "$root/com.samsung.shealth.tracker.heart_rate.$stamp.csv" to csv(
                "com.samsung.shealth.tracker.heart_rate",
                "com.samsung.health.heart_rate.heart_rate,com.samsung.health.heart_rate.start_time," +
                    "com.samsung.health.heart_rate.time_offset,com.samsung.health.heart_rate.datauuid,",
                "77.0,2025-07-01 20:15:32.766,UTC+0200,hr-1,",
                "85.0,2025-07-01 20:20:32.766,UTC+0200,hr-2,",
            ),
        )
        val sink = RecordingSink()

        val summary = importerFor(archive, sink).import().last() as ImportProgress.Finished

        assertEquals(2, summary.summary.recordsByType["com.samsung.shealth.tracker.heart_rate"])
    }

    // --- Source dossier -----------------------------------------------------

    @Test
    fun `imports an export directory, the shape samsung health actually writes`() = runTest {
        val root = directoryOf(
            "com.samsung.shealth.sleep.$stamp.csv" to csv(
                "com.samsung.shealth.sleep",
                "sleep_score,sleep_duration,com.samsung.health.sleep.start_time," +
                    "com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset," +
                    "com.samsung.health.sleep.datauuid,",
                "71,401,2025-07-02 00:45:00.000,2025-07-02 07:26:00.000,UTC+0200,n-1,",
            ),
            "jsons/com.samsung.health.hrv/9/abc.binning_data.json" to
                """[{"start_time":1751511522616,"end_time":1751511821616,"sdnn":50.0,"rmssd":60.0}]""",
        )
        val sink = RecordingSink()

        val summary = SamsungHealthImporter(sink, DirectoryExportSource(root))
            .import()
            .last() as ImportProgress.Finished

        assertEquals(1, sink.sleepNights.size)
        assertEquals(71, sink.sleepNights.single().score)
        assertEquals(1, sink.hrv.size)
        assertEquals(2, summary.summary.totalRecords)
    }

    @Test
    fun `reads a directory and its zipped copy identically`() = runTest {
        val entries = arrayOf(
            "root/com.samsung.shealth.step_daily_trend.$stamp.csv" to csv(
                "com.samsung.shealth.step_daily_trend",
                "source_type,count,day_time,datauuid,",
                "-2,9715,2024-11-08 00:00:00.000,d-1,",
                "-2,4210,2024-11-09 00:00:00.000,d-2,",
            ),
        )

        val fromDirectory = RecordingSink()
        SamsungHealthImporter(fromDirectory, DirectoryExportSource(directoryOf(*entries))).import().last()

        val fromArchive = RecordingSink()
        SamsungHealthImporter(fromArchive, ZipExportSource(zipOf(*entries))).import().last()

        assertEquals(fromDirectory.dailySteps, fromArchive.dailySteps)
        assertEquals(2, fromDirectory.dailySteps.size)
    }

    @Test
    fun `ignores a directory that holds no samsung export`() = runTest {
        val root = directoryOf("notes.txt" to "rien à voir", "photos/vacances.jpg" to "binaire")
        val sink = RecordingSink()

        val summary = SamsungHealthImporter(sink, DirectoryExportSource(root))
            .import()
            .last() as ImportProgress.Finished

        assertTrue(sink.isEmpty())
        assertEquals(0, summary.summary.totalRecords)
    }
}
