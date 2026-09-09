package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.StressSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class HealthAggregatorTest {

    private val zone = ZoneOffset.UTC
    private val aggregator = HealthAggregator(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)

    private fun at(n: Int, hour: Int, minute: Int = 0): Instant =
        day(n).atTime(hour, minute).toInstant(zone)

    private fun night(n: Int, minutes: Int, score: Int? = null, bedHour: Int = 23) = SleepNight(
        id = "n-$n",
        date = day(n),
        bedTime = day(n - 1).atTime(bedHour, 0).toInstant(zone),
        wakeTime = day(n).atTime(7, 0).toInstant(zone),
        durationMinutes = minutes,
        score = score,
        localBedTime = LocalTime.of(bedHour, 0),
    )

    // --- Journalisation -----------------------------------------------------

    @Test
    fun `builds one snapshot per day of the requested range`() {
        val snapshot = aggregator.aggregate(
            range = day(1)..day(5),
            dailySteps = listOf(DailySteps(day(2), 8000), DailySteps(day(4), 3000)),
        )

        assertEquals(5, snapshot.days.size)
        assertEquals(day(1), snapshot.days.first().date)
        assertEquals(8000, snapshot.days[1].steps)
        assertNull(snapshot.days[2].steps)
    }

    @Test
    fun `estimates the resting heart rate as the fifth percentile of the day`() {
        val samples = (1..100).map {
            HeartRateSample(id = "hr-$it", time = at(1, 8, it % 60), beatsPerMinute = it)
        }

        val snapshot = aggregator.aggregate(range = day(1)..day(1), heartRates = samples)

        // Le 5e centile de 1..100 vaut 5 : c'est une estimation robuste du repos.
        assertEquals(5, snapshot.days.single().restingHeartRate)
        assertEquals(51, snapshot.days.single().averageHeartRate) // moyenne de 1..100 = 50,5
    }

    @Test
    fun `ignores a day with too few heart rate samples for a resting estimate`() {
        val samples = listOf(HeartRateSample("hr-1", at(1, 8), 60), HeartRateSample("hr-2", at(1, 9), 62))

        val snapshot = aggregator.aggregate(range = day(1)..day(1), heartRates = samples)

        assertNull(snapshot.days.single().restingHeartRate)
        assertEquals(61, snapshot.days.single().averageHeartRate)
    }

    @Test
    fun `averages the hourly stress scores of a day`() {
        val stress = listOf(
            StressSample("s-1", at(1, 8), at(1, 9), 20),
            StressSample("s-2", at(1, 9), at(1, 10), 40),
            StressSample("s-3", at(1, 10), at(1, 11), 60),
        )

        assertEquals(40, aggregator.aggregate(day(1)..day(1), stress = stress).days.single().averageStress)
    }

    @Test
    fun `carries the last known weight forward on days without a measurement`() {
        val body = listOf(
            BodyComposition(id = "w-1", time = at(1, 8), weightKg = 90f),
            BodyComposition(id = "w-2", time = at(4, 8), weightKg = 88f),
        )

        val days = aggregator.aggregate(day(1)..day(5), bodyCompositions = body).days

        assertEquals(90f, days[0].weightKg!!, 0.01f)
        assertEquals(90f, days[1].weightKg!!, 0.01f) // report de la dernière pesée connue
        assertEquals(88f, days[3].weightKg!!, 0.01f)
        assertEquals(88f, days[4].weightKg!!, 0.01f)
    }

    @Test
    fun `leaves the weight empty before the very first measurement`() {
        val body = listOf(BodyComposition(id = "w-1", time = at(4, 8), weightKg = 88f))

        val days = aggregator.aggregate(day(1)..day(5), bodyCompositions = body).days

        assertNull(days[0].weightKg)
        assertEquals(88f, days[3].weightKg!!, 0.01f)
    }

    // --- Régularité du sommeil ----------------------------------------------

    @Test
    fun `measures bedtime regularity as the spread of bedtimes`() {
        val nights = listOf(night(2, 420, bedHour = 23), night(3, 420, bedHour = 23), night(4, 420, bedHour = 23))

        val regularity = aggregator.aggregate(day(1)..day(5), sleepNights = nights).sleepRegularity!!

        assertEquals(0.0, regularity.bedtimeSpreadHours, 0.01)
        assertEquals(LocalTime.of(23, 0), regularity.averageBedtime)
    }

    @Test
    fun `treats bedtimes across midnight as a continuous scale`() {
        // 23 h et 01 h sont distants de 2 h, pas de 22 h.
        val nights = listOf(night(2, 420, bedHour = 23), night(3, 420, bedHour = 1))

        val regularity = aggregator.aggregate(day(1)..day(5), sleepNights = nights).sleepRegularity!!

        assertEquals(1.0, regularity.bedtimeSpreadHours, 0.01)
        assertEquals(LocalTime.of(0, 0), regularity.averageBedtime)
    }

    @Test
    fun `falls back on bedTime when localBedTime is missing, as for a Health Connect night`() {
        // Health Connect ne porte pas localBedTime, contrairement à l'export Samsung.
        val withoutLocalBedTime = SleepNight(
            id = "hc-1",
            date = day(2),
            bedTime = at(1, 23, 30),
            wakeTime = at(2, 7),
            durationMinutes = 450,
            localBedTime = null,
        )

        val snapshot = aggregator.aggregate(day(2)..day(2), sleepNights = listOf(withoutLocalBedTime))

        assertEquals(LocalTime.of(23, 30), snapshot.days.single().bedTime)
        val regularity = snapshot.sleepRegularity!!
        assertEquals(1, regularity.nightsMeasured)
        assertEquals(LocalTime.of(23, 30), regularity.averageBedtime)
    }

    @Test
    fun `computes the sleep debt against the target duration`() {
        val nights = (2..8).map { night(it, minutes = 300) } // 5 h par nuit

        val summary = aggregator.aggregate(
            range = day(1)..day(8),
            sleepNights = nights,
            sleepTargetMinutes = 450,
        ).sleepRegularity!!

        // 7 nuits à 150 min de déficit font 1050 min de dette.
        assertEquals(1050, summary.sleepDebtMinutes)
    }

    // --- Tendances -----------------------------------------------------------

    @Test
    fun `compares the recent window against the one before it`() {
        val steps = (1..28).map { DailySteps(LocalDate.of(2026, 3, it), steps = if (it > 14) 8000 else 4000) }

        val trend = aggregator
            .aggregate(LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 28), dailySteps = steps)
            .trends
            .single { it.metric == HealthMetric.STEPS }

        assertEquals(8000.0, trend.recentAverage, 0.1)
        assertEquals(4000.0, trend.previousAverage!!, 0.1)
        assertEquals(100.0, trend.changePercent!!, 0.1)
        assertEquals(TrendDirection.UP, trend.direction)
    }

    @Test
    fun `reports a stable trend when the change stays inside the noise band`() {
        val steps = (1..28).map { DailySteps(LocalDate.of(2026, 3, it), steps = if (it > 14) 4100 else 4000) }

        val trend = aggregator
            .aggregate(LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 28), dailySteps = steps)
            .trends
            .single { it.metric == HealthMetric.STEPS }

        assertEquals(TrendDirection.STABLE, trend.direction)
    }

    @Test
    fun `treats a falling resting heart rate as an improvement`() {
        val samples = (1..28).flatMap { dayNumber ->
            val base = if (dayNumber > 14) 50 else 60
            (0..29).map {
                HeartRateSample(
                    id = "hr-$dayNumber-$it",
                    time = LocalDate.of(2026, 3, dayNumber).atTime(8, it).toInstant(zone),
                    beatsPerMinute = base + it % 5,
                )
            }
        }

        val trend = aggregator
            .aggregate(LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 28), heartRates = samples)
            .trends
            .single { it.metric == HealthMetric.RESTING_HEART_RATE }

        assertEquals(TrendDirection.DOWN, trend.direction)
        assertTrue("Une FC de repos qui baisse est une amélioration", trend.isImprovement == true)
    }

    @Test
    fun `produces no trend when the previous window holds no data`() {
        val steps = (15..28).map { DailySteps(LocalDate.of(2026, 3, it), steps = 8000) }

        val trend = aggregator
            .aggregate(LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 28), dailySteps = steps)
            .trends
            .single { it.metric == HealthMetric.STEPS }

        assertNull(trend.previousAverage)
        assertNull(trend.changePercent)
        assertEquals(TrendDirection.STABLE, trend.direction)
    }

    // --- Couverture ----------------------------------------------------------

    @Test
    fun `reports how many days actually carry data`() {
        val snapshot = aggregator.aggregate(
            range = day(1)..day(10),
            dailySteps = listOf(DailySteps(day(2), 100), DailySteps(day(3), 200)),
            sleepNights = listOf(night(5, 400)),
        )

        assertEquals(3, snapshot.daysWithData)
        assertEquals(10, snapshot.days.size)
    }

    @Test
    fun `keeps the daily energy score and its components`() {
        val snapshot = aggregator.aggregate(
            range = day(1)..day(2),
            energyScores = listOf(EnergyScore(date = day(1), total = 78, sleep = 72, activity = 88)),
        )

        assertEquals(78, snapshot.days.first().energyScore)
    }

    @Test
    fun `averages the hrv samples of a day`() {
        val hrv = listOf(
            HrvSample("h-1", at(1, 2), sdnnMillis = 50f, rmssdMillis = 60f),
            HrvSample("h-2", at(1, 3), sdnnMillis = 70f, rmssdMillis = 80f),
        )

        assertEquals(70f, aggregator.aggregate(day(1)..day(1), hrv = hrv).days.single().hrvRmssd!!, 0.01f)
    }

    @Test
    fun `prefers the activity summary for active minutes and falls back on nothing`() {
        val snapshot = aggregator.aggregate(
            range = day(1)..day(2),
            dailyActivities = listOf(DailyActivity(date = day(1), activeMinutes = 88, activeCalories = 396)),
        )

        assertEquals(88, snapshot.days.first().activeMinutes)
        assertNull(snapshot.days[1].activeMinutes)
    }

    // --- Nuits fractionnées ---------------------------------------------------

    @Test
    fun `sums the durations of a night split into several sessions`() {
        // Samsung écrit parfois deux sessions pour une même nuit : un coucher avant
        // minuit interrompu, puis une reprise. Les deux se rattachent au même réveil.
        val first = SleepNight(
            id = "n-a",
            date = day(2),
            bedTime = day(1).atTime(23, 0).toInstant(zone),
            wakeTime = day(2).atTime(1, 0).toInstant(zone),
            durationMinutes = 120,
            score = 40,
            efficiencyPercent = 70f,
            latencyMinutes = 20,
            physicalRecovery = 30,
            mentalRecovery = 35,
            remMinutes = 10,
            lightMinutes = 80,
            deepMinutes = 20,
            awakeMinutes = 10,
            localBedTime = LocalTime.of(23, 0),
        )
        val second = SleepNight(
            id = "n-b",
            date = day(2),
            bedTime = day(2).atTime(2, 0).toInstant(zone),
            wakeTime = day(2).atTime(7, 0).toInstant(zone),
            durationMinutes = 300,
            score = 82,
            efficiencyPercent = 90f,
            latencyMinutes = 5,
            physicalRecovery = 70,
            mentalRecovery = 75,
            remMinutes = 50,
            lightMinutes = 180,
            deepMinutes = 60,
            awakeMinutes = 10,
            localBedTime = LocalTime.of(2, 0),
        )

        val snapshot = aggregator.aggregate(range = day(2)..day(2), sleepNights = listOf(first, second))
        val merged = snapshot.days.single()

        // La somme des deux sessions : 120 + 300 = 420 minutes, pas 120 ni 300 seules.
        assertEquals(420, merged.sleepMinutes)
        // Le score retenu est celui de la session la plus longue (la seconde, 300 min).
        assertEquals(82, merged.sleepScore)
        // Le coucher retenu est celui de la première session.
        assertEquals(LocalTime.of(23, 0), merged.bedTime)
    }

    @Test
    fun `keeps a single unfractioned night unchanged`() {
        val single = night(2, minutes = 420, score = 88, bedHour = 23)

        val merged = aggregator.aggregate(range = day(2)..day(2), sleepNights = listOf(single))
            .days.single()

        assertEquals(420, merged.sleepMinutes)
        assertEquals(88, merged.sleepScore)
    }

    @Test
    fun `feeds the sleep regularity computation with merged nights, not raw sessions`() {
        // Une nuit fractionnée en deux sessions à des heures très différentes ne doit
        // compter que pour une seule heure de coucher dans le calcul de régularité,
        // sinon l'écart-type circulaire est artificiellement gonflé.
        val fragmentedA = night(2, minutes = 120, bedHour = 20) // coucher très tôt, session interrompue
        val fragmentedB = night(2, minutes = 300, bedHour = 23) // reprise plus tard
        val regularNights = listOf(3, 4, 5).map { night(it, minutes = 420, bedHour = 23) }

        val regularity = aggregator.aggregate(
            range = day(1)..day(6),
            sleepNights = listOf(fragmentedA, fragmentedB) + regularNights,
        ).sleepRegularity!!

        // Quatre nuits distinctes, pas cinq sessions : la nuit du 2 ne compte qu'une fois.
        assertEquals(4, regularity.nightsMeasured)
    }
}
