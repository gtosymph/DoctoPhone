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

  /** Coquille de l'export (gabarit + styles + scripts) : ne dépend jamais du modèle, chargée une fois. */
  let cachedShell = null;

  async function fetchText(url) {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Impossible de charger ${url} pour l'export (HTTP ${response.status}).`);
    }
    return response.text();
  }

  async function loadShell() {
    if (cachedShell) return cachedShell;
    const [template, css, ...scripts] = await Promise.all([
      fetchText(TEMPLATE_URL),
      fetchText(CSS_URL),
      ...SCRIPT_URLS.map(fetchText),
    ]);
    cachedShell = { template, css, scripts: scripts.join('\n') };
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

  root.HA.reportExport = { buildExportHtml, downloadExportHtml, escapeJsonForScript, exportFileName };
})();
