package com.kmt.healthanalyzer.domain.report

import kotlin.math.abs

/**
 * Les huit tuiles de synthèse en tête de rapport.
 *
 * Portage fidèle de `buildTiles` côté JavaScript (`web/lib/report-sections/tiles.js`) :
 * mêmes seuils, mêmes textes, même format — le test de parité les compare au caractère
 * près. Les seuils sont des repères usuels de bien-être, pas un avis médical ; ils sont
 * documentés sur chaque tuile pour être ajustés facilement.
 *
 * Sans donnée, une tuile porte `neutral` et la valeur [TileFormat.NO_DATA], jamais un
 * statut inventé.
 */
internal fun buildTiles(
    input: ReportInput,
    sleep: SleepSection,
    heart: HeartSection,
    activity: ActivitySection,
    body: BodySection,
    stress: StressSection,
): List<ReportTile> = listOf(
    sleepTile(sleep),
    bedtimeTile(sleep),
    restingHeartRateTile(heart),
    hrvTile(heart),
    bodyMassIndexTile(body),
    steps30Tile(activity),
    bloodPressureTile(heart),
    stressTile(stress),
)

/** Trouve le premier seuil dont [thresholds] couvre [value] ; le dernier n'a pas de borne. */
private fun tierOf(value: Double, thresholds: List<Pair<Double?, String>>): String =
    thresholds.firstOrNull { (max, _) -> max == null || value <= max }?.second ?: "critical"

// --- Sommeil : 7-9 h recommandées pour un adulte (National Sleep Foundation). ---

private fun sleepTile(sleep: SleepSection): ReportTile {
    val meanHours = sleep.kpi.meanHours
        ?: return makeTile("sleep", "Sommeil", TileFormat.NO_DATA, null, "Aucune nuit mesurée", "neutral")

    val deviation = if (meanHours in 7.0..9.0) 0.0 else abs(meanHours - 8.0)
    val status = tierOf(deviation, listOf(0.0 to "good", 1.5 to "warn", 2.5 to "serious", null to "critical"))
    return makeTile(
        "sleep", "Sommeil", TileFormat.duration(meanHours), null,
        "${TileFormat.number(sleep.kpi.nights.toDouble(), 0)} nuits mesurées", status,
    )
}

// --- Régularité du coucher : écart-type circulaire sous 1 h très régulier, au-delà de 3 h très irrégulier. ---

private fun bedtimeTile(sleep: SleepSection): ReportTile {
    val spread = sleep.kpi.bedSpreadHours
    val median = sleep.kpi.bedMedian
    if (spread == null || median == null) {
        return makeTile("bedtime", "Coucher médian", TileFormat.NO_DATA, null, "Aucune donnée", "neutral")
    }
    val status = tierOf(spread, listOf(1.0 to "good", 2.0 to "warn", 3.0 to "serious", null to "critical"))
    return makeTile(
        "bedtime", "Coucher médian", TileFormat.clock(median), null,
        "± ${TileFormat.number(spread, 1)} h", status,
    )
}

// --- FC de repos : 60-100 bpm normal pour un adulte ; en dessous de 60, souvent le signe d'une bonne forme. ---

private fun restingHeartRateTile(heart: HeartSection): ReportTile {
    val value = heart.kpi.restingMean
        ?: return makeTile("restingHeartRate", "FC de repos", TileFormat.NO_DATA, "bpm", "Aucune donnée", "neutral")

    val status = tierOf(value, listOf(65.0 to "good", 75.0 to "warn", 90.0 to "serious", null to "critical"))
    return makeTile("restingHeartRate", "FC de repos", TileFormat.number(value, 0)!!, "bpm", "Moyenne sur la période", status)
}

// --- HRV (rMSSD) : très individuel, à lire en tendance ; au-delà de 50 ms confortable, sous 20 ms récupération réduite. ---

