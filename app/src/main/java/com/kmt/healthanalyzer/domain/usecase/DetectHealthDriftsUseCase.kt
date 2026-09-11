package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.domain.drift.DriftReport
import javax.inject.Inject

/**
 * Les dérives seules, extraites du bilan hebdomadaire.
 *
 * Appelé par l'écran de rapport (`DriftViewModel`) et par `WeeklyDriftCheckWorker`. Il ne
 * calcule rien de son côté : il délègue à [ReviewWeekUseCase], qui alimente aussi l'écran
 * d'accueil « Cette semaine ». Un seul chemin de calcul pour trois consommateurs, jamais
 * deux implémentations qui pourraient diverger — l'utilisateur ne doit pas voir un écran
 * qui contredit sa notification.
 *
 * Entièrement lu sur l'appareil ; aucune donnée de santé ne sort de ce chantier.
 */
class DetectHealthDriftsUseCase @Inject constructor(
    private val reviewWeek: ReviewWeekUseCase,
) {
    suspend operator fun invoke(): DriftReport {
        val review = reviewWeek()
        return DriftReport(generatedAt = review.generatedAt, drifts = review.drifts)
    }
}
