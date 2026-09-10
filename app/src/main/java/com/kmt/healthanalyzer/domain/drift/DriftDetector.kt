package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import kotlin.math.abs
import kotlin.math.sqrt

/** Ce que décrit un [MetricDrift] : un déplacement de moyenne, ou une dispersion accrue. */
enum class DriftKind { MEAN_SHIFT, SPREAD_INCREASE }

/**
 * Une dérive détectée sur une mesure, référence personnelle contre semaine écoulée.
 *
 * Les champs `baseline*` décrivent la référence — jusqu'à [DriftDetector.BASELINE_WINDOW_DAYS]
 * jours mesurés, en dehors de la semaine écoulée — et les champs `recent*` la semaine
 * écoulée elle-même ([DriftDetector.RECENT_WINDOW_DAYS] jours). [signalStrength] est le
 * score qui a déclenché la dérive : un z-score pour [DriftKind.MEAN_SHIFT], un rapport
 * d'écarts-types pour [DriftKind.SPREAD_INCREASE] — voir [DriftDetector] pour la méthode.
 *
 * Ce type ne porte aucun jugement (« bon », « mauvais », « inquiétant ») : il décrit un
 * écart chiffré. C'est [DriftNarrator] qui le met en mots, toujours au constat, jamais au
 * diagnostic — voir sa documentation.
 */
data class MetricDrift(
    val metric: DriftMetric,
    val kind: DriftKind,
    val baselineMean: Double,
    val baselineStdDev: Double,
    val baselineDays: Int,
    val recentMean: Double,
    val recentStdDev: Double,
    val recentDays: Int,
    val signalStrength: Double,
)

/**
 * Détecte une dérive statistiquement significative entre la semaine écoulée et la
 * référence personnelle de l'utilisateur, mesure par mesure.
 *
 * Objet sans état, sans dépendance à Android : chaque fonction est une transformation pure
 * de deux listes de valeurs quotidiennes vers un [MetricDrift] ou `null`, testable en JVM.
 * L'appelant ([HealthDriftAnalyzer]) est responsable de découper les séries en fenêtre
 * récente et fenêtre de référence, et de ne passer que des valeurs réellement mesurées :
 * un trou de données ne doit jamais devenir un zéro ici, sous peine d'être lu comme une
 * vraie baisse.
 *
 * ## Pourquoi une référence personnelle, pas un seuil de population
 *
 * Chacun a sa propre fréquence cardiaque de repos, son propre nombre de pas habituel :
 * seul l'écart d'une personne à elle-même a du sens. La référence est donc calculée sur
 * l'historique récent de l'utilisateur, jamais sur une norme externe.
 *
 * ## Pourquoi un test statistique plutôt qu'un seuil en valeur absolue
 *
 * Ce sont des données de santé : une fausse alerte n'est pas un désagrément, c'est une
 * inquiétude infligée à quelqu'un. Un écart de 5 bpm ne veut pas dire la même chose chez
 * quelqu'un dont la fréquence cardiaque varie de ±2 bpm au quotidien que chez quelqu'un
 * dont elle varie de ±15 bpm. [detectShift] compare donc l'écart entre les deux moyennes à
 * l'erreur-type combinée des deux fenêtres — l'approche standard d'un test z à deux
 * échantillons — plutôt qu'à un nombre de battements fixe.
 *
 * Limite assumée : les mesures d'un jour à l'autre ne sont pas indépendantes (une nuit
 * courte influence souvent la suivante), ce qu'un test z classique suppose. Le z-score
 * produit n'est donc pas une vraie p-value. C'est pour cette raison que [Z_SCORE_THRESHOLD]
 * est choisi au-dessus du seuil académique habituel (1,96) et que des minimums de jours
 * mesurés ([MIN_BASELINE_MEASURED_DAYS], [MIN_RECENT_MEASURED_DAYS]) s'ajoutent en garde-fou :
 * la méthode reste une heuristique conservatrice, pas un test médical.
 */
