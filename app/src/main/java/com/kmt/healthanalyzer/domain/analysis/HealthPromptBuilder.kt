package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.ReportModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Met une durée en minutes sous la forme `5 h 09`. */
fun formatMinutes(minutes: Int): String = "%d h %02d".format(minutes / 60, minutes % 60)

/**
 * Prépare les messages envoyés au modèle de langage.
 *
 * Deux règles gouvernent ce fichier.
 *
 * 1. **Confidentialité.** Seuls des agrégats quittent l'appareil : des moyennes par
 *    semaine et des tendances. Aucun identifiant Samsung, aucun horodatage précis,
 *    aucune mesure individuelle ne sort. Un tiers qui lirait le message ne pourrait
 *    pas reconstituer l'emploi du temps de l'utilisateur.
 * 2. **Coût.** Deux ans de mesures brutes représentent des centaines de milliers de
 *    jetons. Le résumé hebdomadaire tient dans quelques milliers, pour une analyse de
 *    qualité équivalente : un modèle raisonne sur des tendances, pas sur des points.
 */
class HealthPromptBuilder {

    fun systemPrompt(): String = SYSTEM_PROMPT

    /**
     * Message utilisateur du récit écrit par le LLM (voir `report/narrative-prompt.txt`
     * pour le message système associé, chargé ailleurs — cette classe ne le connaît pas).
     *
     * [ReportModel] porte des séries quotidiennes ; il ne doit jamais partir tel quel.
     * [com.kmt.healthanalyzer.domain.analysis.narrativeInputOf] n'en recopie que les
     * champs agrégés (voir [NarrativeInput]) avant sérialisation : la frontière de
     * confidentialité tient à la forme des types, pas à un filtrage au moment de l'écriture.
     */
    fun narrativeUserPrompt(report: ReportModel): String = NARRATIVE_JSON.encodeToString(narrativeInputOf(report))

    fun userPrompt(snapshot: HealthSnapshot, question: String?): String = buildString {
        appendLine("# Données de santé")
        appendLine()
        appendLine("Période observée : ${snapshot.from.format(DATE)} à ${snapshot.to.format(DATE)}.")
        appendLine("Jours porteurs de mesures : ${snapshot.daysWithData} sur ${snapshot.days.size}.")
        appendLine()

        appendTrends(snapshot)
        appendSleepRegularity(snapshot)
        appendWeeklyTable(snapshot)

        if (!question.isNullOrBlank()) {
            appendLine()
            appendLine("# Question de l'utilisateur")
            appendLine(question.trim())
        }
    }

    private fun StringBuilder.appendTrends(snapshot: HealthSnapshot) {
        if (snapshot.trends.isEmpty()) return
        appendLine("## Tendances récentes")
        appendLine("Comparaison des deux dernières fenêtres de même longueur.")
        appendLine()
        snapshot.trends.forEach { trend ->
            val change = trend.changePercent?.let { " (%+.0f %%, %s)".format(it, wording(trend)) } ?: ""
            appendLine(
                "- ${trend.metric.label} : %.1f %s%s"
                    .format(Locale.FRANCE, trend.recentAverage, trend.metric.unit, change)
            )
        }
        appendLine()
    }

    private fun wording(trend: MetricTrend): String = when (trend.direction) {
        TrendDirection.UP -> "en hausse"
        TrendDirection.DOWN -> "en baisse"
        TrendDirection.STABLE -> "stable"
    }

    private fun StringBuilder.appendSleepRegularity(snapshot: HealthSnapshot) {
        val sleep = snapshot.sleepRegularity ?: return
        appendLine("## Sommeil")
        appendLine("- Nuits mesurées : ${sleep.nightsMeasured}")
        appendLine("- Durée moyenne : ${formatMinutes(sleep.averageDurationMinutes)}")
        appendLine("- Objectif : ${formatMinutes(sleep.targetMinutes)}")
        sleep.averageBedtime?.let { appendLine("- Heure de coucher moyenne : ${it.format(TIME)}") }
        appendLine(
            "- Régularité du coucher : écart-type de %.1f h".format(Locale.FRANCE, sleep.bedtimeSpreadHours)
        )
        appendLine("- Dette de sommeil cumulée : ${formatMinutes(sleep.sleepDebtMinutes)}")
        appendLine()
    }

