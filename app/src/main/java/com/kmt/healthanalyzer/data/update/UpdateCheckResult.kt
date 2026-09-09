package com.kmt.healthanalyzer.data.update

/** Résultat d'une vérification de mise à jour réussie (voir [UpdateError] pour les échecs). */
sealed class UpdateCheckResult {

    /** Le `versionCode` publié n'est pas strictement supérieur à celui installé. */
    data object UpToDate : UpdateCheckResult()

    /** Une version plus récente est publiée ; [apkDownloadUrl] est déjà vérifiée (hôte GitHub, HTTPS). */
    data class UpdateAvailable(val release: ReleaseInfo, val apkDownloadUrl: String) : UpdateCheckResult()
}
