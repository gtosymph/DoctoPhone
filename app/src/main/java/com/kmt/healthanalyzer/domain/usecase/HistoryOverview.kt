package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.domain.report.ReportModel

/** Un mois de l'historique et son nombre de jours porteurs d'au moins une mesure. */
data class MonthCoverage(val month: String, val daysWithData: Int)

/**
 * Aperçu de tout l'historique importé, à la résolution du mois.
 *
 * [earliest] et [latest] sont les dates du premier et du dernier jour porteurs d'une
 * mesure, tous types confondus — pas la période interrogée pour construire [report], qui
 * peut déborder de part et d'autre des données réelles. [months] est trié
 * chronologiquement, un mois par entrée, sans trou : un mois sans aucune mesure
 * n'apparaît simplement pas.
 */
data class HistoryOverview(
    val earliest: String?,
    val latest: String?,
    val months: List<MonthCoverage>,
)

/**
 * Construit l'aperçu mensuel de [report], qui doit couvrir tout l'historique importé —
 * jamais une fenêtre restreinte, sinon l'aperçu mentirait sur l'étendue réelle des données.
 *
 * Bon marché à calculer : aucune requête, une relecture en mémoire d'un [ReportModel] déjà
 * construit. Bon marché à lire pour le modèle de langage : un compte de jours par mois,
 * jamais une date précise ni une valeur — le même genre d'agrégat que `sleep.monthly.nights`,
 * déjà toléré par la frontière de confidentialité (voir CLAUDE.md, section
 * « Confidentialité »). [ReportModel] lui-même ne quitte jamais l'appareil ; seul le texte
 * produit par [describeHistoryOverview] part dans le prompt système.
 *
 * Le jour actif d'un mois est l'union des séries quotidiennes et nocturnes du rapport : une
 * nuit de sommeil, un total de pas, une pesée... comptent chacune pour un jour, et un jour
 * porteur de plusieurs mesures ne compte qu'une fois.
 */
fun historyOverviewOf(report: ReportModel): HistoryOverview {
    val dates = buildSet {
        report.sleep.nightly.forEach { add(it.date) }
        report.activity.stepsDaily.forEach { add(it.date) }
        report.heart.restingDaily.forEach { add(it.date) }
        report.heart.hrvDaily.forEach { add(it.date) }
        report.stress.daily.forEach { add(it.date) }
        report.stress.vitalityDaily.forEach { add(it.date) }
        report.body.daily.forEach { add(it.date) }
        report.breathing.spo2Daily.forEach { add(it.date) }
        report.breathing.respiratoryDaily.forEach { add(it.date) }
        report.breathing.skinTempDaily.forEach { add(it.date) }
    }

    val months = dates
        .groupingBy { it.substring(0, 7) }
        .eachCount()
        .map { (month, count) -> MonthCoverage(month, count) }
        .sortedBy { it.month }

    return HistoryOverview(earliest = dates.minOrNull(), latest = dates.maxOrNull(), months = months)
}

/**
 * Met [overview] en texte pour `{{OVERVIEW}}` dans `report/chat-prompt.txt`.
 *
 * C'est tout ce que le modèle sait de l'historique avant de choisir une fenêtre avec
 * `healthrange` : de quand à quand des données existent, et combien de jours par mois.
 */
fun describeHistoryOverview(overview: HistoryOverview): String {
    if (overview.months.isEmpty()) return "Aucune donnée n'a encore été importée."
    return buildString {
        appendLine("Historique disponible : du ${overview.earliest} au ${overview.latest}.")
        appendLine("Jours avec au moins une mesure, par mois :")
        overview.months.forEach { appendLine("- ${it.month} : ${it.daysWithData} jours") }
    }.trimEnd()
}
