package com.kmt.healthanalyzer.data.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val GITHUB_API_BASE_URL = "https://api.github.com"
private const val RELEASE_JSON_ASSET_NAME = "release.json"
private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403

@Serializable
private data class GitHubReleaseResponse(val assets: List<GitHubAsset> = emptyList())

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/**
 * Interroge la release GitHub la plus récente du dépôt [UpdateConfig.GITHUB_REPOSITORY].
 *
 * Le dépôt est public : l'API anonyme répond sans jeton, plafonnée à 60 requêtes par heure
 * et par IP. Cette requête ne porte ni corps ni paramètre — c'est un simple GET — et aucune
 * donnée de santé ni identifiant n'y transite.
 *
 * Chaque URL de téléchargement trouvée dans la réponse (le fichier `release.json`, puis
 * l'APK une fois `release.json` lu) est une donnée renvoyée par un tiers, pas une consigne :
 * elle passe par [assetUrlValidator] avant d'être suivie. En production, ce validateur est
 * [DownloadUrlValidator], qui n'accepte que des hôtes GitHub en HTTPS. Les tests
 * l'assouplissent pour pointer vers un serveur simulé, à l'image de `apiBaseUrl`.
 *
 * Pas de constructeur `@Inject` : Dagger ignore les valeurs par défaut de Kotlin et exigerait
 * un binding pour [apiBaseUrl] et [assetUrlValidator], qu'aucun module ne fournit. Comme
 * `AnthropicClient` et les autres clients LLM, cette classe est construite à la main, ici par
 * `UpdateModule`.
 */
class GitHubReleaseClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    apiBaseUrl: HttpUrl = GITHUB_API_BASE_URL.toHttpUrl(),
    private val assetUrlValidator: (String) -> HttpUrl = DownloadUrlValidator::requireTrustedHttpsUrl,
) {

    private val latestReleaseEndpoint: HttpUrl = apiBaseUrl.newBuilder()
        .addPathSegments("repos/${UpdateConfig.GITHUB_REPOSITORY}/releases/latest")
        .build()

    /**
     * @throws UpdateError si la vérification échoue (réseau, quota, aucune release, réponse
     *   illisible, ou URL de téléchargement non fiable).
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val latestRelease = fetchLatestRelease()
        val releaseJsonAsset = latestRelease.assets.firstOrNull { it.name == RELEASE_JSON_ASSET_NAME }
            ?: throw UpdateError.MissingReleaseAsset()
        val release = fetchReleaseInfo(releaseJsonAsset.browserDownloadUrl)

        if (release.versionCode <= currentVersionCode) return@withContext UpdateCheckResult.UpToDate

        val apkAsset = latestRelease.assets.firstOrNull { it.name == release.apk }
            ?: throw UpdateError.MissingApkAsset()
        UpdateCheckResult.UpdateAvailable(release, apkAsset.browserDownloadUrl)
    }

    private fun fetchLatestRelease(): GitHubReleaseResponse {
        val body = execute(Request.Builder().url(latestReleaseEndpoint).get().build())
        return try {
            json.decodeFromString(GitHubReleaseResponse.serializer(), body)
        } catch (malformed: Exception) {
            throw UpdateError.Malformed(malformed)
        }
    }

    private fun fetchReleaseInfo(releaseJsonUrl: String): ReleaseInfo {
        val validatedUrl = assetUrlValidator(releaseJsonUrl)
        val body = execute(Request.Builder().url(validatedUrl).get().build())
        return try {
            json.decodeFromString(ReleaseInfo.serializer(), body)
        } catch (malformed: Exception) {
            throw UpdateError.Malformed(malformed)
        }
    }

    /** Exécute la requête et traduit les échecs de transport et les codes HTTP en [UpdateError]. */
    private fun execute(request: Request): String {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (networkFailure: IOException) {
            throw UpdateError.Network(networkFailure)
        }
        response.use { httpResponse ->
            val bodyText = try {
                httpResponse.body?.string().orEmpty()
            } catch (readFailure: IOException) {
                throw UpdateError.Network(readFailure)
            }
            if (httpResponse.isSuccessful) return bodyText
            throw when (httpResponse.code) {
                HTTP_NOT_FOUND -> UpdateError.NoReleasePublished()
                HTTP_FORBIDDEN -> UpdateError.RateLimited()
                else -> UpdateError.Server(httpResponse.code)
            }
        }
    }
}
