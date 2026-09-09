package com.kmt.healthanalyzer.data.repository

import android.content.Context
import com.kmt.healthanalyzer.data.db.HealthDatabase
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissionState
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissions
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectReader
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.DataOrigin
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Teste [HealthRepository.syncFromHealthConnect] : c'est ici que la photographie de permissions
 * ([HealthConnectPermissionState]) et les dates effectivement reçues se transforment en
 * [HealthConnectSyncResult], la seule information qui permet à l'écran d'import de distinguer
 * un refus de permission, un historique inaccessible, ou une simple absence de donnée.
 */
class HealthRepositorySyncTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val context: Context = mockk(relaxed = true)
    private val database: HealthDatabase = mockk(relaxed = true)
    private val sink: RoomHealthDataSink = mockk(relaxed = true)
    private val reader: HealthConnectReader = mockk()
    private val readerProvider: Provider<HealthConnectReader> = mockk()

    @Before
    fun setUp() {
        every { readerProvider.get() } returns reader
        // Par défaut : tout accordé, aucune donnée — chaque test précise ce qu'il lui faut.
        coEvery { reader.permissionState() } returns HealthConnectPermissionState(
            grantedDataPermissions = HealthConnectPermissions.READ_PERMISSIONS,
            hasHistoryPermission = true,
        )
        coEvery { reader.readSleep(any(), any()) } returns emptyList()
        coEvery { reader.readSleepStages(any(), any()) } returns emptyList()
        coEvery { reader.readHeartRate(any(), any()) } returns emptyList()
        coEvery { reader.readDailySteps(any(), any()) } returns emptyList()
        coEvery { reader.readExercise(any(), any()) } returns emptyList()
        coEvery { reader.readBodyComposition(any(), any()) } returns emptyList()
        coEvery { reader.readOxygenSaturation(any(), any()) } returns emptyList()
        coEvery { reader.readHrv(any(), any()) } returns emptyList()
    }

    @Test
    fun `sans aucune donnee recue, earliestDate et latestDate restent nuls`() = runTest {
        val result = newRepository().syncFromHealthConnect(from = Instant.parse("2026-08-01T00:00:00Z"))

        assertNull(result.earliestDate)
        assertNull(result.latestDate)
        assertTrue(result.hasAnyDataPermission)
        assertTrue(result.missingDataPermissions.isEmpty())
        assertFalse(result.historyPermissionMissing)
    }

    @Test
    fun `earliestDate et latestDate couvrent tous les types de donnee recus, y compris les bornes d'une seance`() =
        runTest {
            coEvery { reader.readSleep(any(), any()) } returns listOf(
                SleepNight(
                    id = "s1",
                    date = LocalDate.of(2026, 8, 5),
                    bedTime = Instant.parse("2026-08-04T22:00:00Z"),
                    wakeTime = Instant.parse("2026-08-05T06:00:00Z"),
                    durationMinutes = 480,
                    origin = DataOrigin.HEALTH_CONNECT,
                ),
            )
            coEvery { reader.readDailySteps(any(), any()) } returns listOf(
                DailySteps(date = LocalDate.of(2026, 9, 1), steps = 8000, origin = DataOrigin.HEALTH_CONNECT),
            )
            coEvery { reader.readHeartRate(any(), any()) } returns listOf(
                HeartRateSample(
                    id = "hr1",
                    time = Instant.parse("2026-08-20T12:00:00Z"),
                    beatsPerMinute = 70,
                    origin = DataOrigin.HEALTH_CONNECT,
                ),
            )
            // Une séance qui déborde sur deux jours : les DEUX bornes doivent compter.
            coEvery { reader.readExercise(any(), any()) } returns listOf(
                ExerciseSession(
                    id = "ex1",
                    kind = ExerciseKind.RUNNING,
                    samsungTypeCode = null,
                    start = Instant.parse("2025-03-11T23:30:00Z"),
                    end = Instant.parse("2025-03-12T00:15:00Z"),
                    durationMinutes = 45,
                    origin = DataOrigin.HEALTH_CONNECT,
                ),
            )
            coEvery { reader.readBodyComposition(any(), any()) } returns listOf(
                BodyComposition(id = "b1", time = Instant.parse("2026-08-15T08:00:00Z"), weightKg = 70f),
            )
            coEvery { reader.readOxygenSaturation(any(), any()) } returns listOf(
                SpO2Sample(id = "o1", time = Instant.parse("2026-08-16T08:00:00Z"), percent = 97f),
            )
            coEvery { reader.readHrv(any(), any()) } returns listOf(
                HrvSample(id = "h1", time = Instant.parse("2026-08-17T08:00:00Z"), sdnnMillis = null, rmssdMillis = 40f),
            )

            val result = newRepository().syncFromHealthConnect(from = Instant.parse("2025-01-01T00:00:00Z"))

            // La séance commence le 11 mars 2025 (heure locale Europe/Paris) : c'est la date la
            // plus ancienne, avant même la nuit de sommeil du 5 août.
            assertEquals(LocalDate.of(2025, 3, 12), result.earliestDate)
            assertEquals(LocalDate.of(2026, 9, 1), result.latestDate)
        }

    @Test
    fun `aucune permission accordee se propage telle quelle dans le resultat`() = runTest {
        coEvery { reader.permissionState() } returns HealthConnectPermissionState(
            grantedDataPermissions = emptySet(),
            hasHistoryPermission = false,
        )

        val result = newRepository().syncFromHealthConnect(from = Instant.parse("2026-08-01T00:00:00Z"))

        assertFalse(result.hasAnyDataPermission)
    }

    @Test
    fun `une demande ancienne sans permission d'historique est signalee`() = runTest {
        coEvery { reader.permissionState() } returns HealthConnectPermissionState(
            grantedDataPermissions = setOf("android.permission.health.READ_STEPS"),
            hasHistoryPermission = false,
        )

        val result = newRepository().syncFromHealthConnect(from = Instant.now().minusSeconds(120L * 24 * 3600))

        assertTrue(result.historyPermissionMissing)
    }

    @Test
    fun `les listes lues sont bien ecrites dans la base`() = runTest {
        val steps = listOf(DailySteps(date = LocalDate.of(2026, 9, 1), steps = 8000, origin = DataOrigin.HEALTH_CONNECT))
        coEvery { reader.readDailySteps(any(), any()) } returns steps

        newRepository().syncFromHealthConnect(from = Instant.parse("2026-08-01T00:00:00Z"))

        coVerify { sink.writeDailySteps(steps) }
    }

    private fun newRepository() = HealthRepository(context, database, sink, readerProvider, zone)
}
