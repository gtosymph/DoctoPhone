package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.drift.DriftDetector
import com.kmt.healthanalyzer.domain.drift.DriftReport
import com.kmt.healthanalyzer.domain.drift.HealthDriftAnalyzer
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Point d'entrée unique de la détection de dérives : construit le rapport de la fenêtre
 * nécessaire ([DriftDetector.TOTAL_WINDOW_DAYS] jours) puis le fait analyser par
 * [HealthDriftAnalyzer].
 *
 * Appelé à la fois par l'écran (dérives affichées à l'ouverture du rapport) et par
 * `WeeklyDriftCheckWorker` (bilan hebdomadaire en tâche de fond) : un seul chemin de
 * calcul, jamais deux implémentations qui pourraient diverger.
 *
 * Entièrement lu sur l'appareil : [HealthRepository.buildReport] ne quitte jamais
 * l'appareil (voir sa documentation), et cette classe ne l'envoie nulle part non plus.
 * Aucune donnée de santé ne sort de ce chantier.
 *
 * `today` n'est délibérément pas un paramètre : un paramètre à valeur par défaut qui lit un
 * champ de l'instance (`LocalDate.now(zone)`) se résout, côté Kotlin, par un appel au
 * champ réel au moment de l'appel — ce qui échoue sur une instance simulée par un test
 * (`mockk()`), dont les champs ne sont jamais initialisés. Calculer `today` à l'intérieur
 * de [invoke] évite l'écueil et garde l'appelant plus simple.
 */
class DetectHealthDriftsUseCase @Inject constructor(
    private val repository: HealthRepository,
    private val zone: ZoneId,
) {
    private val analyzer = HealthDriftAnalyzer()

    suspend operator fun invoke(): DriftReport {
        val today = LocalDate.now(zone)
        val range = today.minusDays((DriftDetector.TOTAL_WINDOW_DAYS - 1).toLong())..today
        val report = repository.buildReport(range, zone)
        return analyzer.analyze(report, today)
    }
}
