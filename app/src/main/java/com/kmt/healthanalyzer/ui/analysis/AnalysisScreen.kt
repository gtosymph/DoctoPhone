package com.kmt.healthanalyzer.ui.analysis

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.ui.webview.JsonModelInterceptor
import java.io.ByteArrayInputStream

/** URL de départ dans la WebView : sert `chat/chat.html` depuis les assets, sans jamais toucher au réseau. */
private const val CHAT_URL = "https://appassets.androidplatform.net/assets/chat/chat.html"

/** Écran de conversation : point d'entrée qui relie [AnalysisViewModel] à [ChatContent]. */
@Composable
fun AnalysisScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // La navigation retire cet écran de la composition quand on quitte l'onglet, et le
    // recompose entièrement au retour : c'est ce qui fait de `LaunchedEffect(Unit)` un
    // signal fiable de « l'écran redevient visible », y compris à la toute première ouverture.
    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    ChatContent(
        state = state,
        onDraftChanged = viewModel::setDraft,
        onSend = viewModel::send,
        onClearConversation = viewModel::clearConversation,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/**
 * Contenu de l'écran de conversation, sans ViewModel : la WebView au-dessus, la saisie
 * native et sa chrome en dessous.
 *
 * Le JavaScript est nécessaire : c'est lui qui dessine la conversation et ses éventuels
 * graphiques. Le risque XSS que lint signale est mitigé ailleurs — page locale
 * uniquement, aucun accès fichier ni réseau, aucune requête non-asset servie (voir
 * `shouldInterceptRequest` ci-dessous) — exactement comme pour l'écran de rapport.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatContent(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClearConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()

    // La page signale sa disponibilité par `AndroidChatHost.onReady()` ; avant ce signal,
    // les scripts de la vue de conversation ne sont pas encore chargés et un envoi échouerait.
    var isPageReady by remember { mutableStateOf(false) }

    // Évite de repousser la même conversation quand une recomposition n'a rien changé.
    var lastSentMessages by remember { mutableStateOf<List<ChatMessageEntity>>(emptyList()) }
    var lastSentReportModelJson by remember { mutableStateOf<String?>(null) }

    // Sert le `ReportModel` par `shouldInterceptRequest` plutôt que comme argument d'un
    // script — voir `ChatJsBridge.kt` : sur tout l'historique, il peut peser plus d'un
    // mégaoctet une fois échappé pour `evaluateJavascript`.
    val modelInterceptor = remember { JsonModelInterceptor(ChatJsBridge.MODEL_URL) }

    var showClearConfirmation by remember { mutableStateOf(false) }
    if (showClearConfirmation) {
        ClearConversationDialog(
            onConfirm = {
                showClearConfirmation = false
                onClearConversation()
            },
            onDismiss = { showClearConfirmation = false },
        )
    }

    // La barre de saisie est ancrée en bas de l'écran : sans `imePadding`, le clavier la
    // recouvrirait plutôt que de la repousser, en mode bord-à-bord (`enableEdgeToEdge`).
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        if (state.isSending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.isRefreshingContext) {
            RefreshingContextBanner()
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
                        ChatJsHost(
                            onReady = { isPageReady = true },
                            onSuggestion = onDraftChanged,
                        ),
                        "AndroidChatHost",
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
                        ): Boolean = request.url.toString() != CHAT_URL
                    }

                    loadUrl(CHAT_URL)
                }
            },
            update = { webView ->
                if (!isPageReady) return@AndroidView

                webView.evaluateJavascript(ChatJsBridge.buildSetThemeScript(darkTheme), null)

                // Met à jour l'interception AVANT de signaler la page, pour que sa relecture
                // trouve toujours le modèle à jour. La page (`chat.html`) redessine
                // elle-même la conversation déjà reçue dès que le modèle arrive — pas besoin
                // de la repousser ici — pour que les graphiques déjà affichés se résolvent.
                if (state.reportModelJson != lastSentReportModelJson) {
                    modelInterceptor.update(state.reportModelJson ?: "{}")
                    lastSentReportModelJson = state.reportModelJson
                    webView.evaluateJavascript(ChatJsBridge.buildSetModelReadyScript(System.currentTimeMillis()), null)
                }
                if (state.messages != lastSentMessages) {
                    webView.evaluateJavascript(ChatJsBridge.buildSetConversationScript(state.messages), null)
                    lastSentMessages = state.messages
                }
            },
        )

        if (state.errorMessage == null) {
            state.statusMessage?.let { StatusNoteBanner(it) }
        }
        state.errorMessage?.let { ChatErrorCard(it, onOpenSettings) }

        ChatInputRow(
            state = state,
            onDraftChanged = onDraftChanged,
            onSend = onSend,
            onClearRequested = { showClearConfirmation = true },
        )
    }
}

@Composable
private fun ChatInputRow(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClearRequested: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f),
            label = { Text("Une question sur vos données ?") },
            minLines = 1,
            maxLines = 5,
            enabled = !state.isSending && !state.isLoadingContext,
        )
        IconButton(
            onClick = onSend,
            enabled = !state.isSending && !state.isLoadingContext && state.draft.isNotBlank(),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
            }
        }
        IconButton(
            onClick = onClearRequested,
            enabled = state.messages.isNotEmpty() && !state.isSending,
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Effacer la conversation")
        }
    }
}

/** Avertit avant d'effacer : l'action est définitive, la conversation n'est gardée nulle part ailleurs. */
@Composable
private fun ClearConversationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Effacer la conversation") },
        text = { Text("Tous les messages de cette conversation seront définitivement supprimés.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Effacer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Signale, discrètement, qu'un nouvel import a été détecté et que le contexte de santé
 * se recharge. N'apparaît qu'après le premier chargement — voir
 * [AnalysisViewModel.onScreenVisible] — la conversation reste utilisable pendant ce temps.
 */
@Composable
private fun RefreshingContextBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            "Nouvelles données importées, mise à jour du contexte…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ligne d'état discrète : la période que le modèle a examinée pour écrire sa dernière
 * réponse, quand il a dû demander une fenêtre avec `healthrange` — voir
 * [ChatWithHealthUseCase] et [ChatUiState.statusMessage]. C'est tout ce que l'utilisateur
 * voit de l'aller-retour ; la demande de fenêtre elle-même n'apparaît jamais dans la
 * conversation.
 */
@Composable
private fun StatusNoteBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatErrorCard(message: String, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (message.contains("clé API", ignoreCase = true)) {
                Button(onClick = onOpenSettings) { Text("Ouvrir les réglages") }
            }
        }
    }
}

/** Renvoie une réponse vide : c'est ainsi qu'on refuse une requête sans jamais toucher au réseau. */
private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/**
 * Pont JavaScript exposé à la page sous le nom `AndroidChatHost`.
 *
 * Android invoque les méthodes `@JavascriptInterface` sur un thread interne à la WebView,
 * jamais sur le thread principal. [onReady] et [onSuggestion] doivent donc être relayés
 * sur le thread principal avant de toucher l'état Compose ou d'appeler `evaluateJavascript`.
 */
private class ChatJsHost(
    private val onReady: () -> Unit,
    private val onSuggestion: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post(onReady)
    }

    /**
     * Une question d'amorce a été cliquée dans la conversation vide (voir
     * `chat-view.js#renderIntro`). Elle ne fait que remplir le champ de saisie natif,
     * jamais l'envoyer directement : la personne doit pouvoir lire la question, la
     * modifier, avant qu'elle ne parte chez le fournisseur de LLM.
     */
    @JavascriptInterface
    fun onSuggestion(text: String) {
        mainHandler.post { onSuggestion.invoke(text) }
    }
}
