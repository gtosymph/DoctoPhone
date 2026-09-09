package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.StressSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ReportBuilderCorrelationsTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 1, 1).plusDays(n.toLong())

    private fun night(n: Int, minutes: Int) = SleepNight(
        id = "n-$n",
        date = day(n),
        bedTime = day(n - 1).atTime(23, 0).toInstant(zone),
        wakeTime = day(n).atTime(7, 0).toInstant(zone),
        durationMinutes = minutes,
        localBedTime = LocalTime.of(23, 0),
    )

    @Test
    fun `finds no correlation below the minimum number of paired days`() {
        val nights = (1..10).map { night(it, minutes = 400 + it) }
        val heartRates = (1..10).flatMap { n ->
            (0..19).map { HeartRateSample("hr-$n-$it", day(n).atTime(3, it).toInstant(zone), 60) }
        }

        val correlations = builder.build(
            ReportInput(range = day(1)..day(10), sleepNights = nights, heartRates = heartRates),
        ).correlations

        assertTrue(correlations.none { it.label == "Durée de sommeil et FC de repos, le même jour" })
    }

    @Test
    fun `computes the yesterday steps to sleep duration correlation with a one day lag`() {
        val n = CorrelationItem.MIN_PAIRS + 5
        val nights = (1..n).map { night(it, minutes = 300 + it * 2) }
        val steps = (0 until n).map { DailySteps(day(it), steps = 3000 + it * 200) } // jour it, veille de la nuit it+1

        val correlations = builder.build(
            ReportInput(range = day(0)..day(n), sleepNights = nights, dailySteps = steps),
        ).correlations

        val item = correlations.single { it.label == "Pas de la veille vers la durée de sommeil" }
        assertEquals(1.0, item.r, 0.01) // les deux séries montent ensemble, décalées d'un jour
        assertEquals(n, item.n)
    }

    @Test
    fun `computes the same-day bedtime to sleep duration correlation`() {
        val n = CorrelationItem.MIN_PAIRS + 5
        val nights = (1..n).map {
            SleepNight(
                id = "n-$it", date = day(it),
                bedTime = day(it - 1).atTime(22 + (it % 2), 0).toInstant(zone),
                wakeTime = day(it).atTime(7, 0).toInstant(zone),
                durationMinutes = 400 - it,
                localBedTime = LocalTime.of(22 + (it % 2), 0),
            )
        }

        val correlations = builder.build(ReportInput(range = day(1)..day(n), sleepNights = nights)).correlations

        assertTrue(correlations.any { it.label == "Heure de coucher et durée de sommeil" })
    }

    @Test
    fun `counts nights without localBedTime in the bedtime correlation, via the bedTime fallback`() {
        // Une nuit Health Connect n'a pas localBedTime ; sans repli sur bedTime, elle
        // sortirait silencieusement de cette corrélation.
        val n = CorrelationItem.MIN_PAIRS + 5
        val nights = (1..n).map {
            SleepNight(
                id = "n-$it", date = day(it),
                bedTime = day(it - 1).atTime(22 + (it % 2), 0).toInstant(zone),
                wakeTime = day(it).atTime(7, 0).toInstant(zone),
                durationMinutes = 400 - it,
                localBedTime = null,
            )
        }

        val item = builder.build(ReportInput(range = day(1)..day(n), sleepNights = nights))
            .correlations.single { it.label == "Heure de coucher et durée de sommeil" }

        assertEquals(n, item.n)
    }

    @Test
    fun `computes the sleep to next day stress correlation with the correct temporal direction`() {
        val n = CorrelationItem.MIN_PAIRS + 5
        val nights = (0 until n).map { night(it, minutes = 300 + it * 3) } // nuit du jour i
        // Stress du jour i+1, corrélé à la durée de sommeil de la nuit i (déjà du jour i).
        val stress = (1..n).map { StressSample("s-$it", day(it).atTime(10, 0).toInstant(zone), day(it).atTime(11, 0).toInstant(zone), score = it) }

        val correlations = builder.build(
            ReportInput(range = day(0)..day(n), sleepNights = nights, stress = stress),
        ).correlations

        val item = correlations.single { it.label == "Durée de sommeil vers le stress du lendemain" }
        assertEquals(1.0, item.r, 0.01)
    }
}
