package com.kmt.healthanalyzer.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.domain.usecase.DetectHealthDriftsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Recalcule les dérives une fois par semaine et prévient l'utilisateur, **seulement s'il y
 * a quelque chose à dire**.
 *
 * La règle qui justifie ce fichier : une app qui notifie chaque semaine « tout va bien »
 * finit désactivée. [DetectHealthDriftsUseCase] est le seul calcul ; ce fichier ne fait que
 * décider s'il faut en informer l'utilisateur, et rien de plus.
 *
 * [AppPreferences.autoChecksEnabled] est revérifié ici en plus de conditionner l'inscription
 * de la tâche (`HealthAnalyzerWorkScheduler`) : une tâche déjà programmée avant que
 * l'utilisateur ne désactive le réglage doit s'arrêter d'elle-même à la prochaine échéance,
 * même si l'annulation n'a pas encore été prise en compte par WorkManager.
 */
@HiltWorker
class WeeklyDriftCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detectDrifts: DetectHealthDriftsUseCase,
    private val preferences: AppPreferences,
    private val notifier: DriftNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        if (preferences.settings.first().autoChecksEnabled) {
            val report = detectDrifts()
            if (report.hasDrift) notifier.notify(report)
        }
        Result.success()
    } catch (failure: Exception) {
        // Silencieux : pas de notification d'erreur, simplement rien cette semaine. La
        // tâche périodique retentera dans sept jours.
        Result.failure()
    }
}
