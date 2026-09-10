package com.kmt.healthanalyzer.domain.drift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftDetectorTest {

    // Huit semaines de référence à fréquence cardiaque de repos stable (54 ± ~1 bpm),
    // suivies d'une semaine récente nettement plus haute (59 ± ~1 bpm). L'écart est
    // largement supérieur au bruit habituel de cette personne.
    private val stableBaseline = List(DriftDetector.MIN_BASELINE_MEASURED_DAYS) { index ->
        54.0 + (index % 3 - 1) // oscille entre 53 et 55
    }

    @Test
    fun `une derive reelle est detectee`() {
        val recent = List(DriftDetector.MIN_RECENT_MEASURED_DAYS) { index -> 59.0 + (index % 3 - 1) }

        val drift = DriftDetector.detectShift(DriftMetric.RESTING_HEART_RATE, stableBaseline, recent)

        requireNotNull(drift)
        assertEquals(DriftKind.MEAN_SHIFT, drift.kind)
        assertTrue("le z-score doit dépasser le seuil", kotlin.math.abs(drift.signalStrength) >= DriftDetector.Z_SCORE_THRESHOLD)
        assertTrue(drift.recentMean > drift.baselineMean)
    }

    @Test
    fun `une variation dans le bruit habituel n'est pas signalee`() {
        // La fréquence cardiaque de repos varie naturellement de plusieurs bpm chez cette
        // personne (bruit large) ; un déplacement de 1 bpm de la moyenne ne doit rien
        // déclencher.
        val noisyBaseline = listOf(50.0, 58.0, 52.0, 56.0, 51.0, 57.0, 53.0, 55.0, 50.0, 58.0,
            52.0, 56.0, 51.0, 57.0, 53.0, 55.0, 50.0, 58.0, 52.0, 56.0, 54.0)
        val recent = listOf(55.0, 53.0, 56.0, 52.0, 57.0)

        val drift = DriftDetector.detectShift(DriftMetric.RESTING_HEART_RATE, noisyBaseline, recent)

        assertNull(drift)
    }

    @Test
    fun `des donnees trop rares ne produisent aucune derive, cote reference`() {
        val sparseBaseline = List(DriftDetector.MIN_BASELINE_MEASURED_DAYS - 1) { 54.0 }
        val recent = List(DriftDetector.MIN_RECENT_MEASURED_DAYS) { 70.0 }

        val drift = DriftDetector.detectShift(DriftMetric.RESTING_HEART_RATE, sparseBaseline, recent)

        assertNull(drift)
    }

    @Test
    fun `des donnees trop rares ne produisent aucune derive, cote semaine recente`() {
        val recent = List(DriftDetector.MIN_RECENT_MEASURED_DAYS - 1) { 70.0 }

        val drift = DriftDetector.detectShift(DriftMetric.RESTING_HEART_RATE, stableBaseline, recent)

        assertNull(drift)
    }

    @Test
    fun `un trou de donnees ne se lit pas comme une baisse`() {
        // L'appelant (HealthDriftAnalyzer) ne doit jamais transformer un jour sans mesure
        // en zéro. Ce test vérifie le comportement du détecteur si on lui passait malgré
        // tout une liste truffée de zéros artificiels : ce n'est PAS son travail de les
        // filtrer, mais il ne doit pas non plus paniquer, et la baisse doit rester
        // proportionnée aux vraies valeurs, jamais amplifiée par les trous.
        // Le vrai test de la règle « trou != baisse » vit dans HealthDriftAnalyzerTest,
        // qui vérifie que les jours sans mesure n'entrent jamais dans les listes passées
        // ici. Ici, on vérifie seulement qu'une liste propre (sans trou) et stable ne
        // signale rien.
        val steadySteps = List(DriftDetector.MIN_BASELINE_MEASURED_DAYS) { 8000.0 }
        val recentSteps = List(DriftDetector.MIN_RECENT_MEASURED_DAYS) { 8000.0 }

        val drift = DriftDetector.detectShift(DriftMetric.STEPS, steadySteps, recentSteps)

        assertNull(drift)
    }

    @Test
    fun `regularite du coucher signale une dispersion en nette hausse`() {
        // Coucher très régulier en référence (toujours -1.0, soit 23h00), très dispersé la
        // semaine récente.
        val regularBaseline = List(DriftDetector.MIN_BASELINE_MEASURED_DAYS) { index ->
            -1.0 + (index % 2) * 0.2 // -1.0 / -0.8, écart-type minuscule
        }
        val irregularRecent = listOf(-3.0, 1.0, -0.5, 2.0, -2.5, 0.5, -1.0)

        val drift = DriftDetector.detectSpreadIncrease(DriftMetric.SLEEP_REGULARITY, regularBaseline, irregularRecent)

        requireNotNull(drift)
        assertEquals(DriftKind.SPREAD_INCREASE, drift.kind)
        assertTrue(drift.recentStdDev > drift.baselineStdDev)
    }

    @Test
    fun `regularite du coucher stable n'est pas signalee`() {
        val baseline = List(DriftDetector.MIN_BASELINE_MEASURED_DAYS) { index -> -1.0 + (index % 3 - 1) * 0.3 }
        val recent = listOf(-1.2, -0.8, -1.1, -0.9, -1.0, -1.3, -0.7)

        val drift = DriftDetector.detectSpreadIncrease(DriftMetric.SLEEP_REGULARITY, baseline, recent)

        assertNull(drift)
    }
}
