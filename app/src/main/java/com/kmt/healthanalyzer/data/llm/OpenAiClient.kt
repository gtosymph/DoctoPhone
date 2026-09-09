package com.kmt.healthanalyzer.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val OPENAI_BASE_URL = "https://api.openai.com"
private const val OPENAI_CHAT_COMPLETIONS_PATH = "v1/chat/completions"
private const val OPENAI_SYSTEM_ROLE = "system"

@Serializable
private data class OpenAiRequestBody(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Float,
)

@Serializable
private data class OpenAiMessage(val role: String, val content: String)

/** OpenAI attend les rôles "user"/"assistant", identiques au nom de [LlmRole] en minuscules. */
private fun LlmRole.toOpenAiRole(): String = name.lowercase()

@Serializable
private data class OpenAiResponseBody(
    val model: String = "",
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
private data class OpenAiChoice(val message: OpenAiMessage)

@Serializable
private data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)

/**
 * Client HTTP pour l'API Chat Completions d'OpenAI.
 *
 * Authentifie via l'en-tête `Authorization: Bearer <clé>`.
 */
class OpenAiClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    baseUrl: HttpUrl = OPENAI_BASE_URL.toHttpUrl(),
) : LlmClient {

    override val provider: LlmProvider = LlmProvider.OPENAI

    private val endpoint: HttpUrl = baseUrl.newBuilder()
        .addPathSegments(OPENAI_CHAT_COMPLETIONS_PATH)
        .build()

    override suspend fun complete(request: LlmRequest, apiKey: String): LlmResponse =
        withContext(Dispatchers.IO) {
            val requestBody = OpenAiRequestBody(
                model = request.model,
                messages = listOf(OpenAiMessage(role = OPENAI_SYSTEM_ROLE, content = request.systemPrompt)) +
                    normalizeConversation(request.messages).map {
                        OpenAiMessage(role = it.role.toOpenAiRole(), content = it.content)
                    },
                maxTokens = request.maxOutputTokens,
                temperature = request.temperature,
            )
            val httpRequest = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(OpenAiRequestBody.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            LlmHttpExecutor.execute(httpClient, httpRequest) { rawBody ->
                val parsed = json.decodeFromString(OpenAiResponseBody.serializer(), rawBody)
                val text = parsed.choices.firstOrNull()?.message?.content
                    ?: error("Réponse OpenAI sans message de choix.")
                LlmResponse(
                    text = text,
                    inputTokens = parsed.usage?.promptTokens,
                    outputTokens = parsed.usage?.completionTokens,
                    model = parsed.model.ifBlank { request.model },
                )
            }
        }
}
