package com.kmt.healthanalyzer.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.preferences.AppSettings
import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.domain.drift.DriftKind
import com.kmt.healthanalyzer.domain.drift.DriftMetric
import com.kmt.healthanalyzer.domain.drift.DriftReport
import com.kmt.healthanalyzer.domain.drift.MetricDrift
import com.kmt.healthanalyzer.domain.usecase.DetectHealthDriftsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le point le plus important du bilan hebdomadaire : **ne rien envoyer** quand il n'y a
 * rien à dire. Une app qui notifie chaque semaine « tout va bien » finit désactivée — voir
 * la documentation de [WeeklyDriftCheckWorker].
 */
class WeeklyDriftCheckWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)
    private val detectDrifts: DetectHealthDriftsUseCase = mockk()
    private val preferences: AppPreferences = mockk()
    private val notifier: DriftNotifier = mockk(relaxed = true)

    private fun settingsFlow(autoChecksEnabled: Boolean) = flowOf(
        AppSettings(provider = LlmProvider.ANTHROPIC, model = "claude", sleepTargetMinutes = 450, autoChecksEnabled = autoChecksEnabled),
    )

    private val sampleDrift = MetricDrift(
        metric = DriftMetric.RESTING_HEART_RATE,
        kind = DriftKind.MEAN_SHIFT,
        baselineMean = 54.0,
        baselineStdDev = 1.0,
        baselineDays = 30,
        recentMean = 60.0,
        recentStdDev = 1.0,
        recentDays = 6,
        signalStrength = 5.0,
    )

    @Test
    fun `sans aucune derive, le bilan n'envoie aucune notification`() = runTest {
        every { preferences.settings } returns settingsFlow(autoChecksEnabled = true)
        coEvery { detectDrifts() } returns DriftReport(generatedAt = Instant.now(), drifts = emptyList())

        val worker = WeeklyDriftCheckWorker(context, params, detectDrifts, preferences, notifier)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { notifier.notify(any()) }
    }

    @Test
    fun `avec une derive, le bilan envoie une notification`() = runTest {
        every { preferences.settings } returns settingsFlow(autoChecksEnabled = true)
        coEvery { detectDrifts() } returns DriftReport(generatedAt = Instant.now(), drifts = listOf(sampleDrift))

        val worker = WeeklyDriftCheckWorker(context, params, detectDrifts, preferences, notifier)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { notifier.notify(any()) }
    }

    @Test
    fun `reglage desactive, aucun calcul ni notification`() = runTest {
        every { preferences.settings } returns settingsFlow(autoChecksEnabled = false)

        val worker = WeeklyDriftCheckWorker(context, params, detectDrifts, preferences, notifier)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { detectDrifts() }
        coVerify(exactly = 0) { notifier.notify(any()) }
    }

    @Test
    fun `un echec du calcul echoue silencieusement`() = runTest {
        every { preferences.settings } returns settingsFlow(autoChecksEnabled = true)
        coEvery { detectDrifts() } throws IllegalStateException("panne")

        val worker = WeeklyDriftCheckWorker(context, params, detectDrifts, preferences, notifier)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { notifier.notify(any()) }
    }
}
