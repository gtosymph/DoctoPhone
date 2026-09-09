package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.StressSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ReportBuilderTilesTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, 1).plusDays((n - 1).toLong())
    private fun at(n: Int, hour: Int): Instant = day(n).atTime(hour, 0).toInstant(zone)

    private val expectedKeys = listOf(
        "sleep", "bedtime", "restingHeartRate", "hrv", "bodyMassIndex", "steps30", "bloodPressure", "stress",
    )

    @Test
    fun `produces exactly the eight expected tiles in a stable order`() {
        val tiles = builder.build(ReportInput(range = day(1)..day(1))).tiles

        assertEquals(expectedKeys, tiles.map { it.key })
    }

    @Test
    fun `marks every tile neutral with a dash when there is no data at all`() {
        val tiles = builder.build(ReportInput(range = day(1)..day(1))).tiles

        tiles.forEach {
            assertEquals("neutral", it.status)
            assertEquals("—", it.value)
        }
    }

    @Test
    fun `flags a healthy average sleep duration as good`() {
        val nights = listOf(
            SleepNight("n-1", day(1), at(0, 23), at(1, 7), durationMinutes = 480, localBedTime = LocalTime.of(23, 0)),
        )

        val tile = builder.build(ReportInput(range = day(1)..day(1), sleepNights = nights)).tiles.first { it.key == "sleep" }

        assertEquals("good", tile.status)
        assertEquals("8 h 00", tile.value)
        assertNull(tile.unit) // valeur composée (une durée) : pas d'unité séparée
        assertEquals("1 nuits mesurées", tile.sub)
    }

    @Test
    fun `flags a severe sleep debt as critical`() {
        val nights = listOf(
            SleepNight("n-1", day(1), at(0, 1), at(1, 5), durationMinutes = 240, localBedTime = LocalTime.of(1, 0)),
        )

        val tile = builder.build(ReportInput(range = day(1)..day(1), sleepNights = nights)).tiles.first { it.key == "sleep" }

        assertEquals("critical", tile.status)
    }

    @Test
    fun `shows the bedtime tile as a clock time with the spread as sub-text`() {
        val nights = listOf(
            SleepNight("n-1", day(1), at(0, 23), at(1, 7), durationMinutes = 480, localBedTime = LocalTime.of(23, 0)),
            SleepNight("n-2", day(2), at(1, 23), at(2, 7), durationMinutes = 480, localBedTime = LocalTime.of(23, 0)),
        )

        val tile = builder.build(ReportInput(range = day(1)..day(2), sleepNights = nights)).tiles.first { it.key == "bedtime" }

        assertEquals("23h00", tile.value) // coucher médian, en heure d'horloge
        assertEquals("± 0,0 h", tile.sub) // toujours 23h : aucune dispersion
        assertNull(tile.unit)
        assertEquals("good", tile.status)
    }

    @Test
    fun `flags a normal resting heart rate as good and a high one as critical`() {
        val normalBeats = (1..30).map { HeartRateSample("hr-$it", at(1, 3).plusSeconds(it.toLong() * 60), 60) }
        val highBeats = (1..30).map { HeartRateSample("hr-$it", at(1, 3).plusSeconds(it.toLong() * 60), 95) }

        val normalTile = builder.build(ReportInput(range = day(1)..day(1), heartRates = normalBeats))
            .tiles.first { it.key == "restingHeartRate" }
        val highTile = builder.build(ReportInput(range = day(1)..day(1), heartRates = highBeats))
            .tiles.first { it.key == "restingHeartRate" }

        assertEquals("good", normalTile.status)
        assertEquals("critical", highTile.status)
    }

    @Test
    fun `classifies the body mass index using the who ranges`() {
        val normal = BodyComposition("w-1", at(1, 8), weightKg = 70f, bodyMassIndex = 22f)
        val obese = BodyComposition("w-2", at(1, 8), weightKg = 110f, bodyMassIndex = 36f)

        val normalTile = builder.build(ReportInput(range = day(1)..day(1), bodyCompositions = listOf(normal)))
            .tiles.first { it.key == "bodyMassIndex" }
        val obeseTile = builder.build(ReportInput(range = day(1)..day(1), bodyCompositions = listOf(obese)))
            .tiles.first { it.key == "bodyMassIndex" }

        assertEquals("good", normalTile.status)
        assertEquals("critical", obeseTile.status)
    }

    @Test
    fun `classifies blood pressure using the aha categories`() {
        val normal = BloodPressureReading("bp-1", at(1, 8), systolic = 115, diastolic = 75)
        val crisis = BloodPressureReading("bp-2", at(1, 8), systolic = 185, diastolic = 100)

        val normalTile = builder.build(ReportInput(range = day(1)..day(1), bloodPressure = listOf(normal)))
            .tiles.first { it.key == "bloodPressure" }
        val crisisTile = builder.build(ReportInput(range = day(1)..day(1), bloodPressure = listOf(crisis)))
            .tiles.first { it.key == "bloodPressure" }

        assertEquals("good", normalTile.status)
        assertEquals("115/75", normalTile.value)
        assertEquals("critical", crisisTile.status)
    }

    @Test
    fun `classifies stress using the samsung-shaped 0 to 100 scale`() {
        val calm = listOf(StressSample("s-1", at(1, 8), at(1, 9), score = 20))
        val high = listOf(StressSample("s-1", at(1, 8), at(1, 9), score = 85))

        val calmTile = builder.build(ReportInput(range = day(1)..day(1), stress = calm)).tiles.first { it.key == "stress" }
        val highTile = builder.build(ReportInput(range = day(1)..day(1), stress = high)).tiles.first { it.key == "stress" }

        assertEquals("good", calmTile.status)
        assertEquals("critical", highTile.status)
    }

    @Test
    fun `classifies hrv where a higher value is better`() {
        val low = listOf(HrvSample("h-1", at(1, 2), null, rmssdMillis = 10f))
        val high = listOf(HrvSample("h-1", at(1, 2), null, rmssdMillis = 60f))

        val lowTile = builder.build(ReportInput(range = day(1)..day(1), hrv = low)).tiles.first { it.key == "hrv" }
        val highTile = builder.build(ReportInput(range = day(1)..day(1), hrv = high)).tiles.first { it.key == "hrv" }

        assertEquals("critical", lowTile.status)
        assertEquals("good", highTile.status)
    }

    @Test
    fun `classifies daily steps against the who activity guideline`() {
        val active = (1..30).map { DailySteps(day(1).minusDays((30 - it).toLong()), steps = 9000) }
        val sedentary = (1..30).map { DailySteps(day(1).minusDays((30 - it).toLong()), steps = 1500) }

        val activeTile = builder.build(ReportInput(range = day(1).minusDays(29)..day(1), dailySteps = active))
            .tiles.first { it.key == "steps30" }
        val sedentaryTile = builder.build(ReportInput(range = day(1).minusDays(29)..day(1), dailySteps = sedentary))
            .tiles.first { it.key == "steps30" }

        assertEquals("good", activeTile.status)
        assertEquals("critical", sedentaryTile.status)
    }
}
