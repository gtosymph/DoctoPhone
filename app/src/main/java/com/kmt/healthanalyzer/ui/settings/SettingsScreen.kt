package com.kmt.healthanalyzer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.domain.analysis.formatMinutes
import com.kmt.healthanalyzer.ui.components.ScreenHeader
import com.kmt.healthanalyzer.ui.components.SectionLabel
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors
import kotlin.math.roundToInt

private const val MIN_SLEEP_TARGET_MINUTES = 240
private const val MAX_SLEEP_TARGET_MINUTES = 720
private const val SLEEP_TARGET_STEP_MINUTES = 15

/**
 * Écran de réglages : point d'entrée qui relie [SettingsViewModel] à [SettingsContent].
 *
 * [updateViewModel] est injecté à part de [viewModel] : c'est la même instance, à portée de
 * l'activité, que [com.kmt.healthanalyzer.ui.navigation.HealthAnalyzerNavHost] a déjà créée
 * pour lancer la vérification discrète au démarrage — la réutiliser ici évite de relancer une
 * seconde vérification à chaque ouverture de cet écran.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Une fois l'APK téléchargé, ouvre l'installeur système sans attendre un second geste de
    // l'utilisateur : « télécharger puis installer » est une seule action de son point de vue.
    // Si l'autorisation d'installer manque, explique pourquoi avant de le renvoyer vers les
    // réglages système, plutôt que d'échouer sans rien dire.
    LaunchedEffect(updateState.status) {
        val status = updateState.status
        if (status is UpdateStatus.ReadyToInstall) {
            if (updateViewModel.canInstallPackages()) {
                context.startActivity(updateViewModel.installIntentFor(status.apkFile))
            } else {
                Toast.makeText(
                    context,
                    "Autorisez l'installation d'apps depuis cette source, puis relancez l'installation.",
                    Toast.LENGTH_LONG,
                ).show()
                context.startActivity(updateViewModel.requestInstallPermissionIntent())
            }
        }
    }

    SettingsContent(
        state = state,
        updateState = updateState,
        onSelectProvider = viewModel::selectProvider,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onModelChanged = viewModel::setModel,
        onSleepTargetChanged = viewModel::setSleepTargetMinutes,
        onClearAllData = viewModel::clearAllData,
        onDismissMessage = viewModel::dismissMessage,
        onCheckUpdate = updateViewModel::checkNow,
        onDownloadAndInstallUpdate = updateViewModel::downloadAndInstall,
        modifier = modifier,
    )
}

/** Contenu de l'écran de réglages, sans ViewModel : testable et prévisualisable. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    updateState: UpdateUiState,
    onSelectProvider: (LlmProvider) -> Unit,
    onSaveApiKey: (LlmProvider, String) -> Unit,
    onClearApiKey: (LlmProvider) -> Unit,
    onModelChanged: (String) -> Unit,
    onSleepTargetChanged: (Int) -> Unit,
    onClearAllData: () -> Unit,
    onDismissMessage: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }
    var modelText by rememberSaveable(state.provider, state.model) { mutableStateOf(state.model) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onDismissMessage()
        }
    }

    val domain = HealthAnalyzerTheme.domainColors

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenHeader(title = "Réglages", accent = domain.sleep.base)

            // --- Fournisseur & connexion ---------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "Fournisseur du modèle de langage", accent = domain.vitality.base)
                ProviderSelector(selected = state.provider, onSelect = onSelectProvider)
                ApiKeyCard(
                    provider = state.provider,
                    hasKey = state.providersWithKey.contains(state.provider),
                    onSave = { key -> onSaveApiKey(state.provider, key) },
                    onClear = { onClearApiKey(state.provider) },
                )
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it; onModelChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modèle") },
                    singleLine = true,
                )
            }

            // --- Objectif de sommeil ---------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "Objectif de sommeil", accent = domain.sleep.base)
                Card(colors = CardDefaults.cardColors(containerColor = domain.sleep.soft)) {
                    Column(Modifier.padding(16.dp)) {
                        SleepTargetSlider(
                            minutes = state.sleepTargetMinutes,
                            trackColor = domain.sleep.base,
                            onChanged = onSleepTargetChanged,
                        )
                    }
                }
            }

            // --- Données : zone à conséquence, signalée par le rouge de « cœur » -------
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "Données", accent = domain.heart.base)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, domain.heart.base.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Vos données restent sur l'appareil. Lors d'une analyse, seuls des " +
                                "agrégats hebdomadaires sont envoyés au fournisseur choisi. La clé " +
                                "API est stockée chiffrée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = domain.heart.ink),
                            border = BorderStroke(1.dp, domain.heart.base),
                        ) {
                            Text("Effacer toutes les données locales")
                        }
                    }
                }
            }

            // --- Mise à jour -------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "Mise à jour", accent = domain.vitality.base)
                UpdateSection(
                    state = updateState,
                    accent = domain.vitality.base,
                    onCheckNow = onCheckUpdate,
                    onDownloadAndInstall = onDownloadAndInstallUpdate,
                )
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Effacer toutes les données locales ?") },
            text = { Text("Cette action supprime définitivement les données importées et synchronisées sur cet appareil.") },
            confirmButton = {
                TextButton(onClick = { showClearAllDialog = false; onClearAllData() }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Annuler") }
            },
        )
    }
}

/**
 * Curseur de l'objectif de sommeil, avec la valeur affichée en grand — un chiffre qui doit
 * sauter aux yeux, à l'image de la direction « éditorial coloré » demandée pour la coquille.
 */
@Composable
private fun SleepTargetSlider(minutes: Int, trackColor: Color, onChanged: (Int) -> Unit) {
    val totalSegments = (MAX_SLEEP_TARGET_MINUTES - MIN_SLEEP_TARGET_MINUTES) / SLEEP_TARGET_STEP_MINUTES
    Column {
        Text(
            text = formatMinutes(minutes),
            style = MaterialTheme.typography.headlineLarge,
            color = trackColor,
        )
        Text(
            text = "objectif de sommeil par nuit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChanged(it.roundToInt()) },
            valueRange = MIN_SLEEP_TARGET_MINUTES.toFloat()..MAX_SLEEP_TARGET_MINUTES.toFloat(),
            steps = totalSegments - 1,
            colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor),
            modifier = Modifier.semantics {
                contentDescription = "Objectif de sommeil, ${formatMinutes(minutes)} par nuit"
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    HealthAnalyzerTheme {
        SettingsContent(
            state = SettingsUiState(providersWithKey = setOf(LlmProvider.ANTHROPIC)),
            updateState = UpdateUiState(),
            onSelectProvider = {},
            onSaveApiKey = { _, _ -> },
            onClearApiKey = {},
            onModelChanged = {},
            onSleepTargetChanged = {},
            onClearAllData = {},
            onDismissMessage = {},
            onCheckUpdate = {},
            onDownloadAndInstallUpdate = {},
        )
    }
}
