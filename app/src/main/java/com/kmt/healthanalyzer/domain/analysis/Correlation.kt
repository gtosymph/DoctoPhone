package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.CorrelationItem
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Corrélation de Pearson entre deux séries quotidiennes.
 *
 * Objet sans état, sans dépendance à Android : les six corrélations du rapport
 * s'appuient dessus pour dire si deux mesures évoluent ensemble.
 */
object Correlation {

    /**
     * Coefficient de corrélation de Pearson sur [pairs].
     *
     * Rend `null` en dessous de [CorrelationItem.MIN_PAIRS] paires, ou si l'une des deux
     * séries n'a aucune variance : la corrélation n'a pas de sens sur une droite plate.
     */
    fun pearson(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < CorrelationItem.MIN_PAIRS) return null

        val xs = pairs.map { it.first }
        val ys = pairs.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()

        var covariance = 0.0
        var varianceX = 0.0
        var varianceY = 0.0
        for ((x, y) in pairs) {
            val dx = x - meanX
            val dy = y - meanY
            covariance += dx * dy
            varianceX += dx * dx
            varianceY += dy * dy
        }
        if (varianceX <= 0.0 || varianceY <= 0.0) return null

        return covariance / sqrt(varianceX * varianceY)
    }

    /**
     * Apparie deux séries quotidiennes jour par jour, avec un décalage optionnel.
     *
     * Pour chaque date `d` porteuse d'une valeur non nulle dans [a], la fonction cherche
     * la valeur de [b] à la date `d + lagDays`. Un [lagDays] positif compare donc [a] à
     * ce que [b] vaut *après* : c'est la forme « la veille vers le lendemain ». Un
     * [lagDays] négatif regarde [b] *avant* la date de [a].
     */
    fun paired(
        a: Map<LocalDate, Double?>,
        b: Map<LocalDate, Double?>,
        lagDays: Int = 0,
    ): List<Pair<Double, Double>> = a.entries
        .sortedBy { it.key }
        .mapNotNull { (date, aValue) ->
            if (aValue == null) return@mapNotNull null
            val bValue = b[date.plusDays(lagDays.toLong())] ?: return@mapNotNull null
            aValue to bValue
        }
}
