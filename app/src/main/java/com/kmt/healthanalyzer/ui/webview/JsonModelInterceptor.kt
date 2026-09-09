package com.kmt.healthanalyzer.ui.webview

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/**
 * Sert un JSON volumineux à une WebView locale par `shouldInterceptRequest`, plutôt que
 * comme argument d'un script `evaluateJavascript` — voir `ChatJsBridge.kt` et
 * `ReportJsBridge.kt` pour la mesure qui motive ce choix : un `ReportModel` sur tout
 * l'historique peut peser plus d'un mégaoctet une fois échappé en littéral JavaScript, et
 * `evaluateJavascript` le construit alors deux fois en mémoire, sur le thread principal, à
 * chaque envoi.
 *
 * [update] s'appelle depuis le thread principal (recomposition Compose), [respond] depuis
 * le thread interne de la WebView qui appelle `shouldInterceptRequest` — deux threads
 * différents. [AtomicReference] rend cet échange sûr sans verrou explicite.
 *
 * Une instance sert **une** URL, à **une** WebView : chaque écran (conversation, rapport)
 * garde la sienne, jamais partagée, pour qu'aucun autre contexte ne puisse récupérer un
 * modèle qui ne lui appartient pas. `respond` rend `null` pour toute autre URL — à
 * l'appelant de retomber sur son comportement existant (assets locaux, puis réponse vide).
 */
class JsonModelInterceptor(private val url: String) {
    private val json = AtomicReference<String?>(null)

    /** Remplace le JSON servi aux prochaines requêtes. À appeler avant de signaler `setModelReady`. */
    fun update(value: String) {
        json.set(value)
    }

    /**
     * Le corps à servir pour [requestUrl] (le défait-cache `?v=...` posé côté JavaScript
     * n'entre pas dans la comparaison), ou `null` si elle ne désigne pas [url] — à charge
     * de l'appelant de continuer sa propre chaîne de résolution. Rend `{}` si [update] n'a
     * encore jamais été appelé, plutôt qu'un corps vide qui casserait `JSON.parse` côté
     * page.
     *
     * Fonction pure, séparée de [respond] : `android.webkit.WebResourceResponse` ne se
     * relit pas dans un test JVM sans Robolectric (son stub Android rend `getData()` nul
     * même après construction) — cette étape reste donc testable telle quelle.
     */
    fun bodyFor(requestUrl: String): String? {
        if (requestUrl.substringBefore('?') != url) return null
        return json.get() ?: "{}"
    }

    /** Enrobe [bodyFor] dans la réponse attendue par `WebViewClient.shouldInterceptRequest`. */
    fun respond(requestUrl: String): WebResourceResponse? {
        val body = bodyFor(requestUrl) ?: return null
        return WebResourceResponse("application/json", "utf-8", ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8)))
    }
}
