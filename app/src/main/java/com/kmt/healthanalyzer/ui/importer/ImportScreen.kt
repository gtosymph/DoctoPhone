package com.kmt.healthanalyzer.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissions
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectStatus
import com.kmt.healthanalyzer.data.samsung.ImportSummary
import com.kmt.healthanalyzer.ui.components.ScreenHeader
import com.kmt.healthanalyzer.ui.theme.DomainColor
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Écran d'import : point d'entrée qui relie [ImportViewModel] à [ImportContent]. */
@Composable
fun ImportScreen(modifier: Modifier = Modifier, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val directoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::importDirectory)
    }

    val archiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importArchive)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        // Le statut Health Connect ne dépend pas de l'octroi des permissions : on le
        // rafraîchit tout de même, au cas où l'utilisateur vient d'installer l'app pendant
        // le flux de permission.
        viewModel.refreshHealthConnectStatus()
    }

    ImportContent(
        state = state,
        onPickDirectory = { directoryLauncher.launch(null) },
        onPickArchive = { archiveLauncher.launch(ARCHIVE_MIME_TYPES) },
        onRequestHealthConnectPermissions = { permissionLauncher.launch(HealthConnectPermissions.REQUESTED_PERMISSIONS) },
        onSyncHealthConnect = viewModel::syncHealthConnect,
        onSyncAllHealthConnect = viewModel::syncAllHealthConnect,
        modifier = modifier,
    )
}

private val ARCHIVE_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

/** Contenu de l'écran d'import, sans ViewModel : testable et prévisualisable. */
@Composable
fun ImportContent(
    state: ImportUiState,
    onPickDirectory: () -> Unit,
    onPickArchive: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit,
    onSyncHealthConnect: () -> Unit,
    onSyncAllHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val domain = HealthAnalyzerTheme.domainColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Import", accent = domain.activity.base)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = domain.activity.base,
                    )
                    Text(
                        "Depuis Samsung Health",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "Dans Samsung Health, ouvrez Paramètres puis Télécharger les données " +
                        "personnelles. L'app écrit un dossier « samsunghealth_… » dans votre " +
                        "stockage. Choisissez ce dossier ci-dessous.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onPickDirectory, enabled = !state.isImporting) {
                    Text("Choisir le dossier Samsung Health")
                }
                Text(
                    "Si votre export a transité par un ordinateur et se trouve compressé, " +
                        "choisissez plutôt l'archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onPickArchive, enabled = !state.isImporting) {
                    Text("Choisir une archive ZIP")
                }
                AnimatedVisibility(state.isImporting, enter = fadeIn(), exit = fadeOut()) {
                    ImportProgressIndicator(fileName = state.currentFile, progress = state.progress)
                }
            }
        }

        AnimatedVisibility(state.errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
            state.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = domain.heart.soft),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = domain.heart.ink,
                        )
                        Text(
                            text = message,
                            modifier = Modifier.padding(start = 12.dp),
                            color = domain.heart.ink,
                        )
                    }
                }
            }
        }

        state.summary?.let { summary -> ImportSummaryCard(summary, accent = domain.activity) }

        HealthConnectSection(
            status = state.healthConnectStatus,
            isSyncing = state.isSyncing,
            syncMessage = state.syncMessage,
            syncHasIssue = state.syncHasIssue,
            onRequestPermissions = onRequestHealthConnectPermissions,
            onSync = onSyncHealthConnect,
            onSyncAll = onSyncAllHealthConnect,
        )
    }
}

@Composable
private fun ImportProgressIndicator(fileName: String?, progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        fileName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

/**
 * Bilan de l'import : le total de mesures est le chiffre qui doit sauter aux yeux, en
 * confirmation immédiate que l'import a bien pris — le détail par type vient en dessous,
 * en corps de texte.
 */
@Composable
private fun ImportSummaryCard(summary: ImportSummary, accent: DomainColor) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = accent.soft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bilan de l'import", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${summary.totalRecords}",
                style = MaterialTheme.typography.headlineLarge,
                color = accent.ink,
            )
            Text(
                "mesures importées, du ${summary.firstDay?.format(DATE_FORMAT) ?: "-"} " +
                    "au ${summary.lastDay?.format(DATE_FORMAT) ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            summary.recordsByType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                Text("• $type : $count", style = MaterialTheme.typography.bodyMedium)
            }
            if (summary.warnings.isNotEmpty()) {
                Text(
                    "Avertissements",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                summary.warnings.forEach { warning ->
                    Text("• $warning", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

@Preview(showBackground = true)
@Composable
private fun ImportContentPreview() {
    HealthAnalyzerTheme {
        ImportContent(
            state = ImportUiState(
                healthConnectStatus = HealthConnectStatus.AVAILABLE,
                summary = ImportSummary(
                    recordsByType = mapOf("Pas" to 1200, "Sommeil" to 340, "Fréquence cardiaque" to 8900),
                    firstDay = LocalDate.now().minusYears(1),
                    lastDay = LocalDate.now(),
                    warnings = listOf("2 fichiers n'ont pas pu être lus."),
                ),
            ),
            onPickDirectory = {},
        onPickArchive = {},
            onRequestHealthConnectPermissions = {},
            onSyncHealthConnect = {},
            onSyncAllHealthConnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportContentImportingPreview() {
    HealthAnalyzerTheme {
        ImportContent(
            state = ImportUiState(
                isImporting = true,
                currentFile = "sleep.csv",
                progress = 0.4f,
                healthConnectStatus = HealthConnectStatus.NOT_INSTALLED,
            ),
            onPickDirectory = {},
        onPickArchive = {},
            onRequestHealthConnectPermissions = {},
            onSyncHealthConnect = {},
            onSyncAllHealthConnect = {},
        )
    }
}