private fun hrvTile(heart: HeartSection): ReportTile {
    val value = heart.kpi.hrvMedian
        ?: return makeTile("hrv", "Variabilité cardiaque", TileFormat.NO_DATA, "ms", "Aucune donnée", "neutral")

    val status = when {
        value >= 50.0 -> "good"
        value >= 30.0 -> "warn"
        value >= 20.0 -> "serious"
        else -> "critical"
    }
    return makeTile("hrv", "Variabilité cardiaque", TileFormat.number(value, 0)!!, "ms", "Médiane RMSSD", status)
}

// --- IMC : catégories de l'Organisation mondiale de la santé. ---

private fun bodyMassIndexTile(body: BodySection): ReportTile {
    val value = body.kpi.bodyMassIndex
        ?: return makeTile("bodyMassIndex", "IMC", TileFormat.NO_DATA, null, "Aucune donnée", "neutral")

    val status = when {
        value >= 18.5 && value < 25.0 -> "good"
        (value >= 17.0 && value < 18.5) || (value >= 25.0 && value < 30.0) -> "warn"
        (value >= 16.0 && value < 17.0) || (value >= 30.0 && value < 35.0) -> "serious"
        else -> "critical"
    }
    return makeTile(
        "bodyMassIndex", "IMC", TileFormat.number(value, 1)!!, null,
        "Dernière mesure : ${TileFormat.date(body.kpi.lastMeasuredOn)}", status,
    )
}

// --- Pas sur 30 jours : 8 000-10 000 pas/jour associés à un bon niveau d'activité, sous 3 000 très sédentaire. ---

private fun steps30Tile(activity: ActivitySection): ReportTile {
    val value = activity.kpi.meanSteps30
        ?: return makeTile("steps30", "Pas (30 j)", TileFormat.NO_DATA, "pas/j", "Aucune donnée", "neutral")

    val status = when {
        value >= 8000.0 -> "good"
        value >= 5000.0 -> "warn"
        value >= 3000.0 -> "serious"
        else -> "critical"
    }
    return makeTile(
        "steps30", "Pas (30 j)", TileFormat.number(value, 0)!!, "pas/j",
        "Moyenne des 30 derniers jours", status,
    )
}

// --- Tension artérielle : catégories usuelles (American Heart Association). ---

private fun bloodPressureTile(heart: HeartSection): ReportTile {
    val last = heart.bloodPressure.lastOrNull()
        ?: return makeTile("bloodPressure", "Tension artérielle", TileFormat.NO_DATA, "mmHg", "Aucune donnée", "neutral")

    val status = when {
        last.systolic < 120 && last.diastolic < 80 -> "good"
        last.systolic < 140 && last.diastolic < 90 -> "warn"
        last.systolic < 180 && last.diastolic < 120 -> "serious"
        else -> "critical"
    }
    return makeTile(
        "bloodPressure", "Tension artérielle",
        "${TileFormat.number(last.systolic.toDouble(), 0)}/${TileFormat.number(last.diastolic.toDouble(), 0)}",
        "mmHg", "Dernière mesure : ${TileFormat.date(last.date)}", status,
    )
}

// --- Stress : échelle Samsung 0-100, plus élevé = plus stressé. ---

private fun stressTile(stress: StressSection): ReportTile {
    val value = stress.kpi.mean
        ?: return makeTile("stress", "Stress", TileFormat.NO_DATA, "/100", "Aucune donnée", "neutral")

    val status = when {
        value <= 30.0 -> "good"
        value <= 45.0 -> "warn"
        value <= 60.0 -> "serious"
        else -> "critical"
    }
    return makeTile("stress", "Stress", TileFormat.number(value, 0)!!, "/100", "Moyenne sur la période", status)
}

private val VALID_STATUSES = setOf("good", "warn", "serious", "critical", "neutral")

/** Retombe sur `neutral` si [status] n'est pas l'une des valeurs admises — comme `makeTile` côté web. */
private fun makeTile(key: String, label: String, value: String, unit: String?, sub: String, status: String): ReportTile =
    ReportTile(key = key, label = label, value = value, unit = unit, sub = sub, status = if (status in VALID_STATUSES) status else "neutral")
