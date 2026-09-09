package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderSleepTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int, minute: Int = 0): Instant = day(n).atTime(hour, minute).toInstant(zone)

    private fun night(
        n: Int,
        durationMinutes: Int,
        bedHour: Int,
        bedMinute: Int = 0,
        score: Int? = 80,
    ) = SleepNight(
        id = "n-$n",
        date = day(n),
        bedTime = day(n - 1).atTime(bedHour, bedMinute).toInstant(zone),
        wakeTime = day(n).atTime(7, 0).toInstant(zone),
        durationMinutes = durationMinutes,
        score = score,
        localBedTime = java.time.LocalTime.of(bedHour, bedMinute),
    )

    @Test
    fun `falls back on bedTime for bedRel when localBedTime is missing, as for a Health Connect night`() {
        // Seul l'export Samsung renseigne localBedTime ; les nuits Health Connect ne
        // l'ont pas. Sans repli sur bedTime, bedRel, bedMedian, bedSpreadHours et la
        // tuile "bedtime" disparaissent silencieusement pour ces nuits-là.
        val withoutLocalBedTime = SleepNight(
            id = "hc-1",
            date = day(2),
            bedTime = at(1, 22, 30),
            wakeTime = at(2, 7),
            durationMinutes = 510,
            score = 80,
            localBedTime = null,
        )

        val model = builder.build(ReportInput(range = day(2)..day(2), sleepNights = listOf(withoutLocalBedTime)))

        val point = model.sleep.nightly.single()
        assertEquals(-1.5, point.bedRel!!, 0.001) // 22h30 -> -1.5, comme si localBedTime était renseigné
        assertNotNull(model.sleep.kpi.bedMedian)
        assertNotNull(model.sleep.kpi.bedSpreadHours)
    }

    @Test
    fun `expresses bed and wake time as decimal hours relative to midnight`() {
        val model = builder.build(
            ReportInput(range = day(2)..day(2), sleepNights = listOf(night(2, 420, bedHour = 22, bedMinute = 30))),
        )

        val point = model.sleep.nightly.single()
        assertEquals(-1.5, point.bedRel!!, 0.001) // 22h30 -> -1.5
        assertEquals(7.0, point.wakeRel!!, 0.001) // réveil à 7h
        assertEquals(7.0, point.hours, 0.001) // 420 min
    }

    @Test
    fun `counts the raw sessions merged into a night`() {
        val fragmented = listOf(
            night(2, 120, bedHour = 23).copy(wakeTime = at(2, 1)),
            night(2, 300, bedHour = 2).copy(bedTime = at(2, 2), wakeTime = at(2, 7)),
        )

        val model = builder.build(ReportInput(range = day(2)..day(2), sleepNights = fragmented))
        assertEquals(2, model.sleep.nightly.single().sessions)

        val single = builder.build(ReportInput(range = day(3)..day(3), sleepNights = listOf(night(3, 420, 23))))
        assertEquals(1, single.sleep.nightly.single().sessions)
    }

    @Test
    fun `computes the sleep kpi over the period`() {
        val nights = listOf(
            night(2, 420, bedHour = 23), // 7h, lundi
            night(3, 360, bedHour = 23), // 6h, mardi
            night(4, 300, bedHour = 1), // 5h, mercredi, coucher après minuit
        )

        val kpi = builder.build(ReportInput(range = day(2)..day(4), sleepNights = nights)).sleep.kpi

        assertEquals(3, kpi.nights)
        assertEquals(6.0, kpi.meanHours!!, 0.01) // (7+6+5)/3
        assertEquals(6.0, kpi.medianHours!!, 0.01)
        assertEquals(0, kpi.snoringNights) // aucun épisode fourni ici ; voir le test dédié
    }

    @Test
    fun `flags the share of nights that start after midnight and after 2am`() {
        val nights = listOf(
            night(2, 420, bedHour = 23), // avant minuit
            night(3, 420, bedHour = 1), // après minuit, avant 2h -> compte pour pctAfterMidnight seulement
            night(4, 420, bedHour = 3), // après 2h -> compte pour les deux
            night(5, 420, bedHour = 22), // avant minuit
        )

        val kpi = builder.build(ReportInput(range = day(2)..day(5), sleepNights = nights)).sleep.kpi

        assertEquals(50.0, kpi.pctAfterMidnight!!, 0.01) // 2 nuits sur 4
        assertEquals(25.0, kpi.pctAfter2h!!, 0.01) // 1 nuit sur 4
    }

    @Test
    fun `flags the share of nights under six hours and over seven`() {
        val nights = listOf(
            night(2, 300, bedHour = 23), // 5h
            night(3, 420, bedHour = 23), // 7h
            night(4, 450, bedHour = 23), // 7h30
            night(5, 360, bedHour = 23), // 6h
        )

        val kpi = builder.build(ReportInput(range = day(2)..day(5), sleepNights = nights)).sleep.kpi

        assertEquals(25.0, kpi.pctUnder6h!!, 0.01) // 1 nuit sur 4 (< 6h strictement)
        assertEquals(50.0, kpi.pctOver7h!!, 0.01) // 2 nuits sur 4 (>= 7h)
    }

    @Test
    fun `computes the sleep debt against the default target of seven and a half hours`() {
        val nights = (2..8).map { night(it, durationMinutes = 300, bedHour = 23) } // 5 h chaque nuit

        val kpi = builder.build(ReportInput(range = day(2)..day(8), sleepNights = nights)).sleep.kpi

        assertEquals(7.5, kpi.targetHours, 0.001)
        // 7 nuits x 2h30 de déficit = 17.5 h
        assertEquals(17.5, kpi.debtHours!!, 0.01)
    }

    @Test
    fun `distributes nights into caller-shaped duration buckets`() {
        val nights = listOf(
            night(2, 240, bedHour = 23), // 4h -> "< 5 h"
            night(3, 390, bedHour = 23), // 6h30 -> "6-7 h"
            night(4, 540, bedHour = 23), // 9h -> "> 8 h"
        )

        val distribution = builder.build(ReportInput(range = day(2)..day(4), sleepNights = nights)).sleep.distribution

        assertEquals(1, distribution.first { it.label == "< 5 h" }.count)
        assertEquals(1, distribution.first { it.label == "6-7 h" }.count)
        assertEquals(1, distribution.first { it.label == "> 8 h" }.count)
    }

    @Test
    fun `summarizes the sleep stages of a month as a percentage of the stage total, not of the duration`() {
        // Une partie de la nuit n'est pas classée : les stades somment à 380 min, pas
        // aux 400 min de durée. Le dénominateur doit être 380, sinon les quatre parts
        // ne totaliseraient pas 100.
        val n = SleepNight(
            id = "n-2", date = day(2),
            bedTime = at(1, 23), wakeTime = at(2, 7),
            durationMinutes = 400,
            remMinutes = 76, lightMinutes = 228, deepMinutes = 57, awakeMinutes = 19,
            localBedTime = java.time.LocalTime.of(23, 0),
        )

        val month = builder.build(ReportInput(range = day(2)..day(2), sleepNights = listOf(n))).sleep.stagesMonthly.single()

        assertEquals(20.0, month.rem, 0.01) // 76/380
        assertEquals(60.0, month.light, 0.01) // 228/380
        assertEquals(15.0, month.deep, 0.01) // 57/380
        assertEquals(5.0, month.awake, 0.01) // 19/380
        assertEquals(100.0, month.deep + month.light + month.rem + month.awake, 0.01)
    }

    @Test
    fun `omits a month with no classified stage at all, rather than showing zeroes`() {
        val n = night(2, 420, bedHour = 23) // sans remMinutes/lightMinutes/deepMinutes/awakeMinutes

        val stagesMonthly = builder.build(ReportInput(range = day(2)..day(2), sleepNights = listOf(n))).sleep.stagesMonthly

        assertEquals(emptyList<SleepStageMonth>(), stagesMonthly)
    }

    @Test
    fun `counts distinct nights with a snoring episode, not the episodes themselves`() {
        // Deux épisodes la même nuit ne doivent compter que pour une seule nuit.
        val nights = listOf(night(2, 420, 23), night(3, 420, 23))
        val snoring = listOf(
            SnoringEpisode(id = "s-1", start = at(1, 23, 30), end = at(1, 23, 40), durationMinutes = 10),
            SnoringEpisode(id = "s-2", start = at(1, 23, 50), end = at(1, 23, 55), durationMinutes = 5),
            SnoringEpisode(id = "s-3", start = at(2, 23, 30), end = at(2, 23, 50), durationMinutes = 20),
            SnoringEpisode(id = "s-4", start = at(2, 23, 55), end = at(2, 23, 55), durationMinutes = 0), // durée nulle, exclue
        )

        val kpi = builder.build(
            ReportInput(range = day(2)..day(3), sleepNights = nights, snoringEpisodes = snoring),
        ).sleep.kpi

        assertEquals(2, kpi.snoringNights) // night(2) et night(3), chacune une seule fois
        assertEquals(2, kpi.snoringMeasuredNights) // les 2 nuits de la période
        assertEquals(10.0, kpi.snoringMedianMinutes!!, 0.01) // médiane de 10, 5, 20 (le 0 exclu)
    }

    @Test
    fun `leaves the apnea result null without any measurement`() {
        val kpi = builder.build(ReportInput(range = day(2)..day(2), sleepNights = listOf(night(2, 420, 23)))).sleep.kpi

        assertNull(kpi.apneaResult)
    }

    @Test
    fun `reports the most recent apnea result when present, without guessing a clinical meaning`() {
        val apnea = listOf(SleepApneaResult(id = "a-1", time = at(2, 7), result = 1))

        val kpi = builder.build(
            ReportInput(range = day(2)..day(2), sleepNights = listOf(night(2, 420, 23)), sleepApneaResults = apnea),
        ).sleep.kpi

        assertEquals("Résultat 1 — à lire dans Samsung Health Monitor", kpi.apneaResult)
    }
}
