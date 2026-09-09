package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.model.SleepNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Samsung écrit parfois plusieurs sessions pour une même nuit (un coucher interrompu,
 * repris plus tard). `HealthAggregator.mergeFragmentedSleepNights` les réunit à la
 * lecture pour qu'une nuit du rapport corresponde à une nuit réelle. Room, lui, continue
 * de stocker une ligne par session : c'est la donnée brute fidèle.
 */
class SleepNightMergerTest {

    private val zone = ZoneOffset.UTC
    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int, minute: Int = 0): Instant = day(n).atTime(hour, minute).toInstant(zone)

    @Test
    fun `sums the durations of every session of the night`() {
        val first = session(
            date = day(2), bed = at(1, 23), wake = at(2, 1), minutes = 120,
        )
        val second = session(
            date = day(2), bed = at(2, 2), wake = at(2, 7), minutes = 300,
        )

        val merged = mergeFragmentedSleepNights(listOf(first, second)).single()

        assertEquals(420, merged.durationMinutes)
    }

    @Test
    fun `keeps the start of the first session as bed time and the end of the last as wake time`() {
        val first = session(date = day(2), bed = at(1, 23), wake = at(2, 1), minutes = 120)
        val second = session(date = day(2), bed = at(2, 2), wake = at(2, 7), minutes = 300)

        val merged = mergeFragmentedSleepNights(listOf(second, first)).single() // ordre d'entrée quelconque

        assertEquals(at(1, 23), merged.bedTime)
        assertEquals(at(2, 7), merged.wakeTime)
        assertEquals(LocalTime.of(23, 0), merged.localBedTime)
    }

    @Test
    fun `takes the score and the recovery indicators from the longest session`() {
        val short = session(date = day(2), bed = at(1, 23), wake = at(2, 1), minutes = 120).copy(
            score = 40, efficiencyPercent = 70f, latencyMinutes = 20, physicalRecovery = 30, mentalRecovery = 35,
        )
        val long = session(date = day(2), bed = at(2, 2), wake = at(2, 7), minutes = 300).copy(
            score = 82, efficiencyPercent = 90f, latencyMinutes = 5, physicalRecovery = 70, mentalRecovery = 75,
        )

        val merged = mergeFragmentedSleepNights(listOf(short, long)).single()

        assertEquals(82, merged.score)
        assertEquals(90f, merged.efficiencyPercent)
        assertEquals(5, merged.latencyMinutes)
        assertEquals(70, merged.physicalRecovery)
        assertEquals(75, merged.mentalRecovery)
    }

    @Test
    fun `sums the sleep stages while ignoring nulls`() {
        val first = session(date = day(2), bed = at(1, 23), wake = at(2, 1), minutes = 120).copy(
            remMinutes = 10, lightMinutes = 80, deepMinutes = null, awakeMinutes = 5,
        )
        val second = session(date = day(2), bed = at(2, 2), wake = at(2, 7), minutes = 300).copy(
            remMinutes = 50, lightMinutes = 180, deepMinutes = 60, awakeMinutes = null,
        )

        val merged = mergeFragmentedSleepNights(listOf(first, second)).single()

        assertEquals(60, merged.remMinutes)
        assertEquals(260, merged.lightMinutes)
        assertEquals(60, merged.deepMinutes)
        assertEquals(5, merged.awakeMinutes)
    }

    @Test
    fun `leaves a stage null when no session of the night carries it`() {
        val first = session(date = day(2), bed = at(1, 23), wake = at(2, 1), minutes = 120).copy(remMinutes = null)
        val second = session(date = day(2), bed = at(2, 2), wake = at(2, 7), minutes = 300).copy(remMinutes = null)

        val merged = mergeFragmentedSleepNights(listOf(first, second)).single()

        assertNull(merged.remMinutes)
    }

    @Test
    fun `leaves a single unfractioned night untouched`() {
        val single = session(date = day(2), bed = at(1, 23), wake = at(2, 7), minutes = 480).copy(score = 88)

        val merged = mergeFragmentedSleepNights(listOf(single)).single()

        assertEquals(480, merged.durationMinutes)
        assertEquals(88, merged.score)
        assertEquals(single.id, merged.id)
    }

    @Test
    fun `keeps nights of different days separate`() {
        val nightA = session(date = day(2), bed = at(1, 23), wake = at(2, 7), minutes = 480)
        val nightB = session(date = day(3), bed = at(2, 23), wake = at(3, 7), minutes = 480)

        val merged = mergeFragmentedSleepNights(listOf(nightA, nightB))

        assertEquals(2, merged.size)
    }

    @Test
    fun `reproduces the measured under-counting on a real-world-shaped export`() {
        // Sur l'export réel : une ligne par nuit donne une moyenne de 5 h 09 par nuit ;
        // l'agrégation par jour de réveil donne 6 h 20. Ce test rejoue l'écart à petite
        // échelle : deux sessions de 2 h et 3 h 10 valent 5 h 10, pas 3 h 10 seules.
        val first = session(date = day(2), bed = at(1, 22), wake = at(2, 0), minutes = 120)
        val second = session(date = day(2), bed = at(2, 1), wake = at(2, 5), minutes = 190)

        val merged = mergeFragmentedSleepNights(listOf(first, second)).single()

        assertEquals(310, merged.durationMinutes) // 5 h 10, pas 3 h 10
    }

    private fun session(
        date: LocalDate,
        bed: Instant,
        wake: Instant,
        minutes: Int,
        id: String = "n-${date}-$bed",
    ) = SleepNight(
        id = id,
        date = date,
        bedTime = bed,
        wakeTime = wake,
        durationMinutes = minutes,
        localBedTime = bed.atZone(zone).toLocalTime(),
    )
}
