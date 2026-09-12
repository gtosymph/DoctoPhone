package com.kmt.healthanalyzer.ui.report

import app.cash.turbine.test
import com.kmt.healthanalyzer.data.report.ReportExporter
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodySection
import com.kmt.healthanalyzer.domain.report.BreathingSection
import com.kmt.healthanalyzer.domain.report.HeartSection
import com.kmt.healthanalyzer.domain.report.ReportMeta
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.ReportNarrative
import com.kmt.healthanalyzer.domain.report.SleepSection
import com.kmt.healthanalyzer.domain.report.StressSection
import com.kmt.healthanalyzer.domain.usecase.AnalyzeReportNarrativeUseCase
import com.kmt.healthanalyzer.domain.usecase.NarrativeResult
import com.kmt.healthanalyzer.domain.usecase.ReviewWeekUseCase
import com.kmt.healthanalyzer.ui.home.TimeRange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val repository: HealthRepository = mockk()
    private val exporter: ReportExporter = mockk()
    private val analyzeNarrative: AnalyzeReportNarrativeUseCase = mockk()
    private val reviewWeek: ReviewWeekUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dateRange couvre les jours de TimeRange en se terminant aujourd'hui`() {
        val today = LocalDate.of(2026, 9, 8)

        val range = ReportViewModel.dateRange(TimeRange.WEEK, today)

        assertEquals(LocalDate.of(2026, 9, 2), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `refresh charge le rapport et le sérialise en JSON`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertNull(state.errorMessage)
        requireNotNull(state.reportJson)
        assertTrue(state.reportJson!!.contains("\"days\":7"))
    }

    @Test
    fun `selectRange relance la construction avec la nouvelle période`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectRange(TimeRange.QUARTER)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TimeRange.QUARTER, viewModel.state.value.range)
        coVerify(exactly = 2) { repository.buildReport(any(), any()) }
    }

    @Test
    fun `une exception du dépôt se traduit en message d'erreur`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } throws IllegalStateException("base illisible")

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertNull(state.reportJson)
        requireNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("base illisible"))
    }

    @Test
    fun `export écrit le fichier et émet son intention de partage`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()
        val file = File("bilan-sante-2026-09-08.html")
        val intent: android.content.Intent = mockk()
        coEvery { exporter.export(any(), any()) } returns file
        every { exporter.shareIntent(file) } returns intent

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.shareEvents.test {
            viewModel.export()
            dispatcher.scheduler.advanceUntilIdle()

            assertSame(intent, awaitItem())
        }
        assertTrue(!viewModel.state.value.isExporting)
        assertNull(viewModel.state.value.exportError)
    }

    @Test
    fun `un export en échec se traduit en message d'erreur, sans partage`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()
        coEvery { exporter.export(any(), any()) } throws IllegalStateException("disque plein")

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.export()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isExporting)
        requireNotNull(state.exportError)
        assertTrue(state.exportError!!.contains("disque plein"))
    }

    @Test
    fun `writeNarrative pousse le récit reçu dans le JSON du rapport`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()
        val narrative = ReportNarrative(headline = "En forme", verdict = "Continuez ainsi.")
        coEvery { analyzeNarrative(any()) } returns NarrativeResult.Success(narrative)

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.writeNarrative()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isWritingNarrative)
        assertNull(state.narrativeError)
        assertEquals(TimeRange.MONTH, state.narrativeRange)
        assertTrue(state.reportJson!!.contains("\"headline\":\"En forme\""))
    }

    @Test
    fun `un échec du récit se traduit en message d'erreur lisible, sans toucher au JSON`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()
        coEvery { analyzeNarrative(any()) } returns
            NarrativeResult.Failure("Aucune clé API n'est enregistrée pour OpenAI.")

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val jsonBeforeNarrative = viewModel.state.value.reportJson

        viewModel.writeNarrative()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isWritingNarrative)
        assertEquals("Aucune clé API n'est enregistrée pour OpenAI.", state.narrativeError)
        assertEquals(jsonBeforeNarrative, state.reportJson)
        assertNull(state.narrativeRange)
    }

    @Test
    fun `un changement de période garde le récit mais le signale comme obsolète`() = runTest(dispatcher) {
        coEvery { repository.buildReport(any(), any()) } returns minimalReport()
        val narrative = ReportNarrative(headline = "En forme", verdict = "Continuez ainsi.")
        coEvery { analyzeNarrative(any()) } returns NarrativeResult.Success(narrative)

        val viewModel = newViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.writeNarrative()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectRange(TimeRange.QUARTER)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(TimeRange.QUARTER, state.range)
        assertEquals(TimeRange.MONTH, state.narrativeRange)
        assertTrue("le rapport montré ne doit plus porter l'ancien récit", !state.reportJson!!.contains("En forme"))
    }

    private fun newViewModel() =
        ReportViewModel(repository, zone, exporter, analyzeNarrative, reviewWeek, dispatcher)

    private fun minimalReport(): ReportModel = ReportModel(
        meta = ReportMeta(
            generatedAt = "2026-09-08T00:00:00Z",
            from = "2026-09-02",
            to = "2026-09-08",
            days = 7,
            nights = 7,
            heartRateSamples = 0,
            hrvWindows = 0,
            activeDays = 0,
            timeZone = "Europe/Paris",
            periodLabel = "7 jours",
        ),
        tiles = emptyList(),
        sleep = SleepSection(),
        heart = HeartSection(),
        activity = ActivitySection(),
        body = BodySection(),
        stress = StressSection(),
        breathing = BreathingSection(),
        correlations = emptyList(),
    )
}
