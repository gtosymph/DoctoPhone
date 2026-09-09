package com.kmt.healthanalyzer.data.llm

/**
 * Fournisseur LLM que l'utilisateur peut choisir dans les réglages.
 *
 * Chaque fournisseur porte son modèle par défaut et l'URL de sa console pour créer une clé API.
 */
enum class LlmProvider(val displayName: String, val defaultModel: String, val consoleUrl: String) {
    ANTHROPIC("Claude (Anthropic)", "claude-sonnet-5", "https://console.anthropic.com/settings/keys"),
    OPENAI("OpenAI", "gpt-5.4", "https://platform.openai.com/api-keys"),
    GEMINI("Google Gemini", "gemini-3.1-pro-preview", "https://aistudio.google.com/apikey"),
}

/**
 * Requête d'analyse envoyée à un fournisseur LLM.
 *
 * [systemPrompt] porte les instructions de contexte, [messages] porte le fil de conversation
 * (données de santé à analyser, puis échanges suivants). Le premier message doit être de
 * l'utilisateur ; voir [normalizeConversation] pour la remise en forme appliquée par les clients.
 */
data class LlmRequest(
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val model: String,
    val maxOutputTokens: Int = 4096,
    val temperature: Float = 0.3f,
) {
    /**
     * Constructeur de compatibilité pour un unique message utilisateur : les appelants
     * historiques à un seul échange (analyse ponctuelle, récit du rapport) continuent de
     * compiler sans modification.
     */
    constructor(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        maxOutputTokens: Int = 4096,
        temperature: Float = 0.3f,
    ) : this(
        systemPrompt = systemPrompt,
        messages = listOf(LlmMessage(LlmRole.USER, userPrompt)),
        model = model,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
    )
}

/** Réponse d'un fournisseur LLM, avec l'usage de jetons quand le fournisseur le communique. */
data class LlmResponse(val text: String, val inputTokens: Int?, val outputTokens: Int?, val model: String)

/**
 * Erreur levée par un [LlmClient]. Chaque sous-type correspond à une cause distincte,
 * pour permettre à la couche appelante d'afficher un message adapté à l'utilisateur.
 */
sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Aucune clé API n'est enregistrée pour ce fournisseur. */
    class MissingApiKey : LlmError("Aucune clé API n'est enregistrée pour ce fournisseur.")

    /** Le fournisseur a refusé la clé API (HTTP 401 ou 403). */
    class InvalidApiKey : LlmError("Le fournisseur refuse la clé API.")

    /** Le fournisseur limite le débit des requêtes (HTTP 429). */
    class RateLimited(val retryAfterSeconds: Int?) : LlmError("Le fournisseur limite le débit des requêtes.")

    /** La requête réseau a échoué avant de recevoir une réponse. */
    class Network(cause: Throwable) : LlmError("La requête réseau a échoué.", cause)

    /** Le fournisseur renvoie une erreur serveur ou une erreur HTTP non gérée spécifiquement. */
    class Server(val statusCode: Int, val body: String) : LlmError("Le fournisseur renvoie une erreur $statusCode.")

    /** La réponse du fournisseur n'a pas pu être interprétée. */
    class Malformed(cause: Throwable) : LlmError("La réponse du fournisseur est illisible.", cause)
}

/** Contrat commun à tous les clients LLM, un par [LlmProvider]. */
interface LlmClient {
    val provider: LlmProvider

    /**
     * Lance une complétion auprès du fournisseur avec la clé API fournie par l'utilisateur.
     *
     * @throws LlmError si le fournisseur refuse la requête ou si la réponse est illisible.
     */
    suspend fun complete(request: LlmRequest, apiKey: String): LlmResponse
}
