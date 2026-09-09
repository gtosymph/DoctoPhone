package com.kmt.healthanalyzer.data.llm

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Fabrique le [LlmClient] correspondant au fournisseur choisi par l'utilisateur dans les réglages.
 */
@Singleton
class LlmClientFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    fun clientFor(provider: LlmProvider): LlmClient = when (provider) {
        LlmProvider.ANTHROPIC -> AnthropicClient(httpClient, json)
        LlmProvider.OPENAI -> OpenAiClient(httpClient, json)
        LlmProvider.GEMINI -> GeminiClient(httpClient, json)
    }
}
