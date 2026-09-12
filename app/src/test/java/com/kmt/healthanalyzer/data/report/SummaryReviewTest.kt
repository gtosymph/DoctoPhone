package com.kmt.healthanalyzer.data.report

import com.kmt.healthanalyzer.domain.drift.DayPoint
import com.kmt.healthanalyzer.domain.drift.DriftDetector
import com.kmt.healthanalyzer.domain.drift.DriftKind
import com.kmt.healthanalyzer.domain.drift.DriftMetric
import com.kmt.healthanalyzer.domain.drift.MetricDrift
import com.kmt.healthanalyzer.domain.drift.MetricReview
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste le bloc hebdomadaire de la synthèse remise au médecin.
 *
 * ## Pourquoi ce type existe
 *
 * Les dérives sont calculées en Kotlin ([com.kmt.healthanalyzer.domain.drift]), et la
 * synthèse est mise en page en JavaScript, comme le reste du rapport. Plutôt que de porter
 * le moteur de dérives une seconde fois — une logique de santé en double finit toujours par
 * diverger — **Kotlin calcule et formate, JavaScript se contente de poser des chaînes**.
 * Ce type est ce qui passe la frontière : que des textes déjà prêts, aucun nombre à
 * réinterpréter de l'autre côté.
 *
 * Conséquence assumée : la version web de l'app, qui n'a pas de moteur de dérives, produit
 * une synthèse sans ce bloc. Le gabarit le prévoit et se contente de l'omettre.
 */
class SummaryReviewTest {

    private val today = LocalDate.of(2026, 9, 11)

    @Test
    fun `les six mesures traversent la frontiere deja formatees`() {
        val summary = SummaryReview.from(reviewWith(sleepRecent = 5.2, sleepBaseline = 6.33))
        val sleep = summary.metrics.first { it.label == DriftMetric.SLEEP_DURATION.label }

        assertEquals(
            "JavaScript ne doit avoir aucun nombre à formater : les conventions françaises " +
                "— virgule décimale, espace fine, durées en 6 h 20 — vivent dans TileFormat, " +
                "d'un seul côté.",
            "5 h 12",
            sleep.recent,
        )
        assertEquals("6 h 20", sleep.baseline)
        assertEquals("−1 h 08", sleep.delta)
    }

    @Test
    fun `les six mesures sont toujours la, meme sans donnee`() {
        val summary = SummaryReview.from(reviewWith())

        assertEquals(DriftMetric.entries.size, summary.metrics.size)
        summary.metrics.forEach { row ->
            assertEquals("Une mesure sans donnée garde sa ligne et montre un tiret.", "—", row.recent)
            assertNull(row.delta)
        }
    }

    @Test
    fun `une semaine trouee porte le nombre de jours mesures`() {
        val review = reviewWith(sleepRecent = 5.2, sleepBaseline = 6.33, sleepRecentDays = 4)
        val sleep = SummaryReview.from(review).metrics.first { it.label == DriftMetric.SLEEP_DURATION.label }

        assertEquals(
            "Un médecin doit pouvoir juger sur quoi repose le chiffre qu'on lui montre.",
            "4 jours mesurés sur 7",
            sleep.note,
        )
    }

    @Test
    fun `une semaine complete ne porte aucune note`() {
        val review = reviewWith(sleepRecent = 5.2, sleepBaseline = 6.33, sleepRecentDays = 7)
        val sleep = SummaryReview.from(review).metrics.first { it.label == DriftMetric.SLEEP_DURATION.label }

        assertNull("Sept jours sur sept se passe de commentaire.", sleep.note)
    }

    @Test
    fun `les derives arrivent en phrases, pas en chiffres bruts`() {
        val drift = MetricDrift(
            metric = DriftMetric.RESTING_HEART_RATE,
            kind = DriftKind.MEAN_SHIFT,
            baselineMean = 54.0, baselineStdDev = 2.0, baselineDays = 56,
            recentMean = 59.0, recentStdDev = 2.4, recentDays = 7, signalStrength = 2.6,
        )
        val summary = SummaryReview.from(reviewWith(drift = drift))

        assertEquals(1, summary.drifts.size)
        assertTrue(
            "La phrase doit venir de DriftNarrator, la même que l'écran et la notification. " +
                "Reçu : ${summary.drifts.first()}",
            summary.drifts.first().startsWith(DriftMetric.RESTING_HEART_RATE.label),
        )
    }

    @Test
    fun `l'etat de comparaison traverse aussi, pour expliquer un bloc vide`() {
        val summary = SummaryReview.from(reviewWith(baselineDays = 12))

        assertEquals("baseline", summary.readinessKind)
        assertEquals(12, summary.readinessMeasuredDays)
        assertEquals(DriftDetector.MIN_BASELINE_MEASURED_DAYS, summary.readinessRequiredDays)
    }

    @Test
    fun `la periode se lit en toutes lettres`() {
        val summary = SummaryReview.from(reviewWith())

        assertEquals("du 5 au 11 septembre 2026", summary.periodLabel)
    }

    @Test
    fun `rien de ce qui traverse ne vient d'un modele de langage`() {
        val summary = SummaryReview.from(reviewWith(sleepRecent = 5.2, sleepBaseline = 6.33))
        val texte = (summary.metrics.flatMap { listOfNotNull(it.label, it.recent, it.baseline, it.delta, it.note) } +
            summary.drifts + summary.periodLabel).joinToString(" ")

        // Un document remis à un médecin ne porte que des chiffres mesurés et des phrases
        // calculées. Le bilan rédigé reste dans le rapport complet, où l'utilisateur sait
        // d'où il vient.
        listOf("il semble", "probablement", "je recommande", "vous devriez").forEach { tournure ->
            assertFalse(
                "Une tournure d'interprétation a traversé : « $tournure »",
                texte.lowercase().contains(tournure),
            )
        }
    }

    // ------------------------------------------------------------------ outils

    private fun reviewWith(
        sleepRecent: Double? = null,
        sleepBaseline: Double? = null,
        sleepRecentDays: Int = 0,
        baselineDays: Int = 56,
        drift: MetricDrift? = null,
    ) = WeeklyReview(
        generatedAt = Instant.parse("2026-09-11T06:00:00Z"),
        from = today.minusDays(6),
        to = today,
        metrics = DriftMetric.entries.map { metric ->
            val isSleep = metric == DriftMetric.SLEEP_DURATION
            MetricReview(
                metric = metric,
                recentValue = if (isSleep) sleepRecent else null,
                baselineValue = if (isSleep) sleepBaseline else null,
                recentDays = if (isSleep) sleepRecentDays else 0,
                baselineDays = baselineDays,
                lastMeasuredOn = null,
                week = (0..6).map { DayPoint(today.minusDays((6 - it).toLong()), null) },
                drift = if (drift != null && metric == drift.metric) drift else null,
            )
        },
    )
}
