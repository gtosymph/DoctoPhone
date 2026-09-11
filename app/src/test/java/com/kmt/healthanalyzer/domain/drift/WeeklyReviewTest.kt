package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodyPoint
import com.kmt.healthanalyzer.domain.report.BodySection
import com.kmt.healthanalyzer.domain.report.DayValue
import com.kmt.healthanalyzer.domain.report.HeartSection
import com.kmt.healthanalyzer.domain.report.BreathingSection
import com.kmt.healthanalyzer.domain.report.ReportMeta
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.SleepNightPoint
import com.kmt.healthanalyzer.domain.report.SleepSection
import com.kmt.healthanalyzer.domain.report.StressSection
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste le bilan hebdomadaire qui alimente l'écran d'accueil « Cette semaine ».
 *
 * La différence avec [DriftReport] tient en une phrase : un rapport de dérives ne contient
 * que ce qui a bougé, alors que l'écran d'accueil montre **les six mesures à chaque fois**,
 * qu'elles aient bougé ou non. Une mesure absente reste affichée, avec la raison de son
 * absence — la retirer donnerait à penser qu'elle n'existe pas.
 *
 * Les deux sortent du même passage sur le même [ReportModel] : les dérives montrées sur
 * l'accueil sont exactement celles que `WeeklyDriftCheckWorker` notifie, jamais un second
 * calcul qui pourrait diverger.
 */
class WeeklyReviewTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val analyzer = HealthDriftAnalyzer()

    @Test
    fun `les six mesures sont la, meme quand l'app n'a aucune donnee`() {
        val review = analyzer.review(fixtureReport(), today)

        assertEquals(
            "L'écran montre six lignes, toujours les mêmes. Une mesure sans donnée garde " +
                "sa ligne et dit pourquoi elle est vide ; la retirer laisserait croire " +
                "qu'elle n'existe pas.",
            DriftMetric.entries.toSet(),
            review.metrics.map { it.metric }.toSet(),
        )
        review.metrics.forEach { row ->
            assertNull("Sans donnée, aucune valeur ne doit être inventée.", row.recentValue)
            assertNull(row.baselineValue)
            assertNull(row.lastMeasuredOn)
        }
    }

    @Test
    fun `la semaine porte sept points, un par jour, trous compris`() {
        // Cinq nuits mesurées sur les sept derniers jours : deux trous, gardés comme trous.
        val nights = listOf(6, 5, 4, 2, 1).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)
        val sleep = review.metrics.first { it.metric == DriftMetric.SLEEP_DURATION }

        assertEquals("Sept jours, un point par jour.", 7, sleep.week.size)
        assertEquals("Le premier point est le plus ancien.", today.minusDays(6), sleep.week.first().date)
        assertEquals("Le dernier point est aujourd'hui.", today, sleep.week.last().date)
        assertEquals(
            "Un jour sans mesure reste un trou. Jamais un zéro : un zéro se dessine, " +
                "et se lit comme une nuit blanche.",
            2,
            sleep.week.count { it.value == null },
        )
    }

    @Test
    fun `la moyenne de la semaine et celle de la reference sont calculees`() {
        val nights = (0..62).map { back ->
            val hours = if (back < 7) 5.0 else 8.0
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = hours)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)
        val sleep = review.metrics.first { it.metric == DriftMetric.SLEEP_DURATION }

        assertEquals(5.0, sleep.recentValue!!, 0.001)
        assertEquals(8.0, sleep.baselineValue!!, 0.001)
        assertEquals(7, sleep.recentDays)
        assertEquals(56, sleep.baselineDays)
        assertEquals(today, sleep.lastMeasuredOn)
    }

    @Test
    fun `sous le minimum d'affichage, l'app se tait au lieu de moyenner deux points`() {
        val nights = (0..1).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)
        val sleep = review.metrics.first { it.metric == DriftMetric.SLEEP_DURATION }

        assertNull(
            "Deux nuits ne font pas une semaine. Une moyenne calculée dessus s'afficherait " +
                "exactement comme une moyenne sur sept, avec la même autorité apparente.",
            sleep.recentValue,
        )
        assertEquals("Le compte de jours mesurés, lui, reste juste : il sert au message.", 2, sleep.recentDays)
    }

    @Test
    fun `montrer une valeur demande moins de jours qu'affirmer une derive`() {
        // Trois nuits sur sept : assez pour décrire la semaine, pas pour affirmer un écart.
        val nights = (0..55).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        } + (0..2).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 5.0)
        }
        val model = fixtureReport(sleep = SleepSection(nightly = nights.distinctBy { it.date + it.hours }))
        val sleep = analyzer.review(model, today).metrics.first { it.metric == DriftMetric.SLEEP_DURATION }

        assertNotNull(
            "Le seuil d'affichage (${DriftMetric.SLEEP_DURATION.minRecentDaysToShow} jours) est " +
                "volontairement plus bas que celui de la détection " +
                "(${DriftDetector.MIN_RECENT_MEASURED_DAYS}). Décrire et affirmer ne se paient " +
                "pas au même prix, et l'écran montre le nombre de jours à côté de la valeur.",
            sleep.recentValue,
        )
    }

    @Test
    fun `une seule pesee suffit a dire le poids de la semaine`() {
        // Le poids est la seule mesure éparse : on se pèse une ou deux fois par semaine.
        // Lui appliquer le seuil des mesures quotidiennes afficherait un tiret presque
        // toujours, alors qu'une pesée répond très bien à « combien je pèse cette semaine ».
        val model = fixtureReport(
            body = BodySection(
                daily = listOf(BodyPoint(date = today.minusDays(2).toString(), weightKg = 82.4)),
            ),
        )
        val weight = analyzer.review(model, today).metrics.first { it.metric == DriftMetric.WEIGHT }

        assertEquals(82.4, weight.recentValue!!, 0.001)
        assertEquals(1, DriftMetric.WEIGHT.minRecentDaysToShow)
    }

    @Test
    fun `la regularite du coucher montre une dispersion, pas une moyenne`() {
        // Un coucher parfaitement régulier en référence, très dispersé sur la semaine.
        val nights = (0..62).map { back ->
            val bed = if (back < 7) (if (back % 2 == 0) -3.0 else 1.0) else -1.0
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0, bedRel = bed)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)
        val regularity = review.metrics.first { it.metric == DriftMetric.SLEEP_REGULARITY }

        assertEquals(
            "La référence est parfaitement régulière : sa dispersion vaut zéro. Une moyenne " +
                "aurait rendu -1,0, ce qui n'est pas ce que cette ligne raconte.",
            0.0,
            regularity.baselineValue!!,
            0.001,
        )
        assertTrue(
            "La semaine alterne deux heures de coucher : sa dispersion est franche.",
            regularity.recentValue!! > 1.0,
        )
    }

    @Test
    fun `les derives du bilan sont exactement celles du rapport de derives`() {
        val nights = (0..62).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = if (back < 7) 4.0 else 8.0)
        }
        val model = fixtureReport(sleep = SleepSection(nightly = nights))

        val review = analyzer.review(model, today)
        val report = analyzer.analyze(model, today)

        assertTrue("Le cas d'essai doit produire au moins une dérive.", report.hasDrift)
        assertEquals(
            "L'accueil et la notification hebdomadaire doivent dire la même chose. Deux " +
                "calculs séparés finiraient par diverger, et l'utilisateur verrait un écran " +
                "qui contredit sa notification.",
            report.drifts,
            review.drifts,
        )
    }

    @Test
    fun `la derniere mesure permet de dire depuis combien de temps elle manque`() {
        val weighIn = today.minusDays(34)
        val model = fixtureReport(
            body = BodySection(daily = listOf(BodyPoint(date = weighIn.toString(), weightKg = 82.4))),
        )
        val weight = analyzer.review(model, today).metrics.first { it.metric == DriftMetric.WEIGHT }

        assertEquals(weighIn, weight.lastMeasuredOn)
        assertEquals(0, weight.recentDays)
        assertNull("Une seule pesée, et hors de la semaine : rien à montrer pour la semaine.", weight.recentValue)
    }

    @Test
    fun `le poids ne porte aucun jugement de direction`() {
        assertNull(
            "Grossir ou maigrir n'est pas bon ou mauvais dans l'absolu, et cette app n'a " +
                "pas à en décider. L'écart s'affiche, sans couleur de jugement.",
            DriftMetric.WEIGHT.higherIsBetter,
        )
        assertEquals(true, DriftMetric.SLEEP_DURATION.higherIsBetter)
        assertEquals(true, DriftMetric.HRV.higherIsBetter)
        assertEquals(false, DriftMetric.RESTING_HEART_RATE.higherIsBetter)
        assertEquals(
            "La régularité se mesure par la dispersion des heures de coucher : plus elle " +
                "est grande, moins le coucher est régulier.",
            false,
            DriftMetric.SLEEP_REGULARITY.higherIsBetter,
        )
    }

    @Test
    fun `sans historique suffisant, le bilan dit combien de jours manquent`() {
        val nights = (0..20).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)

        val readiness = review.readiness
        assertTrue(
            "Vingt-et-un jours au total, dont sept dans la semaine récente : il reste " +
                "quatorze jours de référence, sous le minimum de 21. Attendu : " +
                "WeeklyReadiness.NotEnoughBaseline, reçu $readiness",
            readiness is WeeklyReadiness.NotEnoughBaseline,
        )
        assertEquals(14, (readiness as WeeklyReadiness.NotEnoughBaseline).measuredDays)
        assertEquals(DriftDetector.MIN_BASELINE_MEASURED_DAYS, readiness.requiredDays)
    }

    @Test
    fun `avec assez d'historique et rien qui bouge, le bilan le dit`() {
        val nights = (0..62).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)

        assertEquals(
            "Assez de données et aucune dérive est un résultat normal, le plus fréquent " +
                "même. Il ne doit pas se confondre avec « pas assez de données ».",
            WeeklyReadiness.Ready,
            review.readiness,
        )
    }

    @Test
    fun `une semaine trop trouee se distingue d'une reference trop courte`() {
        // Référence pleine, mais deux jours mesurés seulement sur la semaine écoulée.
        val nights = (7..62).map { back ->
            SleepNightPoint(date = today.minusDays(back.toLong()).toString(), hours = 7.0)
        } + listOf(0L, 1L).map { back ->
            SleepNightPoint(date = today.minusDays(back).toString(), hours = 7.0)
        }
        val review = analyzer.review(fixtureReport(sleep = SleepSection(nightly = nights)), today)

        val readiness = review.readiness
        assertTrue("Attendu NotEnoughRecent, reçu $readiness", readiness is WeeklyReadiness.NotEnoughRecent)
        assertEquals(2, (readiness as WeeklyReadiness.NotEnoughRecent).measuredDays)
        assertEquals(DriftDetector.MIN_RECENT_MEASURED_DAYS, readiness.requiredDays)
    }

    @Test
    fun `la periode couverte est celle des sept derniers jours`() {
        val review = analyzer.review(fixtureReport(), today)

        assertEquals(today.minusDays(6), review.from)
        assertEquals(today, review.to)
    }

    @Test
    fun `chaque mesure trouve sa serie dans le modele`() {
        val model = fixtureReport(
            sleep = SleepSection(
                nightly = listOf(SleepNightPoint(date = today.toString(), hours = 6.5, bedRel = -1.5)),
            ),
            heart = HeartSection(
                restingDaily = listOf(DayValue(today.toString(), 54.0)),
                hrvDaily = listOf(DayValue(today.toString(), 41.2)),
            ),
            activity = ActivitySection(stepsDaily = listOf(DayValue(today.toString(), 9120.0))),
            body = BodySection(daily = listOf(BodyPoint(date = today.toString(), weightKg = 82.4))),
        )
        val review = analyzer.review(model, today)

        fun lastOf(metric: DriftMetric) = review.metrics.first { it.metric == metric }.week.last().value

        assertEquals(6.5, lastOf(DriftMetric.SLEEP_DURATION)!!, 0.001)
        assertEquals(-1.5, lastOf(DriftMetric.SLEEP_REGULARITY)!!, 0.001)
        assertEquals(54.0, lastOf(DriftMetric.RESTING_HEART_RATE)!!, 0.001)
        assertEquals(41.2, lastOf(DriftMetric.HRV)!!, 0.001)
        assertEquals(9120.0, lastOf(DriftMetric.STEPS)!!, 0.001)
        assertEquals(82.4, lastOf(DriftMetric.WEIGHT)!!, 0.001)
        assertNotNull(review.generatedAt)
    }

    /**
     * Un [ReportModel] dont seules les sections utiles au bilan sont renseignées.
     *
     * Même forme que le constructeur d'essai de [HealthDriftAnalyzerTest] : les deux
     * chantiers lisent le même modèle et gagnent à le construire de la même façon.
     */
    private fun fixtureReport(
        sleep: SleepSection = SleepSection(),
        heart: HeartSection = HeartSection(),
        activity: ActivitySection = ActivitySection(),
        body: BodySection = BodySection(),
    ): ReportModel = ReportModel(
        meta = ReportMeta(
            generatedAt = "2026-09-10T00:00:00Z",
            from = "2026-07-10",
            to = "2026-09-10",
            days = 63,
            nights = 0,
            heartRateSamples = 0,
            hrvWindows = 0,
            activeDays = 0,
            timeZone = "Europe/Paris",
            periodLabel = "10 juil. 2026 - 10 sept. 2026",
        ),
        tiles = emptyList(),
        sleep = sleep,
        heart = heart,
        activity = activity,
        body = body,
        stress = StressSection(),
        breathing = BreathingSection(),
        correlations = emptyList(),
    )
}
