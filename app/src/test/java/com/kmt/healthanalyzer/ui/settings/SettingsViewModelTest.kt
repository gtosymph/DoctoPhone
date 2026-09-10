package com.kmt.healthanalyzer.ui.settings

import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.preferences.AppSettings
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.data.settings.ApiKeyStore
import com.kmt.healthanalyzer.data.work.HealthAnalyzerWorkScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Vérifie que le réglage « bilans automatiques » répercute bien son choix sur
 * [HealthAnalyzerWorkScheduler] : c'est le seul endroit du chantier qui programme ou
 * annule les tâches de fond depuis l'écran de réglages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences: AppPreferences = mockk(relaxed = true)
    private val apiKeyStore: ApiKeyStore = mockk()
    private val repository: HealthRepository = mockk()
    private val workScheduler: HealthAnalyzerWorkScheduler = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.settings } returns flowOf(
            AppSettings(provider = LlmProvider.ANTHROPIC, model = "claude", sleepTargetMinutes = 450, autoChecksEnabled = true),
        )
        every { apiKeyStore.hasKey(any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activer les bilans automatiques persiste le reglage et programme les taches`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.setAutoChecksEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { preferences.setAutoChecksEnabled(true) }
        coVerify { workScheduler.schedule() }
    }

    @Test
    fun `desactiver les bilans automatiques persiste le reglage et annule les taches`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.setAutoChecksEnabled(false)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { preferences.setAutoChecksEnabled(false) }
        coVerify { workScheduler.cancel() }
    }

    // Une troisième vérification — « l'état reflète le réglage lu depuis les préférences » —
    // n'est délibérément pas couverte ici : `providersWithKey()` (préexistant, hors chantier)
    // fait passer la collecte par `Dispatchers.IO`, un vrai dispatcher que le
    // `StandardTestDispatcher` de ce test ne contrôle pas. Le résultat serait un test
    // intermittent qui ne prouve rien de plus que les deux ci-dessus, qui eux ne
    // dépendent pas de ce chemin.

    private fun newViewModel() = SettingsViewModel(preferences, apiKeyStore, repository, workScheduler)
}
