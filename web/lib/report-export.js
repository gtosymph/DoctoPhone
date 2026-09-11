/**
 * Export du rapport en une page HTML autonome, à partir du gabarit
 * `report/export-template.html` (voir son commentaire d'en-tête pour le contrat
 * des trois marqueurs `{{STYLES}}`, `{{SCRIPTS}}`, `{{MODEL}}`).
 *
 * Le fichier produit ne dépend d'aucun réseau : styles, moteur de dessin et
 * modèle sont inclus tels quels. Il porte les mesures de santé de l'utilisateur
 * en clair — c'est un choix assumé (l'export sert à garder ou partager un
 * bilan), à ne pas confondre avec `lib/prompt.js`, qui lui ne laisse jamais
 * sortir que des agrégats vers un fournisseur de LLM.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const TEMPLATE_URL = 'report/export-template.html';
  const CSS_URL = 'report/report.css';

  // Bornes posées dans `report/report.css` autour de ses quatre règles `@font-face`.
  const FONTS_START = '/* ha-font-faces:start */';
  const FONTS_END = '/* ha-font-faces:end */';

  // Seule fonte embarquée dans l'export : les titres sont en serif, tous en 600.
  const SERIF_URL = 'report/fonts/source-serif-4-600.woff2';

  // La pile de `--sans` moins « Public Sans », qui n'est pas embarquée.
  const SYSTEM_SANS = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';

  // Uniquement ce qu'il faut pour DESSINER un modèle déjà construit : ni
  // `report-builder.js`, ni les constructeurs de section (`lib/report-sections/`),
  // qui ne servent qu'à fabriquer le modèle depuis les mesures brutes.
  const SCRIPT_URLS = [
    'lib/report-model.js',
    'report/report-engine.js',
    'report/report-charts-sleep.js',
    'report/report-charts-heart.js',
    'report/report-charts-activity.js',
    'report/report-charts-vitals.js',
    'report/report-sections.js',
    'report/report-render.js',
  ];


  /**
   * Encode un binaire en base64 sans dérouler tout le tableau d'un coup.
   *
   * `String.fromCharCode(...octets)` sur une fonte de 24 ko passe 24 000 arguments à un
   * appel de fonction : selon le moteur, cela lève un `RangeError` au lieu d'encoder.
   * Le découpage par tranches évite complètement cette limite.
   *
   * @param {ArrayBuffer} buffer
   * @returns {string}
   */
  function toBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    const CHUNK = 0x8000;
    let binary = '';
    for (let i = 0; i < bytes.length; i += CHUNK) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
    }
    return btoa(binary);
  }

  /**
   * Prépare la feuille de style pour un export autonome : les polices y voyagent en
   * `data:`, plus par chemin relatif.
   *
   * `report.css` déclare ses fontes en `url("fonts/…woff2")`, ce qui est juste pour la
   * version web et pour la WebView Android : toutes deux servent un dossier. L'export,
   * lui, est un fichier unique qu'on recopie, qu'on envoie, qu'on ouvre depuis un
   * téléchargement. Le chemin relatif y échoue **sans message**, et le rapport retombe
   * sur la police du système — un défaut invisible jusqu'à l'impression.
   *
   * Jumeau exact de `ReportExportTemplate.inlineExportFonts` côté Kotlin : les deux
   * versions doivent produire le même document. Toute modification ici se reporte là-bas.
   *
   * @param {string} css le contenu de `report/report.css`
   * @param {string} serifSemiBoldBase64 la fonte serif 600, encodée
   * @returns {string}
   */
  function inlineExportFonts(css, serifSemiBoldBase64) {
    if (!serifSemiBoldBase64) {
      throw new Error(`La fonte serif de l'export est vide : ${SERIF_URL} est introuvable.`);
    }
    const start = css.indexOf(FONTS_START);
    const end = css.indexOf(FONTS_END);
    if (start === -1 || end <= start) {
      throw new Error(
        `Les bornes ${FONTS_START} / ${FONTS_END} sont absentes ou inversées dans report.css. `
        + 'Elles délimitent les règles @font-face que l\'export doit remplacer ; sans elles, '
        + 'le fichier produit chercherait ses polices dans un dossier voisin qui n\'existe '
        + 'pas, et perdrait sa typographie en silence.'
      );
    }

    const embedded =
      '/* Polices de l\'export : embarquées, car un fichier autonome n\'a pas de dossier voisin. */\n'
      + '@font-face {\n'
      + '  font-family: "Source Serif 4";\n'
      + `  src: url(data:font/woff2;base64,${serifSemiBoldBase64}) format("woff2");\n`
      + '  font-weight: 600;\n'
      + '  font-style: normal;\n'
      + '}\n';

    // L'override de `--sans` se pose APRÈS toute la feuille, jamais à la place des
    // `@font-face` : `:root` est déclaré plus bas dans `report.css` et reprendrait la
    // main sur une définition placée plus haut.
    return css.slice(0, start) + embedded + css.slice(end + FONTS_END.length)
      + '\n/* La sans n\'est pas embarquée : le texte courant de l\'export suit le système. */\n'
      + `:root, :root[data-theme="dark"] { --sans: ${SYSTEM_SANS}; }\n`;
  }

  /** Coquille de l'export (gabarit + styles + scripts) : ne dépend jamais du modèle, chargée une fois. */
  let cachedShell = null;

  async function fetchText(url) {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Impossible de charger ${url} pour l'export (HTTP ${response.status}).`);
    }
    return response.text();
  }

  async function fetchBinary(url) {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Impossible de charger ${url} pour l'export (HTTP ${response.status}).`);
    }
    return response.arrayBuffer();
  }

  async function loadShell() {
    if (cachedShell) return cachedShell;
    const [template, css, serif, ...scripts] = await Promise.all([
      fetchText(TEMPLATE_URL),
      fetchText(CSS_URL),
      fetchBinary(SERIF_URL),
      ...SCRIPT_URLS.map(fetchText),
    ]);
    cachedShell = {
      template,
      css: inlineExportFonts(css, toBase64(serif)),
      scripts: scripts.join('\n'),
    };
    return cachedShell;
  }

  /**
   * Échappe un JSON sérialisé pour qu'il tienne sans risque dans le
   * `<script type="application/json">` du gabarit : chaque `<` devient
   * `<`. Sans cela, une chaîne du modèle contenant `</script>` (dans un
   * récit écrit par un LLM, par exemple) couperait le document en deux.
   *
   * @param {string} json un JSON déjà sérialisé (`JSON.stringify`)
   * @returns {string}
   */
  function escapeJsonForScript(json) {
    return json.replace(/</g, '\\u003c');
  }

  /**
   * Remplace la DERNIÈRE occurrence d'un marqueur, jamais la première.
   *
   * Le commentaire d'en-tête du gabarit cite les trois marqueurs à titre
   * documentaire, avant leur véritable emplacement plus bas dans le fichier :
   * un `String.replace` simple toucherait cette mention plutôt que le vrai
   * placeholder. Une découpe directe évite aussi l'interprétation de `$1`,
   * `$&`, etc. que `String.replace` ferait sur un remplacement texte — le
   * bundle de scripts regorge de `${...}` (gabarits littéraux JS).
   */
  function replaceLastOccurrence(text, marker, value) {
    const index = text.lastIndexOf(marker);
    if (index === -1) return text;
    return text.slice(0, index) + value + text.slice(index + marker.length);
  }

  /**
   * Construit le document HTML complet et autonome de l'export.
   *
   * @param {object} model un `ReportModel` conforme à `report-model.js`
   * @returns {Promise<string>}
   */
  async function buildExportHtml(model) {
    const shell = await loadShell();
    const modelJson = escapeJsonForScript(JSON.stringify(model));
    let html = shell.template;
    html = replaceLastOccurrence(html, '{{STYLES}}', shell.css);
    html = replaceLastOccurrence(html, '{{SCRIPTS}}', shell.scripts);
    html = replaceLastOccurrence(html, '{{MODEL}}', modelJson);
    return html;
  }

  /** Nom de fichier proposé au téléchargement, basé sur la date de génération du rapport. */
  function exportFileName(model) {
    const generatedAt = model && model.meta && model.meta.generatedAt;
    const date = (typeof generatedAt === 'string' && generatedAt.length >= 10)
      ? generatedAt.slice(0, 10)
      : new Date().toISOString().slice(0, 10);
    return `bilan-sante-${date}.html`;
  }

  /**
   * Construit l'export et déclenche son téléchargement dans le navigateur.
   *
   * @param {object} model un `ReportModel` conforme à `report-model.js`
   */
  async function downloadExportHtml(model) {
    const html = await buildExportHtml(model);
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = exportFileName(model);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  root.HA.reportExport = {
    buildExportHtml, downloadExportHtml, escapeJsonForScript, exportFileName, inlineExportFonts,
  };
})();
