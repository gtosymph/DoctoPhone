package com.kmt.healthanalyzer.data.update

import com.kmt.healthanalyzer.data.llm.assertFailsWithType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La réponse de l'API GitHub est une donnée renvoyée par un tiers, pas une consigne : ces
 * tests vérifient que [DownloadUrlValidator] refuse de suivre une URL de téléchargement qui
 * ne provient pas d'un hôte GitHub, ou qui n'est pas en HTTPS.
 */
class DownloadUrlValidatorTest {

    @Test
    fun `une URL github com en https est acceptee`() = runTest {
        val url = DownloadUrlValidator.requireTrustedHttpsUrl(
            "https://github.com/gtosymph/DoctoPhone/releases/download/v1/release.json",
        )

        assertEquals("github.com", url.host)
    }

    @Test
    fun `une URL githubusercontent com en https est acceptee`() = runTest {
        val url = DownloadUrlValidator.requireTrustedHttpsUrl(
            "https://release-assets.githubusercontent.com/foo/bar.apk",
        )

        assertEquals("release-assets.githubusercontent.com", url.host)
    }

    @Test
    fun `une URL en http declenche InsecureUrl`() = runTest {
        assertFailsWithType<UpdateError.InsecureUrl> {
            DownloadUrlValidator.requireTrustedHttpsUrl("http://github.com/gtosymph/DoctoPhone/release.json")
        }
    }

    @Test
    fun `une URL dont l'hote n'est pas GitHub declenche UntrustedDownloadHost`() = runTest {
        assertFailsWithType<UpdateError.UntrustedDownloadHost> {
            DownloadUrlValidator.requireTrustedHttpsUrl("https://evil.example.com/release.json")
        }
    }

    @Test
    fun `un hote qui contient githubusercontent com en suffixe frauduleux est refuse`() = runTest {
        // "notgithubusercontent.com" ne se termine pas par ".githubusercontent.com" : un hôte
        // qui imite le nom ne doit pas passer la vérification par simple inclusion de texte.
        assertFailsWithType<UpdateError.UntrustedDownloadHost> {
            DownloadUrlValidator.requireTrustedHttpsUrl("https://notgithubusercontent.com/release.json")
        }
    }

    @Test
    fun `une chaine qui n'est pas une URL declenche InsecureUrl`() = runTest {
        assertFailsWithType<UpdateError.InsecureUrl> {
            DownloadUrlValidator.requireTrustedHttpsUrl("pas-une-url")
        }
    }
}
