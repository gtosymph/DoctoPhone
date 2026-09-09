package com.kmt.healthanalyzer.ui.settings

import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.update.ApkDownloader
import com.kmt.healthanalyzer.data.update.GitHubReleaseClient
import com.kmt.healthanalyzer.data.update.UpdateCheckResult
import com.kmt.healthanalyzer.data.update.UpdateInstaller
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * La vérification automatique au démarrage (voir [UpdateViewModel.init]) est silencieuse par
 * construction : ces tests vérifient qu'une panne imprévue — sur le contrôle lui-même, ou sur
 * l'horodatage qui le suit — ne fait jamais tomber l'app. C'est le défaut réel décrit dans le
 * CLAUDE.md du projet, section « Les règles ProGuard portent du sens » : une écriture
 * DataStore qui plante au démarrage en release, sur un simple réglage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val releaseClient: GitHubReleaseClient = mockk()
    private val apkDownloader: ApkDownloader = mockk()
    private val installer: UpdateInstaller = mockk()
    private val preferences: AppPreferences = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { preferences.lastUpdateCheckEpochMillis() } returns null
        coEvery { preferences.setLastUpdateCheckEpochMillis(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `une exception du client qui n'est pas un UpdateError ne fait pas tomber la verification silencieuse`() =
        runTest(dispatcher) {
            coEvery { releaseClient.checkForUpdate(any()) } throws IllegalStateException("panne imprévue")

            // Ne doit lever aucune exception : sinon `runTest` échoue, la coroutine de
            // `init` l'ayant laissée remonter.
            val viewModel = newViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            // Silencieuse : aucun message d'erreur montré à l'utilisateur...
            assertEquals(UpdateStatus.Idle, viewModel.state.value.status)
            // ...mais la tentative est bien horodatée, pour ne pas retenter à chaque ouverture.
            coVerify(exactly = 1) { preferences.setLastUpdateCheckEpochMillis(any()) }
        }

    @Test
    fun `un echec d'ecriture de l'horodatage ne fait pas tomber la verification silencieuse`() =
        runTest(dispatcher) {
            coEvery { releaseClient.checkForUpdate(any()) } throws IllegalStateException("panne imprévue")
            coEvery { preferences.setLastUpdateCheckEpochMillis(any()) } throws IOException("disque plein")

            val viewModel = newViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `un echec de lecture de l'horodatage ne fait pas tomber le demarrage et traite la verification comme due`() =
        runTest(dispatcher) {
            // Un fichier de préférences corrompu lève à la LECTURE (CorruptionException côté
            // DataStore), pas seulement à l'écriture. C'est la toute première instruction du
            // chemin de démarrage, avant même l'appel au client : une exception ici ne doit
            // ni faire tomber l'app, ni empêcher la vérification — le pire cas acceptable est
            // une vérification de trop, jamais un plantage.
            coEvery { preferences.lastUpdateCheckEpochMillis() } throws IOException("fichier corrompu")
            coEvery { releaseClient.checkForUpdate(any()) } returns UpdateCheckResult.UpToDate

            val viewModel = newViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UpdateStatus.UpToDate, viewModel.state.value.status)
            coVerify(exactly = 1) { releaseClient.checkForUpdate(any()) }
        }

    @Test
    fun `une verification manuelle qui echoue avec une exception imprevue montre un message`() = runTest(dispatcher) {
        // La vérification manuelle (bouton « Vérifier ») n'est pas silencieuse : contrairement
        // au démarrage, l'utilisateur doit voir que quelque chose a échoué.
        coEvery { releaseClient.checkForUpdate(any()) } throws IllegalStateException("panne imprévue")

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.checkNow()
        dispatcher.scheduler.advanceUntilIdle()

        val status = viewModel.state.value.status
        assert(status is UpdateStatus.Failed) { "attendu Failed, obtenu $status" }
    }

    private fun newViewModel() = UpdateViewModel(releaseClient, apkDownloader, installer, preferences)
}
