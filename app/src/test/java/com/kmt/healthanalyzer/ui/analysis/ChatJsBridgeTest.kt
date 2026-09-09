package com.kmt.healthanalyzer.ui.analysis

import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que le JSON de la conversation traverse `evaluateJavascript` sans casser le
 * script, même quand le texte porte des caractères qui pourraient être mal interprétés.
 *
 * C'est particulièrement important ici : le contenu vient des réponses du modèle de
 * langage, donc par nature non fiable — contrairement au rapport, dont le JSON vient de
 * calculs locaux.
 *
 * Le `ReportModel` lui-même ne passe plus par [ChatJsBridge] — voir sa documentation pour
 * la mesure qui motive ce choix. [buildSetModelReadyScript] ne pousse qu'un entier.
 */
class ChatJsBridgeTest {

    @Test
    fun `buildSetConversationScript n'appelle setConversation qu'avec un seul argument`() {
        val script = ChatJsBridge.buildSetConversationScript(emptyList())

        assertTrue(script.startsWith("HA.host.setConversation("))
        assertTrue(script.endsWith(");"))
        val arguments = script.removePrefix("HA.host.setConversation(").removeSuffix(");")
        assertFalse("un seul argument attendu : la conversation, jamais le modèle", arguments.contains(", \""))
    }

    @Test
    fun `les guillemets, apostrophes, script de fermeture et saut de ligne traversent intacts`() {
        val awkwardContent = "C'est \"le\" jour</script>\nsuivant"
        val messages = listOf(
            ChatMessageEntity(id = 1, role = ChatRole.ASSISTANT, content = awkwardContent, createdAtEpochMillis = 42),
        )

        val script = ChatJsBridge.buildSetConversationScript(messages)
        val literal = extractFirstArgument(script)
        val conversationJson = Json.decodeFromString(String.serializer(), literal)
        val recovered = Json.decodeFromString(ListSerializer(ChatMessageDto.serializer()), conversationJson)

        assertEquals(1, recovered.size)
        assertEquals("assistant", recovered[0].role)
        assertEquals(awkwardContent, recovered[0].text)
        assertEquals(42L, recovered[0].at)
    }

    @Test
    fun `un antislash brut ne casse pas l'echappement`() {
        val messages = listOf(
            ChatMessageEntity(id = 1, role = ChatRole.USER, content = "C:\\Users\\test", createdAtEpochMillis = 0),
        )

        val script = ChatJsBridge.buildSetConversationScript(messages)
        val literal = extractFirstArgument(script)
        val conversationJson = Json.decodeFromString(String.serializer(), literal)
        val recovered = Json.decodeFromString(ListSerializer(ChatMessageDto.serializer()), conversationJson)

        assertEquals("C:\\Users\\test", recovered[0].text)
    }

    @Test
    fun `les roles passent en minuscules sur le fil`() {
        val messages = listOf(
            ChatMessageEntity(id = 1, role = ChatRole.USER, content = "Bonjour", createdAtEpochMillis = 0),
            ChatMessageEntity(id = 2, role = ChatRole.ASSISTANT, content = "Bonjour à vous", createdAtEpochMillis = 1),
        )

        val script = ChatJsBridge.buildSetConversationScript(messages)
        val literal = extractFirstArgument(script)
        val conversationJson = Json.decodeFromString(String.serializer(), literal)
        val recovered = Json.decodeFromString(ListSerializer(ChatMessageDto.serializer()), conversationJson)

        assertEquals("user", recovered[0].role)
        assertEquals("assistant", recovered[1].role)
    }

    @Test
    fun `buildSetModelReadyScript encadre la version, un entier, sans guillemets`() {
        val script = ChatJsBridge.buildSetModelReadyScript(1234L)

        assertEquals("HA.host.setModelReady(1234);", script)
    }

    @Test
    fun `buildSetModelReadyScript ne porte jamais le JSON du modèle, seulement sa version`() {
        val script = ChatJsBridge.buildSetModelReadyScript(System.currentTimeMillis())

        assertFalse("le modèle doit être servi par shouldInterceptRequest, jamais ici", script.contains("{"))
    }

    @Test
    fun `le theme sombre produit un appel setTheme dark`() {
        assertEquals("HA.host.setTheme(\"dark\");", ChatJsBridge.buildSetThemeScript(dark = true))
    }

    @Test
    fun `le theme clair produit un appel setTheme light`() {
        assertEquals("HA.host.setTheme(\"light\");", ChatJsBridge.buildSetThemeScript(dark = false))
    }

    /** Isole le littéral JSON du seul argument de `setConversation`. */
    private fun extractFirstArgument(script: String): String {
        require(script.startsWith("HA.host.setConversation(") && script.endsWith(");"))
        return script.removePrefix("HA.host.setConversation(").removeSuffix(");")
    }
}
