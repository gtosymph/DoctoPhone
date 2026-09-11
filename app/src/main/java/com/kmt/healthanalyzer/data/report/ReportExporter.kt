package com.kmt.healthanalyzer.data.report

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produit un fichier HTML autonome du rapport (`assets/report/export-template.html`),
 * pour le partager hors de l'app, et l'expose par un [FileProvider].
 *
 * Le gabarit embarque déjà le moteur de dessin ; cette classe se contente de lire les
 * assets et d'assembler feuille de style, scripts et modèle avec [ReportExportTemplate].
 * Tout se fait sur [Dispatchers.IO] : lecture d'assets et écriture disque n'ont rien à
 * faire sur le thread principal.
 */
@Singleton
class ReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Écrit l'export dans le cache et renvoie le fichier produit.
     *
     * Enlève d'abord tout export précédent : `EXPORT_DIR_NAME` n'a pas d'autre usage (voir
     * `res/xml/file_paths.xml`), et sans ce nettoyage, un ancien rapport oublié dans le
     * cache resterait partageable indéfiniment.
     *
     * @param reportJson le modèle du rapport déjà sérialisé, tel quel — pas de nouvelle
     *   sérialisation ici, pour rester la copie exacte de ce que la WebView montre à l'écran.
     */
    suspend fun export(reportJson: String, day: LocalDate = LocalDate.now()): File =
        withContext(Dispatchers.IO) {
            val html = buildHtml(reportJson)
            val dir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "bilan-sante-$day.html")
            file.writeText(html, Charsets.UTF_8)
            file
        }

    /**
     * L'intention de partage du fichier déjà exporté.
     *
     * L'autorité `packageName.fileprovider` doit correspondre à celle déclarée dans
     * `AndroidManifest.xml` : les deux dérivent du même `applicationId`.
     */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildHtml(reportJson: String): String {
        val template = readAsset(TEMPLATE_PATH)
        val styles = ReportExportTemplate.inlineExportFonts(
            styles = readAsset(STYLES_PATH),
            serifSemiBoldBase64 = readAssetAsBase64(ReportExportTemplate.SERIF_ASSET_NAME),
        )
        val scripts = SCRIPT_PATHS.joinToString("\n") { readAsset(it) }
        return ReportExportTemplate.fill(template, styles, scripts, reportJson)
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /**
     * Lit un asset binaire et le rend en base64, sans retour à la ligne.
     *
     * [Base64.NO_WRAP] n'est pas un détail de forme : le résultat entre dans une `url()`
     * CSS, et un retour à la ligne y couperait la valeur.
     */
    private fun readAssetAsBase64(path: String): String =
        context.assets.open(path).use { stream ->
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        }

    companion object {
        /** Sous-dossier du cache exposé par `res/xml/file_paths.xml`, rien d'autre ne l'est. */
        const val EXPORT_DIR_NAME = "rapports"

        private const val TEMPLATE_PATH = "report/export-template.html"
        private const val STYLES_PATH = "report/report.css"

        /** Concaténés dans cet ordre : le contrat de données, le moteur, puis les graphiques. */
        private val SCRIPT_PATHS = listOf(
            "lib/report-model.js",
            "report/report-engine.js",
            "report/report-charts-sleep.js",
            "report/report-charts-heart.js",
            "report/report-charts-activity.js",
            "report/report-charts-vitals.js",
            "report/report-sections.js",
            "report/report-render.js",
        )
    }
}
