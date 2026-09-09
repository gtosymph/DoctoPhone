package com.kmt.healthanalyzer.ui.report

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.kmt.healthanalyzer.ui.components.ScreenHeader
import com.kmt.healthanalyzer.ui.home.TimeRange
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors
import com.kmt.healthanalyzer.ui.webview.JsonModelInterceptor
import java.io.ByteArrayInputStream

/** URL de départ dans la WebView : sert `report/report.html` depuis les assets, sans jamais toucher au réseau. */
private const val REPORT_URL = "https://appassets.androidplatform.net/assets/report/report.html"

/** Écran de rapport : point d'entrée qui relie [ReportViewModel] à [ReportContent]. */
@Composable
fun ReportScreen(modifier: Modifier = Modifier, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Événement à usage unique : ouvrir le sélecteur de partage dès que le fichier est prêt.
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { intent ->
            context.startActivity(Intent.createChooser(intent, "Partager le bilan"))
        }
    }

    ReportContent(
        state = state,
        onRangeSelected = viewModel::selectRange,
        onRefresh = viewModel::refresh,
        onExport = viewModel::export,
        onWriteNarrative = viewModel::writeNarrative,
        modifier = modifier,
    )
}

/**
 * Contenu de l'écran de rapport, sans ViewModel : la chrome native au-dessus, la WebView en dessous.
 *
 * Le JavaScript est nécessaire : c'est lui qui dessine le rapport. Le risque XSS que lint
 * signale est mitigé ailleurs — page locale uniquement, aucun accès fichier ni réseau, aucune
 * requête non-asset servie (voir `shouldInterceptRequest` ci-dessous).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReportContent(
    state: ReportUiState,
    onRangeSelected: (TimeRange) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onWriteNarrative: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()

    // La page signale sa disponibilité par `AndroidReportHost.onReady()` ; avant ce signal,
    // les scripts du moteur de rapport ne sont pas encore chargés et un envoi échouerait.
    var isPageReady by remember { mutableStateOf(false) }

    // Évite de repousser deux fois le même rapport quand une recomposition n'a rien changé.
    var lastSentReportJson by remember { mutableStateOf<String?>(null) }

    // Sert le `ReportModel` par `shouldInterceptRequest` plutôt que comme argument d'un
    // script — voir `ReportJsBridge.kt` : le même mécanisme sert la conversation
    // d'analyse, dont le modèle peut peser plus d'un mégaoctet une fois échappé pour
    // `evaluateJavascript` sur tout l'historique.
    val modelInterceptor = remember { JsonModelInterceptor(ReportJsBridge.MODEL_URL) }

    var showExportConfirmation by remember { mutableStateOf(false) }
    if (showExportConfirmation) {
        ExportConfirmationDialog(
            periodLabel = state.range.label,
            onConfirm = {
                showExportConfirmation = false
                onExport()
            },
            onDismiss = { showExportConfirmation = false },
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = "Rapport",
                accent = HealthAnalyzerTheme.domainColors.vitality.base,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser le rapport")
                    }
                    IconButton(
                        onClick = { showExportConfirmation = true },
                        enabled = state.reportJson != null && !state.isExporting,
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = "Exporter le bilan")
                        }
                    }
                },
            )
            PeriodSelector(selected = state.range, onSelected = onRangeSelected)
            NarrativeRow(state = state, onWriteNarrative = onWriteNarrative)
            AnimatedVisibility(state.errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                state.errorMessage?.let { ErrorCard(it) }
            }
            AnimatedVisibility(state.exportError != null, enter = fadeIn(), exit = fadeOut()) {
                state.exportError?.let { ErrorCard(it) }
            }
            AnimatedVisibility(state.narrativeError != null, enter = fadeIn(), exit = fadeOut()) {
                state.narrativeError?.let { ErrorCard(it) }
            }
        }

        AnimatedVisibility(state.isLoading, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()

                WebView(context).apply {
                    // Aucune donnée de santé ne doit sortir de l'appareil : ni fichiers locaux
                    // arbitraires, ni fournisseurs de contenu, ni stockage persistant côté page.
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false

                    addJavascriptInterface(
                        ReportJsHost(onReady = { isPageReady = true }),
                        "AndroidReportHost",
                    )

                    webViewClient = object : WebViewClientCompat() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? =
                            modelInterceptor.respond(request.url.toString())
                                ?: assetLoader.shouldInterceptRequest(request.url)
                                ?: blockedResponse()

                        // Défense en profondeur : la page n'a jamais besoin de naviguer ailleurs
                        // que sur son propre document ; toute tentative est refusée.
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = request.url.toString() != REPORT_URL
                    }

                    loadUrl(REPORT_URL)
                }
            },
            update = { webView ->
                if (!isPageReady) return@AndroidView

                webView.evaluateJavascript(ReportJsBridge.buildSetThemeScript(darkTheme), null)

                val reportJson = state.reportJson
                if (reportJson != null && reportJson != lastSentReportJson) {
                    // Met à jour l'interception AVANT de signaler la page, pour que sa
                    // relecture trouve toujours le modèle à jour.
                    modelInterceptor.update(reportJson)
                    lastSentReportJson = reportJson
                    webView.evaluateJavascript(ReportJsBridge.buildSetModelReadyScript(System.currentTimeMillis()), null)
                }
            },
        )
    }
}

/**
 * Sélecteur de la période observée, en pleine largeur.
 *
 * Utilise [TimeRange.shortLabel] : sur quatre segments, le libellé long ("90 jours")
 * repassait à la ligne dès que la rangée devait aussi loger les boutons d'action — d'où
 * leur déplacement dans [ScreenHeader]. [TimeRange.label], plus explicite, reste employé
 * partout où le libellé est seul sur sa ligne (dialogue d'export, message du récit).
 */