object DriftDetector {

    /**
     * Longueur de la fenêtre récente : une semaine, le rythme du bilan hebdomadaire. Un
     * changement qui ne tient pas sur sept jours n'est pas ce que ce chantier veut signaler
     * — il vise une dérive qui dure, pas un mauvais jour.
     */
    const val RECENT_WINDOW_DAYS: Int = 7

    /**
     * Longueur de la fenêtre de référence : huit semaines précédant la semaine récente.
     * Assez long pour moyenner les variations normales (un rhume, un week-end chargé) sans
     * les confondre avec une dérive ; assez court pour rester la routine *actuelle* de la
     * personne, pas une moyenne sur toute une année qui inclurait une autre saison de vie.
     */
    const val BASELINE_WINDOW_DAYS: Int = 56

    /** Fenêtre totale à charger : la référence puis la semaine récente, bout à bout. */
    const val TOTAL_WINDOW_DAYS: Int = BASELINE_WINDOW_DAYS + RECENT_WINDOW_DAYS

    /**
     * Jours mesurés minimum dans la semaine récente, sur les [RECENT_WINDOW_DAYS] possibles.
     * Deux jours sans mesure sont tolérés (montre posée pour la recharger, oubliée un
     * jour) ; en dessous, la semaine est trop trouée pour représenter quoi que ce soit —
     * voir la règle « un trou n'est pas une baisse ».
     */
    const val MIN_RECENT_MEASURED_DAYS: Int = 5

    /**
     * Jours mesurés minimum dans la fenêtre de référence, sur les [BASELINE_WINDOW_DAYS]
     * possibles. Trois semaines pleines : assez pour que la moyenne et l'écart-type de
     * référence ne soient pas dominés par une poignée de points isolés, chez un utilisateur
     * qui ne porte pas sa montre tous les jours.
     */
    const val MIN_BASELINE_MEASURED_DAYS: Int = 21

    /**
     * Score z à partir duquel un déplacement de moyenne est signalé.
     *
     * 2,0 correspond, pour un test z à deux échantillons classique, à un risque d'environ
     * 5 % de signaler un écart qui ne serait que du bruit (seuil académique usuel : 1,96).
     * Choisi volontairement un peu au-dessus de ce repère pour compenser l'autocorrélation
     * des mesures de santé d'un jour à l'autre (voir la documentation de l'objet), qui rend
     * le bruit réel plus généreux qu'un test z ne le suppose.
     */
    const val Z_SCORE_THRESHOLD: Double = 2.0

    /**
     * Rapport d'écarts-types à partir duquel la régularité du sommeil est jugée dégradée.
     *
     * Comparer deux écarts-types rigoureusement demanderait un test F, hors de portée sans
     * bibliothèque statistique. 1,5 est un repère pragmatique et volontairement prudent :
     * l'écart-type des heures de coucher doit augmenter d'au moins 50 % — un changement
     * net, pas une fluctuation d'une semaine à l'autre — avant d'être signalé.
     */
    const val STD_DEV_RATIO_THRESHOLD: Double = 1.5

    /**
     * Plancher, en heures, utilisé seulement quand la référence est parfaitement régulière
     * (écart-type de zéro : impossible de calculer un rapport). Sans lui, la moindre
     * seconde de bruit d'arrondi flottant deviendrait une dérive « infinie ». Un quart
     * d'heure d'écart-type reste, dans ce cas précis, un changement net et réel.
     */
    const val ZERO_BASELINE_SPREAD_FLOOR_HOURS: Double = 0.25

