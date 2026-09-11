package com.kmt.healthanalyzer.domain.drift

import java.time.Instant
import java.time.LocalDate

/**
 * Un jour de la semaine écoulée. [value] vaut `null` quand la mesure manque.
 *
 * Le trou est gardé tel quel, jamais remplacé par zéro : un zéro se dessine sur une courbe
 * et se lit comme une nuit blanche ou une journée sans un pas, alors qu'il ne veut dire que
 * « montre posée ce jour-là ».
 */
data class DayPoint(val date: LocalDate, val value: Double?)

/**
 * Une ligne de l'écran « Cette semaine » : une mesure, sa semaine, sa référence.
 *
 * **Cette ligne existe toujours, même sans aucune donnée.** C'est la différence avec
 * [MetricDrift], qui n'apparaît que lorsqu'un écart est détecté. L'écran montre six lignes
 * à chaque ouverture ; une mesure retirée faute de données laisserait croire que l'app ne la
 * suit pas.
 *
 * [recentValue] et [baselineValue] sont **le nombre à montrer**, et ce n'est pas toujours
 * une moyenne : pour [DriftMetric.SLEEP_REGULARITY], c'est l'écart-type des heures de
 * coucher, parce que la régularité *est* une dispersion. Les deux restent `null` tant que
 * la fenêtre n'a pas assez de jours mesurés — voir [DriftDetector.MIN_RECENT_MEASURED_DAYS]
 * et [DriftDetector.MIN_BASELINE_MEASURED_DAYS]. L'app se tait plutôt que de moyenner trois
 * points en donnant l'illusion d'en valoir sept.
 *
 * [recentDays] et [baselineDays] comptent les jours **réellement mesurés**, pas la longueur
 * de la fenêtre. Ils restent justes même quand les valeurs sont nulles : ils servent à dire
 * à l'utilisateur ce qui manque.
 */
data class MetricReview(
    val metric: DriftMetric,
    val recentValue: Double?,
    val baselineValue: Double?,
    val recentDays: Int,
    val baselineDays: Int,
    val lastMeasuredOn: LocalDate?,
    val week: List<DayPoint>,
    val drift: MetricDrift?,
) {
    /**
     * L'écart entre la semaine et la référence, ou `null` si l'un des deux manque.
     *
     * Signé, et dans l'unité de la mesure : c'est [DriftMetric.format] qui le met en mots.
     */
    val delta: Double? =
        if (recentValue != null && baselineValue != null) recentValue - baselineValue else null
}

/**
 * Ce que l'écran peut dire du bloc « Ce qui a changé ».
 *
 * Trois états, et il faut les distinguer : « aucune dérive » et « pas assez de données » se
 * ressemblent à l'écran — les deux montrent une liste vide — et ne veulent pas du tout dire
 * la même chose. Confondre les deux, c'est laisser croire que tout va bien alors que l'app
 * n'a simplement rien pu comparer.
 */
sealed interface WeeklyReadiness {

    /** Assez de données des deux côtés : la liste des dérives, vide ou non, veut dire quelque chose. */
    data object Ready : WeeklyReadiness

    /** La référence est trop courte. [measuredDays] est le meilleur compte parmi les six mesures. */
    data class NotEnoughBaseline(
        val measuredDays: Int,
        val requiredDays: Int = DriftDetector.MIN_BASELINE_MEASURED_DAYS,
    ) : WeeklyReadiness

    /** La référence tient, mais la semaine écoulée est trop trouée pour la représenter. */
    data class NotEnoughRecent(
        val measuredDays: Int,
        val requiredDays: Int = DriftDetector.MIN_RECENT_MEASURED_DAYS,
    ) : WeeklyReadiness
}

/**
 * Le bilan de la semaine écoulée : les six mesures, et ce qui a bougé.
 *
 * Produit par [HealthDriftAnalyzer.review] en un seul passage, dont [DriftReport] est
 * extrait. Les dérives montrées sur l'écran d'accueil sont donc **exactement** celles que
 * `WeeklyDriftCheckWorker` notifie : un seul calcul, jamais deux qui finiraient par se
 * contredire à l'écran.
 */
data class WeeklyReview(
    val generatedAt: Instant,
    val from: LocalDate,
    val to: LocalDate,
    val metrics: List<MetricReview>,
) {
    val drifts: List<MetricDrift> get() = metrics.mapNotNull { it.drift }

    val hasDrift: Boolean get() = drifts.isNotEmpty()

    /**
     * Ce que l'écran a le droit de conclure.
     *
     * Le compte retenu est le **meilleur** des six mesures, pas leur somme ni leur moyenne :
     * il suffit qu'une seule mesure ait assez d'historique pour qu'une comparaison ait un
     * sens. Quelqu'un qui porte sa montre la nuit mais pas le jour a un historique de
     * sommeil complet et presque aucun pas ; lui dire qu'il manque des données serait faux.
     */
    val readiness: WeeklyReadiness
        get() {
            val bestBaseline = metrics.maxOfOrNull { it.baselineDays } ?: 0
            if (bestBaseline < DriftDetector.MIN_BASELINE_MEASURED_DAYS) {
                return WeeklyReadiness.NotEnoughBaseline(bestBaseline)
            }
            val bestRecent = metrics.maxOfOrNull { it.recentDays } ?: 0
            if (bestRecent < DriftDetector.MIN_RECENT_MEASURED_DAYS) {
                return WeeklyReadiness.NotEnoughRecent(bestRecent)
            }
            return WeeklyReadiness.Ready
        }
}
