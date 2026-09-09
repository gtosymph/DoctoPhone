package com.kmt.healthanalyzer.data.update

/**
 * Réduit le nom de fichier annoncé par `release.json` (champ `apk`) à un nom de fichier sûr.
 *
 * Ce nom vient de la réponse GitHub, au même titre que l'URL de téléchargement : c'est une
 * donnée, pas une consigne (voir [DownloadUrlValidator]). `File(dir, "../../databases/health.db")`
 * écrirait hors du dossier de cache dédié à la mise à jour, potentiellement dans la base de
 * données de santé. On ne garde donc que le dernier segment du chemin annoncé, et on exige
 * l'extension `.apk` plutôt que d'accepter ce qui vient : une fois le dernier segment isolé,
 * aucune traversée de répertoire n'est plus possible, et l'extension écarte une cible qui ne
 * serait pas un APK.
 */
object ApkFileNameValidator {

    /** @throws UpdateError.Malformed si [announced] n'est pas un nom de fichier `.apk` simple. */
    fun requireSafeApkFileName(announced: String): String {
        val leaf = announced.substringAfterLast('/').substringAfterLast('\\')
        if (leaf.isBlank() || leaf == "." || leaf == ".." || !leaf.endsWith(".apk")) {
            throw UpdateError.Malformed()
        }
        return leaf
    }
}
