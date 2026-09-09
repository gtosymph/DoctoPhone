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
}
