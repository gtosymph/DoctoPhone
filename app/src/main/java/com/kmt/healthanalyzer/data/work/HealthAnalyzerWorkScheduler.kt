package com.kmt.healthanalyzer.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme (et arrête) les deux tâches de fond du chantier : la synchronisation Health
 * Connect nocturne et le bilan de dérives hebdomadaire.
 *
 * Point d'entrée unique de ce côté : `HealthAnalyzerApp` l'appelle au démarrage selon le
 * réglage courant, et `SettingsViewModel` l'appelle à chaque bascule du réglage
 * « bilans automatiques ». `enqueueUniquePeriodicWork` rend l'appel idempotent — relancer
 * [schedule] à chaque démarrage de l'app ne recrée pas la tâche ni ne redécale son horaire.
 */
@Singleton
class HealthAnalyzerWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun schedule() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, syncRequest())
        workManager.enqueueUniquePeriodicWork(DRIFT_CHECK_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, driftCheckRequest())
    }

    /** Coupe les deux tâches — c'est le « tout couper » du réglage. */
    fun cancel() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(SYNC_WORK_NAME)
        workManager.cancelUniqueWork(DRIFT_CHECK_WORK_NAME)
    }

    /**
     * Synchronisation nocturne : une fois par jour suffit, les mesures de santé n'ont pas
     * besoin d'être fraîches à la minute. Contraintes demandées par le chantier —
     * batterie pas faible, appareil au repos — pour qu'elle ne se voie jamais : Health
     * Connect est une lecture locale, sans réseau, donc aucune contrainte de connexion.
     */
    private fun syncRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(syncConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    /**
     * Contraintes de la synchronisation de nuit.
     *
     * **Ne remets jamais `setRequiresDeviceIdle(true)` ici.** Android interdit de combiner le
     * mode « appareil au repos » et une politique de reprise, et le refus n'arrive pas à la
     * compilation mais à la mise en file :
     *
     *     java.lang.IllegalArgumentException: Cannot set backoff criteria on an idle mode job
     *
     * Comme la programmation part du démarrage de l'app, l'exception fermait l'app à
     * l'ouverture, avant tout écran. C'est arrivé en vrai, sur l'appareil de l'utilisateur.
     *
     * Entre les deux, la reprise vaut mieux que le repos de l'appareil. Sans reprise, une
     * synchronisation qui échoue est perdue jusqu'au lendemain. Et « appareil au repos » est
     * une contrainte sévère : sur un téléphone utilisé dans la journée, elle peut retarder la
     * tâche de plusieurs jours. La lecture de Health Connect est locale et brève — elle n'a
     * pas besoin que le téléphone dorme.
     */
    internal fun syncConstraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Bilan hebdomadaire : pas de contrainte de repos de l'appareil — contrairement à la
     * synchronisation, il doit pouvoir prévenir l'utilisateur, donc tourner à une heure où
     * une notification a une chance d'être vue, pas seulement en pleine nuit.
     */
    private fun driftCheckRequest(): PeriodicWorkRequest {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        return PeriodicWorkRequestBuilder<WeeklyDriftCheckWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        const val SYNC_WORK_NAME = "health_connect_nightly_sync"
        const val DRIFT_CHECK_WORK_NAME = "weekly_drift_check"
    }
}
