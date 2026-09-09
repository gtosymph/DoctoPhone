package com.kmt.healthanalyzer.ui.rationale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors

/**
 * Écran de justification exigé par Health Connect : explique pourquoi l'app lit des
 * données de santé, avant même que l'utilisateur ait ouvert l'app normalement.
 *
 * Affiché quand `MainActivity` reçoit `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
 * (Android 14+) ou `android.intent.action.VIEW_PERMISSION_USAGE` (Android 13 et avant).
 */
@Composable
fun PermissionsRationaleScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = HealthAnalyzerTheme.domainColors.vitality.base,
            )
            Text("Pourquoi Health Analyzer lit vos données de santé", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Health Analyzer lit les mesures de Health Connect (pas, sommeil, fréquence " +
                    "cardiaque, variabilité cardiaque, poids, saturation en oxygène et autres " +
                    "mesures associées) pour compléter l'historique importé depuis Samsung Health.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "L'app est en lecture seule : elle n'écrit jamais dans Health Connect.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Elle demande aussi la lecture de l'historique au-delà de 30 jours, car un bilan " +
                    "sur un mois ne montre pas de tendance : les graphiques ont besoin de plusieurs " +
                    "mois de recul.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Vos données restent sur l'appareil. Lors d'une analyse, seuls des agrégats " +
                    "hebdomadaires (moyennes, tendances) sont envoyés au fournisseur de modèle " +
                    "de langage choisi dans les réglages, jamais une mesure individuelle ni un " +
                    "horodatage précis.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Vous pouvez retirer l'autorisation à tout moment depuis Health Connect ou les " +
                    "réglages système de l'appareil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsRationaleScreenPreview() {
    HealthAnalyzerTheme {
        PermissionsRationaleScreen()
    }
}
