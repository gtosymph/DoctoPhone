package com.kmt.healthanalyzer.domain.analysis

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Statistiques circulaires sur des heures de la journée (0.0 à 24.0).
 *
 * Une heure de coucher est un point sur un cercle, pas sur une droite : 23 h et 1 h sont
 * distants de 2 h, pas de 22 h. Les moyennes et écarts-types ordinaires se trompent dès
 * qu'un groupe de mesures chevauche minuit ; ces fonctions passent par les coordonnées
 * trigonométriques du cercle pour l'éviter.
 */
object CircularStats {

    private const val HOURS_PER_DAY = 24.0
    private const val TWO_PI = 2.0 * Math.PI

    /** Moyenne circulaire, en heures (0.0 à 24.0). `null` si [hoursOfDay] est vide. */
    fun mean(hoursOfDay: List<Double>): Double? {
        if (hoursOfDay.isEmpty()) return null
        val (meanSin, meanCos) = sinCosMeans(hoursOfDay)
        return angleToHours(atan2(meanSin, meanCos))
    }

    /**
     * Écart-type circulaire, en heures.
     *
     * Formule : `sqrt(-2 * ln(R)) / (2 pi) * 24`, où `R` est la longueur du vecteur
     * résultant (0 = dispersion totale, 1 = toutes les heures identiques). Quand `R`
     * tombe à zéro, les mesures sont réparties uniformément sur le cercle : l'écart-type
     * n'a plus de sens directionnel, on retombe sur la moitié d'une journée.
     */
    fun stdDevHours(hoursOfDay: List<Double>): Double? {
        if (hoursOfDay.isEmpty()) return null
        val (meanSin, meanCos) = sinCosMeans(hoursOfDay)
        // R ne peut mathématiquement pas dépasser 1.0 (moyenne de vecteurs unitaires),
        // mais la somme en virgule flottante d'heures identiques ou presque peut la
        // pousser légèrement au-delà (ex. 1.0000000000000002), rendant ln(R) positif et
        // sqrt(-2 * ln(R)) NaN. On plafonne : au-delà de 1.0, ce n'est que du bruit
        // d'arrondi, jamais une vraie dispersion.
        val resultantLength = sqrt(meanSin * meanSin + meanCos * meanCos).coerceAtMost(1.0)
        return if (resultantLength <= 0.0) {
            HOURS_PER_DAY / 2.0
        } else {
            sqrt(-2.0 * ln(resultantLength)) / TWO_PI * HOURS_PER_DAY
        }
    }

    /**
     * Médiane sur une échelle recentrée autour de 18 h.
     *
     * Une médiane linéaire scinderait un groupe de couchers à cheval sur minuit (23 h
     * finirait loin de 1 h dans le tri). Recentrer l'échelle sur 18 h déplace la coupure
     * du cercle à 6 h du matin, loin des heures de coucher habituelles.
     */
    fun circularMedian(hoursOfDay: List<Double>): Double? {
        if (hoursOfDay.isEmpty()) return null
        val shifted = hoursOfDay.map { offsetFrom(it, RECENTER_HOUR) }
        val medianShifted = SeriesStats.median(shifted) ?: return null
        return (medianShifted + RECENTER_HOUR).mod(HOURS_PER_DAY)
    }

    /** Décalage de [hour] par rapport à [center], ramené dans `[-12, 12)`. */
    private fun offsetFrom(hour: Double, center: Double): Double =
        ((hour - center + HOURS_PER_DAY / 2.0).mod(HOURS_PER_DAY)) - HOURS_PER_DAY / 2.0

    private fun sinCosMeans(hoursOfDay: List<Double>): Pair<Double, Double> {
        val angles = hoursOfDay.map { it / HOURS_PER_DAY * TWO_PI }
        return angles.map { sin(it) }.average() to angles.map { cos(it) }.average()
    }

    private fun angleToHours(angle: Double): Double =
        (angle.let { if (it < 0) it + TWO_PI else it } / TWO_PI * HOURS_PER_DAY)

    private const val RECENTER_HOUR = 18.0
}
