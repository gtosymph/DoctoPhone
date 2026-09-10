package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.report.TileFormat

/**
 * Les mesures suivies par la détection de dérives.
 *
 * Chaque valeur porte son étiquette française et sait formater un nombre brut en texte
 * lisible ([format]) — même convention que [TileFormat] (virgule décimale, espace fine
 * pour les milliers), pour que le rapport et les dérives se lisent de la même façon.
 *
 * La liste couvre le minimum demandé pour ce chantier : durée et régularité du sommeil,
 * fréquence cardiaque de repos, variabilité cardiaque, pas quotidiens, poids. Toutes ces
 * valeurs existent déjà dans [com.kmt.healthanalyzer.domain.report.ReportModel] ; voir
 * [HealthDriftAnalyzer].
 */
enum class DriftMetric(val label: String) {
    SLEEP_DURATION("Durée de sommeil") {
        override fun format(value: Double): String = TileFormat.duration(value)
    },

    /**
     * Écart-type des heures de coucher (en heures), pas la durée : voir [DriftKind.SPREAD_INCREASE].
     */
    SLEEP_REGULARITY("Régularité du coucher") {
        override fun format(value: Double): String = "${number(value, 1)} h"
    },

    RESTING_HEART_RATE("Fréquence cardiaque de repos") {
        override fun format(value: Double): String = "${number(value, 0)} bpm"
    },

    HRV("Variabilité cardiaque (HRV)") {
        override fun format(value: Double): String = "${number(value, 1)} ms"
    },

    STEPS("Pas quotidiens") {
        override fun format(value: Double): String = "${number(value, 0)} pas"
    },

    WEIGHT("Poids") {
        override fun format(value: Double): String = "${number(value, 1)} kg"
    },
    ;

    abstract fun format(value: Double): String

    /** [TileFormat.number] ne rend `null` que pour NaN/infini, ce que ce chantier ne produit jamais. */
    protected fun number(value: Double, decimals: Int): String = TileFormat.number(value, decimals) ?: "—"
}
