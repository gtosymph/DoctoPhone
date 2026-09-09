package com.kmt.healthanalyzer.data.llm

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Type MIME utilisé pour tous les corps de requête JSON envoyés aux fournisseurs LLM. */
internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Exécute un appel HTTP vers un fournisseur LLM et traduit les échecs de transport et les codes
 * HTTP en [LlmError]. Ce comportement est commun aux trois clients ; seule la construction de la
 * requête et le décodage du corps de succès leur sont propres.
 */
internal object LlmHttpExecutor {
    suspend fun <T> execute(
        httpClient: OkHttpClient,
        request: Request,
        parseSuccess: (String) -> T,
    ): T {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw LlmError.Network(e)
        }

        response.use { httpResponse ->
            val bodyText = try {
                httpResponse.body?.string().orEmpty()
            } catch (e: IOException) {
                throw LlmError.Network(e)
            }
            if (httpResponse.isSuccessful) {
                return try {
                    parseSuccess(bodyText)
                } catch (e: Exception) {
                    throw LlmError.Malformed(e)
                }
            }
            throw httpResponse.code.toLlmError(bodyText, httpResponse.header("retry-after"))
        }
    }

    private fun Int.toLlmError(body: String, retryAfterHeader: String?): LlmError = when (this) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> LlmError.InvalidApiKey()
        HTTP_TOO_MANY_REQUESTS -> LlmError.RateLimited(retryAfterHeader?.toIntOrNull())
        else -> LlmError.Server(this, body)
    }
}
