package com.kmt.healthanalyzer.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import com.kmt.healthanalyzer.data.llm.LlmClient
import com.kmt.healthanalyzer.data.llm.LlmClientFactory
import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.data.llm.LlmRequest
import com.kmt.healthanalyzer.data.llm.LlmResponse
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.preferences.AppSettings
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.data.settings.ApiKeyStore
import com.kmt.healthanalyzer.domain.analysis.HealthPromptBuilder
import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodySection
import com.kmt.healthanalyzer.domain.report.BreathingSection
import com.kmt.healthanalyzer.domain.report.DayValue
import com.kmt.healthanalyzer.domain.report.HeartSection
import com.kmt.healthanalyzer.domain.report.ReportMeta
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.SleepSection
import com.kmt.healthanalyzer.domain.report.StressSection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le mécanisme `healthrange` : le modèle choisit sa fenêtre, l'application reconstruit le
 * rapport et redemande une réponse, jusqu'à deux fois par tour — voir CLAUDE.md et
 * `report/chat-prompt.txt`. Ces tests couvrent la boucle elle-même ([ChatWithHealthUseCase.invoke]),
 * pas la validation du bloc (voir [HealthRangeRequestTest]) ni l'aperçu mensuel (voir
 * [HistoryOverviewTest]).
 */
class ChatWithHealthUseCaseTest {

    private val context: Context = mockk()
    private val repository: HealthRepository = mockk()
    private val preferences: AppPreferences = mockk()
    private val apiKeyStore: ApiKeyStore = mockk()
    private val clientFactory: LlmClientFactory = mockk()
    private val client: LlmClient = mockk()
    private val zone = ZoneOffset.UTC

    private val historyReport = broadHistoryReport()
    private val requests = mutableListOf<LlmRequest>()

    private lateinit var useCase: ChatWithHealthUseCase

    @Before
    fun setUp() {
        val assets: AssetManager = mockk()
        every { context.assets } returns assets
        every { assets.open("report/chat-prompt.txt") } answers {
            ByteArrayInputStream(TEMPLATE.toByteArray(Charsets.UTF_8))
        }

        every { preferences.settings } returns flowOf(
            AppSettings(provider = LlmProvider.ANTHROPIC, model = "claude-sonnet-5", sleepTargetMinutes = 480),
        )
        coEvery { apiKeyStore.key(LlmProvider.ANTHROPIC) } returns "sk-test"
        every { clientFactory.clientFor(LlmProvider.ANTHROPIC) } returns client
        requests.clear()

        useCase = ChatWithHealthUseCase(
            context = context,
            repository = repository,
            preferences = preferences,
            apiKeyStore = apiKeyStore,
            clientFactory = clientFactory,
            promptBuilder = HealthPromptBuilder(),
            zone = zone,
        )
    }

    @Test
    fun `sans bloc healthrange, la reponse part telle quelle et aucune fenetre n'est choisie`() = runTest {
        stubReplies("Votre sommeil s'améliore.")

        val result = useCase(userTurn("Comment je dors ?"), ChatContext(historyReport, "{}")) as ChatResult.Success

        assertEquals("Votre sommeil s'améliore.", result.reply)
        assertNull(result.context.activeReport)
        assertNull(result.statusNote)
        assertTrue(
            "sans fenêtre choisie, le prompt ne doit porter aucun chiffre détaillé",
            requests.single().systemPrompt.contains("Aucune période n'est encore choisie"),
        )
        coVerify(exactly = 0) { repository.buildReport(any(), any()) }
    }

    @Test
    fun `une fenetre valide est accordee, le rapport reconstruit et la ligne d'etat renseignee`() = runTest {
        val rebuilt = broadHistoryReport()
        coEvery {
            repository.buildReport(LocalDate.of(2025, 3, 1)..LocalDate.of(2025, 3, 31), zone)
        } returns rebuilt

        stubReplies(
            "Je vais regarder. " + rangeBlock("2025-03-01", "2025-03-31"),
            "En mars, vous avez dormi 6h30 en moyenne.",
        )

        val result = useCase(userTurn("Et en mars ?"), ChatContext(historyReport, "{}")) as ChatResult.Success

        assertEquals("En mars, vous avez dormi 6h30 en moyenne.", result.reply)
        assertEquals(rebuilt, result.context.activeReport)
        assertTrue(result.statusNote!!.contains("mars"))
        // Le second appel doit porter les vrais agrégats, plus le gabarit vide du premier.
        assertEquals(2, requests.size)
        assertTrue(requests[0].systemPrompt.contains("Aucune période n'est encore choisie"))
        assertTrue(!requests[1].systemPrompt.contains("Aucune période n'est encore choisie"))
        coVerify(exactly = 1) { repository.buildReport(any(), any()) }
    }