@Composable
private fun PeriodSelector(selected: TimeRange, onSelected: (TimeRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TimeRange.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == selected,
                onClick = { onSelected(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRange.entries.size),
                label = { Text(entry.shortLabel) },
            )
        }
    }
}

/**
 * Bouton de rédaction du récit, avec son état d'attente et l'avertissement quand un récit
 * déjà rédigé porte sur une autre période que celle actuellement affichée.
 */
@Composable
private fun NarrativeRow(state: ReportUiState, onWriteNarrative: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onWriteNarrative,
            enabled = state.reportJson != null && !state.isWritingNarrative,
        ) {
            if (state.isWritingNarrative) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Rédiger le bilan")
        }
        if (state.narrativeRange != null && state.narrativeRange != state.range) {
            Text(
                "Bilan rédigé pour « ${state.narrativeRange.label} ».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Avertit avant de partager le rapport : le fichier produit contient des mesures de
 * santé détaillées, et le partage — une fois fait — n'est plus sous le contrôle de l'app.
 *
 * Le message nomme la période couverte et précise qu'il s'agit de mesures détaillées, pas
 * d'un simple résumé, pour que la décision de partager se prenne en connaissance de cause.
 */
@Composable
private fun ExportConfirmationDialog(periodLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exporter le bilan") },
        text = {
            Text(
                "Le fichier produit couvre la période « $periodLabel » et porte vos mesures " +
                    "de santé détaillées, jour par jour — pas un simple résumé. Ne le partagez " +
                    "qu'avec des personnes à qui vous voulez les confier.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Exporter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Renvoie une réponse vide : c'est ainsi qu'on refuse une requête sans jamais toucher au réseau. */
private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/**
 * Pont JavaScript exposé à la page sous le nom `AndroidReportHost`.
 *
 * Android invoque les méthodes `@JavascriptInterface` sur un thread interne à la WebView,
 * jamais sur le thread principal. [onReady] doit donc être relayé sur le thread principal
 * avant de toucher l'état Compose ou d'appeler `evaluateJavascript`.
 */
private class ReportJsHost(private val onReady: () -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post(onReady)
    }
}
