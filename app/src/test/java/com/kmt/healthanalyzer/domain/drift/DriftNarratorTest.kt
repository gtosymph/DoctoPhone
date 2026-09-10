package com.kmt.healthanalyzer.domain.drift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DriftNarratorTest {

    private val shift = MetricDrift(
        metric = DriftMetric.RESTING_HEART_RATE,
        kind = DriftKind.MEAN_SHIFT,
        baselineMean = 54.0,
        baselineStdDev = 1.2,
        baselineDays = 30,
        recentMean = 59.0,
        recentStdDev = 1.1,
        recentDays = 6,
        signalStrength = 3.4,
    )

    private val spread = MetricDrift(
        metric = DriftMetric.SLEEP_REGULARITY,
        kind = DriftKind.SPREAD_INCREASE,
        baselineMean = -1.0,
        baselineStdDev = 0.4,
        baselineDays = 30,
        recentMean = -0.5,
        recentStdDev = 1.8,
        recentDays = 6,
        signalStrength = 4.5,
    )

    @Test
    fun `decrit un deplacement de moyenne avec les deux chiffres`() {
        val text = DriftNarrator.describe(shift)

        assertEquals(
            "Fréquence cardiaque de repos : 54 bpm en moyenne ces dernières semaines, 59 bpm sur la semaine écoulée.",
            text,
        )
    }

    @Test
    fun `decrit une dispersion accrue avec les deux ecarts-types`() {
        val text = DriftNarrator.describe(spread)

        assertEquals(
            "Régularité du coucher : l'écart type était de 0,4 h ces dernières semaines, il est de 1,8 h " +
                "sur la semaine écoulée — le coucher varie davantage que d'habitude.",
            text,
        )
    }

    @Test
    fun `ne contient aucun vocabulaire medical ou alarmant`() {
        val forbidden = listOf("risque", "danger", "attention", "diagnostic", "maladie", "inquiét", "grave", "urgent")

        val texts = listOf(DriftNarrator.describe(shift), DriftNarrator.describe(spread))

        texts.forEach { text ->
            forbidden.forEach { word ->
                assertFalse("« $text » ne doit pas contenir « $word »", text.contains(word, ignoreCase = true))
            }
        }
    }
}
