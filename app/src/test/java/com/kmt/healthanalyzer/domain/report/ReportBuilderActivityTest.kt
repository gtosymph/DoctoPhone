package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderActivityTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int): Instant = day(n).atTime(hour, 0).toInstant(zone)

    @Test
    fun `carries the daily step count as a series`() {
        val steps = listOf(DailySteps(day(1), 8000), DailySteps(day(2), 3000))

        val daily = builder.build(ReportInput(range = day(1)..day(2), dailySteps = steps)).activity.stepsDaily

        assertEquals(listOf(8000.0, 3000.0), daily.map { it.value })
    }

    @Test
    fun `smooths steps with a seven day rolling mean that stays null until enough samples`() {
        val steps = (1..10).map { DailySteps(day(it), steps = 1000 * it) }

        val rolling = builder.build(ReportInput(range = day(1)..day(10), dailySteps = steps)).activity.stepsRolling7

        assertNull(rolling[0].value)
        // Fenêtre des jours 1 à 7 : moyenne de 1000..7000 = 4000.
        assertEquals(4000.0, rolling[6].value!!, 0.01)
    }

    @Test
    fun `finds the best step day of the period`() {
        val steps = listOf(DailySteps(day(1), 4000), DailySteps(day(2), 12000), DailySteps(day(3), 6000))

        val kpi = builder.build(ReportInput(range = day(1)..day(3), dailySteps = steps)).activity.kpi

        assertEquals(12000, kpi.bestSteps)
        assertEquals("2026-03-02", kpi.bestStepsDate)
    }

    @Test
    fun `flags the share of very low and very high step days`() {
        val steps = listOf(
            DailySteps(day(1), 1000), // < 3000
            DailySteps(day(2), 9000), // >= 8000
            DailySteps(day(3), 5000), // ni l'un ni l'autre
            DailySteps(day(4), 2000), // < 3000
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(4), dailySteps = steps)).activity.kpi

        assertEquals(50.0, kpi.pctDaysUnder3000!!, 0.01)
        assertEquals(25.0, kpi.pctDaysOver8000!!, 0.01)
    }

    @Test
    fun `sums the exercise minutes and counts the sessions of the period`() {
        val sessions = listOf(
            ExerciseSession(
                id = "e-1", kind = ExerciseKind.RUNNING, samsungTypeCode = null,
                start = at(1, 7), end = at(1, 7).plusSeconds(1800), durationMinutes = 30, calories = 250f,
            ),
            ExerciseSession(
                id = "e-2", kind = ExerciseKind.WALKING, samsungTypeCode = null,
                start = at(2, 7), end = at(2, 7).plusSeconds(3600), durationMinutes = 60, calories = 200f,
            ),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(2), exerciseSessions = sessions)).activity.kpi

        assertEquals(90.0, kpi.totalExerciseMinutes!!, 0.01)
        assertEquals(2, kpi.exerciseSessions)
    }

    @Test
    fun `groups exercise minutes by french kind label, sorted alphabetically on the label`() {
        val sessions = listOf(
            ExerciseSession("e-1", ExerciseKind.RUNNING, null, at(1, 7), at(1, 7).plusSeconds(1800), 30),
            ExerciseSession("e-2", ExerciseKind.RUNNING, null, at(2, 7), at(2, 7).plusSeconds(1200), 20),
            ExerciseSession("e-3", ExerciseKind.YOGA, null, at(3, 7), at(3, 7).plusSeconds(2400), 40),
        )

        val byKind = builder.build(ReportInput(range = day(1)..day(3), exerciseSessions = sessions)).activity.exerciseByKind

        assertEquals(listOf("Course", "Yoga"), byKind.map { it.label })
        val running = byKind.first { it.label == "Course" }
        assertEquals(50.0, running.value!!, 0.01)
        assertEquals(2, running.count)
        assertEquals(40.0, byKind.first { it.label == "Yoga" }.value!!, 0.01)
    }

    @Test
    fun `sums the exercise calories of a month, not their average per session`() {
        val sessions = listOf(
            ExerciseSession("e-1", ExerciseKind.RUNNING, null, at(1, 7), at(1, 7).plusSeconds(1800), 30, calories = 250f),
            ExerciseSession("e-2", ExerciseKind.WALKING, null, at(2, 7), at(2, 7).plusSeconds(3600), 60, calories = 200f),
        )

        val month = builder.build(ReportInput(range = day(1)..day(2), exerciseSessions = sessions)).activity.exerciseMonthly.single()

        assertEquals(450.0, month.calories!!, 0.01) // 250 + 200, pas (250+200)/2
    }
}
