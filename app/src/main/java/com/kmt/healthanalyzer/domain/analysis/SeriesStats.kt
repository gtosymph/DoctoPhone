package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.LabelValue
import com.kmt.healthanalyzer.domain.report.MonthValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * Statistiques de série utilisées par les graphiques du rapport.
 *
 * Objet sans état, sans dépendance à Android : chaque fonction est une transformation
 * pure d'une liste vers une autre, testable directement en JVM.
 */
object SeriesStats {

    /**
     * Moyenne glissante sur une fenêtre de [window] positions.
     *
     * Une position reste `null` tant que sa fenêtre contient moins de [minSamples]
     * valeurs non nulles : la moyenne des tout premiers jours ne veut rien dire sur
     * une fenêtre presque vide.
     */
    fun rollingMean(values: List<Double?>, window: Int, minSamples: Int): List<Double?> {
        require(window > 0) { "La fenêtre doit être positive." }
        return values.indices.map { index ->
            val start = (index - window + 1).coerceAtLeast(0)
            val windowValues = values.subList(start, index + 1).filterNotNull()
            if (windowValues.size < minSamples) null else windowValues.average()
        }
    }

    /**
     * Centile par indexation directe, comme `restingHeartRate` : indice
     * `((size - 1) * p).toInt()` sur la liste triée. `null` si la série est vide.
     */
    fun percentile(values: List<Double>, p: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    /** Médiane statistique : moyenne des deux valeurs centrales pour une série paire. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()

    /**
     * Écart-type de population (division par `n`, jamais `n - 1`).
     *
     * Le rapport décrit un ensemble observé — les mesures de la période — il n'estime
     * pas une population plus large à partir d'un échantillon. `null` seulement si la
     * série est vide ; une seule valeur a un écart-type de zéro, pas indéfini.
     */
    fun stdDev(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val avg = values.average()
        val variance = values.sumOf { (it - avg) * (it - avg) } / values.size
        return sqrt(variance)
    }

    /**
     * Moyenne par jour de semaine, toujours les sept jours dans l'ordre lundi à dimanche.
     * Les jours sans mesure portent une valeur `null` et un compte de zéro.
     */
    fun byDayOfWeek(values: Map<LocalDate, Double?>): List<LabelValue> {
        val byDay = values.entries
            .filter { it.value != null }
            .groupBy({ it.key.dayOfWeek }) { it.value!! }

        return DayOfWeek.entries.map { day ->
            val samples = byDay[day].orEmpty()
            LabelValue(label = FRENCH_WEEKDAY_LABELS.getValue(day), value = mean(samples), count = samples.size)
        }
    }

    /** Moyenne par mois, clé ISO `AAAA-MM`, triée chronologiquement. */
    fun byMonth(values: Map<LocalDate, Double?>): List<MonthValue> {
        return values.entries
            .groupBy { it.key.format(MONTH_FORMAT) }
            .toSortedMap()
            .map { (month, entries) -> MonthValue(month = month, value = mean(entries.mapNotNull { it.value })) }
    }

    /**
     * Moyenne par heure locale, toujours les 24 heures dans l'ordre, `0` étant minuit.
     *
     * [samples] porte l'heure locale déjà extraite par l'appelant (0-23) associée à sa
     * valeur : cette fonction ne connaît ni fuseau ni horodatage.
     *
     * [LabelValue.label] est l'heure en clair, sans zéro devant et sans suffixe (`"0"` à
     * `"23"`) : le moteur de dessin la convertit en nombre pour placer le point, et un
     * `"00h"` donnerait `NaN`. Le suffixe est ajouté à l'affichage, pas ici.
     */
    fun byHour(samples: List<Pair<Int, Double>>): List<LabelValue> {
        val byHour = samples.groupBy({ it.first }) { it.second }
        return (0..23).map { hour ->
            val values = byHour[hour].orEmpty()
            LabelValue(label = hour.toString(), value = mean(values), count = values.size)
        }
    }

    /** Une tranche de valeurs, bornes propres à l'appelant : [lower] inclus, [upper] exclu. */
    data class Bucket(val label: String, val lower: Double, val upper: Double)

    /** Comptage des valeurs par tranche, dans l'ordre des [buckets] fournis par l'appelant. */
    fun distribution(values: List<Double>, buckets: List<Bucket>): List<LabelValue> = buckets.map { bucket ->
        val count = values.count { it >= bucket.lower && it < bucket.upper }
        LabelValue(label = bucket.label, value = count.toDouble(), count = count)
    }

    private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    private val FRENCH_WEEKDAY_LABELS: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "lun.",
        DayOfWeek.TUESDAY to "mar.",
        DayOfWeek.WEDNESDAY to "mer.",
        DayOfWeek.THURSDAY to "jeu.",
        DayOfWeek.FRIDAY to "ven.",
        DayOfWeek.SATURDAY to "sam.",
        DayOfWeek.SUNDAY to "dim.",
    )
}