    /**
     * Détecte un déplacement de moyenne entre la référence et la semaine récente.
     *
     * Utilisé pour la durée de sommeil, la fréquence cardiaque de repos, la HRV, les pas
     * et le poids : toutes des mesures où un déplacement de moyenne est ce qui compte.
     * Rend `null` sans donnée insuffisante, sans écart, ou sous le seuil — jamais un état
     * inventé.
     */
    fun detectShift(metric: DriftMetric, baselineValues: List<Double>, recentValues: List<Double>): MetricDrift? {
        if (baselineValues.size < MIN_BASELINE_MEASURED_DAYS || recentValues.size < MIN_RECENT_MEASURED_DAYS) {
            return null
        }

        val baselineMean = SeriesStats.mean(baselineValues) ?: return null
        val recentMean = SeriesStats.mean(recentValues) ?: return null
        val baselineStdDev = SeriesStats.stdDev(baselineValues) ?: return null
        val recentStdDev = SeriesStats.stdDev(recentValues) ?: return null

        val standardError = sqrt(
            (baselineStdDev * baselineStdDev) / baselineValues.size +
                (recentStdDev * recentStdDev) / recentValues.size,
        )
        val delta = recentMean - baselineMean
        val zScore = zScoreOf(delta, standardError)

        if (abs(zScore) < Z_SCORE_THRESHOLD) return null

        return MetricDrift(
            metric = metric,
            kind = DriftKind.MEAN_SHIFT,
            baselineMean = baselineMean,
            baselineStdDev = baselineStdDev,
            baselineDays = baselineValues.size,
            recentMean = recentMean,
            recentStdDev = recentStdDev,
            recentDays = recentValues.size,
            signalStrength = zScore,
        )
    }

    /**
     * Détecte une dispersion récente significativement plus large que la référence.
     *
     * Utilisé pour la régularité du coucher : [baselineValues] et [recentValues] portent
     * l'heure de coucher de chaque nuit (relative à minuit, comme
     * [com.kmt.healthanalyzer.domain.report.SleepNightPoint.bedRel]), pas une durée. C'est
     * l'écart-type qui compte ici, pas la moyenne — voir [STD_DEV_RATIO_THRESHOLD].
     */
    fun detectSpreadIncrease(
        metric: DriftMetric,
        baselineValues: List<Double>,
        recentValues: List<Double>,
    ): MetricDrift? {
        if (baselineValues.size < MIN_BASELINE_MEASURED_DAYS || recentValues.size < MIN_RECENT_MEASURED_DAYS) {
            return null
        }

        val baselineMean = SeriesStats.mean(baselineValues) ?: return null
        val recentMean = SeriesStats.mean(recentValues) ?: return null
        val baselineStdDev = SeriesStats.stdDev(baselineValues) ?: return null
        val recentStdDev = SeriesStats.stdDev(recentValues) ?: return null

        val ratio = if (baselineStdDev > 0.0) recentStdDev / baselineStdDev else null
        val isDrift = ratio?.let { it >= STD_DEV_RATIO_THRESHOLD }
            ?: (recentStdDev >= ZERO_BASELINE_SPREAD_FLOOR_HOURS)
        if (!isDrift) return null

        return MetricDrift(
            metric = metric,
            kind = DriftKind.SPREAD_INCREASE,
            baselineMean = baselineMean,
            baselineStdDev = baselineStdDev,
            baselineDays = baselineValues.size,
            recentMean = recentMean,
            recentStdDev = recentStdDev,
            recentDays = recentValues.size,
            signalStrength = ratio ?: Double.POSITIVE_INFINITY,
        )
    }

    /**
     * `delta / erreur-type`, sauf quand l'erreur-type est nulle (les deux fenêtres sont
     * chacune parfaitement constantes) : dans ce cas précis, la moindre différence entre
     * les deux moyennes est un écart total à une variabilité nulle, donc un signal maximal
     * — sauf si les deux moyennes sont elles-mêmes égales, où il n'y a rien à signaler.
     */
    private fun zScoreOf(delta: Double, standardError: Double): Double = when {
        standardError > 0.0 -> delta / standardError
        delta != 0.0 -> if (delta > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        else -> 0.0
    }
}
