package com.kmt.healthanalyzer.domain.report

import kotlinx.serialization.Serializable

/**
 * Contrat de données du rapport de santé.
 *
 * Ce modèle est la **seule** frontière entre le calcul et le dessin. Le moteur de
 * graphiques est écrit une fois en SVG (`web/report/`) ; il est utilisé tel quel par la
 * version web et par la WebView Android. Les deux plateformes doivent donc produire
 * exactement le même JSON, sinon les deux versions divergent.
 *
 * Règles à respecter en modifiant ce fichier :
 * - Toute modification ici doit être reportée dans `web/lib/report-model.js`.
 * - Les dates sont des chaînes ISO `AAAA-MM-JJ`, les mois des chaînes `AAAA-MM`.
 * - Les heures relatives sont exprimées en heures décimales autour de minuit :
 *   `-1.5` vaut 22h30, `2.25` vaut 2h15. Ce choix permet de tracer une nuit sur un axe
 *   continu, sans coupure à minuit.
 * - Une valeur absente vaut `null`, jamais zéro. Un zéro se dessine ; un trou ne se
 *   dessine pas.
 *
 * Ce modèle ne quitte jamais l'appareil. Il porte des mesures quotidiennes, donc il ne
 * doit jamais être envoyé à un fournisseur de LLM. La seule porte de sortie reste
 * `HealthPromptBuilder`, qui n'envoie que des agrégats hebdomadaires.
 */
@Serializable
data class ReportModel(
    val meta: ReportMeta,
    val tiles: List<ReportTile>,
    val sleep: SleepSection,
    val heart: HeartSection,
    val activity: ActivitySection,
    val body: BodySection,
    val stress: StressSection,
    val breathing: BreathingSection,
    val correlations: List<CorrelationItem>,
    val narrative: ReportNarrative? = null,
)

/** En-tête du rapport : période couverte et volumétrie, pour situer la fiabilité des chiffres. */
@Serializable
data class ReportMeta(
    val generatedAt: String,
    val from: String?,
    val to: String?,
    val days: Int,
    val nights: Int,
    val heartRateSamples: Int,
    val hrvWindows: Int,
    val activeDays: Int,
    val timeZone: String,
    val periodLabel: String,
    val profile: ReportProfile? = null,
)

@Serializable
data class ReportProfile(
    val heightCm: Double? = null,
    val weightKg: Double? = null,
)

/**
 * Une tuile de synthèse en tête de rapport.
 *
 * [status] pilote la couleur de la pastille. Les valeurs admises sont `good`, `warn`,
 * `serious`, `critical` et `neutral`. Le moteur de dessin ignore toute autre valeur et
 * retombe sur `neutral`.
 *
 * [value] et [sub] sont les **seuls** champs du modèle qui portent du texte déjà formaté.
 * Partout ailleurs le modèle garde ses nombres bruts et le moteur de dessin formate.
 * Les règles de typographie française s'appliquent ici, et elles s'appliquent de la même
 * façon dans les deux implémentations : virgule décimale, espace fine insaisissable pour
 * les milliers, heure d'horloge en `0h44`, durée en `5 h 48`, date en `23 juin 2025`.
 *
 * [value] ne répète jamais l'unité : elle va dans [unit]. `value = "54"` avec
 * `unit = "bpm"`, `value = "131/82"` avec `unit = "mmHg"`.
 *
 * [unit] ne vaut `null` que lorsque la valeur porte déjà sa notation en elle-même : une
 * durée `"5 h 48"`, une heure d'horloge `"0h44"`, un indice sans unité comme l'IMC.
 *
 * Sans donnée, [value] vaut `"—"` et [status] vaut `neutral`. Jamais un état inventé.
 */
@Serializable
data class ReportTile(
    val key: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val sub: String,
    val status: String,
)

/** Une valeur rattachée à un jour. */
@Serializable
data class DayValue(val date: String, val value: Double?)

/** Une valeur rattachée à un mois `AAAA-MM`. */
@Serializable
data class MonthValue(val month: String, val value: Double?)

/**
 * Une valeur rattachée à une étiquette libre : jour de semaine, tranche horaire, tranche de durée.
 *
 * Pour une série horaire, [label] est l'heure en clair, sans zéro devant et sans suffixe :
 * `"0"` à `"23"`. Le moteur de dessin la convertit en nombre pour placer le point ; un
 * `"00h"` donnerait `NaN` et effacerait la courbe. Le suffixe est ajouté à l'affichage.
 *
 * Pour un jour de semaine, [label] vaut `"lun."` à `"dim."`, dans cet ordre.
 *
 * [count] porte l'effectif quand il éclaire la valeur : le nombre de nuits d'une tranche
 * de durée, le nombre de jours mesurés derrière une moyenne.
 */
@Serializable
data class LabelValue(val label: String, val value: Double?, val count: Int? = null)

/**
 * Une corrélation de Pearson mesurée sur les jours appariés.
 *
 * [n] est le nombre de jours où les deux mesures existent. En dessous de
 * [CorrelationItem.MIN_PAIRS] jours, le coefficient n'est pas montré : il ne veut rien dire.
 */
@Serializable
data class CorrelationItem(
    val label: String,
    val r: Double,
    val n: Int,
) {
    companion object {
        const val MIN_PAIRS = 30
        const val STRONG = 0.30
    }
}

/**
 * Le texte du rapport, écrit par le LLM à partir des seuls agrégats.
 *
 * Cette partie est nulle tant que l'utilisateur n'a pas lancé d'analyse. Le rapport se
 * montre alors sans prose : les graphiques et les chiffres suffisent.
 */
@Serializable
data class ReportNarrative(
    val headline: String,
    val verdict: String,
    val sections: Map<String, SectionNarrative> = emptyMap(),
    val plan: List<PlanStep> = emptyList(),
)

@Serializable
data class SectionNarrative(
    val verdict: String,
    val points: List<String> = emptyList(),
)

@Serializable
data class PlanStep(
    val title: String,
    val body: String,
)
