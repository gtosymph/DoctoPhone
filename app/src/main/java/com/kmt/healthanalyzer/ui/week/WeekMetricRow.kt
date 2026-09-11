package com.kmt.healthanalyzer.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kmt.healthanalyzer.domain.drift.DayPoint
import com.kmt.healthanalyzer.domain.drift.DriftDetector
import com.kmt.healthanalyzer.domain.drift.DriftMetric
import com.kmt.healthanalyzer.domain.drift.MetricReview
import com.kmt.healthanalyzer.ui.theme.DomainColor
import com.kmt.healthanalyzer.ui.theme.DomainColors
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Une ligne de mesure : le libellé et sa référence, la valeur de la semaine et l'écart, la
 * courbe des sept jours.
 *
 * Une ligne, pas une carte. La direction éditoriale réserve le cadre à ce qui est
 * détachable ; six cartes empilées ici donneraient six objets de même poids là où il ne
 * s'agit que d'une liste. Ce sont les filets et le blanc qui séparent.
 */
@Composable
fun WeekMetricRow(row: MetricReview, today: LocalDate, modifier: Modifier = Modifier) {
    val color = row.metric.domainColor(HealthAnalyzerTheme.domainColors)

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.metric.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.secondaryLine(today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = row.recentValue?.let(row.metric::format) ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (row.recentValue == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                row.DeltaLine()
            }

            WeekSparkline(
                points = row.week,
                color = color.base,
                modifier = Modifier.width(74.dp).height(26.dp),
            )
        }
    }
}

/**
 * L'écart à la référence, signé et coloré selon le sens de la mesure.
 *
 * Sans couleur pour le poids : [DriftMetric.higherIsBetter] y vaut `null`, parce que
 * grossir ou maigrir n'est bon ou mauvais que dans un contexte que cette app n'a pas.
 */
