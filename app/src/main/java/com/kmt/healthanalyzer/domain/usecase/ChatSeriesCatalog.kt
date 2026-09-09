package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.domain.report.ReportModel

/**
 * Catalogue des séries que la conversation peut proposer de tracer.
 *
 * Reflet, côté Kotlin, du catalogue JavaScript `web/report/chart-catalog.js` : mêmes
 * clés `ref`, mêmes libellés, même seuil de disponibilité (au moins deux points). Les
 * deux catalogues doivent rester alignés — toute série ajoutée d'un côté doit l'être de
 * l'autre, sinon le modèle proposerait une référence que le moteur de dessin ne connaît
 * pas, ou inversement une série dessinable resterait inconnue du modèle.
 *
 * Seuls la clé, le libellé et l'unité sortent de l'appareil dans le prompt système,
 * jamais une valeur : ce fichier ne fait que compter des points pour savoir si une série
 * mérite d'être annoncée, il n'en lit jamais le contenu.
 */
private data class SeriesCatalogEntry(
    val ref: String,
    val label: String,
    val unit: String,
    val count: (ReportModel) -> Int,
)

private val SERIES_CATALOG: List<SeriesCatalogEntry> = listOf(
    // ---------------------------------------------------------------- sommeil
    SeriesCatalogEntry("sleep.nightly.hours", "Durée de sommeil par nuit", "heures") { it.sleep.nightly.size },
    SeriesCatalogEntry("sleep.nightly.score", "Score de sommeil par nuit", "/100") {
        it.sleep.nightly.count { night -> night.score != null }
    },
    SeriesCatalogEntry("sleep.nightly.bedRel", "Heure de coucher par nuit", "heure") {
        it.sleep.nightly.count { night -> night.bedRel != null }
    },
    SeriesCatalogEntry("sleep.nightly.efficiency", "Efficacité du sommeil par nuit", "%") {
        it.sleep.nightly.count { night -> night.efficiencyPercent != null }
    },
    SeriesCatalogEntry("sleep.monthly.meanHours", "Durée moyenne de sommeil par mois", "heures") {
        it.sleep.monthly.size
    },
    SeriesCatalogEntry("sleep.monthly.meanScore", "Score de sommeil moyen par mois", "/100") {
        it.sleep.monthly.count { month -> month.meanScore != null }
    },
    SeriesCatalogEntry("sleep.dayOfWeek", "Durée de sommeil par jour de semaine", "heures") {
        it.sleep.dayOfWeek.size
    },
    SeriesCatalogEntry("sleep.distribution", "Nombre de nuits par tranche de durée", "nuits") {
        it.sleep.distribution.size
    },

    // ---------------------------------------------------------------- coeur
    SeriesCatalogEntry("heart.restingDaily", "Fréquence cardiaque de repos par jour", "bpm") {
        it.heart.restingDaily.size
    },
    SeriesCatalogEntry("heart.hrvDaily", "Variabilité cardiaque par jour", "ms") { it.heart.hrvDaily.size },
    SeriesCatalogEntry("heart.hourly", "Fréquence cardiaque par heure", "bpm") { it.heart.hourly.size },
    SeriesCatalogEntry("heart.monthly.resting", "FC de repos par mois", "bpm") {
        it.heart.monthly.count { month -> month.resting != null }
    },
    SeriesCatalogEntry("heart.monthly.average", "FC moyenne par mois", "bpm") {
        it.heart.monthly.count { month -> month.average != null }
    },
    SeriesCatalogEntry("heart.hrvMonthly", "Variabilité cardiaque par mois", "ms") { it.heart.hrvMonthly.size },

    // ---------------------------------------------------------------- activite
    SeriesCatalogEntry("activity.stepsDaily", "Pas par jour", "pas") { it.activity.stepsDaily.size },
    SeriesCatalogEntry("activity.stepsRolling7", "Pas, moyenne glissante sur 7 jours", "pas") {
        it.activity.stepsRolling7.size
    },
    SeriesCatalogEntry("activity.stepsMonthly", "Pas par jour, moyenne mensuelle", "pas") {
        it.activity.stepsMonthly.size
    },
    SeriesCatalogEntry("activity.stepsDayOfWeek", "Pas par jour de semaine", "pas") {
        it.activity.stepsDayOfWeek.size
    },
    SeriesCatalogEntry("activity.floorsMonthly", "Étages par jour, moyenne mensuelle", "étages") {
        it.activity.floorsMonthly.size
    },
    SeriesCatalogEntry("activity.exerciseMonthly.minutes", "Minutes d'exercice par mois", "min") {
        it.activity.exerciseMonthly.size
    },

    // ---------------------------------------------------------------- corps
    SeriesCatalogEntry("body.weightKg", "Poids", "kg") { it.body.daily.size },
    SeriesCatalogEntry("body.bodyFatPercent", "Masse grasse", "%") {
        it.body.daily.count { day -> day.bodyFatPercent != null }
    },
    SeriesCatalogEntry("body.skeletalMuscleKg", "Masse musculaire squelettique", "kg") {
        it.body.daily.count { day -> day.skeletalMuscleKg != null }
    },
    SeriesCatalogEntry("body.bodyMassIndex", "Indice de masse corporelle", "") {
        it.body.daily.count { day -> day.bodyMassIndex != null }
    },

    // ---------------------------------------------------------------- stress
    SeriesCatalogEntry("stress.daily", "Stress par jour", "/100") { it.stress.daily.size },
    SeriesCatalogEntry("stress.monthly.mean", "Stress moyen par mois", "/100") { it.stress.monthly.size },
    SeriesCatalogEntry("stress.monthly.percentAbove60", "Part du temps au-dessus de 60, par mois", "%") {
        it.stress.monthly.size
    },
    SeriesCatalogEntry("stress.hourly", "Stress par heure", "/100") { it.stress.hourly.size },
    SeriesCatalogEntry("stress.dayOfWeek", "Stress par jour de semaine", "/100") { it.stress.dayOfWeek.size },
    SeriesCatalogEntry("stress.vitalityDaily", "Score de vitalité par jour", "/100") {
        it.stress.vitalityDaily.size
    },

    // ---------------------------------------------------------------- respiration
    SeriesCatalogEntry("breathing.spo2Daily", "Oxygénation par jour", "%") { it.breathing.spo2Daily.size },
    SeriesCatalogEntry("breathing.spo2Monthly.mean", "Oxygénation moyenne par mois", "%") {
        it.breathing.spo2Monthly.size
    },
    SeriesCatalogEntry("breathing.respiratoryDaily", "Fréquence respiratoire par nuit", "/min") {
        it.breathing.respiratoryDaily.size
    },
    SeriesCatalogEntry("breathing.skinTempDaily", "Température cutanée par nuit", "°C") {
        it.breathing.skinTempDaily.size
    },
)

/**
 * Décrit, en texte, les séries que le rapport rend disponibles — au moins deux points,
 * comme `chart-catalog.js` l'exige côté dessin. Une ligne par série : sa clé `ref`, son
 * libellé, son unité. Le modèle recopie la clé dans le bloc `healthchart` qu'il écrit ;
 * il ne recopie jamais une valeur, qui ne lui est de toute façon jamais donnée.
 */
/**
 * Les clés du catalogue, dans l'ordre de déclaration.
 *
 * Sert au test de parité avec `web/report/chart-catalog.js` : les deux catalogues doivent
 * déclarer exactement les mêmes séries, sans quoi le modèle se verrait annoncer une série
 * que l'application refuserait ensuite de dessiner.
 */
fun catalogRefs(): List<String> = SERIES_CATALOG.map { it.ref }

fun describeAvailableSeries(report: ReportModel): String {
    val available = SERIES_CATALOG.filter { it.count(report) >= 2 }
    if (available.isEmpty()) return "Aucune série n'est disponible pour cette période."
    return available.joinToString("\n") { entry ->
        val unitSuffix = if (entry.unit.isBlank()) "" else " (${entry.unit})"
        "- ${entry.ref} — ${entry.label}$unitSuffix"
    }
}
