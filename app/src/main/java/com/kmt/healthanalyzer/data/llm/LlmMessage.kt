package com.kmt.healthanalyzer.data.llm

/** Rôle d'un message dans une conversation multi-tours avec un fournisseur LLM. */
enum class LlmRole { USER, ASSISTANT }

/**
 * Un message du fil de conversation envoyé à un fournisseur LLM.
 *
 * Le rôle système ne fait pas partie de ce fil : il reste porté à part par
 * [LlmRequest.systemPrompt], car les trois fournisseurs le traitent différemment du fil
 * `messages`.
 */
data class LlmMessage(val role: LlmRole, val content: String)

/**
 * Remet en forme un fil de conversation pour respecter la contrainte commune aux trois
 * fournisseurs : le premier message doit être de l'utilisateur, et les rôles doivent
 * alterner strictement. Un fil qui viole cette règle (par exemple reconstruit depuis un
 * historique tronqué) est corrigé silencieusement plutôt qu'envoyé tel quel, ce que les
 * fournisseurs rejetteraient.
 */
internal fun normalizeConversation(messages: List<LlmMessage>): List<LlmMessage> {
    var trimmed = messages
    while (trimmed.isNotEmpty() && trimmed.first().role == LlmRole.ASSISTANT) {
        trimmed = trimmed.drop(1)
    }

    val merged = mutableListOf<LlmMessage>()
    for (message in trimmed) {
        val last = merged.lastOrNull()
        if (last != null && last.role == message.role) {
            merged[merged.lastIndex] = last.copy(content = "${last.content}\n\n${message.content}")
        } else {
            merged.add(message)
        }
    }
    return merged
}
