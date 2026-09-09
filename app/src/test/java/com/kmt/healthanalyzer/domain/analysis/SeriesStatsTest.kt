package com.kmt.healthanalyzer.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeriesStatsTest {

    // --- rollingMean -----------------------------------------------------

    @Test
    fun `stays null until the window holds enough samples`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val rolling = SeriesStats.rollingMean(values, window = 3, minSamples = 3)

        assertNull(rolling[0])
        assertNull(rolling[1])
        assertEquals(2.0, rolling[2]!!, 0.0001) // moyenne de 1,2,3
        assertEquals(3.0, rolling[3]!!, 0.0001) // moyenne de 2,3,4
        assertEquals(4.0, rolling[4]!!, 0.0001) // moyenne de 3,4,5
    }

    @Test
    fun `ignores nulls in the window without counting them as samples`() {
        val values = listOf(1.0, null, 3.0, null, 5.0)

        val rolling = SeriesStats.rollingMean(values, window = 3, minSamples = 2)

        // Fenêtre [1, null, 3] : deux valeurs réelles, moyenne 2.0.
        assertEquals(2.0, rolling[2]!!, 0.0001)
        // Fenêtre [null, 3, null] : une seule valeur réelle, sous le seuil.
        assertNull(rolling[3])
    }

    // --- percentile / median / mean / stdDev ------------------------------

    @Test
    fun `computes a percentile with the same convention as the resting heart rate`() {
        val values = (1..100).map { it.toDouble() }

        assertEquals(5.0, SeriesStats.percentile(values, 0.05)!!, 0.0001)
    }

    @Test
    fun `returns null percentile for an empty series`() {
        assertNull(SeriesStats.percentile(emptyList(), 0.5))
    }

    @Test
    fun `computes the median of an even and an odd series`() {
        assertEquals(3.0, SeriesStats.median(listOf(1.0, 5.0, 3.0, 2.0, 4.0))!!, 0.0001)
        assertEquals(2.5, SeriesStats.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 0.0001)
    }

    @Test
    fun `computes the mean of a series`() {
        assertEquals(3.0, SeriesStats.mean(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, 0.0001)
        assertNull(SeriesStats.mean(emptyList()))
    }

    @Test
    fun `computes the population standard deviation of a series`() {
        // Écart-type de population (division par n) de 2, 4, 4, 4, 5, 5, 7, 9 : moyenne 5,
        // variance 32/8 = 4, écart-type 2. Le rapport décrit l'ensemble observé, il
        // n'estime pas une population plus large : jamais de division par n - 1.
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)

        assertEquals(2.0, SeriesStats.stdDev(values)!!, 0.0001)
    }

    @Test
    fun `returns zero standard deviation for a single value, and null only for an empty series`() {
        assertEquals(0.0, SeriesStats.stdDev(listOf(4.0))!!, 0.0001)
        assertNull(SeriesStats.stdDev(emptyList()))
    }

    // --- byDayOfWeek -------------------------------------------------------

    @Test
    fun `averages by day of week with french short labels in monday-first order`() {
        // Lundi 2026-03-02, mardi 2026-03-03, ...
        val values = mapOf(
            LocalDate.of(2026, 3, 2) to 10.0, // lundi
            LocalDate.of(2026, 3, 9) to 20.0, // lundi
            LocalDate.of(2026, 3, 3) to 5.0, // mardi
        )

        val byDay = SeriesStats.byDayOfWeek(values)

        assertEquals(7, byDay.size)
        assertEquals(listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."), byDay.map { it.label })
        assertEquals(15.0, byDay[0].value!!, 0.0001) // lundi : (10+20)/2
        assertEquals(5.0, byDay[1].value!!, 0.0001) // mardi
        assertNull(byDay[2].value) // mercredi : aucune donnée
        assertEquals(2, byDay[0].count)
    }

    @Test
    fun `ignores null values when averaging by day of week`() {
        val values = mapOf(
            LocalDate.of(2026, 3, 2) to 10.0,
            LocalDate.of(2026, 3, 9) to null,
        )

        val byDay = SeriesStats.byDayOfWeek(values)

        assertEquals(10.0, byDay[0].value!!, 0.0001)
        assertEquals(1, byDay[0].count)
    }

    // --- byMonth -------------------------------------------------------

    @Test
    fun `averages by month with an ISO yyyy-MM key`() {
        val values = mapOf(
            LocalDate.of(2026, 3, 1) to 10.0,
            LocalDate.of(2026, 3, 15) to 20.0,
            LocalDate.of(2026, 4, 1) to 30.0,
        )

        val byMonth = SeriesStats.byMonth(values)

        assertEquals(listOf("2026-03", "2026-04"), byMonth.map { it.month })
        assertEquals(15.0, byMonth[0].value!!, 0.0001)
        assertEquals(30.0, byMonth[1].value!!, 0.0001)
    }

    // --- byHour -------------------------------------------------------

    @Test
    fun `averages by local hour across the full 0 to 23 range`() {
        val samples = listOf(8 to 60.0, 8 to 80.0, 22 to 40.0)

        val byHour = SeriesStats.byHour(samples)

        assertEquals(24, byHour.size)
        assertEquals(70.0, byHour[8].value!!, 0.0001)
        assertEquals(40.0, byHour[22].value!!, 0.0001)
        assertNull(byHour[0].value)
    }

    @Test
    fun `labels the hour without a leading zero or a suffix, so the drawing engine can parse it as a number`() {
        val byHour = SeriesStats.byHour(listOf(8 to 60.0))

        assertEquals("0", byHour[0].label)
        assertEquals("8", byHour[8].label)
        assertEquals("23", byHour[23].label)
    }

    // --- distribution -------------------------------------------------------

    @Test
    fun `counts values per caller-provided bucket`() {
        val buckets = listOf(
            SeriesStats.Bucket("< 6 h", Double.NEGATIVE_INFINITY, 360.0),
            SeriesStats.Bucket("6-8 h", 360.0, 480.0),
            SeriesStats.Bucket("> 8 h", 480.0, Double.POSITIVE_INFINITY),
        )
        val values = listOf(300.0, 400.0, 420.0, 500.0)

        val distribution = SeriesStats.distribution(values, buckets)

        assertEquals(listOf("< 6 h", "6-8 h", "> 8 h"), distribution.map { it.label })
        assertEquals(1, distribution[0].count)
        assertEquals(2, distribution[1].count)
        assertEquals(1, distribution[2].count)
    }
}
