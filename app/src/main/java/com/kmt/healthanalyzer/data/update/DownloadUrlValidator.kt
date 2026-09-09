package com.kmt.healthanalyzer.data.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Vérifie qu'une URL de téléchargement venue de la réponse GitHub est sûre à suivre.
 *
 * La release renvoyée par l'API est une donnée fournie par un tiers, pas une consigne :
 * [GitHubReleaseClient] et le téléchargeur d'APK passent chaque URL qu'ils reçoivent par ce
 * validateur avant de lancer la moindre requête. Si l'URL ne pointe pas vers un hôte GitHub
 * en HTTPS, l'app refuse de la suivre plutôt que de faire confiance à une donnée externe.
 */
object DownloadUrlValidator {

    /** GitHub sert ses fichiers joints depuis ces hôtes (ou un sous-domaine direct). */
    private val TRUSTED_HOST_SUFFIXES = listOf("github.com", "githubusercontent.com")

    /**
     * @throws UpdateError.InsecureUrl si [rawUrl] n'est pas une URL HTTPS valide.
     * @throws UpdateError.UntrustedDownloadHost si l'hôte n'est pas un hôte GitHub connu.
     */
    fun requireTrustedHttpsUrl(rawUrl: String): HttpUrl {
        val url = rawUrl.toHttpUrlOrNull() ?: throw UpdateError.InsecureUrl(rawUrl)
        if (url.scheme != "https") throw UpdateError.InsecureUrl(rawUrl)
        if (TRUSTED_HOST_SUFFIXES.none { suffix -> url.host == suffix || url.host.endsWith(".$suffix") }) {
            throw UpdateError.UntrustedDownloadHost(url.host)
        }
        return url
    }
}
