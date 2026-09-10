package com.kmt.healthanalyzer.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.kmt.healthanalyzer.data.repository.HealthConnectSyncResult
import com.kmt.healthanalyzer.data.repository.HealthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HealthSyncWorker] ne doit jamais se voir : elle ne notifie rien, elle ne lance aucune
 * exception vers WorkManager — un échec se traduit en nouvelle tentative, puis en abandon
 * silencieux.
 */
class HealthSyncWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val repository: HealthRepository = mockk()

    @Test
    fun `reprend le lendemain du dernier jour deja en base`() = runTest {
        coEvery { repository.lastRecordedDay() } returns LocalDate.of(2026, 8, 1)
        val fromSlot = mutableListOf<Instant>()
        coEvery { repository.syncFromHealthConnect(capture(fromSlot), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        val worker = newWorker(params(runAttemptCount = 0))
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val expectedFrom = LocalDate.of(2026, 8, 2).atStartOfDay(zone).toInstant()
        assertEquals(expectedFrom, fromSlot.single())
    }

    @Test
    fun `sans historique local, remonte 30 jours`() = runTest {
        coEvery { repository.lastRecordedDay() } returns null
        coEvery { repository.syncFromHealthConnect(any(), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        val worker = newWorker(params(runAttemptCount = 0))
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `un echec retente au lieu de faire remonter une exception bruyante`() = runTest {
        coEvery { repository.lastRecordedDay() } returns LocalDate.of(2026, 8, 1)
        coEvery { repository.syncFromHealthConnect(any(), any()) } throws IllegalStateException("Health Connect indisponible")

        val worker = newWorker(params(runAttemptCount = 0))
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `un echec repete abandonne silencieusement plutot que de retenter indefiniment`() = runTest {
        coEvery { repository.lastRecordedDay() } returns LocalDate.of(2026, 8, 1)
        coEvery { repository.syncFromHealthConnect(any(), any()) } throws IllegalStateException("Health Connect indisponible")

        val worker = newWorker(params(runAttemptCount = 10))
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    private fun params(runAttemptCount: Int): WorkerParameters {
        val params: WorkerParameters = mockk(relaxed = true)
        every { params.runAttemptCount } returns runAttemptCount
        return params
    }

    private fun newWorker(params: WorkerParameters) = HealthSyncWorker(context, params, repository, zone)
}
