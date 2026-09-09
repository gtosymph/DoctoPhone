package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyFloors
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressSample
import com.kmt.healthanalyzer.domain.report.ReportBuilder
import com.kmt.healthanalyzer.domain.report.ReportInput
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Mesure — pas n'estime — le poids réel du `ReportModel` sérialisé que
 * [ChatWithHealthUseCase.loadContext] construit sur tout l'historique, et celui du
 * littéral qui traverse `WebView.evaluateJavascript` une fois ré-encodé comme chaîne
 * JavaScript (voir `ChatJsBridge.buildSetConversationScript` / `ReportJsBridge.buildSetReportScript`,
 * qui suivent le même schéma).
 *
 * Le jeu de données couvre quatre ans (1460 jours), avec au moins une mesure de chaque
 * catégorie presque chaque jour — le cas qui fait grossir le rapport, puisque la plupart
 * des sections de [com.kmt.healthanalyzer.domain.report.ReportModel] portent une entrée
 * par jour (`sleep.nightly`, `activity.stepsDaily`, `heart.restingDaily`...). La densité
 * des échantillons bruts (fréquence cardiaque, stress...) importe peu ici : ils sont
 * agrégés en un point par jour avant de sortir du [ReportBuilder], donc seul le nombre de
 * jours couverts fait grossir le JSON final — pas le nombre d'échantillons par jour.
 *
 * N'affirme rien : écrit le résultat dans un fichier, pour qu'il soit lu tel quel plutôt
 * que reconstruit depuis une estimation.
 */
class ChatContextSizeTest {

    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2018, 1, 1)

    private fun day(n: Int) = start.plusDays(n.toLong())
    private fun at(n: Int, hour: Int, minute: Int = 0): Instant = day(n).atTime(hour, minute).toInstant(zone)

    @Test
    fun `mesure la taille du ReportModel sur quatre ans d'historique quasi quotidien`() = measure(days = 1460, label = "4 ans")

    @Test
    fun `mesure la taille du ReportModel sur huit ans d'historique quasi quotidien`() = measure(days = 2920, label = "8 ans")

    private fun measure(days: Int, label: String) {
        val sleepNights = (0 until days).map { n ->
            SleepNight(
                id = "sleep-$n",
                date = day(n),
                bedTime = day(n - 1).atTime(23, 0).toInstant(zone),
                wakeTime = day(n).atTime(7, 0).toInstant(zone),
                durationMinutes = 480,
                score = 70 + (n % 20),
                efficiencyPercent = 88f,
                localBedTime = LocalTime.of(23, 0),
            )
        }
        val heartRates = (0 until days).flatMap { n ->
            (0 until 5).map { i -> HeartRateSample("hr-$n-$i", at(n, 6 + i * 3), 55 + (n + i) % 30) }
        }
        val stress = (0 until days).flatMap { n ->
            (0 until 3).map { i -> StressSample("st-$n-$i", at(n, 8 + i * 4), at(n, 8 + i * 4, 10), 20 + (n + i) % 60) }
        }
        val hrv = (0 until days).map { n -> HrvSample("hrv-$n", at(n, 7), sdnnMillis = 45f, rmssdMillis = 38f) }
        val spo2 = (0 until days).map { n -> SpO2Sample("spo2-$n", at(n, 3), percent = 96f) }
        val dailySteps = (0 until days).map { n -> DailySteps(day(n), steps = 4000 + (n % 8000)) }
        val dailyFloors = (0 until days).map { n -> DailyFloors(day(n), floors = n % 15) }
        val energyScores = (0 until days).map { n -> EnergyScore(day(n), total = 60 + (n % 40)) }
        val respiratoryRates = (0 until days).map { n -> RespiratoryRateSample("resp-$n", at(n, 4), breathsPerMinute = 14f) }
        val skinTemperatures = (0 until days).map { n -> SkinTemperatureSample("skin-$n", at(n, 4), celsius = 34.5f) }
        val bodyCompositions = (0 until days step 7).map { n -> BodyComposition("body-$n", at(n, 8), weightKg = 78f) }
        val exerciseSessions = (0 until days step 2).map { n ->
            ExerciseSession(
                id = "ex-$n",
                kind = ExerciseKind.RUNNING,
                samsungTypeCode = 1002,
                start = at(n, 18),
                end = at(n, 18, 30),
                durationMinutes = 30,
                calories = 250f,
                distanceMeters = 5000f,
                meanHeartRate = 140,
            )
        }

        val input = ReportInput(
            range = day(0)..day(days - 1),
            sleepNights = sleepNights,
            heartRates = heartRates,
            stress = stress,
            hrv = hrv,
            spO2 = spo2,
            dailySteps = dailySteps,
            dailyFloors = dailyFloors,
            energyScores = energyScores,
            respiratoryRates = respiratoryRates,
            skinTemperatures = skinTemperatures,
            bodyCompositions = bodyCompositions,
            exerciseSessions = exerciseSessions,
        )

        val model = ReportBuilder(zone).build(input)

        val json = Json { encodeDefaults = true }
        val reportJson = json.encodeToString(model)

        // Le coût que l'ancien chemin payait à CHAQUE envoi, sur le thread principal :
        // échapper `reportJson` en littéral JavaScript pour `evaluateJavascript`. Le nouveau
        // chemin (`shouldInterceptRequest`) ne le paie plus du tout — `JsonModelInterceptor.update`
        // n'est qu'une affectation de référence. Moyenné sur plusieurs passes, après une
        // passe de chauffe, pour amortir la variance de la JVM.
        repeat(3) { json.encodeToString(String.serializer(), reportJson) } // chauffe
        val escapeRuns = 20
        val escapeNanos = (1..escapeRuns).map {
            val start = System.nanoTime()
            json.encodeToString(String.serializer(), reportJson)
            System.nanoTime() - start
        }
        val escapeMeanMillis = escapeNanos.average() / 1_000_000.0
        val doubleEncodedLiteral = json.encodeToString(String.serializer(), reportJson)

        val reportBytes = reportJson.toByteArray(StandardCharsets.UTF_8).size
        val literalBytes = doubleEncodedLiteral.toByteArray(StandardCharsets.UTF_8).size

        val report = buildString {
            appendLine("=== $label ($days jours) ===")
            appendLine("nuits=${sleepNights.size}")
            appendLine("reportJson.length(UTF-16 code units)=${reportJson.length}")
            appendLine("reportJson bytes(UTF-8)=$reportBytes")
            appendLine("literal (double-encodé, ce qui traverse evaluateJavascript) length=${doubleEncodedLiteral.length}")
            appendLine("literal bytes(UTF-8)=$literalBytes")
            appendLine("script complet (HA.host.setConversation(...);) longueur approx=${literalBytes + 40}")
            appendLine(
                "coût de l'échappement double (ancien chemin, evaluateJavascript), " +
                    "moyenne sur $escapeRuns passes après chauffe = %.2f ms".format(escapeMeanMillis),
            )
        }
        println(report)
        // Le résultat va dans le répertoire de construction, pas dans un chemin absolu.
        // La version précédente écrivait dans un dossier de travail propre à une machine :
        // ailleurs — sur le serveur d'intégration, par exemple — le dossier parent n'existe
        // pas, `appendText` lève `FileNotFoundException`, et la mesure fait échouer toute la
        // suite de tests au lieu de simplement ne rien mesurer.
        val out = File("build/reports/report-size-measurement.txt")
        out.parentFile?.mkdirs()
        out.appendText(report)
    }
}
