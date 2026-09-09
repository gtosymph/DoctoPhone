package com.kmt.healthanalyzer.data.update

import com.kmt.healthanalyzer.data.llm.assertFailsWithType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubReleaseClientTest {

    private lateinit var server: MockWebServer
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * Le validateur d'hôte réel n'accepte que des hôtes GitHub, jamais le serveur simulé du
     * test. Les tests qui vérifient le chemin nominal (téléchargement effectif de
     * `release.json` depuis le serveur simulé) assouplissent donc ce contrôle ; les tests
     * qui vérifient le contrôle lui-même le laissent à sa valeur par défaut.
     */
    private fun clientWithRelaxedHostCheck(): GitHubReleaseClient = GitHubReleaseClient(
        httpClient = httpClient,
        json = json,
        apiBaseUrl = server.url("/"),
        assetUrlValidator = { rawUrl -> rawUrl.toHttpUrl() },
    )

    private fun clientWithDefaultHostCheck(): GitHubReleaseClient = GitHubReleaseClient(
        httpClient = httpClient,
        json = json,
        apiBaseUrl = server.url("/"),
    )

    private fun releasesLatestBody(releaseJsonUrl: String, apkUrl: String) = """
        {
          "tag_name": "v0.2.123",
          "assets": [
            {"name": "release.json", "browser_download_url": "$releaseJsonUrl"},
            {"name": "DoctoPhone-0.2.123.apk", "browser_download_url": "$apkUrl"}
          ]
        }
    """.trimIndent()

    @Test
    fun `une version plus recente est proposee`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = server.url("/release.json").toString(),
                    apkUrl = server.url("/DoctoPhone-0.2.123.apk").toString(),
                ),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"versionCode":123,"versionName":"0.2.123","apk":"DoctoPhone-0.2.123.apk"}""",
            ),
        )

        val result = client.checkForUpdate(currentVersionCode = 100)

        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals(123, available.release.versionCode)
        assertEquals("0.2.123", available.release.versionName)
        assertEquals(server.url("/DoctoPhone-0.2.123.apk").toString(), available.apkDownloadUrl)
    }

    @Test
    fun `une version identique n'est pas proposee`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = server.url("/release.json").toString(),
                    apkUrl = server.url("/DoctoPhone-0.2.123.apk").toString(),
                ),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"versionCode":123,"versionName":"0.2.123","apk":"DoctoPhone-0.2.123.apk"}""",
            ),
        )

        val result = client.checkForUpdate(currentVersionCode = 123)

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `une version plus ancienne n'est pas proposee`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = server.url("/release.json").toString(),
                    apkUrl = server.url("/DoctoPhone-0.2.100.apk").toString(),
                ),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"versionCode":100,"versionName":"0.2.100","apk":"DoctoPhone-0.2.100.apk"}""",
            ),
        )

        val result = client.checkForUpdate(currentVersionCode = 123)

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `release json absent des fichiers joints declenche MissingReleaseAsset`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v0.2.123",
                  "assets": [
                    {"name": "DoctoPhone-0.2.123.apk", "browser_download_url": "${server.url("/apk")}"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertFailsWithType<UpdateError.MissingReleaseAsset> {
            client.checkForUpdate(currentVersionCode = 1)
        }
    }

    @Test
    fun `release json illisible declenche Malformed`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = server.url("/release.json").toString(),
                    apkUrl = server.url("/apk").toString(),
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("pas du json"))

        assertFailsWithType<UpdateError.Malformed> {
            client.checkForUpdate(currentVersionCode = 1)
        }
    }

    @Test
    fun `release json incomplet declenche Malformed`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = server.url("/release.json").toString(),
                    apkUrl = server.url("/apk").toString(),
                ),
            ),
        )
        // versionName et apk manquent.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"versionCode":123}"""))

        assertFailsWithType<UpdateError.Malformed> {
            client.checkForUpdate(currentVersionCode = 1)
        }
    }

    @Test
    fun `reponse 404 declenche NoReleasePublished`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        assertFailsWithType<UpdateError.NoReleasePublished> {
            client.checkForUpdate(currentVersionCode = 1)
        }
    }

    @Test
    fun `reponse 403 declenche RateLimited`() = runTest {
        val client = clientWithRelaxedHostCheck()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"rate limit exceeded"}"""))

        assertFailsWithType<UpdateError.RateLimited> {
            client.checkForUpdate(currentVersionCode = 1)
        }
    }

    @Test
    fun `un asset release json dont l'hote n'est pas GitHub est refuse`() = runTest {
        val client = clientWithDefaultHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = "https://evil.example.com/release.json",
                    apkUrl = "https://github.com/gtosymph/DoctoPhone/releases/download/v1/app.apk",
                ),
            ),
        )

        assertFailsWithType<UpdateError.UntrustedDownloadHost> {
            client.checkForUpdate(currentVersionCode = 1)
        }

        // Une seule requête a été émise : celle vers /releases/latest. La validation rejette
        // l'URL avant qu'une seconde requête ne parte vers l'hôte non fiable.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `un asset release json en http est refuse`() = runTest {
        val client = clientWithDefaultHostCheck()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releasesLatestBody(
                    releaseJsonUrl = "http://github.com/gtosymph/DoctoPhone/releases/download/v1/release.json",
                    apkUrl = "https://github.com/gtosymph/DoctoPhone/releases/download/v1/app.apk",
                ),
            ),
        )

        assertFailsWithType<UpdateError.InsecureUrl> {
            client.checkForUpdate(currentVersionCode = 1)
        }

        assertTrue(server.requestCount == 1)
    }
}
