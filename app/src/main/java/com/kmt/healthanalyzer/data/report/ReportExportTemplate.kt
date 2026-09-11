package com.kmt.healthanalyzer.data.report

/**
 * Remplit le gabarit d'export (`assets/report/export-template.html`) sans dépendre
 * d'Android : ces fonctions ne lisent ni fichier ni asset, elles ne font que du texte.
 *
 * Voir le commentaire d'en-tête d'`export-template.html` pour le contrat des trois
 * marqueurs `{{STYLES}}`, `{{SCRIPTS}}` et `{{MODEL}}`.
 */
internal object ReportExportTemplate {

    private const val STYLES_MARKER = "{{STYLES}}"
    private const val SCRIPTS_MARKER = "{{SCRIPTS}}"
    private const val MODEL_MARKER = "{{MODEL}}"

    /**
     * Remplace les trois marqueurs du gabarit.
     *
     * L'ordre compte : [MODEL_MARKER] est remplacé en dernier, pour qu'un JSON dont le
     * contenu contiendrait par hasard la chaîne `{{STYLES}}` ou `{{SCRIPTS}}` ne soit
     * jamais lui-même retouché par un remplacement suivant.
     */
    fun fill(template: String, styles: String, scripts: String, reportJson: String): String =
        template
            .replace(STYLES_MARKER, styles)
            .replace(SCRIPTS_MARKER, scripts)
            .replace(MODEL_MARKER, escapeForInlineScript(reportJson))

    /**
     * Échappe un JSON pour un `<script type="application/json">` inline.
     *
     * Chaque `<` devient la séquence `\u003c`. En JSON valide, `<` n'apparaît jamais en
     * dehors d'une chaîne : le remplacer partout est donc sûr, et `\u003c` s'y lit comme
     * un échappement Unicode ordinaire, restitué par `JSON.parse`. Sans cet échappement,
     * une mesure ou un libellé contenant `</script>` couperait le document en deux : le
     * navigateur arrêterait l'élément `<script>` avant la fin du JSON.
     */
    fun escapeForInlineScript(json: String): String = json.replace("<", "\\u003c")

    /** Bornes posées dans `web/report/report.css` autour de ses quatre règles `@font-face`. */
    private const val FONTS_START = "/* ha-font-faces:start */"
    private const val FONTS_END = "/* ha-font-faces:end */"

    /**
     * Pile de repli pour le texte courant de l'export.
     *
     * Identique à celle de `--sans` dans `report.css`, moins « Public Sans » : la sans
     * n'est pas embarquée. La laisser en tête ferait chercher au navigateur une famille
     * absente avant de se rabattre, sans autre effet que du bruit.
     */
    private const val SYSTEM_SANS =
        "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif"

    /**
     * Prépare la feuille de style pour un export autonome : les polices y voyagent en
     * `data:`, plus par chemin relatif.
     *
     * `report.css` déclare ses fontes en `url("fonts/…woff2")`. C'est juste pour la version
     * web et pour la WebView, qui servent toutes deux un dossier. L'export, lui, est un
     * fichier unique qu'on recopie, qu'on envoie par messagerie, qu'on ouvre depuis un
     * téléchargement : il n'a aucun dossier voisin. Le chemin relatif y échoue **sans
     * message**, et le rapport retombe sur la police du système — un défaut invisible
     * jusqu'à l'impression.
     *
     * Une seule fonte est embarquée, la serif en 600 : seuls les titres sont en serif, et
     * ils sont tous en 600. Elle pèse environ 32 Ko une fois en base64. Embarquer les
     * quatre ajouterait 100 Ko à chaque export pour un gain que le papier ne montre pas.
     *
     * @param styles le contenu de `report.css`, tel que lu dans les assets
     * @param serifSemiBoldBase64 `fonts/source-serif-4-600.woff2`, encodé en base64
     * @throws IllegalStateException si une borne manque ou si elles sont inversées — mieux
     *   vaut interrompre l'export que publier un document muet sur ses polices
     * @throws IllegalArgumentException si l'encodage est vide
     */
    fun inlineExportFonts(styles: String, serifSemiBoldBase64: String): String {
        require(serifSemiBoldBase64.isNotBlank()) {
            "La fonte serif de l'export est vide : l'asset $SERIF_ASSET_NAME est introuvable " +
                "ou n'a pas été lu."
        }

        val start = styles.indexOf(FONTS_START)
        val end = styles.indexOf(FONTS_END)
        check(start >= 0 && end > start) {
            "Les bornes $FONTS_START / $FONTS_END sont absentes ou inversées dans report.css. " +
                "Elles délimitent les règles @font-face que l'export doit remplacer ; sans " +
                "elles, le fichier produit chercherait ses polices dans un dossier voisin " +
                "qui n'existe pas, et perdrait sa typographie en silence."
        }

        val embedded = buildString {
            append("/* Polices de l'export : embarquées, car un fichier autonome n'a pas de " +
                "dossier voisin. */\n")
            append("@font-face {\n")
            append("  font-family: \"Source Serif 4\";\n")
            append("  src: url(data:font/woff2;base64,$serifSemiBoldBase64) format(\"woff2\");\n")
            append("  font-weight: 600;\n")
            append("  font-style: normal;\n")
            append("}\n")
        }

        val head = styles.substring(0, start)
        val tail = styles.substring(end + FONTS_END.length)

        // L'override de `--sans` se pose APRÈS toute la feuille, jamais à la place des
        // `@font-face` : `:root` est déclaré plus bas dans `report.css` et reprendrait
        // la main sur une définition placée plus haut.
        return head + embedded + tail +
            "\n/* La sans n'est pas embarquée : le texte courant de l'export suit le système. */\n" +
            ":root, :root[data-theme=\"dark\"] { --sans: $SYSTEM_SANS; }\n"
    }

    /** Chemin de la fonte à embarquer, sous `assets/`. Cité dans le message d'erreur ci-dessus. */
    const val SERIF_ASSET_NAME = "report/fonts/source-serif-4-600.woff2"
}
