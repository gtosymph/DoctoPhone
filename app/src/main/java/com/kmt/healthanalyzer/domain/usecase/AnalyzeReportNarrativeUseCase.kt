package com.kmt.healthanalyzer.domain.usecase

import android.content.Context
import com.kmt.healthanalyzer.data.llm.LlmClientFactory
import com.kmt.healthanalyzer.data.llm.LlmError
import com.kmt.healthanalyzer.data.llm.LlmRequest
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.settings.ApiKeyStore
import com.kmt.healthanalyzer.domain.analysis.HealthPromptBuilder
import com.kmt.healthanalyzer.domain.analysis.NarrativeParseResult
import com.kmt.healthanalyzer.domain.analysis.NarrativeResponseParser
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.ReportNarrative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Le résultat d'une écriture de récit : le texte prêt à poser dans le rapport, ou une raison lisible. */
sealed interface NarrativeResult {
    data class Success(val narrative: ReportNarrative) : NarrativeResult
    /** L'échec porte sa cause ; `StateCopy.forFailure` en tire la phrase et le geste. */
    data class Failure(val cause: Throwable) : NarrativeResult
}

/**
 * Fait écrire le récit du rapport par le modèle de langage choisi par l'utilisateur.
 *
 * Point d'entrée unique pour l'écran de rapport (`android-report-ui`) : il lui suffit
 * d'appeler ce cas d'usage avec le [ReportModel] déjà calculé — aucun prompt ne se
 * construit ailleurs.
 *
 * Le prompt système vient de `assets/report/narrative-prompt.txt`, chargé ici et nulle
 * part ailleurs : il n'est ni réécrit ni dupliqué en Kotlin. Le prompt utilisateur ne
 * porte que les agrégats du rapport, construits par
 * [HealthPromptBuilder.narrativeUserPrompt] — le [ReportModel] complet, avec ses séries
 * quotidiennes, ne quitte jamais l'appareil.
 */
class AnalyzeReportNarrativeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val clientFactory: LlmClientFactory,
    private val promptBuilder: HealthPromptBuilder,
) {
    suspend operator fun invoke(report: ReportModel): NarrativeResult = withContext(Dispatchers.IO) {
        val settings = preferences.settings.first()
        val apiKey = apiKeyStore.key(settings.provider)
            ?: return@withContext NarrativeResult.Failure(LlmError.MissingApiKey())

        val request = LlmRequest(
            systemPrompt = readNarrativeSystemPrompt(),
            userPrompt = promptBuilder.narrativeUserPrompt(report),
            model = settings.model,
        )

        val response = try {
            clientFactory.clientFor(settings.provider).complete(request, apiKey)
        } catch (failure: LlmError) {
            return@withContext NarrativeResult.Failure(failure)
        }

        when (val parsed = NarrativeResponseParser.parse(response.text)) {
            is NarrativeParseResult.Success -> NarrativeResult.Success(parsed.narrative)
            // La cause de lecture est enveloppée plutôt que montrée : « le bilan n'a pas
            // le bon nombre de sections » n'apprend rien au lecteur, alors que
            // `LlmError.Malformed` lui propose de réessayer ou de changer de modèle. Le
            // détail reste dans l'exception, donc dans le journal.
            is NarrativeParseResult.Failure ->
                NarrativeResult.Failure(LlmError.Malformed(IllegalStateException(parsed.reason)))
        }
    }

    private fun readNarrativeSystemPrompt(): String =
        context.assets.open(NARRATIVE_PROMPT_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val NARRATIVE_PROMPT_ASSET_PATH = "report/narrative-prompt.txt"
    }
}
