package com.kmt.healthanalyzer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Section « Mise à jour » de l'écran de réglages : version installée, état de la dernière
 * vérification, et les deux actions manuelles (vérifier, télécharger puis installer).
 */
@Composable
fun UpdateSection(
    state: UpdateUiState,
    accent: Color,
    onCheckNow: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Version installée : ${state.installedVersionName} (${state.installedVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
            )
            UpdateStatusRow(state.status, accent)
            UpdateActionRow(status = state.status, onCheckNow = onCheckNow, onDownloadAndInstall = onDownloadAndInstall)
        }
    }
}

@Composable
private fun UpdateStatusRow(status: UpdateStatus, accent: Color) {
    when (status) {
        UpdateStatus.Idle -> Text(
            "Vérifiez si une nouvelle version est disponible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UpdateStatus.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
            Text("Vérification en cours…", style = MaterialTheme.typography.bodySmall)
        }

        UpdateStatus.UpToDate -> Text("L'app est à jour.", style = MaterialTheme.typography.bodySmall)

        is UpdateStatus.Available -> Text(
            "Version ${status.release.versionName} disponible.",
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
        )

        is UpdateStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status.totalBytes > 0) {
                val fraction = (status.bytesReceived.toFloat() / status.totalBytes).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { fraction }, color = accent, modifier = Modifier.fillMaxWidth())
                Text(
                    "Téléchargement en cours… ${(fraction * 100).roundToInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                // `Content-Length` absent de la réponse (totalBytes = -1) : la taille totale
                // est inconnue, une barre déterminée mentirait sur l'avancement. Une barre
                // indéterminée montre une activité sans prétendre la connaître.
                LinearProgressIndicator(color = accent, modifier = Modifier.fillMaxWidth())
                Text("Téléchargement en cours…", style = MaterialTheme.typography.bodySmall)
            }
        }

        is UpdateStatus.ReadyToInstall -> Text(
            "Téléchargement terminé, installation en cours…",
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
        )

        is UpdateStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun UpdateActionRow(status: UpdateStatus, onCheckNow: () -> Unit, onDownloadAndInstall: () -> Unit) {
    when (status) {
        UpdateStatus.Idle, UpdateStatus.UpToDate, is UpdateStatus.Failed ->
            OutlinedButton(onClick = onCheckNow) { Text("Vérifier") }

        is UpdateStatus.Available ->
            Button(onClick = onDownloadAndInstall) { Text("Télécharger et installer") }

        UpdateStatus.Checking, is UpdateStatus.Downloading, is UpdateStatus.ReadyToInstall -> Unit
    }
}
