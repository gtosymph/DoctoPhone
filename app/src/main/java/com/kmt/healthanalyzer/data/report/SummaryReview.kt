package com.kmt.healthanalyzer.data.report

import com.kmt.healthanalyzer.domain.drift.DriftDetector
import com.kmt.healthanalyzer.domain.drift.DriftNarrator
import com.kmt.healthanalyzer.domain.drift.MetricReview
import com.kmt.healthanalyzer.domain.drift.WeeklyReadiness
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * Une ligne du tableau hebdomadaire de la synthèse. **Que des chaînes déjà formatées.**
 *
 * [recent] vaut « — » quand la mesure manque : la ligne reste, parce qu'un médecin doit
 * voir qu'on suit cette mesure et qu'elle n'a rien donné, pas croire qu'on ne la suit pas.
 *
 * [note] dit sur combien de jours repose le chiffre, mais seulement quand la semaine est
 * trop trouée pour qu'une comparaison soit affirmée. Sept jours sur sept se passe de
 * commentaire.
 */
@Serializable
data class SummaryMetric(
    val label: String,
    val recent: String,
    val baseline: String,
    val delta: String? = null,
    val note: String? = null,
)

/**
 * Le bloc hebdomadaire de la synthèse médecin, prêt à poser dans la page.
 *
 * ## Pourquoi ce type existe
 *
 * Les dérives sont calculées en Kotlin ([com.kmt.healthanalyzer.domain.drift]) ; la
 * synthèse est mise en page en JavaScript, comme tout le reste du rapport. Porter le moteur
 * de dérives une seconde fois donnerait deux implémentations d'une logique de santé, qui
 * finiraient par diverger — le projet accepte déjà ce doublon pour `ReportModel` et le
 * catalogue de séries, chaque fois sous la garde d'un test de parité, et il ne vaut pas la
 * peine de le payer une troisième fois.
 *
 * **Kotlin calcule et formate, JavaScript pose des chaînes.** Les conventions françaises —
 * virgule décimale, espace fine pour les milliers, durées en « 6 h 20 » — vivent dans
 * [com.kmt.healthanalyzer.domain.report.TileFormat] et n'en sortent pas.
 *
 * Conséquence assumée : la version web, qui n'a pas de moteur de dérives, produit une
 * synthèse sans ce bloc. Le gabarit le prévoit et l'omet.
 *
 * ## Ce qui ne traverse jamais
 *
 * Aucun texte de modèle de langage. Le bilan rédigé reste dans le rapport complet, où
 * l'utilisateur sait d'où il vient. Une synthèse remise à un médecin ne porte que des
 * chiffres mesurés et des phrases calculées.
 */
@Serializable
data class SummaryReview(
    val periodLabel: String,
    val metrics: List<SummaryMetric>,
    val drifts: List<String>,
    val readinessKind: String,
    val readinessMeasuredDays: Int,
    val readinessRequiredDays: Int,
) {
    companion object {

        fun from(review: WeeklyReview): SummaryReview = SummaryReview(
            periodLabel = periodLabel(review.from, review.to),
            metrics = review.metrics.map { it.toSummaryMetric() },
            drifts = review.drifts.map(DriftNarrator::describe),
            readinessKind = review.readiness.kind(),
            readinessMeasuredDays = review.readiness.measuredDays(),
            readinessRequiredDays = review.readiness.requiredDays(),
        )

        private fun MetricReview.toSummaryMetric(): SummaryMetric = SummaryMetric(
            label = metric.label,
            recent = recentValue?.let(metric::format) ?: MISSING,
            baseline = baselineValue?.let(metric::format) ?: MISSING,
            delta = delta?.let { value ->
                // Un vrai signe moins typographique (U+2212), comme partout ailleurs dans le
                // rapport : le trait d'union du clavier est plus court et ne s'aligne pas sur
                // la chasse du plus, ce qui se voit dans une colonne de variations.
                val sign = if (value > 0) "+" else "−"
                sign + metric.format(abs(value))
            },
            note = noteFor(recentDays),
        )

        /**
         * « 4 jours mesurés sur 7 », et seulement quand la semaine est trop trouée pour
         * qu'une dérive soit affirmée.
         *
         * Un médecin doit pouvoir juger sur quoi repose le chiffre qu'on lui montre. Le
         * silence, ici, veut dire « semaine complète ».
         */
        private fun noteFor(recentDays: Int): String? = when {
            recentDays == 0 -> null
            recentDays >= DriftDetector.MIN_RECENT_MEASURED_DAYS -> null
            recentDays == 1 -> "1 jour mesuré sur ${DriftDetector.RECENT_WINDOW_DAYS}"
            else -> "$recentDays jours mesurés sur ${DriftDetector.RECENT_WINDOW_DAYS}"
        }

        private fun WeeklyReadiness.kind(): String = when (this) {
            WeeklyReadiness.Ready -> "ready"
            is WeeklyReadiness.NotEnoughBaseline -> "baseline"
            is WeeklyReadiness.NotEnoughRecent -> "recent"
        }

        private fun WeeklyReadiness.measuredDays(): Int = when (this) {
            WeeklyReadiness.Ready -> 0
            is WeeklyReadiness.NotEnoughBaseline -> measuredDays
            is WeeklyReadiness.NotEnoughRecent -> measuredDays
        }

        private fun WeeklyReadiness.requiredDays(): Int = when (this) {
            WeeklyReadiness.Ready -> 0
            is WeeklyReadiness.NotEnoughBaseline -> requiredDays
            is WeeklyReadiness.NotEnoughRecent -> requiredDays
        }

        /**
         * « du 5 au 11 septembre 2026 ».
         *
         * L'année figure, contrairement à l'écran d'accueil : un document imprimé se retrouve
         * dans un dossier des mois plus tard, et « du 5 au 11 septembre » n'y dit plus rien.
         */
        private fun periodLabel(from: LocalDate, to: LocalDate): String {
            val dayOnly = DateTimeFormatter.ofPattern("d", Locale.FRANCE)
            val full = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
            val start = if (from.month == to.month && from.year == to.year) {
                dayOnly.format(from)
            } else {
                full.format(from)
            }
            return "du $start au ${full.format(to)}"
        }

        /** Le tiret cadratin du reste du rapport, pas un trait d'union ni une case vide. */
        private const val MISSING = "—"
    }
}
