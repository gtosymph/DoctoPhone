package com.kmt.healthanalyzer.data.update

/**
 * Erreur levée lors de la vérification ou du téléchargement d'une mise à jour. Chaque
 * sous-type porte un message déjà en français, prêt à être montré à l'utilisateur.
 */
sealed class UpdateError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** La requête réseau a échoué avant de recevoir une réponse. */
    class Network(cause: Throwable) : UpdateError("La vérification de mise à jour a échoué : pas de réseau.", cause)

    /** GitHub renvoie 404 : le dépôt n'a encore publié aucune release. */
    class NoReleasePublished : UpdateError("Aucune version n'a encore été publiée.")

    /** GitHub renvoie 403 : le quota de l'API anonyme (60 requêtes/heure/IP) est atteint. */
    class RateLimited : UpdateError("Le quota de vérifications GitHub est atteint pour l'instant. Réessayez plus tard.")

    /** GitHub renvoie une autre erreur HTTP non gérée spécifiquement. */
    class Server(val statusCode: Int) : UpdateError("GitHub renvoie une erreur $statusCode.")

    /** La réponse de GitHub, ou le fichier `release.json`, n'a pas pu être interprété. */
    class Malformed(cause: Throwable? = null) : UpdateError("La réponse de GitHub est illisible.", cause)

    /** La release la plus récente ne porte pas de fichier joint `release.json`. */
    class MissingReleaseAsset : UpdateError("La dernière release ne contient pas de fichier release.json.")

    /** `release.json` désigne un APK qui n'est pas parmi les fichiers joints de la release. */
    class MissingApkAsset : UpdateError("La dernière release ne contient pas l'APK annoncé.")

    /**
     * Une URL de téléchargement venue de la réponse GitHub n'est pas en HTTPS.
     *
     * La réponse de l'API est une donnée renvoyée par un tiers, pas une consigne : l'app
     * refuse de suivre une URL non chiffrée plutôt que de lui faire confiance.
     */
    class InsecureUrl(val url: String) : UpdateError("L'URL de téléchargement n'est pas sécurisée (HTTPS requis).")

    /** Une URL de téléchargement venue de la réponse GitHub ne pointe pas vers un hôte GitHub. */
    class UntrustedDownloadHost(val host: String) :
        UpdateError("L'URL de téléchargement ne provient pas de GitHub.")
}
