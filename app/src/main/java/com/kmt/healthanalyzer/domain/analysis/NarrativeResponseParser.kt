package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.ReportNarrative
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Le résultat de la lecture de la réponse du LLM : soit le récit, soit une raison lisible. */
sealed interface NarrativeParseResult {
    data class Success(val narrative: ReportNarrative) : NarrativeParseResult
    data class Failure(val reason: String) : NarrativeParseResult
}

/**
 * Lit la réponse brute du LLM et en tire un [ReportNarrative].
 *
 * La consigne du prompt système demande un objet JSON seul, sans texte autour. Les
 * modèles ne la suivent pas toujours à la lettre : ils entourent parfois le JSON de
 * phrases d'introduction ou d'un bloc de code Markdown (` ```json `). Cette fonction
 * cherche donc le premier objet JSON équilibré dans le texte plutôt que d'exiger une
 * correspondance exacte, et rend un [NarrativeParseResult.Failure] lisible — jamais une
 * exception — quand rien d'exploitable n'est trouvé.
 */
object NarrativeResponseParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    fun parse(rawText: String): NarrativeParseResult {
        val jsonText = extractFirstJsonObject(rawText)
            ?: return NarrativeParseResult.Failure("Aucun objet JSON n'a été trouvé dans la réponse du modèle.")

        return try {
            NarrativeParseResult.Success(JSON.decodeFromString<ReportNarrative>(jsonText))
        } catch (failure: Exception) {
            NarrativeParseResult.Failure(
                "La réponse du modèle ne correspond pas au format attendu : ${failure.message ?: failure::class.simpleName}",
            )
        }
    }

    /**
     * Trouve le premier objet JSON équilibré (accolades appariées), en ignorant les
     * accolades qui apparaissent à l'intérieur d'une chaîne de caractères.
     */
    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
