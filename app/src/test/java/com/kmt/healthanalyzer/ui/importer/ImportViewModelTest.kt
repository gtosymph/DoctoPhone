package com.kmt.healthanalyzer.ui.importer

import com.kmt.healthanalyzer.data.healthconnect.HealthConnectAvailabilityChecker
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectStatus
import com.kmt.healthanalyzer.data.repository.HealthConnectSyncResult
import com.kmt.healthanalyzer.ui.state.StateCopy
import com.kmt.healthanalyzer.data.repository.HealthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Vérifie que [ImportViewModel.syncHealthConnect] distingue les trois situations qu'une liste
 * vide ne permet pas de distinguer à l'écran : aucune permission accordée, historique
 * inaccessible faute de permission dédiée, et absence réelle de donnée (voir
 * [HealthConnectSyncResult] et le CLAUDE.md du projet, section « Le lecteur de santé »).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val repository: HealthRepository = mockk()
    private val availabilityChecker: HealthConnectAvailabilityChecker = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { availabilityChecker.status() } returns HealthConnectStatus.AVAILABLE
        coEvery { repository.lastRecordedDay() } returns LocalDate.of(2026, 8, 1)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `aucune permission accordee produit un message qui invite a autoriser l'acces`() = runTest(dispatcher) {
        coEvery { repository.syncFromHealthConnect(any(), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = false,
            missingDataPermissions = setOf("android.permission.health.READ_STEPS"),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isSyncing)
        assertTrue(state.syncHasIssue)
        assertTrue(state.syncMessage!!.contains("Aucune permission"))
        assertTrue(state.syncMessage!!.contains("Autoriser l'accès"))
    }

    @Test
    fun `historique manquant produit un message qui explique la limite de 30 jours`() = runTest(dispatcher) {
        coEvery { repository.syncFromHealthConnect(any(), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = true,
            earliestDate = LocalDate.of(2026, 8, 10),
            latestDate = LocalDate.of(2026, 9, 8),
        )

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.syncHasIssue)
        assertTrue(state.syncMessage!!.contains(StateCopy.HISTORY_PERMISSION_MISSING))
    }

    @Test
    fun `permissions completes et donnees presentes montrent l'etendue reelle sans alerte`() = runTest(dispatcher) {
        coEvery { repository.syncFromHealthConnect(any(), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = LocalDate.of(2025, 3, 12),
            latestDate = LocalDate.of(2026, 9, 9),
        )

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.syncHasIssue)
        assertEquals("Données disponibles du 12 mars 2025 au 9 septembre 2026.", state.syncMessage)
    }

    @Test
    fun `permissions completes sans aucune donnee le dit explicitement`() = runTest(dispatcher) {
        coEvery { repository.syncFromHealthConnect(any(), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.syncHasIssue)
        assertEquals("Health Connect n'a rendu aucune donnée sur la période demandée.", state.syncMessage)
    }

    @Test
    fun `une exception du depot se traduit en message d'erreur signale comme un probleme`() = runTest(dispatcher) {
        coEvery { repository.syncFromHealthConnect(any(), any()) } throws IllegalStateException("Health Connect indisponible")

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isSyncing)
        assertTrue(state.syncHasIssue)
        assertTrue(state.syncMessage!!.contains("Health Connect indisponible"))
    }

    @Test
    fun `syncFromHealthConnect part du lendemain du dernier jour deja en base`() = runTest(dispatcher) {
        val fromSlot = mutableListOf<Instant>()
        coEvery { repository.syncFromHealthConnect(capture(fromSlot), any()) } returns HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        val viewModel = newViewModel()
        viewModel.syncHealthConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val expectedFrom = LocalDate.of(2026, 8, 2).atStartOfDay(zone).toInstant()
        assertEquals(expectedFrom, fromSlot.single())
    }

    private fun newViewModel() = ImportViewModel(repository, availabilityChecker, zone)
}
