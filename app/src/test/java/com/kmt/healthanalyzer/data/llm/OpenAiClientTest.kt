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

class OpenAiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiClient

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiClient(httpClient, json, server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleRequest() = LlmRequest(
        systemPrompt = "Tu es un assistant santé.",
        userPrompt = "Analyse mes données de sommeil.",
        model = "gpt-5.4",
    )

    @Test
    fun `reponse nominale est correctement deserialisee`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "model": "gpt-5.4",
                  "choices": [{"message": {"role": "assistant", "content": "Analyse terminée."}}],
                  "usage": {"prompt_tokens": 200, "completion_tokens": 60}
                }
                """.trimIndent(),
            ),
        )

        val result = client.complete(sampleRequest(), "sk-openai-test")

        assertEquals("Analyse terminée.", result.text)
        assertEquals(200, result.inputTokens)
        assertEquals(60, result.outputTokens)
        assertEquals("gpt-5.4", result.model)
    }

    @Test
    fun `401 declenche InvalidApiKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        assertFailsWithType<LlmError.InvalidApiKey> {
            client.complete(sampleRequest(), "sk-openai-invalide")
        }
    }

    @Test
    fun `429 avec Retry-After declenche RateLimited`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("{}"),
        )

        val error = assertFailsWithType<LlmError.RateLimited> {
            client.complete(sampleRequest(), "sk-openai-test")
        }

        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun `500 declenche Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("erreur interne"))

        val error = assertFailsWithType<LlmError.Server> {
            client.complete(sampleRequest(), "sk-openai-test")
        }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun `corps JSON invalide declenche Malformed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("pas du json"))

        assertFailsWithType<LlmError.Malformed> {
            client.complete(sampleRequest(), "sk-openai-test")
        }
    }

    @Test
    fun `la requete envoyee porte le bon chemin les bons en-tetes et le bon corps`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"model":"gpt-5.4","choices":[{"message":{"role":"assistant","content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1}}""",
            ),
        )

        client.complete(sampleRequest(), "sk-openai-test")

        val recorded = server.takeRequest()
        val requestBody = recorded.body.readUtf8()

        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-openai-test", recorded.getHeader("Authorization"))
        assertTrue(requestBody.contains("\"role\":\"system\""))
        assertTrue(requestBody.contains("\"role\":\"user\""))
        assertTrue(requestBody.contains("Tu es un assistant santé."))
    }

    @Test
    fun `la conversation multi-tours garde le systeme en tete puis les tours dans l'ordre`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"model":"gpt-5.4","choices":[{"message":{"role":"assistant","content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1}}""",
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
                model = "gpt-5.4",
            ),
            "sk-openai-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.startsWith("{\"model\":\"gpt-5.4\",\"messages\":[{\"role\":\"system\""))
        assertTrue(requestBody.contains("\"role\":\"user\",\"content\":\"Bonjour\""))
        assertTrue(requestBody.contains("\"role\":\"assistant\",\"content\":\"Bonjour, que puis-je faire ?\""))
        assertTrue(requestBody.contains("\"role\":\"user\",\"content\":\"Analyse mon sommeil.\""))
    }

    @Test
    fun `deux messages assistant consecutifs sont fusionnes avant l'envoi`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"model":"gpt-5.4","choices":[{"message":{"role":"assistant","content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1}}""",
            ),
        )

        client.complete(
            LlmRequest(
                systemPrompt = "Tu es un assistant santé.",
                messages = listOf(
                    LlmMessage(LlmRole.USER, "Bonjour"),
                    LlmMessage(LlmRole.ASSISTANT, "Première partie."),
                    LlmMessage(LlmRole.ASSISTANT, "Seconde partie."),
                ),
                model = "gpt-5.4",
            ),
            "sk-openai-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"content\":\"Première partie.\\n\\nSeconde partie.\""))
    }
}
