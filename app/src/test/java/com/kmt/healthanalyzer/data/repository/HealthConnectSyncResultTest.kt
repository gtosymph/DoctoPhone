package com.kmt.healthanalyzer.data.repository

import java.time.Instant
import com.kmt.healthanalyzer.ui.state.StateCopy
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste [HealthConnectSyncResult] et [isHistoryPermissionMissing], qui distinguent les trois
 * situations qu'une simple liste vide ne permet pas de distinguer à l'écran d'import : aucune
 * permission accordée, historique au-delà de 30 jours inaccessible, ou données réellement
 * absentes (voir le CLAUDE.md du projet, section « READ_HEALTH_DATA_HISTORY »).
 */
class HealthConnectSyncResultTest {

    // --- toUserMessage / hasIssue ---

    @Test
    fun `aucune permission accordee prend le pas sur tout le reste`() {
        val result = HealthConnectSyncResult(
            hasAnyDataPermission = false,
            missingDataPermissions = setOf("android.permission.health.READ_STEPS"),
            historyPermissionMissing = true,
            earliestDate = LocalDate.of(2026, 1, 1),
            latestDate = LocalDate.of(2026, 9, 9),
        )

        assertTrue(result.hasIssue)
        assertTrue(result.toUserMessage().contains("Aucune permission Health Connect n'est accordée"))
        // Le message ne doit pas en plus prétendre montrer une étendue de données.
        assertFalse(result.toUserMessage().contains("Données disponibles"))
    }

    @Test
    fun `permissions completes et donnees presentes montrent l'etendue reelle sans alerte`() {
        val result = HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = LocalDate.of(2025, 3, 12),
            latestDate = LocalDate.of(2026, 9, 9),
        )

        assertFalse(result.hasIssue)
        assertEquals("Données disponibles du 12 mars 2025 au 9 septembre 2026.", result.toUserMessage())
    }

    @Test
    fun `permissions completes sans aucune donnee le dit explicitement`() {
        val result = HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = false,
            earliestDate = null,
            latestDate = null,
        )

        assertFalse(result.hasIssue)
        assertEquals("Health Connect n'a rendu aucune donnée sur la période demandée.", result.toUserMessage())
    }

    @Test
    fun `historique manquant s'ajoute au message meme quand des donnees sont revenues`() {
        val result = HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = emptySet(),
            historyPermissionMissing = true,
            earliestDate = LocalDate.of(2026, 8, 10),
            latestDate = LocalDate.of(2026, 9, 9),
        )

        assertTrue(result.hasIssue)
        val message = result.toUserMessage()
        assertTrue(message.contains("Données disponibles du 10 août 2026 au 9 septembre 2026."))
        assertTrue(message.contains(StateCopy.HISTORY_PERMISSION_MISSING))
    }

    @Test
    fun `permissions de type de donnee manquantes s'ajoutent au message`() {
        val result = HealthConnectSyncResult(
            hasAnyDataPermission = true,
            missingDataPermissions = setOf("android.permission.health.READ_WEIGHT"),
            historyPermissionMissing = false,
            earliestDate = LocalDate.of(2026, 8, 10),
            latestDate = LocalDate.of(2026, 9, 9),
        )

        assertTrue(result.hasIssue)
        assertTrue(result.toUserMessage().contains("Certaines permissions de type de donnée"))
    }

    // --- isHistoryPermissionMissing ---

    @Test
    fun `la permission d'historique accordee neutralise toute demande ancienne`() {
        val from = Instant.parse("2020-01-01T00:00:00Z")

        assertFalse(isHistoryPermissionMissing(hasHistoryPermission = true, from = from))
    }

    @Test
    fun `une demande a l'interieur des 30 derniers jours ne signale rien`() {
        val now = Instant.parse("2026-09-09T00:00:00Z")
        val from = now.minus(10, ChronoUnit.DAYS)

        assertFalse(isHistoryPermissionMissing(hasHistoryPermission = false, from = from, now = now))
    }

    @Test
    fun `une demande plus ancienne que 30 jours sans permission d'historique est signalee`() {
        val now = Instant.parse("2026-09-09T00:00:00Z")
        val from = now.minus(90, ChronoUnit.DAYS)

        assertTrue(isHistoryPermissionMissing(hasHistoryPermission = false, from = from, now = now))
    }

    @Test
    fun `pile 30 jours n'est pas encore hors fenetre`() {
        val now = Instant.parse("2026-09-09T00:00:00Z")
        val from = now.minus(30, ChronoUnit.DAYS)

        assertFalse(isHistoryPermissionMissing(hasHistoryPermission = false, from = from, now = now))
    }
}
