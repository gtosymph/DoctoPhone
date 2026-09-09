package com.kmt.healthanalyzer.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircularStatsTest {

    @Test
    fun `mean of identical hours is that hour`() {
        val hours = listOf(23.0, 23.0, 23.0)

        assertEquals(23.0, CircularStats.mean(hours)!!, 0.01)
    }

    @Test
    fun `mean treats hours across midnight as a continuous scale`() {
        // 23 h et 1 h sont distants de 2 h, pas de 22 h : la moyenne doit tomber à minuit.
        val hours = listOf(23.0, 1.0)

        assertEquals(0.0, CircularStats.mean(hours)!!, 0.01)
    }

    @Test
    fun `standard deviation is zero when every hour is identical`() {
        val hours = listOf(23.0, 23.0, 23.0)

        assertEquals(0.0, CircularStats.stdDevHours(hours)!!, 0.01)
    }

    @Test
    fun `standard deviation uses sqrt(-2 ln R) over two pi times 24`() {
        // Deux points symétriques autour de minuit, à une heure d'écart chacun.
        // R = cos(15°) = 0.96593 ; sigma = sqrt(-2 ln R) / (2 pi) * 24 ~= 1.006 h.
        val hours = listOf(23.0, 1.0)

        assertEquals(1.006, CircularStats.stdDevHours(hours)!!, 0.01)
    }

    @Test
    fun `standard deviation stays zero, never NaN, over many identical hours`() {
        // Sommer des centaines de sinus et cosinus identiques peut pousser la longueur
        // du vecteur résultant très légèrement au-delà de 1.0 par bruit d'arrondi, ce
        // qui rend ln(R) positif et sqrt(-2 ln R) NaN sans un plafond à 1.0.
        val hours = List(730) { 23.0 }

        val spread = CircularStats.stdDevHours(hours)!!

        assertEquals(0.0, spread, 0.0001)
        assertTrue(!spread.isNaN())
    }

    @Test
    fun `returns null for an empty series`() {
        assertNull(CircularStats.mean(emptyList()))
        assertNull(CircularStats.stdDevHours(emptyList()))
        assertNull(CircularStats.circularMedian(emptyList()))
    }

    @Test
    fun `median recentered around 18h keeps bedtimes across midnight together`() {
        // Couchers dispersés de part et d'autre de minuit : une médiane linéaire
        // séparerait 23 h et 1 h par 22 h ; la médiane circulaire recentrée les garde
        // proches.
        val hours = listOf(22.5, 23.0, 23.5, 0.5, 1.0)

        val median = CircularStats.circularMedian(hours)!!

        // La médiane linéaire de cette liste triée est 23.5 ; recentrée, elle doit
        // rester proche de minuit, pas être tirée vers midi.
        assertTrue(median in 23.0..24.5 || median in 0.0..1.0)
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}
