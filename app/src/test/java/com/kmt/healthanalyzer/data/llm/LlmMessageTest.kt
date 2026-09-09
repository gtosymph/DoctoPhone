package com.kmt.healthanalyzer.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vérifie la remise en forme du fil de conversation partagée par les trois clients :
 * premier message utilisateur, alternance stricte des rôles.
 */
class LlmMessageTest {

    @Test
    fun `un fil deja alterne reste inchange`() {
        val messages = listOf(
            LlmMessage(LlmRole.USER, "Bonjour"),
            LlmMessage(LlmRole.ASSISTANT, "Bonjour, que puis-je faire ?"),
            LlmMessage(LlmRole.USER, "Analyse mon sommeil."),
        )

        assertEquals(messages, normalizeConversation(messages))
    }

    @Test
    fun `deux messages utilisateur consecutifs sont fusionnes`() {
        val messages = listOf(
            LlmMessage(LlmRole.USER, "Premier message."),
            LlmMessage(LlmRole.USER, "Deuxième message."),
        )

        val result = normalizeConversation(messages)

        assertEquals(listOf(LlmMessage(LlmRole.USER, "Premier message.\n\nDeuxième message.")), result)
    }

    @Test
    fun `deux messages assistant consecutifs sont fusionnes`() {
        val messages = listOf(
            LlmMessage(LlmRole.USER, "Bonjour"),
            LlmMessage(LlmRole.ASSISTANT, "Première partie."),
            LlmMessage(LlmRole.ASSISTANT, "Seconde partie."),
        )

        val result = normalizeConversation(messages)

        assertEquals(
            listOf(
                LlmMessage(LlmRole.USER, "Bonjour"),
                LlmMessage(LlmRole.ASSISTANT, "Première partie.\n\nSeconde partie."),
            ),
            result,
        )
    }

    @Test
    fun `un message assistant en tete est enleve`() {
        val messages = listOf(
            LlmMessage(LlmRole.ASSISTANT, "Je ne devrais pas être en tête."),
            LlmMessage(LlmRole.USER, "Analyse mon sommeil."),
        )

        val result = normalizeConversation(messages)

        assertEquals(listOf(LlmMessage(LlmRole.USER, "Analyse mon sommeil.")), result)
    }

    @Test
    fun `plusieurs messages assistant en tete sont tous enleves`() {
        val messages = listOf(
            LlmMessage(LlmRole.ASSISTANT, "Premier residu."),
            LlmMessage(LlmRole.ASSISTANT, "Second residu."),
            LlmMessage(LlmRole.USER, "Analyse mon sommeil."),
        )

        val result = normalizeConversation(messages)

        assertEquals(listOf(LlmMessage(LlmRole.USER, "Analyse mon sommeil.")), result)
    }

    @Test
    fun `un fil compose uniquement d'assistants devient une liste vide`() {
        val messages = listOf(
            LlmMessage(LlmRole.ASSISTANT, "Seul."),
        )

        assertEquals(emptyList<LlmMessage>(), normalizeConversation(messages))
    }
}
