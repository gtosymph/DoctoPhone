package com.kmt.healthanalyzer.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kmt.healthanalyzer.domain.drift.DriftMetric
import com.kmt.healthanalyzer.domain.drift.DriftNarrator
import com.kmt.healthanalyzer.domain.drift.MetricDrift
import com.kmt.healthanalyzer.ui.components.SectionLabel
import com.kmt.healthanalyzer.ui.theme.DomainColor
import com.kmt.healthanalyzer.ui.theme.DomainColors
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors

/**
 * Montre les dérives détectées entre la référence personnelle et la semaine écoulée — voir
 * `DetectHealthDriftsUseCase` pour le calcul, `DriftNarrator` pour le texte.
 *
 * Sans dérive — le cas le plus fréquent — l'écran le dit sobrement, sans que cela ressemble
 * à une erreur : une simple ligne, l'icône et le ton d'un état normal, pas d'un vide.
 * Pendant le chargement, rien ne s'affiche : la section apparaît une fois le résultat prêt,
 * plutôt que de clignoter un état vide avant de se remplir.
 */
@Composable
fun DriftSection(state: DriftUiState, modifier: Modifier = Modifier) {
    if (state.isLoading) return

    val domain = HealthAnalyzerTheme.domainColors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "Dérives récentes", accent = domain.vitality.base)

        when {
            state.errorMessage != null -> Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.drifts.isEmpty() -> NoDriftRow()

            else -> state.drifts.forEach { drift ->
                DriftRow(drift = drift, color = drift.metric.domainColor(domain))
            }
        }
    }
}

@Composable
private fun NoDriftRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Aucune dérive détectée : vos mesures récentes restent dans vos habitudes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DriftRow(drift: MetricDrift, color: DomainColor) {
    Card(colors = CardDefaults.cardColors(containerColor = color.soft)) {
        Text(
            text = DriftNarrator.describe(drift),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color.ink,
        )
    }
}

/**
 * Une dérive porte la couleur de son domaine, à l'image du reste de l'écran d'accueil :
 * sommeil (durée, régularité), cœur (fréquence de repos, HRV), activité (pas), corps (poids).
 */
private fun DriftMetric.domainColor(domain: DomainColors): DomainColor = when (this) {
    DriftMetric.SLEEP_DURATION, DriftMetric.SLEEP_REGULARITY -> domain.sleep
    DriftMetric.RESTING_HEART_RATE, DriftMetric.HRV -> domain.heart
    DriftMetric.STEPS -> domain.activity
    DriftMetric.WEIGHT -> domain.body
}
