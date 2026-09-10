package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.report.TileFormat

/**
 * Met un [MetricDrift] en mots, en français, sans jamais l'interpréter.
 *
 * Contrainte technique, pas seulement éditoriale : l'app n'est pas un dispositif médical et
 * ne pose aucun diagnostic (voir `CLAUDE.md` et `web/report/chat-prompt.txt`). Une dérive se
 * **décrit** — les deux chiffres, la période — elle ne se **qualifie** jamais : pas de
 * "risque", pas d'avis, pas de conseil, pas de couleur alarmante dans le texte lui-même.
 * Un exemple qui reste correct : « la fréquence cardiaque de repos est passée de 54 à 59
 * battements par minute sur la semaine écoulée. » Un exemple qui ne le serait pas :
 * « attention, votre cœur s'emballe. »
 *
 * Aucun accord de genre au participe passé (« passé »/« passée ») : plutôt que de porter une
 * table d'exceptions grammaticales par mesure, chaque phrase suit une structure neutre,
 * « [mesure] : [référence] d'habitude, [récent] sur la semaine écoulée » — correcte quel
 * que soit le genre du nom de la mesure.
 */
object DriftNarrator {

    fun describe(drift: MetricDrift): String = when (drift.kind) {
        DriftKind.MEAN_SHIFT -> describeShift(drift)
        DriftKind.SPREAD_INCREASE -> describeSpreadIncrease(drift)
    }

    private fun describeShift(drift: MetricDrift): String =
        "${drift.metric.label} : ${drift.metric.format(drift.baselineMean)} en moyenne ces dernières " +
            "semaines, ${drift.metric.format(drift.recentMean)} sur la semaine écoulée."

    private fun describeSpreadIncrease(drift: MetricDrift): String =
        "${drift.metric.label} : l'écart type était de ${hours(drift.baselineStdDev)} ces dernières " +
            "semaines, il est de ${hours(drift.recentStdDev)} sur la semaine écoulée — le coucher " +
            "varie davantage que d'habitude."

    private fun hours(value: Double): String = "${TileFormat.number(value, 1) ?: "—"} h"
}
