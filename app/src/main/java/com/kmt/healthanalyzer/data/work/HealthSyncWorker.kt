package com.kmt.healthanalyzer.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kmt.healthanalyzer.data.repository.HealthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId

/**
 * Reprend dans Health Connect ce qui suit le dernier jour déjà en base, une fois par nuit.
 *
 * Même calcul de départ que `ImportViewModel.syncHealthConnect` (le geste manuel) : reprendre
 * là où la base s'arrête, ou remonter 30 jours faute d'historique local. Les deux chemins
 * restent des appels séparés à [HealthRepository.syncFromHealthConnect] — ce fichier ne
 * duplique aucun calcul, il choisit seulement une valeur de départ avant de lui passer la
 * main.
 *
 * Silencieuse par construction, comme demandé : jamais de notification, jamais d'exception
 * qui remonterait à l'utilisateur. Un échec (Health Connect momentanément indisponible,
 * permission retirée) déclenche une nouvelle tentative avec recul via [Result.retry], puis
 * abandonne sans bruit après [MAX_ATTEMPTS] essais — la nuit suivante retentera de toute
 * façon, la tâche étant périodique.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: HealthRepository,
    private val zone: ZoneId,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val from = repository.lastRecordedDay()
            ?.plusDays(1)
            ?.atStartOfDay(zone)
            ?.toInstant()
            ?: Instant.now().minusSeconds(DEFAULT_SYNC_WINDOW_SECONDS)

        repository.syncFromHealthConnect(from)
        Result.success()
    } catch (failure: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        /** Sans donnée locale, la première synchronisation remonte à 30 jours. */
        const val DEFAULT_SYNC_WINDOW_SECONDS = 30L * 24 * 3600
        const val MAX_ATTEMPTS = 3
    }
}
