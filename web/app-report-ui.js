/**
 * Orchestration DOM de l'onglet Rapport : état affiché (vide / attente / contenu),
 * bouton « Rédiger le bilan » (récit LLM) et bouton « Exporter le rapport ».
 *
 * Séparé de `app.js` pour deux raisons : garder ce dernier sous la limite de
 * taille de fichier, et parce que ce module lit ses réglages (clé API,
 * fournisseur, objectif de sommeil) directement dans `localStorage` plutôt que
 * de partager l'état en mémoire de `app.js` — une clé API modifiée dans
 * Réglages doit être vue au clic suivant, sans recharger la page.
 *
 * Expose `HA.reportUI = { init, setHasData, render }` : `app.js` appelle `init()`
 * une fois, `setHasData()` chaque fois que le nombre de jours importés change, et
 * `render()` à chaque changement de période, d'onglet ou d'objectif de sommeil.
 */
(() => {
  'use strict';

  const REPORT_TIME_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone;

  // ======================================================================
  // État
  // ======================================================================

  let hasImportedData = false;
  let currentReportModel = null;
  let currentPeriodValue = null;

  // Jeton de la dernière demande de rapport : la construction du modèle est
  // asynchrone (lecture IndexedDB par plage, calcul des sections). Sans ce
  // garde, changer deux fois de période rapidement pourrait laisser le calcul
  // le plus lent écraser à la fin le résultat du calcul le plus récent.
  let reportRenderToken = 0;

  // Le récit survit à un changement de période (voir `render()`), mais se
  // signale comme rédigé sur une autre période tant qu'il n'a pas été
  // régénéré pour la période actuellement affichée.
  let savedNarrative = null;
  let savedNarrativePeriodValue = null;

  // ======================================================================
  // Réglages (lecture directe de localStorage, jamais mise en cache)
  // ======================================================================

  function readSettings() {
    let stored = {};
    try {
      stored = JSON.parse(localStorage.getItem('ha.settings') || '{}');
    } catch (error) {
      stored = {};
    }
    const provider = stored.provider || 'gemini';
    return {
      provider,
      apiKey: stored.apiKey || '',
      model: (stored.model && stored.model.trim()) || HA.llm.DEFAULT_MODELS[provider],
      sleepTargetMinutes: stored.sleepTargetMinutes || HA.aggregate.DEFAULT_SLEEP_TARGET_MINUTES,
    };
  }

  // ======================================================================
  // États visibles
  // ======================================================================

  function showReportState(state) {
    document.getElementById('dashboard-empty').hidden = state !== 'empty';
    document.getElementById('dashboard-loading').hidden = state !== 'loading';
    document.getElementById('dashboard-content').hidden = state !== 'content';
    document.getElementById('report-actions').hidden = state !== 'content';
  }

  function setReportEmptyMessage(text) {
    document.getElementById('dashboard-empty-text').textContent = text;
  }

  function showInlineStatus(elementId, kind, text) {
    const el = document.getElementById(elementId);
    el.textContent = text;
    el.className = `report-inline-status ${kind}`;
    el.hidden = false;
  }

  function hideInlineStatus(elementId) {
    document.getElementById(elementId).hidden = true;
  }

  /** Rappelle que le récit affiché date d'une autre période, sans écraser une erreur déjà affichée. */
  function updateNarrativeStaleNotice() {
    const statusEl = document.getElementById('report-narrative-status');
    if (!statusEl.hidden && statusEl.classList.contains('error')) return;
    if (savedNarrative && savedNarrativePeriodValue !== currentPeriodValue) {
      showInlineStatus('report-narrative-status', 'info', 'Rédigé sur une autre période — régénérez pour actualiser.');
    } else {
      hideInlineStatus('report-narrative-status');
    }
  }

  // ======================================================================
  // Rendu du rapport
  // ======================================================================

  /**
   * Construit puis dessine le rapport de l'onglet Rapport pour la période choisie.
   *
   * L'absence totale de données ne passe pas par `validateReportModel` : un
   * historique vide reste un modèle valide (voir `HA.reportModel.emptyReportModel`).
   * `hasImportedData` (mis à jour par `app.js` via `setHasData`) reste donc la
   * source de vérité pour distinguer « rien d'importé » d'un rapport réellement
   * vide sur la période choisie.
   */
  async function render() {
    const token = ++reportRenderToken;
    if (!hasImportedData) {
      currentReportModel = null;
      setReportEmptyMessage('Aucune donnée importée.');
      showReportState('empty');
      return;
    }

    showReportState('loading');
    const periodValue = document.getElementById('dashboard-period').value;
    const range = HA.appReport.periodToRange(periodValue, new Date());
    const settings = readSettings();
    const options = {
      ...range,
      zone: REPORT_TIME_ZONE,
      sleepTargetHours: settings.sleepTargetMinutes / 60,
    };
    const host = document.getElementById('dashboard-content');

    let incomplete = false;
    let model = null;
    try {
      model = await HA.appReport.renderReportInto(host, options, (problems) => {
        incomplete = true;
        console.error('[rapport] modèle incomplet', problems);
      });
    } catch (error) {
      console.error('[rapport] échec du calcul du rapport', error);
      incomplete = true;
    }

    if (token !== reportRenderToken) return; // une demande plus récente a déjà pris le relais

    if (incomplete || !model) {
      currentReportModel = null;
      setReportEmptyMessage("Le rapport n'a pas pu être calculé. Essayez de réimporter vos données.");
      showReportState('empty');
      return;
    }

    currentReportModel = model;
    currentPeriodValue = periodValue;
    if (savedNarrative) {
      currentReportModel.narrative = savedNarrative;
      HA.report.redraw();
    }
    showReportState('content');
    updateNarrativeStaleNotice();
  }

  // ======================================================================
  // Récit écrit par le LLM
  // ======================================================================

  async function onNarrativeClick() {
    if (!currentReportModel) return;
    const settings = readSettings();
    if (!settings.apiKey) {
      showInlineStatus('report-narrative-status', 'error', 'Renseignez une clé API dans les réglages avant de rédiger le bilan.');
      return;
    }

    const button = document.getElementById('report-narrative-btn');
    const periodAtClick = currentPeriodValue;
    button.disabled = true;
    showInlineStatus('report-narrative-status', 'loading', 'Rédaction du bilan en cours…');

    try {
      const systemPrompt = await HA.narrative.loadSystemPrompt();
      const userPrompt = HA.prompt.narrativeUserPrompt(currentReportModel);
      const { text } = await HA.llm.analyze(settings.provider, {
        apiKey: settings.apiKey,
        model: settings.model,
        systemPrompt,
        userPrompt,
      });
      const { narrative, error } = HA.narrative.parseNarrativeResponse(text);
      if (!narrative) {
        showInlineStatus('report-narrative-status', 'error', error || "Le bilan n'a pas pu être lu.");
        return;
      }
      savedNarrative = narrative;
      savedNarrativePeriodValue = periodAtClick;
      if (currentReportModel) {
        currentReportModel.narrative = narrative;
        HA.report.redraw();
      }
      updateNarrativeStaleNotice();
    } catch (error) {
      showInlineStatus('report-narrative-status', 'error', error.message);
    } finally {
      button.disabled = false;
    }
  }

  // ======================================================================
  // Export
  // ======================================================================

  async function onExportClick() {
    if (!currentReportModel) return;
    const button = document.getElementById('report-export-btn');
    button.disabled = true;
    hideInlineStatus('report-export-status');
    try {
      await HA.reportExport.downloadExportHtml(currentReportModel);
    } catch (error) {
      console.error('[export] échec', error);
      showInlineStatus('report-export-status', 'error', `L'export a échoué : ${error.message}`);
    } finally {
      button.disabled = false;
    }
  }

  // ======================================================================
  // Initialisation
  // ======================================================================

  function init() {
    document.getElementById('dashboard-period').addEventListener('change', render);
    document.getElementById('dashboard-go-import').addEventListener('click', () => {
      const importTab = document.querySelector('.tab[data-view="import"]');
      if (importTab) importTab.click();
    });
    document.getElementById('report-narrative-btn').addEventListener('click', onNarrativeClick);
    document.getElementById('report-export-btn').addEventListener('click', onExportClick);
  }

  function setHasData(value) {
    hasImportedData = value;
  }

  self.HA = self.HA || {};
  self.HA.reportUI = { init, setHasData, render };
})();
