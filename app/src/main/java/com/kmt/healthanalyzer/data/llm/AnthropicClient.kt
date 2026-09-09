package com.kmt.healthanalyzer.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient

private const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"
private const val ANTHROPIC_MESSAGES_PATH = "v1/messages"
private const val ANTHROPIC_API_VERSION = "2023-06-01"

@Serializable
private data class AnthropicRequestBody(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Float,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

/**
 * Anthropic attend les rôles "user"/"assistant", identiques au nom de [LlmRole] en minuscules.
 */
private fun LlmRole.toAnthropicRole(): String = name.lowercase()

@Serializable
private data class AnthropicResponseBody(
    val content: List<AnthropicContentBlock> = emptyList(),
    val model: String = "",
    val usage: AnthropicUsage? = null,
)

@Serializable
private data class AnthropicContentBlock(val type: String = "", val text: String = "")

@Serializable
private data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)

/**
 * Client HTTP pour l'API Messages d'Anthropic (Claude).
 *
 * Authentifie via l'en-tête `x-api-key` et l'en-tête `anthropic-version`.
 */
class AnthropicClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    baseUrl: HttpUrl = ANTHROPIC_BASE_URL.toHttpUrl(),
) : LlmClient {

    override val provider: LlmProvider = LlmProvider.ANTHROPIC

    private val endpoint: HttpUrl = baseUrl.newBuilder()
        .addPathSegments(ANTHROPIC_MESSAGES_PATH)
        .build()

    override suspend fun complete(request: LlmRequest, apiKey: String): LlmResponse =
        withContext(Dispatchers.IO) {
            val requestBody = AnthropicRequestBody(
                model = request.model,
                maxTokens = request.maxOutputTokens,
                temperature = request.temperature,
                system = request.systemPrompt,
                messages = normalizeConversation(request.messages).map {
                    AnthropicMessage(role = it.role.toAnthropicRole(), content = it.content)
                },
            )
            val httpRequest = Request.Builder()
                .url(endpoint)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_API_VERSION)
                .post(json.encodeToString(AnthropicRequestBody.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            LlmHttpExecutor.execute(httpClient, httpRequest) { rawBody ->
                val parsed = json.decodeFromString(AnthropicResponseBody.serializer(), rawBody)
                val text = parsed.content.firstOrNull()?.text
                    ?: error("Réponse Anthropic sans bloc de texte.")
                LlmResponse(
                    text = text,
                    inputTokens = parsed.usage?.inputTokens,
                    outputTokens = parsed.usage?.outputTokens,
                    model = parsed.model.ifBlank { request.model },
                )
            }
        }
}
