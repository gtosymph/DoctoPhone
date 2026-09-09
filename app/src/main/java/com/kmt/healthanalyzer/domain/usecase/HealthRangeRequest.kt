package com.kmt.healthanalyzer.domain.usecase

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Ce que le modèle demande en écrivant un bloc `healthrange` — voir `report/chat-prompt.txt`.
 * Exact symétrique de `healthchart`, décrit dans CLAUDE.md.
 */
sealed interface HealthRangeOutcome {
    /** Aucun bloc `healthrange` dans la réponse : rien à faire. */
    data object NotFound : HealthRangeOutcome

    /** Une fenêtre exploitable, corrigée au besoin — [note] le dit au modèle, sinon `null`. */
    data class Accepted(val from: LocalDate, val to: LocalDate, val note: String?) : HealthRangeOutcome

    /** Un bloc présent mais irrécupérable, avec une raison que le modèle peut lire et corriger. */
    data class Rejected(val reason: String) : HealthRangeOutcome
}

/**
 * Lit et valide le bloc `healthrange` d'une réponse du modèle.
 *
 * Même esprit indulgent que `chart-spec.js` (voir CLAUDE.md) : corrige ce qui se corrige
 * (bornes inversées, fenêtre partiellement hors historique) et ne refuse que
 * l'irrécupérable, avec un message assez clair pour que le modèle se corrige lui-même au
 * prochain tour plutôt que de planter en silence.
 */
object HealthRangeRequest {

    private val BLOCK = Regex("```healthrange[ \\t]*\\r?\\n([\\s\\S]*?)```")
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    /** Tolère un format français glissé par erreur — un seul champ à la fois, pas d'ambiguïté de série. */
    private val FRENCH = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * @param reply le texte brut de la réponse du modèle
     * @param overview l'aperçu de tout l'historique — sert à borner et à détecter une
     *   fenêtre vide (aucun mois couvert ne porte de mesure)
     */
    fun parse(reply: String, overview: HistoryOverview): HealthRangeOutcome {
        val match = BLOCK.find(reply) ?: return HealthRangeOutcome.NotFound
        val raw = match.groupValues[1]

        val json = try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (failure: Exception) {
            return HealthRangeOutcome.Rejected("le bloc healthrange n'est pas un JSON valide : ${failure.message}")
        }

        val fromRaw = json["from"]?.stringOrNull()?.trim().orEmpty()
        val toRaw = json["to"]?.stringOrNull()?.trim().orEmpty()
        if (fromRaw.isBlank() || toRaw.isBlank()) {
            return HealthRangeOutcome.Rejected(
                "les champs \"from\" et \"to\" sont obligatoires, au format AAAA-MM-JJ.",
            )
        }

        val from = parseDate(fromRaw)
            ?: return HealthRangeOutcome.Rejected("la date \"$fromRaw\" est illisible ; utilise le format AAAA-MM-JJ.")
        val to = parseDate(toRaw)
            ?: return HealthRangeOutcome.Rejected("la date \"$toRaw\" est illisible ; utilise le format AAAA-MM-JJ.")

        val notes = mutableListOf<String>()
        var correctedFrom = from
        var correctedTo = to
        if (correctedFrom > correctedTo) {
            val swap = correctedFrom
            correctedFrom = correctedTo
            correctedTo = swap
            notes += "les bornes étaient inversées, elles ont été remises dans l'ordre"
        }

        val earliest = overview.earliest?.let(::parseDate)
        val latest = overview.latest?.let(::parseDate)
        if (earliest == null || latest == null) {
            return HealthRangeOutcome.Rejected("aucune donnée n'a encore été importée.")
        }
        if (correctedTo < earliest || correctedFrom > latest) {
            return HealthRangeOutcome.Rejected(
                "aucune mesure n'existe entre $fromRaw et $toRaw ; " +
                    "l'historique disponible va de ${overview.earliest} à ${overview.latest}.",
            )
        }
        if (correctedFrom < earliest || correctedTo > latest) {
            correctedFrom = maxOf(correctedFrom, earliest)
            correctedTo = minOf(correctedTo, latest)
            notes += "la fenêtre a été ramenée à l'historique disponible (${overview.earliest} à ${overview.latest})"
        }

        val touchesData = overview.months.any { monthOverlaps(it.month, correctedFrom, correctedTo) }
        if (!touchesData) {
            return HealthRangeOutcome.Rejected("aucune mesure n'existe entre $fromRaw et $toRaw.")
        }

        return HealthRangeOutcome.Accepted(correctedFrom, correctedTo, notes.joinToString(", ").ifBlank { null })
    }

    private fun parseDate(raw: String): LocalDate? =
        try {
            LocalDate.parse(raw, ISO)
        } catch (failure: DateTimeParseException) {
            try {
                LocalDate.parse(raw, FRENCH)
            } catch (secondFailure: DateTimeParseException) {
                null
            }
        }

    private fun monthOverlaps(month: String, from: LocalDate, to: LocalDate): Boolean =
        try {
            val yearMonth = YearMonth.parse(month)
            yearMonth.atDay(1) <= to && yearMonth.atEndOfMonth() >= from
        } catch (failure: DateTimeException) {
            false
        }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.let {
        if (it.isString) it.content else null
    }
}
