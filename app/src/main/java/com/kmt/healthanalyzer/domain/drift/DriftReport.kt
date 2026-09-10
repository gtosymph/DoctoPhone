package com.kmt.healthanalyzer.domain.drift

import java.time.Instant

/**
 * Le résultat d'une passe de détection : zéro ou plusieurs [MetricDrift].
 *
 * [hasDrift] pilote autant l'écran (montrer la liste ou le message sobre « aucune dérive »)
 * que le bilan hebdomadaire (envoyer une notification ou ne rien envoyer) : voir
 * `WeeklyDriftCheckWorker`. Une liste vide est un résultat normal, pas une erreur — c'est
 * même le cas le plus fréquent.
 */
data class DriftReport(
    val generatedAt: Instant,
    val drifts: List<MetricDrift>,
) {
    val hasDrift: Boolean get() = drifts.isNotEmpty()
}