    @Test
    fun `une fenetre refusee redonne une chance au modele, dans la limite du tour`() = runTest {
        stubReplies(
            "```healthrange\n{ \"from\": \"1990-01-01\", \"to\": \"1990-01-31\" }\n```", // hors historique
            "Sans cette période, voici ce que je peux dire : rien de particulier.",
        )

        val result = useCase(userTurn("Et en 1990 ?"), ChatContext(historyReport, "{}")) as ChatResult.Success

        assertEquals("Sans cette période, voici ce que je peux dire : rien de particulier.", result.reply)
        assertNull(result.context.activeReport)
        coVerify(exactly = 0) { repository.buildReport(any(), any()) }
    }

    @Test
    fun `au dela de deux demandes de fenetre, l'application repond avec ce qu'elle a`() = runTest {
        coEvery { repository.buildReport(any(), zone) } returns broadHistoryReport()

        stubReplies(
            rangeBlock("2025-01-01", "2025-01-31"),
            rangeBlock("2025-02-01", "2025-02-28"),
            rangeBlock("2025-03-01", "2025-03-31"), // une troisième demande : plus écoutée
        )

        val result = useCase(userTurn("Compare janvier, février, mars"), ChatContext(historyReport, "{}"))
            as ChatResult.Success

        // La réponse du dernier appel autorisé part telle quelle, healthrange non résolu :
        // c'est à la vue (chat-view.js) de le filtrer avant affichage, en filet de sécurité.
        assertEquals(rangeBlock("2025-03-01", "2025-03-31"), result.reply)
        assertEquals(3, requests.size)
        coVerify(exactly = 2) { repository.buildReport(any(), zone) }
    }

    private fun stubReplies(vararg texts: String) {
        var index = 0
        coEvery { client.complete(any(), any()) } coAnswers {
            requests += firstArg<LlmRequest>()
            val text = texts[minOf(index, texts.lastIndex)]
            index += 1
            LlmResponse(text = text, inputTokens = null, outputTokens = null, model = "claude-sonnet-5")
        }
    }

    private fun userTurn(question: String) = listOf(ChatTurn(ChatRole.USER, question))

    private fun rangeBlock(from: String, to: String) = "```healthrange\n{ \"from\": \"$from\", \"to\": \"$to\" }\n```"

    /** Un rapport avec assez de mois couverts (2024 et 2025) pour que les fenêtres des tests soient acceptées. */
    private fun broadHistoryReport(): ReportModel {
        val days = (0 until 24).map { i ->
            val year = 2024 + i / 12
            val month = (i % 12) + 1
            DayValue(date = "%04d-%02d-15".format(year, month), value = 8000.0)
        }
        return ReportModel(
            meta = ReportMeta(
                generatedAt = "2026-09-08T00:00:00Z",
                from = "2024-01-01",
                to = "2025-12-31",
                days = 730,
                nights = 0,
                heartRateSamples = 0,
                hrvWindows = 0,
                activeDays = 24,
                timeZone = "UTC",
                periodLabel = "tout l'historique",
            ),
            tiles = emptyList(),
            sleep = SleepSection(),
            heart = HeartSection(),
            activity = ActivitySection(stepsDaily = days),
            body = BodySection(),
            stress = StressSection(),
            breathing = BreathingSection(),
            correlations = emptyList(),
        )
    }

    private companion object {
        const val TEMPLATE = "SERIES: {{SERIES}}\nOVERVIEW: {{OVERVIEW}}\nAGGREGATES: {{AGGREGATES}}"
    }
}
