package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.report.CorrelationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CorrelationTest {

    private fun day(n: Int) = LocalDate.of(2026, 3, n)

    // --- pearson -------------------------------------------------------------

    @Test
    fun `computes a perfect positive correlation`() {
        val pairs = (1..CorrelationItem.MIN_PAIRS).map { it.toDouble() to it.toDouble() }

        assertEquals(1.0, Correlation.pearson(pairs)!!, 0.0001)
    }

    @Test
    fun `computes a perfect negative correlation`() {
        val pairs = (1..CorrelationItem.MIN_PAIRS).map { it.toDouble() to (100 - it).toDouble() }

        assertEquals(-1.0, Correlation.pearson(pairs)!!, 0.0001)
    }

    @Test
    fun `returns null below the minimum number of pairs`() {
        val pairs = (1 until CorrelationItem.MIN_PAIRS).map { it.toDouble() to it.toDouble() }

        assertNull(Correlation.pearson(pairs))
    }

    @Test
    fun `returns null when a series has no variance`() {
        val pairs = (1..CorrelationItem.MIN_PAIRS).map { it.toDouble() to 42.0 }

        assertNull(Correlation.pearson(pairs))
    }

    // --- paired ----------------------------------------------------------------

    @Test
    fun `pairs two daily series on matching dates only`() {
        val a = mapOf(day(1) to 1.0, day(2) to 2.0, day(3) to 3.0)
        val b = mapOf(day(1) to 10.0, day(3) to 30.0)

        val pairs = Correlation.paired(a, b)

        assertEquals(listOf(1.0 to 10.0, 3.0 to 30.0), pairs)
    }

    @Test
    fun `drops a day when either series holds a null value`() {
        val a = mapOf(day(1) to 1.0, day(2) to null)
        val b = mapOf(day(1) to 10.0, day(2) to 20.0)

        assertEquals(listOf(1.0 to 10.0), Correlation.paired(a, b))
    }

    @Test
    fun `applies a positive lag to look up the other series after the reference date`() {
        // Pas de la veille (jour 1 et 2) vers le sommeil du lendemain (jour 2 et 3).
        val steps = mapOf(day(1) to 8000.0, day(2) to 3000.0)
        val sleep = mapOf(day(2) to 400.0, day(3) to 300.0)

        val pairs = Correlation.paired(steps, sleep, lagDays = 1)

        assertEquals(listOf(8000.0 to 400.0, 3000.0 to 300.0), pairs)
    }

    @Test
    fun `applies a negative lag to look up the other series before the reference date`() {
        val a = mapOf(day(2) to 400.0, day(3) to 350.0)
        val b = mapOf(day(1) to 30.0) // seul jour(2) - 1 existe dans b

        val pairs = Correlation.paired(a, b, lagDays = -1)

        assertEquals(listOf(400.0 to 30.0), pairs)
    }
}
