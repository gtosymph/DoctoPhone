package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.drift.DriftDetector
import com.kmt.healthanalyzer.domain.drift.HealthDriftAnalyzer
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Point d'entrée unique du bilan de la semaine : construit le rapport de la fenêtre
 * nécessaire ([DriftDetector.TOTAL_WINDOW_DAYS] jours) puis le fait résumer par
 * [HealthDriftAnalyzer].
 *
 * C'est la source de l'écran d'accueil « Cette semaine ». [DetectHealthDriftsUseCase]
 * passe par ici et n'en garde que les dérives : l'écran d'accueil, l'écran de rapport et la
 * notification hebdomadaire lisent donc **un seul calcul**. Deux chemins séparés finiraient
 * par diverger, et l'utilisateur verrait un écran qui contredit sa notification.
 *
 * Entièrement lu sur l'appareil : [HealthRepository.buildReport] ne quitte jamais
 * l'appareil, et cette classe ne l'envoie nulle part non plus. Aucune donnée de santé ne
 * sort de ce chantier.
 *
 * `today` n'est délibérément pas un paramètre à valeur par défaut. Un défaut qui lit un
 * champ de l'instance (`LocalDate.now(zone)`) se résout, côté Kotlin, par un appel au champ
 * réel au moment de l'appel — ce qui échoue sur une instance simulée par un test
 * (`mockk()`), dont les champs ne sont jamais initialisés.
 */
class ReviewWeekUseCase @Inject constructor(
    private val repository: HealthRepository,
    private val zone: ZoneId,
) {
    private val analyzer = HealthDriftAnalyzer()

    suspend operator fun invoke(): WeeklyReview {
        val today = LocalDate.now(zone)
        val range = today.minusDays((DriftDetector.TOTAL_WINDOW_DAYS - 1).toLong())..today
        val report = repository.buildReport(range, zone)
        return analyzer.review(report, today)
    }
}
