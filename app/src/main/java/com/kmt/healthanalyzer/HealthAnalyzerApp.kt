package com.kmt.healthanalyzer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.work.HealthAnalyzerWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implémente [Configuration.Provider] pour que WorkManager utilise [workerFactory] — sans
 * quoi les tâches [com.kmt.healthanalyzer.data.work.HealthSyncWorker] et
 * [com.kmt.healthanalyzer.data.work.WeeklyDriftCheckWorker], qui reçoivent leurs
 * dépendances par injection Hilt plutôt que par un constructeur sans argument, ne pourraient
 * jamais être instanciées par WorkManager.
 *
 * Le réglage « bilans automatiques » est relu à chaque démarrage : c'est ici, pas
 * seulement dans les réglages, que la programmation des tâches périodiques est (re)posée —
 * un désinstalle-réinstalle ou une restauration de sauvegarde doit retrouver le même état.
 */
@HiltAndroidApp
class HealthAnalyzerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var scheduler: HealthAnalyzerWorkScheduler

    @Inject lateinit var preferences: AppPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (preferences.settings.first().autoChecksEnabled) scheduler.schedule() else scheduler.cancel()
        }
    }
}