@Composable
private fun MetricReview.DeltaLine() {
    val delta = delta
    if (delta == null || baselineValue == null) {
        Text(
            text = " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val trend = HealthAnalyzerTheme.trendColors
    val improving = metric.higherIsBetter?.let { higherIsBetter -> (delta > 0) == higherIsBetter }
    val color = when {
        abs(delta) < 1e-9 || improving == null -> trend.neutral
        improving -> trend.improvement
        else -> trend.degradation
    }
    val sign = if (delta > 0) "+" else "−"

    Text(
        text = "$sign${metric.format(abs(delta))}",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/**
 * La seconde ligne : la référence, et ce qui manque quand il manque quelque chose.
 *
 * Cette ligne a été réécrite après avoir vu l'écran sur un vrai jeu de données. L'archive
 * importée s'arrêtait quelques jours plus tôt, et les six mesures montraient un tiret sous
 * une référence bien remplie. L'écran donnait à lire « aucune donnée », alors que la vraie
 * information était « il manque les derniers jours ».
 *
 * Trois choses peuvent donc s'y trouver, dans cet ordre d'utilité :
 * - la référence, qui donne l'échelle de lecture de la valeur ;
 * - le nombre de jours mesurés, quand la semaine est trop trouée pour qu'une dérive soit
 *   affirmée. La valeur affichée reste juste, mais elle repose sur moins de jours et
 *   l'utilisateur doit pouvoir en juger ;
 * - la date de la dernière mesure, quand il n'y a rien d'autre à dire. « Dernière mesure il
 *   y a 34 jours » vaut mieux qu'un tiret muet : l'app fonctionne, c'est la mesure qui
 *   manque.
 */
private fun MetricReview.secondaryLine(today: LocalDate): String {
    val parts = buildList {
        baselineValue?.let { add("référence ${metric.format(it)}") }

        val window = DriftDetector.RECENT_WINDOW_DAYS
        if (recentDays in 1 until DriftDetector.MIN_RECENT_MEASURED_DAYS) {
            add(if (recentDays == 1) "1 jour mesuré sur $window" else "$recentDays jours mesurés sur $window")
        }

        if (isEmpty()) {
            val days = lastMeasuredOn?.let { ChronoUnit.DAYS.between(it, today) }
            add(
                when {
                    days == null -> "aucune mesure"
                    days <= 0L -> "mesurée aujourd'hui, pas encore assez d'historique"
                    days == 1L -> "dernière mesure hier"
                    else -> "dernière mesure il y a $days jours"
                },
            )
        }
    }
    return parts.joinToString(" · ")
}

/**
 * Les sept jours en petit : un trait, et le dernier point marqué.
 *
 * Sans axe, sans grille, sans échelle affichée — ce n'est pas un graphique du rapport, c'est
 * la forme de la semaine. Trois règles tiennent ce dessin :
 *
 * - un trou reste un trou. Le trait se coupe, il ne rejoint pas deux points en sautant les
 *   jours manquants, ce qui inventerait une continuité qui n'existe pas ;
 * - le dernier point mesuré est marqué, parce que c'est l'état actuel et que c'est ce que
 *   l'œil cherche ;
 * - moins de deux points mesurés donnent un trait pointillé, pas une courbe plate. Une
 *   ligne droite se lirait comme une semaine parfaitement stable.
 */
@Composable
fun WeekSparkline(points: List<DayPoint>, color: Color, modifier: Modifier = Modifier) {
    val measured = points.count { it.value != null }
    val empty = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier.clearAndSetSemantics {
            // La ligne porte déjà ses chiffres en toutes lettres : décrire la courbe en plus
            // ferait répéter la même information à un lecteur d'écran.
            contentDescription = ""
        },
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = 2.6.dp.toPx()
        val inset = radius + strokeWidth

        if (measured < 2) {
            val middle = size.height / 2f
            drawLine(
                color = empty,
                start = androidx.compose.ui.geometry.Offset(inset, middle),
                end = androidx.compose.ui.geometry.Offset(size.width - inset, middle),
                strokeWidth = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
            return@Canvas
        }

        val values = points.mapNotNull { it.value }
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 1e-9 }

        fun xOf(index: Int): Float =
            inset + (size.width - 2 * inset) * index / (points.size - 1).coerceAtLeast(1)

        fun yOf(value: Double): Float {
            // Sans étendue — sept jours rigoureusement identiques — la courbe se pose au
            // milieu plutôt que de diviser par zéro ou de coller à un bord.
            val ratio = span?.let { (value - min) / it } ?: 0.5
            return (size.height - inset) - (size.height - 2 * inset) * ratio.toFloat()
        }

        val path = Path()
        var started = false
        points.forEachIndexed { index, point ->
            val value = point.value
            if (value == null) {
                started = false
                return@forEachIndexed
            }
            val x = xOf(index)
            val y = yOf(value)
            if (started) path.lineTo(x, y) else path.moveTo(x, y)
            started = true
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth))

        val lastIndex = points.indexOfLast { it.value != null }
        if (lastIndex >= 0) {
            drawCircle(
                color = color,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(xOf(lastIndex), yOf(points[lastIndex].value!!)),
            )
        }
    }
}

/**
 * Une mesure porte la couleur de son domaine : sommeil (durée, régularité), cœur (fréquence
 * de repos, HRV), activité (pas), corps (poids).
 *
 * Même correspondance que `DriftSection` sur l'écran de rapport — les deux montrent les
 * mêmes mesures et doivent les colorer pareil.
 */
internal fun DriftMetric.domainColor(domain: DomainColors): DomainColor = when (this) {
    DriftMetric.SLEEP_DURATION, DriftMetric.SLEEP_REGULARITY -> domain.sleep
    DriftMetric.RESTING_HEART_RATE, DriftMetric.HRV -> domain.heart
    DriftMetric.STEPS -> domain.activity
    DriftMetric.WEIGHT -> domain.body
}
