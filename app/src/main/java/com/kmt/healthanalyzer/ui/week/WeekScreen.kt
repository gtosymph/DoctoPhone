package com.kmt.healthanalyzer.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmt.healthanalyzer.domain.drift.DriftNarrator
import com.kmt.healthanalyzer.domain.drift.MetricDrift
import com.kmt.healthanalyzer.domain.drift.WeeklyReadiness
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import com.kmt.healthanalyzer.ui.components.ScreenHeader
import com.kmt.healthanalyzer.ui.components.SectionLabel
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'écran d'accueil : le bilan des sept derniers jours.
 *
 * ## Pourquoi hebdomadaire, et pas « Aujourd'hui »
 *
 * L'app s'ouvre une fois par semaine, et avant un rendez-vous médical. Un écran qui
 * montrerait la nuit passée ne servirait donc presque jamais. La fenêtre de sept jours est
 * en outre exactement celle du moteur de dérives : l'écran réutilise un calcul qui existe
 * déjà au lieu d'en créer un second qui finirait par le contredire.
 *
 * ## Ce que cet écran ne fait pas
 *
 * Aucun graphique du rapport, aucun onglet de domaine, **aucun sélecteur de période**. Sa
 * fenêtre est fixe : sept jours contre les huit semaines précédentes. Lui ajouter un
 * sélecteur en ferait un second rapport, et l'app en a déjà un.
 */
@Composable
fun WeekScreen(
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = HealthAnalyzerTheme.domainColors.vitality.base

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(
            title = "Cette semaine",
            subtitle = state.review?.let { periodLabel(it.from, it.to) },
            accent = accent,
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Réglages")
                }
            },
        )

        when {
            !state.hasAnyData -> FirstUseBanner(onOpenImport = onOpenImport)

            state.errorMessage != null -> Text(
                text = state.errorMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            state.isLoading || state.review == null -> LoadingRow(accent)

            else -> {
                val review = state.review!!
                ChangedSection(review, accent)
                MetricsSection(review, accent)
                ReportSection(onOpenReport = onOpenReport)
            }
        }

        DataFootnote(state = state, onRefresh = viewModel::refresh)
    }
}

/**
 * Le bloc « Ce qui a changé », en tête parce que c'est la seule chose que l'utilisateur ne
 * peut pas lire sur sa montre.
 *
 * Trois états, et il faut les distinguer : « aucune dérive » et « pas assez de données » se
 * ressemblent à l'écran — les deux montrent une liste vide — et ne veulent pas du tout dire
 * la même chose. Un écran vide sans explication se lit comme une panne.
 */
@Composable
private fun ChangedSection(review: WeeklyReview, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = "Ce qui a changé", accent = accent)

        when (val readiness = review.readiness) {
            is WeeklyReadiness.NotEnoughBaseline -> Explanation(
                "Il faut ${readiness.requiredDays} jours de référence pour comparer. " +
                    "Vous en avez ${readiness.measuredDays}.",
            )

            is WeeklyReadiness.NotEnoughRecent -> Explanation(
                "${readiness.requiredDays} jours mesurés sont nécessaires cette semaine. " +
                    "Vous en avez ${readiness.measuredDays}.",
            )

            WeeklyReadiness.Ready -> if (review.hasDrift) {
                review.drifts.forEach { DriftRow(it) }
            } else {
                NothingChangedRow()
            }
        }
    }
}

@Composable
private fun DriftRow(drift: MetricDrift) {
    val color = drift.metric.domainColor(HealthAnalyzerTheme.domainColors)
    Card(colors = CardDefaults.cardColors(containerColor = color.soft)) {
        Text(
            text = DriftNarrator.describe(drift),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color.ink,
        )
    }
}

@Composable
private fun NothingChangedRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Rien n'a bougé cette semaine par rapport à vos huit dernières semaines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Explanation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Les six mesures, toujours les six.
 *
 * Une mesure sans donnée garde sa ligne et dit ce qui lui manque. La retirer donnerait à
 * penser que l'app ne la suit pas, alors qu'elle attend seulement une pesée.
 */
@Composable
private fun MetricsSection(review: WeeklyReview, accent: Color) {
    Column {
        SectionLabel(text = "Les six mesures", accent = accent, modifier = Modifier.padding(bottom = 4.dp))
        review.metrics.forEach { row -> WeekMetricRow(row = row, today = review.to) }
    }
}

/**
 * La porte vers le rapport complet — le seul bloc encadré de l'écran.
 *
 * Le cadre se mérite : c'est ici, et nulle part ailleurs, qu'on produit un fichier à
 * emporter. L'export lui-même vit dans le rapport, où la période se choisit ; le dupliquer
 * ici obligerait à choisir une période sur un écran qui n'en a pas.
 */
@Composable
private fun ReportSection(onOpenReport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Préparer un rendez-vous", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Le rapport complet porte 19 graphiques, la période de votre choix, " +
                    "et l'export à emporter chez le médecin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenReport) { Text("Ouvrir le rapport complet") }
        }
    }
}

/** Tant qu'aucune mesure n'existe, tout le reste s'efface au profit du seul geste utile. */
@Composable
private fun FirstUseBanner(onOpenImport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aucune donnée pour l'instant", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Importez votre export Samsung Health, ou connectez Health Connect. " +
                    "Tout reste sur cet appareil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenImport) { Text("Importer mes données") }
        }
    }
}

@Composable
private fun LoadingRow(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accent, strokeWidth = 2.dp)
        Text(
            text = "Comparaison de la semaine écoulée à vos huit dernières semaines…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * L'état des données, en bas et en petit.
 *
 * Volontairement discret : il répond à « l'app est-elle à jour ? », une question qu'on ne
 * se pose qu'en cas de doute. La mettre en haut ferait passer l'intendance avant le bilan.
 */
@Composable
private fun DataFootnote(state: WeekUiState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.coveredDays?.let { "$it jours couverts" } ?: "Période couverte inconnue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh) { Text("Actualiser") }
    }
}

/**
 * « du 4 au 10 septembre », ou « du 28 août au 3 septembre » quand la semaine change de mois.
 *
 * Le mois n'est répété que s'il change : une date complète des deux côtés alourdit la ligne
 * sans rien apprendre.
 */
internal fun periodLabel(from: LocalDate, to: LocalDate): String {
    val dayOnly = DateTimeFormatter.ofPattern("d", Locale.FRANCE)
    val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
    val start = if (from.month == to.month) dayOnly.format(from) else dayMonth.format(from)
    return "du $start au ${dayMonth.format(to)}"
}
