package com.kmt.healthanalyzer.ui.report

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Construit les scripts poussés dans la WebView du rapport.
 *
 * **Le `ReportModel` ne passe plus par `evaluateJavascript`.** Sur un an (la période la
 * plus longue de cet écran), il reste sous 400 Ko et tenait dans une chaîne de script.
 * Mais le même chemin sert aussi la conversation d'analyse, qui elle porte tout
 * l'historique sans borne : mesuré à environ 800 Ko (double encodage compris) sur quatre
 * ans, plus de 1,5 Mo sur huit — voir `ChatJsBridge`. `evaluateJavascript` construirait
 * cette chaîne deux fois (le JSON, puis son échappement en littéral JavaScript) en
 * mémoire Java UTF-16, sur le thread principal, à chaque rafraîchissement. Corriger les
 * deux écrans du même geste évite de laisser cette même bombe à retardement ici le jour
 * où « tout l'historique » s'ajoutera au rapport.
 *
 * Le modèle part donc par `WebResourceResponse`, dans `ReportScreen.kt`
 * (`shouldInterceptRequest`, voir [MODEL_URL]), jamais comme argument d'un script. Cette
 * classe ne pousse plus qu'un signal court, sans charge utile, qui dit à la page d'aller
 * relire le modèle à jour à cette URL — `WebView.evaluateJavascript` exécute une chaîne de
 * code JavaScript, donc même un signal aussi court reste un littéral échappé comme
 * `Json.encodeToString(String.serializer(), ...)` le fait déjà pour le thème.
 *
 * Côté page, `report.html` lit `HA.host.setModelReady(version)`.
 */
object ReportJsBridge {

    /**
     * URL virtuelle du `ReportModel` courant, interceptée par `ReportScreen.kt` — jamais
     * servie par `WebViewAssetLoader` (ce n'est pas un fichier des assets), jamais servie
     * à une autre WebView que celle du rapport.
     */
    const val MODEL_URL = "https://appassets.androidplatform.net/model/report.json"

    private val json = Json

    /**
     * Le signal (sans charge utile) qui dit à la page de relire [MODEL_URL] : une nouvelle
     * version du `ReportModel` y est disponible. [version] n'est qu'un défait-cache — un
     * entier suffit, jamais besoin d'échappement de chaîne.
     */
    fun buildSetModelReadyScript(version: Long): String = "HA.host.setModelReady($version);"

    /** Le script qui force le thème clair ou sombre. */
    fun buildSetThemeScript(dark: Boolean): String {
        val theme = if (dark) "dark" else "light"
        val literal = json.encodeToString(String.serializer(), theme)
        return "HA.host.setTheme($literal);"
    }
}
