package com.kmt.healthanalyzer.ui.analysis

import com.kmt.healthanalyzer.data.db.dao.ChatMessageDao
import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.preferences.AppSettings
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodySection
import com.kmt.healthanalyzer.domain.report.BreathingSection
import com.kmt.healthanalyzer.domain.report.HeartSection
import com.kmt.healthanalyzer.domain.report.ReportMeta
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.SleepSection
import com.kmt.healthanalyzer.domain.report.StressSection
import com.kmt.healthanalyzer.domain.usecase.ChatContext
import com.kmt.healthanalyzer.data.llm.LlmError
import com.kmt.healthanalyzer.domain.usecase.ChatResult
import com.kmt.healthanalyzer.ui.state.StateAction
import com.kmt.healthanalyzer.ui.state.StateCopy
import com.kmt.healthanalyzer.domain.usecase.ChatTurn
import com.kmt.healthanalyzer.domain.usecase.ChatWithHealthUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val chatMessageDao: ChatMessageDao = mockk()
    private val chatWithHealth: ChatWithHealthUseCase = mockk()
    private val repository: HealthRepository = mockk()
    private val preferences: AppPreferences = mockk()
    private val chatContext = ChatContext(historyReport = minimalReport(), historyReportJson = """{"meta":{}}""")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.settings } returns flowOf(
            AppSettings(provider = LlmProvider.ANTHROPIC, model = "claude-sonnet-5", sleepTargetMinutes = 480),
        )
        coEvery { chatMessageDao.all() } returns emptyList()
        coEvery { chatWithHealth.loadContext() } returns chatContext
        coEvery { repository.recordCount() } returns 100
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `au premier onScreenVisible, l'historique et le contexte du rapport se chargent`() = runTest(dispatcher) {
        val history = listOf(
            ChatMessageEntity(id = 1, role = ChatRole.USER, content = "Comment je dors ?", createdAtEpochMillis = 1),
        )
        coEvery { chatMessageDao.all() } returns history

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(history, state.messages)
        assertEquals(chatContext.historyReportJson, state.reportModelJson)
        assertTrue(!state.isLoadingContext)
        assertTrue(!state.isRefreshingContext)
        assertEquals("Claude (Anthropic)", state.providerName)
        coVerify(exactly = 1) { chatWithHealth.loadContext() }
    }

    @Test
    fun `un second onScreenVisible sans nouvel import ne recharge pas le contexte`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatWithHealth.loadContext() }
        assertTrue(!viewModel.state.value.isRefreshingContext)
    }

    @Test
    fun `un import detecte par un nouveau compte recharge le contexte silencieusement`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        val refreshedContext =
            ChatContext(historyReport = minimalReport(), historyReportJson = """{"meta":{"days":91}}""")
        coEvery { repository.recordCount() } returns 150
        coEvery { chatWithHealth.loadContext() } returns refreshedContext

        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { chatWithHealth.loadContext() }
        val state = viewModel.state.value
        assertEquals(refreshedContext.historyReportJson, state.reportModelJson)
        assertTrue(!state.isRefreshingContext)
        assertTrue(!state.isLoadingContext)
    }

    @Test
    fun `un echec du comptage se traduit en message d'erreur, sans rester bloque en chargement`() = runTest(dispatcher) {
        coEvery { repository.recordCount() } throws IllegalStateException("base illisible")

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isLoadingContext)
        requireNotNull(state.errorMessage)
        // La cause technique reste au journal ; l'écran, lui, propose un geste.
        assertFalse(state.errorMessage!!.contains("base illisible"))
        assertEquals(StateAction.RETRY, state.errorAction)
    }

    @Test
    fun `un echec du chargement du contexte se traduit en message d'erreur`() = runTest(dispatcher) {
        coEvery { chatWithHealth.loadContext() } throws IllegalStateException("rapport illisible")

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isLoadingContext)
        requireNotNull(state.errorMessage)
        assertFalse(state.errorMessage!!.contains("rapport illisible"))
        assertEquals(StateAction.RETRY, state.errorAction)
    }

    @Test
    fun `envoyer avant que le contexte ne soit charge affiche une erreur, sans rien ecrire`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        // Pas d'appel à onScreenVisible : le contexte n'est jamais chargé.

        viewModel.setDraft("Comment je dors ?")
        viewModel.send()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { chatMessageDao.insert(any()) }
        requireNotNull(viewModel.state.value.errorMessage)
        assertEquals("Comment je dors ?", viewModel.state.value.draft)
    }

    @Test
    fun `envoyer un brouillon vide ne fait rien`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.send()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { chatMessageDao.insert(any()) }
    }

    @Test
    fun `envoyer une question l'ecrit tout de suite, puis ajoute la reponse recue`() = runTest(dispatcher) {
        coEvery { chatMessageDao.insert(match { it.role == ChatRole.USER }) } returns 1L
        coEvery { chatMessageDao.insert(match { it.role == ChatRole.ASSISTANT }) } returns 2L
        coEvery { chatWithHealth(any(), any()) } returns
            ChatResult.Success("Votre sommeil s'améliore.", chatContext)

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDraft("Comment je dors ?")
        viewModel.send()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isSending)
        assertNull(state.errorMessage)
        assertEquals(2, state.messages.size)
        assertEquals(ChatRole.USER, state.messages[0].role)
        assertEquals("Comment je dors ?", state.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, state.messages[1].role)
        assertEquals("Votre sommeil s'améliore.", state.messages[1].content)
        assertEquals("", state.draft)
        assertNull("aucune fenêtre n'a été redemandée, pas de ligne d'état à montrer", state.statusMessage)

        coVerify {
            chatWithHealth(
                match { history: List<ChatTurn> -> history.size == 1 && history[0].role == ChatRole.USER },
                chatContext,
            )
        }
    }

    @Test
    fun `une reponse qui a choisi une fenetre grave cette fenetre sur le message et met a jour la ligne d'etat`() =
        runTest(dispatcher) {
            coEvery { chatMessageDao.insert(any()) } returns 1L
            val windowReport = minimalReport()
            val contextWithWindow = chatContext.copy(activeReport = windowReport)
            coEvery { chatWithHealth(any(), any()) } returns
                ChatResult.Success(
                    reply = "En janvier, votre sommeil était plus court.",
                    context = contextWithWindow,
                    statusNote = "Le modèle examine 1 janvier 2025 à 31 janvier 2025.",
                )

            val viewModel = newViewModel()
            viewModel.onScreenVisible()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setDraft("Et en janvier ?")
            viewModel.send()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.state.value
            // Le modèle affiché reste tout l'historique : un graphique se résout avec la
            // fenêtre gravée sur son propre message, jamais avec la fenêtre active du moment.
            assertEquals(chatContext.historyReportJson, state.reportModelJson)
            assertEquals("Le modèle examine 1 janvier 2025 à 31 janvier 2025.", state.statusMessage)
            val assistantMessage = state.messages.last()
            assertEquals(windowReport.meta.from, assistantMessage.rangeFrom)
            assertEquals(windowReport.meta.to, assistantMessage.rangeTo)

            // Le contexte mis à jour (avec sa fenêtre active) doit être repassé au tour suivant.
            viewModel.setDraft("Et en février ?")
            viewModel.send()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                chatWithHealth(
                    match { history: List<ChatTurn> -> history.size == 3 },
                    contextWithWindow,
                )
            }
        }

    @Test
    fun `une reponse sans fenetre choisie ne grave aucune fenetre sur le message`() = runTest(dispatcher) {
        coEvery { chatMessageDao.insert(any()) } returns 1L
        coEvery { chatWithHealth(any(), any()) } returns
            ChatResult.Success(reply = "Bonjour !", context = chatContext)

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDraft("Bonjour")
        viewModel.send()
        dispatcher.scheduler.advanceUntilIdle()

        val assistantMessage = viewModel.state.value.messages.last()
        assertNull(assistantMessage.rangeFrom)
        assertNull(assistantMessage.rangeTo)
    }

    @Test
    fun `un echec du modele garde la question deja envoyee et affiche le message d'erreur`() = runTest(dispatcher) {
        coEvery { chatMessageDao.insert(any()) } returns 1L
        coEvery { chatWithHealth(any(), any()) } returns ChatResult.Failure(LlmError.MissingApiKey())

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDraft("Comment je dors ?")
        viewModel.send()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(!state.isSending)
        // L'écran ne reprend pas le message de l'exception : il montre la phrase de
        // `StateCopy`, la seule qui dise aussi où vit la clé et quel bouton la mène là.
        assertEquals(StateCopy.MISSING_API_KEY, state.errorMessage)
        assertEquals(StateAction.OPEN_SETTINGS, state.errorAction)
        assertEquals(1, state.messages.size)
        assertEquals(ChatRole.USER, state.messages[0].role)
    }

    @Test
    fun `effacer la conversation vide Room et l'etat`() = runTest(dispatcher) {
        val history = listOf(
            ChatMessageEntity(id = 1, role = ChatRole.USER, content = "Bonjour", createdAtEpochMillis = 1),
        )
        coEvery { chatMessageDao.all() } returns history
        coEvery { chatMessageDao.deleteAll() } returns Unit

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.clearConversation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<ChatMessageEntity>(), viewModel.state.value.messages)
        coVerify { chatMessageDao.deleteAll() }
    }

    private fun newViewModel() = AnalysisViewModel(chatMessageDao, chatWithHealth, repository, preferences)

    private companion object {
        fun minimalReport(): ReportModel = ReportModel(
            meta = ReportMeta(
                generatedAt = "2026-09-08T00:00:00Z",
                from = "2026-06-10",
                to = "2026-09-08",
                days = 90,
                nights = 90,
                heartRateSamples = 0,
                hrvWindows = 0,
                activeDays = 0,
                timeZone = "Europe/Paris",
                periodLabel = "90 jours",
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

    @Test
    fun `annuler un envoi rend la main et garde la question posee`() = runTest(dispatcher) {
        coEvery { chatMessageDao.insert(any()) } returns 1L
        // Un appel qui ne rend jamais : c'est exactement la situation où l'on veut
        // pouvoir renoncer — un fournisseur lent, un réseau qui traîne.
        coEvery { chatWithHealth(any(), any()) } coAnswers { awaitCancellation() }

        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDraft("Comment je dors ?")
        viewModel.send()
        dispatcher.scheduler.runCurrent()
        assertTrue("l'envoi doit être en cours avant d'être annulé", viewModel.state.value.isSending)

        viewModel.cancelSend()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse("l'écran reste bloqué sur « Analyse en cours… »", state.isSending)
        assertNull("renoncer n'est pas un échec : aucun message d'erreur", state.errorMessage)
        // La question a bien eu lieu, elle est en base : l'effacer donnerait le sentiment
        // que l'app a perdu ce qu'on venait d'écrire.
        assertEquals(1, state.messages.size)
        assertEquals(ChatRole.USER, state.messages[0].role)
    }

    @Test
    fun `annuler sans envoi en cours ne fait rien`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onScreenVisible()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSending)
        assertNull(viewModel.state.value.errorMessage)
    }

}
