package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.Correlation
import java.time.LocalDate

/**
 * Les six corrélations du rapport, calculées à partir des sections déjà construites —
 * pas des mesures brutes — pour que le coefficient porte exactement les mêmes chiffres
 * que les graphiques (`sleep.nightly`, `heart.restingDaily`, `activity.stepsDaily`,
 * `stress.daily`), sans recalcul divergent.
 *
 * [Correlation.paired] apparie `a[d]` à `b[d + lagDays]` : un [lagDays] positif compare
 * donc `a` à ce que `b` vaut *après*. Les six corrélations s'expriment naturellement
 * avec cette seule convention — y compris « la durée de sommeil vers le stress du
 * lendemain », dont le sommeil (a) précède le stress (b) d'un jour, donc `lagDays = 1`
 * comme les deux corrélations « de la veille ».
 */
internal fun buildCorrelations(sleep: SleepSection, heart: HeartSection, activity: ActivitySection, stress: StressSection): List<CorrelationItem> {
    val sleepHours = sleep.nightly.associate { LocalDate.parse(it.date) to it.hours }
    val sleepScore = sleep.nightly.associate { LocalDate.parse(it.date) to it.score?.toDouble() }
    val bedtime = sleep.nightly.associate { LocalDate.parse(it.date) to it.bedRel }
    val restingDaily = heart.restingDaily.associate { LocalDate.parse(it.date) to it.value }
    val hrvDaily = heart.hrvDaily.associate { LocalDate.parse(it.date) to it.value }
    val stepsDaily = activity.stepsDaily.associate { LocalDate.parse(it.date) to it.value }
    val stressDaily = stress.daily.associate { LocalDate.parse(it.date) to it.value }

    return listOfNotNull(
        correlationItem("Durée de sommeil et FC de repos, le même jour", sleepHours, restingDaily, lagDays = 0),
        correlationItem("Durée de sommeil et variabilité cardiaque", sleepHours, hrvDaily, lagDays = 0),
        correlationItem("Pas de la veille vers la durée de sommeil", stepsDaily, sleepHours, lagDays = 1),
        correlationItem("Pas de la veille vers le score de sommeil", stepsDaily, sleepScore, lagDays = 1),
        correlationItem("Heure de coucher et durée de sommeil", bedtime, sleepHours, lagDays = 0),
        correlationItem("Durée de sommeil vers le stress du lendemain", sleepHours, stressDaily, lagDays = 1),
    )
}

private fun correlationItem(
    label: String,
    a: Map<LocalDate, Double?>,
    b: Map<LocalDate, Double?>,
    lagDays: Int,
): CorrelationItem? {
    val pairs = Correlation.paired(a, b, lagDays)
    val r = Correlation.pearson(pairs) ?: return null
    return CorrelationItem(label = label, r = r, n = pairs.size)
}
