package com.kmt.healthanalyzer.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
private const val GEMINI_MODELS_PATH = "v1beta/models"
private const val GEMINI_GENERATE_CONTENT_SUFFIX = ":generateContent"

@Serializable
private data class GeminiRequestBody(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)

@Serializable
private data class GeminiPart(val text: String)

/**
 * Gemini nomme "model" ce que les autres fournisseurs nomment "assistant" ; `role` est omis
 * (valeur par défaut `null`) pour `systemInstruction`, qui n'en porte pas.
 */
private fun LlmRole.toGeminiRole(): String = when (this) {
    LlmRole.USER -> "user"
    LlmRole.ASSISTANT -> "model"
}

@Serializable
private data class GeminiGenerationConfig(val maxOutputTokens: Int, val temperature: Float)

@Serializable
private data class GeminiResponseBody(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
private data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
)

/**
 * Client HTTP pour l'API generateContent de Google Gemini.
 *
 * Authentifie via l'en-tête `x-goog-api-key`. Le modèle fait partie du chemin de l'URL.
 */
class GeminiClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: HttpUrl = GEMINI_BASE_URL.toHttpUrl(),
) : LlmClient {

    override val provider: LlmProvider = LlmProvider.GEMINI

    private fun endpointFor(model: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegments(GEMINI_MODELS_PATH)
        .addEncodedPathSegment("$model$GEMINI_GENERATE_CONTENT_SUFFIX")
        .build()

    override suspend fun complete(request: LlmRequest, apiKey: String): LlmResponse =
        withContext(Dispatchers.IO) {
            val requestBody = GeminiRequestBody(
                contents = normalizeConversation(request.messages).map {
                    GeminiContent(parts = listOf(GeminiPart(text = it.content)), role = it.role.toGeminiRole())
                },
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = request.systemPrompt))),
                generationConfig = GeminiGenerationConfig(
                    maxOutputTokens = request.maxOutputTokens,
                    temperature = request.temperature,
                ),
            )
            val httpRequest = Request.Builder()
                .url(endpointFor(request.model))
                .addHeader("x-goog-api-key", apiKey)
                .post(json.encodeToString(GeminiRequestBody.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            LlmHttpExecutor.execute(httpClient, httpRequest) { rawBody ->
                val parsed = json.decodeFromString(GeminiResponseBody.serializer(), rawBody)
                val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: error("Réponse Gemini sans contenu candidat.")
                LlmResponse(
                    text = text,
                    inputTokens = parsed.usageMetadata?.promptTokenCount,
                    outputTokens = parsed.usageMetadata?.candidatesTokenCount,
                    model = request.model,
                )
            }
        }
}
