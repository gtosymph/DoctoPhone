package com.kmt.healthanalyzer.data.update

/**
 * Dépôt GitHub public qui publie les releases de l'app.
 *
 * Unique endroit où ce nom est écrit : [GitHubReleaseClient] construit l'URL de l'API à
 * partir de cette constante, plutôt que de la recopier dans chaque appelant.
 */
internal object UpdateConfig {
    const val GITHUB_REPOSITORY = "gtosymph/DoctoPhone"
}
