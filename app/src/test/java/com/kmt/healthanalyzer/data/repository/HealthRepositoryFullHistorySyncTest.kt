package com.kmt.healthanalyzer.data.repository

import android.content.Context
import com.kmt.healthanalyzer.data.db.HealthDatabase
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissionState
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissions
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectReader
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.DataOrigin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Teste [HealthRepository.syncAllFromHealthConnect], la récupération de tout l'historique.
 *
 * Ce test existe à cause d'un défaut réel, signalé par l'utilisateur sur son appareil : après
 * avoir choisi « 1 an » dans le rapport, il ne voyait que les 30 derniers jours. La cause
 * n'était ni Health Connect ni la permission d'historique, mais l'app elle-même — la première
 * synchronisation ne demandait que 30 jours, en dur. Le rapport lit la base locale : une base
 * courte ne peut pas produire un rapport long, quelle que soit la période choisie à l'écran.
 *
 * La lecture se fait par tranches plutôt qu'en une seule requête : plusieurs années de mesures
 * cardiaques tiennent difficilement en mémoire d'un coup, et une tranche écrite est une tranche
 * acquise même si la suivante échoue.
 */
class HealthRepositoryFullHistorySyncTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val context: Context = mockk(relaxed = true)
    private val database: HealthDatabase = mockk(relaxed = true)
    private val sink: RoomHealthDataSink = mockk(relaxed = true)
    private val reader: HealthConnectReader = mockk()
    private val readerProvider: Provider<HealthConnectReader> = mockk()

    /** Instant de référence fixe : sans lui, les bornes calculées changeraient à chaque exécution. */
    private val now: Instant = Instant.parse("2026-09-10T12:00:00Z")

    @Before
    fun setUp() {
        every { readerProvider.get() } returns reader
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
    fun `la synchronisation complete remonte bien au-dela des 30 derniers jours`() = runTest {
        val requestedStarts = mutableListOf<Instant>()
        coEvery { reader.readDailySteps(any(), any()) } answers {
            requestedStarts += firstArg<Instant>()
            emptyList()
        }

        newRepository().syncAllFromHealthConnect(to = now)

        val oldestRequested = requestedStarts.minOrNull()
        assertTrue(
            "Aucune tranche n'a été demandée : la synchronisation complète n'a rien lu.",
            oldestRequested != null,
        )
        assertTrue(
            "La synchronisation complète n'est pas remontée au-delà de 30 jours : la plus " +
                "ancienne tranche demandée commence à $oldestRequested, alors que le seuil de " +
                "30 jours est à ${now.minus(30, ChronoUnit.DAYS)}. C'est exactement le défaut " +
                "signalé par l'utilisateur.",
            oldestRequested!!.isBefore(now.minus(30, ChronoUnit.DAYS)),
        )
    }

    @Test
    fun `elle rapporte la date la plus ancienne trouvee, meme a plusieurs mois en arriere`() = runTest {
        // Une seule journée porteuse de données, six mois en arrière : la marche arrière ne doit
        // pas s'arrêter avant de l'avoir atteinte.
        val ancientDay = LocalDate.of(2026, 3, 15)
        val ancientInstant = ancientDay.atStartOfDay(zone).toInstant()
        coEvery { reader.readDailySteps(any(), any()) } answers {
            val from: Instant = firstArg()
            val to: Instant = secondArg()
            if (!ancientInstant.isBefore(from) && ancientInstant.isBefore(to)) {
                listOf(DailySteps(date = ancientDay, steps = 8000, origin = DataOrigin.HEALTH_CONNECT))
            } else {
                emptyList()
            }
        }

        val result = newRepository().syncAllFromHealthConnect(to = now)

        assertEquals(ancientDay, result.earliestDate)
        assertEquals(ancientDay, result.latestDate)
    }

    @Test
    fun `elle s'arrete apres une longue suite de tranches vides plutot que de creuser sans fin`() = runTest {
        var chunkCount = 0
        coEvery { reader.readDailySteps(any(), any()) } answers {
            chunkCount += 1
            emptyList()
        }

        newRepository().syncAllFromHealthConnect(to = now)

        // Sans garde d'arrêt, la marche arrière irait jusqu'au plafond de profondeur — soit
        // beaucoup plus de tranches. Le nombre exact importe moins que le fait qu'elle renonce.
        assertTrue(
            "La synchronisation a lu $chunkCount tranches vides d'affilée : elle ne renonce " +
                "jamais, et une base neuve coûterait des centaines de requêtes inutiles.",
            chunkCount <= 12,
        )
    }

    @Test
    fun `sans permission d'historique, elle le signale au lieu de creuser en silence`() = runTest {
        coEvery { reader.permissionState() } returns HealthConnectPermissionState(
            grantedDataPermissions = HealthConnectPermissions.READ_PERMISSIONS,
            hasHistoryPermission = false,
        )

        val result = newRepository().syncAllFromHealthConnect(to = now)

        assertTrue(
            "L'utilisateur demande tout son historique sans avoir accordé la permission qui " +
                "l'autorise : l'app doit le dire, sinon il croit que ses données ont disparu.",
            result.historyPermissionMissing,
        )
    }

    @Test
    fun `sans aucune permission de donnee, elle renonce tout de suite`() = runTest {
        coEvery { reader.permissionState() } returns HealthConnectPermissionState(
            grantedDataPermissions = emptySet(),
            hasHistoryPermission = false,
        )
        var chunkCount = 0
        coEvery { reader.readDailySteps(any(), any()) } answers {
            chunkCount += 1
            emptyList()
        }

        val result = newRepository().syncAllFromHealthConnect(to = now)

        assertEquals(
            "Aucune permission n'est accordée : lire plusieurs tranches ne peut rien donner.",
            1,
            chunkCount,
        )
        assertTrue(result.hasIssue)
    }

    private fun newRepository() = HealthRepository(context, database, sink, readerProvider, zone)
}
