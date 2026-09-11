package com.kmt.healthanalyzer.ui.week

import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.drift.DriftMetric
import com.kmt.healthanalyzer.domain.drift.MetricReview
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import com.kmt.healthanalyzer.domain.usecase.ReviewWeekUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Teste le ViewModel de l'écran d'accueil « Cette semaine ».
 *
 * L'écran est le premier que l'utilisateur voit à chaque ouverture. Sa règle principale
 * n'est donc pas d'afficher joliment un bilan, c'est de **ne jamais empêcher l'app de
 * s'ouvrir** : un calcul qui échoue se dit, il ne remonte pas.
 *
 * Le précédent est réel — une contrainte de tâche de fond refusée par Android fermait l'app
 * au démarrage, et 450 tests verts n'avaient rien vu. Voir `CLAUDE.md`, « Une suite verte
 * ne dit rien du démarrage ».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val reviewWeek: ReviewWeekUseCase = mockk()
    private val repository: HealthRepository = mockk()

    private val today = LocalDate.of(2026, 9, 10)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.recordCount() } returns 4200
        coEvery { repository.firstRecordedDay() } returns today.minusDays(293)
        coEvery { repository.lastRecordedDay() } returns today
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `le bilan calcule arrive tel quel a l'ecran`() = runTest {
        val review = reviewWith(sleepRecent = 5.2, sleepBaseline = 6.3)
        coEvery { reviewWeek() } returns review

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertSame(
            "Le ViewModel ne recalcule rien et ne reformate rien : il transporte le bilan " +
                "produit par le seul chemin de calcul de l'app.",
            review,
            state.review,
        )
        assertNull(state.errorMessage)
    }

    @Test
    fun `un calcul qui echoue se dit, il ne ferme pas l'ecran`() = runTest {
        coEvery { reviewWeek() } throws IllegalStateException("base illisible")

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull("Aucun bilan inventé pour combler le vide.", state.review)
        assertTrue(
            "Le message doit nommer la cause : « base illisible » aide, « une erreur est " +
                "survenue » n'aide personne. Reçu : ${state.errorMessage}",
            state.errorMessage.orEmpty().contains("base illisible"),
        )
    }

    @Test
    fun `l'annulation n'est pas avalee comme une erreur`() = runTest {
        // `CancellationException` hérite d'`Exception` en Kotlin : un `catch (Exception)` nu
        // l'attrape et casse la concurrence structurée. Elle survient normalement quand
        // l'écran est quitté pendant le calcul ; la transformer en message d'erreur poserait
        // un texte rouge sur un écran que l'utilisateur vient d'abandonner.
        coEvery { reviewWeek() } throws CancellationException("écran quitté")

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        assertNull(
            "Une annulation n'est pas un échec de calcul et ne doit pas s'afficher.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun `sans aucune mesure, l'ecran propose l'import au lieu d'un bilan vide`() = runTest {
        coEvery { repository.recordCount() } returns 0
        coEvery { repository.firstRecordedDay() } returns null
        coEvery { repository.lastRecordedDay() } returns null
        coEvery { reviewWeek() } returns reviewWith()

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        assertFalse(
            "Une base vide n'est pas un bilan « tout va bien » : c'est une app qu'il reste " +
                "à alimenter. L'écran doit mener à l'import, pas montrer six tirets.",
            viewModel.state.value.hasAnyData,
        )
    }

    @Test
    fun `l'etat des donnees dit la periode couverte`() = runTest {
        coEvery { reviewWeek() } returns reviewWith()

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasAnyData)
        assertEquals(
            "294 jours entre la première et la dernière mesure, bornes comprises.",
            294,
            state.coveredDays,
        )
    }

    @Test
    fun `un echec de l'etat des donnees ne prive pas l'ecran de son bilan`() = runTest {
        coEvery { repository.recordCount() } throws IllegalStateException("compte indisponible")
        coEvery { reviewWeek() } returns reviewWith(sleepRecent = 5.2, sleepBaseline = 6.3)

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(
            "Le bilan est la raison d'être de l'écran ; le nombre de jours couverts est une " +
                "mention de bas de page. La seconde ne doit pas emporter le premier.",
            state.review != null,
        )
        assertNull(state.coveredDays)
    }

    @Test
    fun `rafraichir relance le calcul`() = runTest {
        var calls = 0
        coEvery { reviewWeek() } answers { calls += 1; reviewWith() }

        val viewModel = WeekViewModel(reviewWeek, repository)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, calls)
    }

    // ------------------------------------------------------------------ outils

    private fun reviewWith(sleepRecent: Double? = null, sleepBaseline: Double? = null) = WeeklyReview(
        generatedAt = Instant.parse("2026-09-10T06:00:00Z"),
        from = today.minusDays(6),
        to = today,
        metrics = DriftMetric.entries.map { metric ->
            MetricReview(
                metric = metric,
                recentValue = if (metric == DriftMetric.SLEEP_DURATION) sleepRecent else null,
                baselineValue = if (metric == DriftMetric.SLEEP_DURATION) sleepBaseline else null,
                recentDays = 0,
                baselineDays = 0,
                lastMeasuredOn = null,
                week = emptyList(),
                drift = null,
            )
        },
    )
}
