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
 *
 * Deux propriétés servent l'écran « Cette semaine » plutôt que la détection elle-même :
 *
 * [higherIsBetter] dit dans quel sens lire un écart. Il vaut `null` pour le poids, et ce
 * n'est pas un oubli : grossir ou maigrir n'est bon ou mauvais que dans un contexte que
 * cette app n'a pas. L'écart s'affiche, sans couleur de jugement. Pour la régularité du
 * coucher, la valeur mesurée est une dispersion — plus elle augmente, moins le coucher est
 * régulier — d'où `false`.
 *
 * [measuresSpread] dit quelle statistique représente la mesure : l'écart-type pour la
 * régularité du coucher, la moyenne pour tout le reste. C'est aussi ce qui choisit entre
 * [DriftDetector.detectSpreadIncrease] et [DriftDetector.detectShift]. *
 * [minRecentDaysToShow] et [minBaselineDaysToShow] disent à partir de combien de jours
 * mesurés une valeur mérite d'être **montrée**. Ils sont plus bas que les minimums de
 * [DriftDetector], et c'est délibéré : décrire une semaine (« 6 h 18 en moyenne sur 4 nuits
 * mesurées ») demande moins de preuves qu'affirmer un changement. L'écran montre toujours
 * le nombre de jours mesurés à côté, pour que la valeur ne paraisse pas plus solide qu'elle
 * ne l'est.
 */
enum class DriftMetric(
    val label: String,
    val higherIsBetter: Boolean?,
    val measuresSpread: Boolean = false,
    val minRecentDaysToShow: Int = 3,
    val minBaselineDaysToShow: Int = 12,
) {
    SLEEP_DURATION("Durée de sommeil", higherIsBetter = true) {
        override fun format(value: Double): String = TileFormat.duration(value)
    },

    /**
     * Écart-type des heures de coucher (en heures), pas la durée : voir [DriftKind.SPREAD_INCREASE].
     */
    SLEEP_REGULARITY("Régularité du coucher", higherIsBetter = false, measuresSpread = true) {
        override fun format(value: Double): String = "${number(value, 1)} h"
    },

    RESTING_HEART_RATE("Fréquence cardiaque de repos", higherIsBetter = false) {
        override fun format(value: Double): String = "${number(value, 0)} bpm"
    },

    HRV("Variabilité cardiaque (HRV)", higherIsBetter = true) {
        override fun format(value: Double): String = "${number(value, 1)} ms"
    },

    STEPS("Pas quotidiens", higherIsBetter = true) {
        override fun format(value: Double): String = "${number(value, 0)} pas"
    },

    /**
     * Le poids est la seule mesure **éparse** de cette liste : on se pèse une ou deux fois
     * par semaine, pas chaque nuit. Exiger trois pesées dans la semaine pour afficher un
     * poids ferait afficher un tiret presque toujours, alors qu'une seule pesée répond très
     * bien à « combien je pèse cette semaine ».
     *
     * Les seuils de *détection* de dérive, eux, ne bougent pas : affirmer qu'un poids a
     * changé demande toujours autant de points qu'ailleurs. Décrire et affirmer ne se
     * paient pas au même prix.
     */
    WEIGHT("Poids", higherIsBetter = null, minRecentDaysToShow = 1, minBaselineDaysToShow = 3) {
        override fun format(value: Double): String = "${number(value, 1)} kg"
    },
    ;

    abstract fun format(value: Double): String

    /** [TileFormat.number] ne rend `null` que pour NaN/infini, ce que ce chantier ne produit jamais. */
    protected fun number(value: Double, decimals: Int): String = TileFormat.number(value, decimals) ?: "—"
}
