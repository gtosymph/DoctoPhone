package com.kmt.healthanalyzer.data.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AnthropicClient

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AnthropicClient(httpClient, json, server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleRequest() = LlmRequest(
        systemPrompt = "Tu es un assistant santé.",
        userPrompt = "Analyse mes données de sommeil.",
        model = "claude-sonnet-5",
    )

    @Test
    fun `reponse nominale est correctement deserialisee`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "content": [{"type": "text", "text": "Analyse terminée."}],
                  "model": "claude-sonnet-5",
                  "usage": {"input_tokens": 120, "output_tokens": 45}
                }
                """.trimIndent(),
            ),
        )

        val result = client.complete(sampleRequest(), "sk-ant-test")

        assertEquals("Analyse terminée.", result.text)
        assertEquals(120, result.inputTokens)
        assertEquals(45, result.outputTokens)
        assertEquals("claude-sonnet-5", result.model)
    }

    @Test
    fun `401 declenche InvalidApiKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        assertFailsWithType<LlmError.InvalidApiKey> {
            client.complete(sampleRequest(), "sk-ant-invalide")
        }
    }

    @Test
    fun `429 avec Retry-After declenche RateLimited`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("{}"),
        )

        val error = assertFailsWithType<LlmError.RateLimited> {
            client.complete(sampleRequest(), "sk-ant-test")
        }

        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun `500 declenche Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("erreur interne"))

        val error = assertFailsWithType<LlmError.Server> {
            client.complete(sampleRequest(), "sk-ant-test")
        }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun `corps JSON invalide declenche Malformed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("pas du json"))

        assertFailsWithType<LlmError.Malformed> {
            client.complete(sampleRequest(), "sk-ant-test")
        }
    }

    @Test
    fun `la requete envoyee porte le bon chemin les bons en-tetes et le bon corps`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}],"model":"claude-sonnet-5","usage":{"input_tokens":1,"output_tokens":1}}""",
            ),
        )

        client.complete(sampleRequest(), "sk-ant-test")

        val recorded = server.takeRequest()
        val requestBody = recorded.body.readUtf8()

        assertEquals("/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertTrue(requestBody.contains("\"system\":\"Tu es un assistant santé.\""))
        assertTrue(requestBody.contains("\"role\":\"user\""))
    }

    @Test
    fun `la conversation multi-tours envoie tous les messages dans l'ordre`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}],"model":"claude-sonnet-5","usage":{"input_tokens":1,"output_tokens":1}}""",
            ),
        )

        client.complete(
            LlmRequest(
                systemPrompt = "Tu es un assistant santé.",
                messages = listOf(
                    LlmMessage(LlmRole.USER, "Bonjour"),
                    LlmMessage(LlmRole.ASSISTANT, "Bonjour, que puis-je faire ?"),
                    LlmMessage(LlmRole.USER, "Analyse mon sommeil."),
                ),
                model = "claude-sonnet-5",
            ),
            "sk-ant-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"role\":\"user\",\"content\":\"Bonjour\""))
        assertTrue(requestBody.contains("\"role\":\"assistant\",\"content\":\"Bonjour, que puis-je faire ?\""))
        assertTrue(requestBody.contains("\"role\":\"user\",\"content\":\"Analyse mon sommeil.\""))
    }

    @Test
    fun `un fil qui commence par l'assistant est corrige avant l'envoi`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}],"model":"claude-sonnet-5","usage":{"input_tokens":1,"output_tokens":1}}""",
            ),
        )

        client.complete(
            LlmRequest(
                systemPrompt = "Tu es un assistant santé.",
                messages = listOf(
                    LlmMessage(LlmRole.ASSISTANT, "Je ne devrais pas être en tête."),
                    LlmMessage(LlmRole.USER, "Analyse mon sommeil."),
                ),
                model = "claude-sonnet-5",
            ),
            "sk-ant-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(!requestBody.contains("Je ne devrais pas être en tête."))
        assertTrue(requestBody.contains("Analyse mon sommeil."))
    }

    @Test
    fun `deux messages utilisateur consecutifs sont fusionnes avant l'envoi`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}],"model":"claude-sonnet-5","usage":{"input_tokens":1,"output_tokens":1}}""",
            ),
        )

        client.complete(
            LlmRequest(
                systemPrompt = "Tu es un assistant santé.",
                messages = listOf(
                    LlmMessage(LlmRole.USER, "Premier message."),
                    LlmMessage(LlmRole.USER, "Deuxième message."),
                ),
                model = "claude-sonnet-5",
            ),
            "sk-ant-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"content\":\"Premier message.\\n\\nDeuxième message.\""))
    }
}
