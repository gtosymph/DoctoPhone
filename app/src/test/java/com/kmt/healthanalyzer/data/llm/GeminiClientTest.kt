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

class GeminiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GeminiClient

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GeminiClient(httpClient, json, server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleRequest() = LlmRequest(
        systemPrompt = "Tu es un assistant santé.",
        userPrompt = "Analyse mes données de sommeil.",
        model = "gemini-2.5-pro",
    )

    @Test
    fun `reponse nominale est correctement deserialisee`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "candidates": [{"content": {"parts": [{"text": "Analyse terminée."}]}}],
                  "usageMetadata": {"promptTokenCount": 300, "candidatesTokenCount": 90}
                }
                """.trimIndent(),
            ),
        )

        val result = client.complete(sampleRequest(), "gemini-key-test")

        assertEquals("Analyse terminée.", result.text)
        assertEquals(300, result.inputTokens)
        assertEquals(90, result.outputTokens)
        assertEquals("gemini-2.5-pro", result.model)
    }

    @Test
    fun `401 declenche InvalidApiKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        assertFailsWithType<LlmError.InvalidApiKey> {
            client.complete(sampleRequest(), "gemini-key-invalide")
        }
    }

    @Test
    fun `429 avec Retry-After declenche RateLimited`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("{}"),
        )

        val error = assertFailsWithType<LlmError.RateLimited> {
            client.complete(sampleRequest(), "gemini-key-test")
        }

        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun `500 declenche Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("erreur interne"))

        val error = assertFailsWithType<LlmError.Server> {
            client.complete(sampleRequest(), "gemini-key-test")
        }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun `corps JSON invalide declenche Malformed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("pas du json"))

        assertFailsWithType<LlmError.Malformed> {
            client.complete(sampleRequest(), "gemini-key-test")
        }
    }

    @Test
    fun `la requete envoyee porte le bon chemin les bons en-tetes et le bon corps`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1}}""",
            ),
        )

        client.complete(sampleRequest(), "gemini-key-test")

        val recorded = server.takeRequest()
        val requestBody = recorded.body.readUtf8()

        assertEquals("/v1beta/models/gemini-2.5-pro:generateContent", recorded.path)
        assertEquals("gemini-key-test", recorded.getHeader("x-goog-api-key"))
        assertTrue(requestBody.contains("\"systemInstruction\""))
        assertTrue(requestBody.contains("Tu es un assistant santé."))
    }

    @Test
    fun `la conversation multi-tours utilise le role model pour l'assistant`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1}}""",
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
                model = "gemini-2.5-pro",
            ),
            "gemini-key-test",
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"role\":\"user\""))
        assertTrue(requestBody.contains("\"role\":\"model\""))
        assertTrue(!requestBody.contains("\"role\":\"assistant\""))
    }

    @Test
    fun `systemInstruction ne porte pas de role`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1}}""",
            ),
        )

        client.complete(sampleRequest(), "gemini-key-test")

        val requestBody = server.takeRequest().body.readUtf8()
        val systemInstructionIndex = requestBody.indexOf("\"systemInstruction\"")
        val generationConfigIndex = requestBody.indexOf("\"generationConfig\"")
        val systemInstructionSegment = requestBody.substring(systemInstructionIndex, generationConfigIndex)
        assertTrue(!systemInstructionSegment.contains("\"role\""))
    }
}
