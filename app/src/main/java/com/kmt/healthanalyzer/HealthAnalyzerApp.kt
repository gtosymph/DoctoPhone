package com.kmt.healthanalyzer

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.work.HealthAnalyzerWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
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
            // Rien de ce qui se passe ici ne doit pouvoir fermer l'app.
            //
            // Ce chemin s'exécute à chaque ouverture, avant le moindre écran, sans que
            // l'utilisateur ait rien demandé. Une exception non rattrapée dans cette coroutine
            // ferme l'app à l'ouverture, sans message et sans geste à incriminer — il ne reste
            // qu'une app qui ne s'ouvre plus.
            //
            // Ce n'est pas une précaution théorique : une contrainte de tâche incompatible
            // (`Cannot set backoff criteria on an idle mode job`) a produit exactement cela sur
            // l'appareil de l'utilisateur. La contrainte fautive est corrigée, mais la leçon
            // est que la programmation des tâches de fond ne vaut pas le démarrage de l'app.
            // Des bilans qui ne se programment pas sont un désagrément ; une app qui ne
            // s'ouvre plus est une panne.
            try {
                if (preferences.settings.first().autoChecksEnabled) scheduler.schedule() else scheduler.cancel()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "Programmation des tâches de fond impossible ; l'app continue sans.", failure)
            }
        }
    }

    private companion object {
        const val TAG = "HealthAnalyzerApp"
    }
}
