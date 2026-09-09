package com.kmt.healthanalyzer.ui.analysis

import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Ce que la WebView reçoit pour un message : le rôle, le texte, l'horodatage, et la
 * fenêtre que le modèle examinait en l'écrivant. Le format attendu par `chat-view.js`
 * (`HA.chat.render`) est `{role, text, at, rangeFrom, rangeTo}` — pas `content`. Ni
 * identifiant : la persistance reste entièrement côté Kotlin (voir `ChatMessageDao`).
 *
 * [rangeFrom] et [rangeTo] reflètent [com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity.rangeFrom]/`rangeTo` :
 * `null` sans fenêtre associée, auquel cas `chat-view.js` résout les graphiques de ce
 * message sur tout l'historique — voir `messageRange`.
 */
@Serializable
internal data class ChatMessageDto(
    val role: String,
    val text: String,
    val at: Long,
    val rangeFrom: String? = null,
    val rangeTo: String? = null,
)

/**
 * Construit les scripts poussés dans la WebView de la conversation.
 *
 * Même principe que [com.kmt.healthanalyzer.ui.report.ReportJsBridge] : chaque littéral
 * JSON doit être encodé une seconde fois, comme chaîne JavaScript, pour traverser
 * `WebView.evaluateJavascript` sans casser le script. C'est ici encore plus
 * indispensable que pour le rapport : le texte des messages de l'assistant vient des
 * réponses du modèle de langage, donc par nature non fiable — il peut contenir des
 * guillemets, des apostrophes, un `</script>` ou un saut de ligne, sans qu'on ait la
 * moindre prise dessus.
 *
 * **Le `ReportModel` ne passe jamais par ici.** Mesuré sur quatre ans d'historique quasi
 * quotidien, sa version sérialisée pèse environ 680 Ko, et le double encodage nécessaire à
 * `evaluateJavascript` la porte à près de 800 Ko — plus de 1,5 Mo sur huit ans. Le
 * construire comme chaîne JavaScript double ce poids en mémoire (UTF-16), sur le thread
 * principal, à chaque envoi. Voir [MODEL_URL] : le modèle part par
 * `WebResourceResponse`, dans `AnalysisScreen.kt` (`shouldInterceptRequest`), jamais comme
 * argument d'un script. Cette classe ne pousse plus que la conversation (bornée à
 * [com.kmt.healthanalyzer.domain.usecase.ChatWithHealthUseCase] messages, donc petite) et
 * un signal court prévenant la page qu'une nouvelle version du modèle est disponible à
 * cette URL.
 *
 * Côté page, `chat.html` lit `HA.host.setConversation(conversationJson)` pour la
 * conversation, et `HA.host.setModelReady(version)` pour aller chercher le `ReportModel` à
 * jour — `chart-catalog.js` en a besoin pour résoudre les graphiques que le modèle désigne
 * par référence.
 */
object ChatJsBridge {

    /**
     * URL virtuelle du `ReportModel` courant, interceptée par `AnalysisScreen.kt` — jamais
     * servie par `WebViewAssetLoader` (ce n'est pas un fichier des assets), jamais servie
     * à une autre WebView que celle de la conversation.
     */
    const val MODEL_URL = "https://appassets.androidplatform.net/model/conversation.json"

    private val json = Json

    /** Le script qui remplace la conversation entière affichée par la page. */
    fun buildSetConversationScript(messages: List<ChatMessageEntity>): String {
        val dto = messages.map {
            ChatMessageDto(
                role = it.role.toWireName(),
                text = it.content,
                at = it.createdAtEpochMillis,
                rangeFrom = it.rangeFrom,
                rangeTo = it.rangeTo,
            )
        }
        val conversationJson = json.encodeToString(ListSerializer(ChatMessageDto.serializer()), dto)
        val conversationLiteral = json.encodeToString(String.serializer(), conversationJson)
        return "HA.host.setConversation($conversationLiteral);"
    }

    /**
     * Le signal (sans charge utile) qui dit à la page de relire [MODEL_URL] : une nouvelle
     * version du `ReportModel` y est disponible. [version] n'est qu'un défait-cache — un
     * entier suffit, jamais besoin d'échappement de chaîne.
     */
    fun buildSetModelReadyScript(version: Long): String = "HA.host.setModelReady($version);"

    /** Le script qui force le thème clair ou sombre, avant même l'arrivée de la conversation. */
    fun buildSetThemeScript(dark: Boolean): String {
        val theme = if (dark) "dark" else "light"
        val literal = json.encodeToString(String.serializer(), theme)
        return "HA.host.setTheme($literal);"
    }

    private fun ChatRole.toWireName(): String = when (this) {
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }
}
