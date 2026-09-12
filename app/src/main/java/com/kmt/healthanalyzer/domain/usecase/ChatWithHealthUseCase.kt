package com.kmt.healthanalyzer.domain.usecase

import android.content.Context
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import com.kmt.healthanalyzer.data.llm.LlmClientFactory
import com.kmt.healthanalyzer.data.llm.LlmError
import com.kmt.healthanalyzer.data.llm.LlmMessage
import com.kmt.healthanalyzer.data.llm.LlmRequest
import com.kmt.healthanalyzer.data.llm.LlmRole
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.data.settings.ApiKeyStore
import com.kmt.healthanalyzer.domain.analysis.HealthPromptBuilder
import com.kmt.healthanalyzer.domain.report.ReportModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Un tour de la conversation, dans l'ordre chronologique. */
data class ChatTurn(val role: ChatRole, val content: String)

/**
 * Le résultat d'un tour de conversation.
 *
 * [Success.context] est la nouvelle valeur à garder pour le tour suivant : elle porte la
 * fenêtre que le modèle a choisi d'examiner, si `healthrange` a été utilisé pendant ce
 * tour. [Success.statusNote] est la ligne d'état facultative à montrer à l'écran — voir
 * [invoke].
 */
sealed interface ChatResult {
    data class Success(val reply: String, val context: ChatContext, val statusNote: String? = null) : ChatResult

    /**
     * L'échec porte sa **cause**, pas sa phrase.
     *
     * Le cas d'usage sait ce qui s'est passé ; il ne sait pas ce que l'écran doit
     * écrire, ni quel bouton l'accompagne. C'est `StateCopy.forFailure` qui tranche, en
     * un seul endroit, pour toute l'app — sans quoi chaque chemin d'erreur finit par
     * rédiger sa propre excuse.
     */
    data class Failure(val cause: Throwable) : ChatResult
}

/**
 * Le contexte de la conversation.
 *
 * [historyReport] couvre **tout** l'historique importé, jamais une fenêtre restreinte. Il
 * sert à décrire au modèle l'étendue réelle des données ([describeHistoryOverview]), et
 * c'est aussi **le seul** rapport que la WebView reçoit ([historyReportJson]) : un
 * graphique de la conversation se résout toujours contre lui, filtré par la fenêtre gravée
 * sur son propre message (`ChatMessageEntity.rangeFrom`/`rangeTo`), jamais contre la
 * fenêtre active du moment — voir `chat-view.js` (`messageRange`). Sans quoi le graphique
 * d'une ancienne réponse changerait de contenu après coup, dès que la conversation change
 * de période.
 *
 * [activeReport] est la fenêtre que le modèle examine actuellement, pour construire les
 * prochains prompts : `null` tant qu'il n'a demandé aucune `healthrange`, sinon le rapport
 * reconstruit sur la période acceptée. Elle est gardée d'un tour à l'autre par l'appelant
 * (voir [AnalysisViewModel][com.kmt.healthanalyzer.ui.analysis.AnalysisViewModel]) pour
 * qu'une question de suivi n'ait pas besoin de redemander la même fenêtre — mais elle ne
 * quitte jamais Kotlin : `activeReport.meta.from`/`.to` sert seulement à graver la fenêtre
 * sur le message écrit pendant ce tour.
 *
 * [historyReportJson] est la sérialisation de [historyReport], calculée une seule fois,
 * pour la WebView — voir `chart-catalog.js`. Ni elle ni [activeReport] ne quittent jamais
 * l'appareil ; seuls des agrégats en sortent, par [HealthPromptBuilder].
 */
data class ChatContext(
    val historyReport: ReportModel,
    val historyReportJson: String,
    val activeReport: ReportModel? = null,
)

/**
 * Fait répondre le modèle de langage choisi par l'utilisateur dans la conversation de
 * l'onglet Analyse.
 *
 * Le modèle choisit lui-même la période qu'il examine — voir CLAUDE.md, section sur la
 * conversation. Le prompt système ne porte plus de fenêtre imposée : il porte un aperçu de
 * tout l'historique, à la résolution du mois ([describeHistoryOverview]), et les agrégats
 * de la fenêtre actuellement active, s'il y en a une ([ChatContext.activeReport]). Pour
 * changer de fenêtre ou en choisir une la première fois, le modèle écrit un bloc de code
 * `healthrange` (voir `report/chat-prompt.txt`) ; [invoke] le lit, reconstruit le rapport
 * sur la période demandée et redemande une réponse au modèle, jusqu'à
 * [MAX_RANGE_REQUESTS] fois par tour. Cet aller-retour est invisible dans la conversation
 * persistée : les tours intermédiaires (la demande de fenêtre, la confirmation des
 * données) n'existent que le temps de cet appel, jamais écrits en base ni affichés — seuls
 * la question de l'utilisateur et la réponse finale le sont.
 *
 * [loadContext] construit le [ChatContext] de départ (tout l'historique, aucune fenêtre
 * active) une seule fois par session ; l'appelant garde ensuite le [ChatContext] rendu par
 * chaque [ChatResult.Success] et le repasse au tour suivant, pour que la fenêtre choisie
 * reste active tant que le modèle n'en redemande pas une autre.
 *
 * Le texte renvoyé n'est pas interprété : le découpage entre prose et blocs `healthchart`
 * se fait dans la vue JavaScript, jamais ici.
 */
class ChatWithHealthUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: HealthRepository,
    private val preferences: AppPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val clientFactory: LlmClientFactory,
    private val promptBuilder: HealthPromptBuilder,
    private val zone: ZoneId,
) {
    /** Construit le contexte de départ : le rapport sur tout l'historique, sérialisé une fois. */
    suspend fun loadContext(): ChatContext = withContext(Dispatchers.IO) {
        val today = LocalDate.now(zone)
        val from = repository.firstRecordedDay() ?: today
        val report = repository.buildReport(from..today, zone)
        ChatContext(historyReport = report, historyReportJson = REPORT_JSON.encodeToString(report))
    }

    suspend operator fun invoke(history: List<ChatTurn>, context: ChatContext): ChatResult = withContext(Dispatchers.IO) {
        val settings = preferences.settings.first()
        val apiKey = apiKeyStore.key(settings.provider)
            ?: return@withContext ChatResult.Failure(LlmError.MissingApiKey())

        val overview = historyOverviewOf(context.historyReport)
        val overviewText = describeHistoryOverview(overview)

        var activeReport = context.activeReport
        var statusNote: String? = null
        var workingHistory = history.takeLast(MAX_HISTORY_MESSAGES)

        fun currentContext() = context.copy(activeReport = activeReport)

        repeat(MAX_LLM_CALLS) { callIndex ->
            val isLastAllowedCall = callIndex == MAX_LLM_CALLS - 1

            val systemPrompt = readChatPromptTemplate()
                .replace("{{SERIES}}", describeAvailableSeries(activeReport ?: context.historyReport))
                .replace("{{OVERVIEW}}", overviewText)
                .replace("{{AGGREGATES}}", aggregatesSection(activeReport))

            val messages = workingHistory.map { LlmMessage(role = it.role.toLlmRole(), content = it.content) }
            val request = LlmRequest(systemPrompt = systemPrompt, messages = messages, model = settings.model)

            val response = try {
                clientFactory.clientFor(settings.provider).complete(request, apiKey)
            } catch (failure: LlmError) {
                return@withContext ChatResult.Failure(failure)
            }

            if (isLastAllowedCall) {
                return@withContext ChatResult.Success(response.text, currentContext(), statusNote)
            }

            when (val outcome = HealthRangeRequest.parse(response.text, overview)) {
                is HealthRangeOutcome.NotFound ->
                    return@withContext ChatResult.Success(response.text, currentContext(), statusNote)

                is HealthRangeOutcome.Rejected -> {
                    workingHistory = workingHistory +
                        ChatTurn(ChatRole.ASSISTANT, response.text) +
                        ChatTurn(ChatRole.USER, rejectionNudge(outcome.reason))
                }

                is HealthRangeOutcome.Accepted -> {
                    val rebuilt = repository.buildReport(outcome.from..outcome.to, zone)
                    activeReport = rebuilt
                    statusNote = examiningNote(outcome.from, outcome.to)
                    workingHistory = workingHistory +
                        ChatTurn(ChatRole.ASSISTANT, response.text) +
                        ChatTurn(ChatRole.USER, dataNudge(outcome))
                }
            }
        }

        // Inatteignable : la dernière itération de la boucle rend toujours une réponse.
        error("La boucle de conversation s'est arrêtée sans réponse.")
    }

    private fun aggregatesSection(activeReport: ReportModel?): String =
        activeReport?.let { promptBuilder.narrativeUserPrompt(it) } ?: NO_WINDOW_CHOSEN_TEXT

    private fun rejectionNudge(reason: String): String =
        "La fenêtre demandée est refusée : $reason " +
            "Corrige-la si tu peux, ou réponds avec ce que tu sais déjà si tu préfères."

    private fun dataNudge(outcome: HealthRangeOutcome.Accepted): String {
        val correction = outcome.note?.let { " ($it)" }.orEmpty()
        return "Les données pour la période ${outcome.from} à ${outcome.to} sont maintenant disponibles$correction. " +
            "Réponds à la question précédente avec ces chiffres."
    }

    private fun examiningNote(from: LocalDate, to: LocalDate): String =
        "Le modèle examine ${from.format(STATUS_DATE)} à ${to.format(STATUS_DATE)}."

    private fun readChatPromptTemplate(): String =
        context.assets.open(CHAT_PROMPT_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun ChatRole.toLlmRole(): LlmRole = when (this) {
        ChatRole.USER -> LlmRole.USER
        ChatRole.ASSISTANT -> LlmRole.ASSISTANT
    }

    private companion object {
        const val CHAT_PROMPT_ASSET_PATH = "report/chat-prompt.txt"

        /** Assez pour garder le fil d'une conversation, sans laisser le coût grossir sans borne. */
        const val MAX_HISTORY_MESSAGES = 20

        /**
         * Au plus deux demandes de fenêtre par tour, plus l'appel qui les suit : trois appels
         * au modèle au maximum. Un modèle qui redemanderait une troisième fenêtre n'est plus
         * écouté — sa réponse de ce dernier appel est rendue telle quelle.
         */
        const val MAX_RANGE_REQUESTS = 2
        const val MAX_LLM_CALLS = MAX_RANGE_REQUESTS + 1

        const val NO_WINDOW_CHOSEN_TEXT =
            "Aucune période n'est encore choisie. Demande une fenêtre avec `healthrange` avant de répondre avec des chiffres."

        val STATUS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

        val REPORT_JSON = Json { encodeDefaults = true }
    }
}