    /**
     * Résume la période en une ligne par semaine.
     *
     * Une ligne par jour ferait exploser le coût sans rien apprendre au modèle : les
     * mesures de santé bougent lentement et le bruit journalier masque la tendance.
     */
    private fun StringBuilder.appendWeeklyTable(snapshot: HealthSnapshot) {
        val weeks = snapshot.days
            .filter { it.hasData }
            .groupBy { it.date.minusDays((it.date.dayOfWeek.value - 1).toLong()) }
            .toList()
            .sortedBy { (weekStart, _) -> weekStart }
            .takeLast(MAX_WEEKS)

        if (weeks.isEmpty()) return

        appendLine("## Moyennes par semaine")
        appendLine()
        appendLine("| Semaine du | Pas | Sommeil | Score sommeil | FC repos | HRV | Stress | Poids | Énergie |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        weeks.forEach { (weekStart, days) ->
            appendLine(
                listOf(
                    weekStart.format(DATE),
                    days.averageOf { it.steps?.toDouble() }?.roundToInt()?.toString() ?: "-",
                    days.averageOf { it.sleepMinutes?.toDouble() }?.let { formatMinutes(it.roundToInt()) } ?: "-",
                    days.averageOf { it.sleepScore?.toDouble() }?.roundToInt()?.toString() ?: "-",
                    days.averageOf { it.restingHeartRate?.toDouble() }?.roundToInt()?.toString() ?: "-",
                    days.averageOf { it.hrvRmssd?.toDouble() }?.roundToInt()?.toString() ?: "-",
                    days.averageOf { it.averageStress?.toDouble() }?.roundToInt()?.toString() ?: "-",
                    days.averageOf { it.weightKg?.toDouble() }?.let { "%.1f".format(Locale.FRANCE, it) } ?: "-",
                    days.averageOf { it.energyScore?.toDouble() }?.roundToInt()?.toString() ?: "-",
                ).joinToString(" | ", prefix = "| ", postfix = " |")
            )
        }
    }

    private fun List<DailySnapshot>.averageOf(select: (DailySnapshot) -> Double?): Double? {
        val values = mapNotNull(select)
        return if (values.isEmpty()) null else values.average()
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Deux ans de données tiennent en 104 semaines ; la table reste petite. */
        const val MAX_WEEKS = 104

        val NARRATIVE_JSON = Json { encodeDefaults = true }

        val SYSTEM_PROMPT = """
            Tu es un assistant d'analyse de données de santé personnelles.

            Ton rôle :
            - Lis les agrégats de mesures que l'utilisateur te donne.
            - Trouve les tendances réelles et les liens entre les mesures.
            - Nomme le point le plus important en premier, avec le chiffre qui le prouve.
            - Propose au plus trois actions concrètes et mesurables.

            Tes limites, à respecter strictement :
            - Tu n'es pas médecin. Tu ne poses aucun diagnostic et tu ne prescris rien.
            - Quand une mesure sort d'une plage habituelle, tu invites à consulter un
              médecin plutôt que d'interpréter.
            - Tu ne conclus jamais au-delà des données fournies. Si une donnée manque,
              tu le dis.
            - Tu distingues une corrélation d'une cause.

            Ta forme :
            - Réponds en français, en phrases courtes.
            - Structure la réponse en sections courtes avec des titres.
            - Cite toujours le chiffre qui appuie une affirmation.
        """.trimIndent()
    }
}

private fun LocalDate.format(formatter: DateTimeFormatter): String = formatter.format(this)
