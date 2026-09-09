/**
 * Interface de Health Analyzer — Web.
 *
 * Ce fichier ne fait qu'orchestrer le DOM : toute la logique métier vit dans
 * `lib/*.js`, chargés avant ce script et exposés sous le namespace global `HA`.
 * Organisation en sections : État, Réglages, Navigation, Import, Initialisation.
 *
 * L'onglet Rapport est entièrement géré par `app-report-ui.js` (`HA.reportUI`,
 * état affiché, récit LLM, export) et `app-report.js` (`HA.appReport`, lecture
 * IndexedDB et construction du modèle). L'onglet Analyse (conversation) est
 * géré de la même façon par `app-chat.js` (`HA.chatUI`). Ce fichier ne fait
 * qu'appeler `.init()`, `.setHasData()` et `.render()` de chacun aux bons moments.
 */
(() => {
  'use strict';

  // ======================================================================
  // État
  // ======================================================================

  /** Agrégats journaliers actuellement chargés (triés par date croissante). */
  let allDays = [];
  let currentWorker = null;

  const DEFAULT_SETTINGS = {
    provider: 'gemini',
    apiKey: '',
    model: '',
    sleepTargetMinutes: HA.aggregate.DEFAULT_SLEEP_TARGET_MINUTES,
  };
  let settings = loadSettings();

  // ======================================================================
  // Réglages (localStorage)
  // ======================================================================

  function loadSettings() {
    try {
      const raw = localStorage.getItem('ha.settings');
      if (!raw) return { ...DEFAULT_SETTINGS };
      return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
    } catch (error) {
      return { ...DEFAULT_SETTINGS };
    }
  }

  function saveSettings() {
    localStorage.setItem('ha.settings', JSON.stringify(settings));
  }

  function initSettingsView() {
    const providerSelect = document.getElementById('settings-provider');
    const apiKeyInput = document.getElementById('settings-api-key');
    const modelInput = document.getElementById('settings-model');
    const sleepTargetInput = document.getElementById('settings-sleep-target');
    const toggleVisibility = document.getElementById('toggle-key-visibility');
    const clearKeyButton = document.getElementById('clear-key');
    const clearAllButton = document.getElementById('clear-all-data');
    const status = document.getElementById('settings-status');

    providerSelect.value = settings.provider;
    apiKeyInput.value = settings.apiKey;
    modelInput.value = settings.model;
    modelInput.placeholder = HA.llm.DEFAULT_MODELS[settings.provider];
    sleepTargetInput.value = (settings.sleepTargetMinutes / 60).toFixed(2).replace(/\.?0+$/, '');

    const showStatus = (text) => {
      status.textContent = text;
      status.hidden = false;
      setTimeout(() => { status.hidden = true; }, 2500);
    };

    providerSelect.addEventListener('change', () => {
      settings.provider = providerSelect.value;
      modelInput.placeholder = HA.llm.DEFAULT_MODELS[settings.provider];
      saveSettings();
    });

    apiKeyInput.addEventListener('input', () => {
      settings.apiKey = apiKeyInput.value;
      saveSettings();
    });

    modelInput.addEventListener('input', () => {
      settings.model = modelInput.value;
      saveSettings();
    });

    sleepTargetInput.addEventListener('change', () => {
      const hours = Number(sleepTargetInput.value);
      if (Number.isFinite(hours) && hours >= 4 && hours <= 12) {
        settings.sleepTargetMinutes = Math.round(hours * 60);
        saveSettings();
        HA.reportUI.render();
      }
    });

    toggleVisibility.addEventListener('click', () => {
      const showing = apiKeyInput.type === 'text';
      apiKeyInput.type = showing ? 'password' : 'text';
      toggleVisibility.textContent = showing ? '👁' : '🙈';
      toggleVisibility.setAttribute('aria-label', showing ? 'Montrer la clé' : 'Masquer la clé');
    });

    clearKeyButton.addEventListener('click', () => {
      settings.apiKey = '';
      apiKeyInput.value = '';
      saveSettings();
      showStatus('Clé API effacée.');
    });

    clearAllButton.addEventListener('click', async () => {
      const confirmed = confirm(
        'Effacer toutes les données locales ? Cela supprime la clé API, les réglages et les agrégats importés.'
      );
      if (!confirmed) return;
      localStorage.removeItem('ha.settings');
      await HA.db.clearAll();
      await HA.chatUI.clearConversation();
      settings = { ...DEFAULT_SETTINGS };
      allDays = [];
      initSettingsView();
      HA.reportUI.setHasData(false);
      HA.reportUI.render();
      HA.chatUI.setHasData(false);
      HA.chatUI.render();
      document.getElementById('import-summary').hidden = true;
      showStatus('Toutes les données locales ont été effacées.');
    });
  }

  // ======================================================================
  // Navigation (onglets)
  // ======================================================================

  /** Bascule vers un onglet donné. Utilisé par les onglets eux-mêmes et par le
   * lien « Aller à l'import » de l'état vide du rapport. */
  function switchToView(viewName) {
    document.querySelectorAll('.tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.view === viewName));
    document.querySelectorAll('.view').forEach((view) => view.classList.toggle('active', view.id === `view-${viewName}`));
    if (viewName === 'dashboard') HA.reportUI.render();
    if (viewName === 'analysis') HA.chatUI.render();
  }

  function initNavigation() {
    document.querySelectorAll('.tab').forEach((tab) => {
      tab.addEventListener('click', () => switchToView(tab.dataset.view));
    });
  }

  // ======================================================================
  // Import
  // ======================================================================

  function initImportView() {
    document.getElementById('import-directory').addEventListener('change', (event) => {
      const files = Array.from(event.target.files || []);
      if (files.length > 0) startImport('directory', { files });
    });
    document.getElementById('import-zip').addEventListener('change', (event) => {
      const file = event.target.files && event.target.files[0];
      if (file) startImport('zip', { zipFile: file });
    });
  }

  function startImport(mode, payload) {
    if (currentWorker) currentWorker.terminate();
    currentWorker = new Worker('worker.js');

    const progressBox = document.getElementById('import-progress');
    const progressBar = document.getElementById('import-progress-bar');
    const progressText = document.getElementById('import-progress-text');
    const summaryBox = document.getElementById('import-summary');

    summaryBox.hidden = true;
    progressBox.hidden = false;
    progressBar.value = 0;
    progressText.textContent = 'Analyse des fichiers…';

    currentWorker.onmessage = async (event) => {
      const message = event.data;
      if (message.type === 'progress') {
        const { done, total, phase } = message.progress;
        const percent = total > 0 ? Math.round((done / total) * 100) : 0;
        progressBar.value = percent;
        const phaseLabel = phase === 'hrv-json' ? 'fichiers de variabilité cardiaque' : 'fichiers CSV';
        progressText.textContent = `${done} / ${total} ${phaseLabel} traités…`;
      } else if (message.type === 'result') {
        progressBox.hidden = true;
        await HA.db.saveDailySnapshots(message.result.days, message.result);
        allDays = message.result.days;
        renderImportSummary(message.result);
        HA.reportUI.setHasData(allDays.length > 0);
        HA.reportUI.render();
        HA.chatUI.setHasData(allDays.length > 0);
        HA.chatUI.render();
        currentWorker.terminate();
        currentWorker = null;
      } else if (message.type === 'error') {
        progressBox.hidden = true;
        summaryBox.hidden = false;
        summaryBox.innerHTML = `<p class="error">Échec de l'import : ${escapeHtml(message.message)}</p>`;
        currentWorker.terminate();
        currentWorker = null;
      }
    };

    currentWorker.postMessage({
      type: 'import',
      mode,
      files: payload.files,
      zipFile: payload.zipFile,
      sleepTargetMinutes: settings.sleepTargetMinutes,
    });
  }

  /**
   * Remplit l'encadré de résumé d'import, puis seulement alors le révèle.
   *
   * L'ordre compte. La version précédente levait `hidden` en premier : un résumé d'un
   * ancien schéma faisait échouer la suite, et l'encadré restait à l'écran, révélé et
   * vide. Le défaut est resté invisible tant que l'encadré n'avait ni fond ni bordure ;
   * il est apparu dès que la coquille lui en a donné.
   *
   * Les champs manquants sont donc tolérés plutôt que fatals, et l'encadré ne se montre
   * que s'il a quelque chose à montrer.
   */
  function renderImportSummary(result) {
    const summaryBox = document.getElementById('import-summary');
    const countLabels = {
      sleep: 'Nuits de sommeil', heartRate: 'Mesures de fréquence cardiaque',
      stress: 'Mesures de stress', hrv: 'Mesures de variabilité cardiaque',
      spO2: 'Mesures de SpO2', weight: 'Pesées', steps: 'Jours avec des pas',
      activity: "Jours avec de l'activité", energyScore: "Jours avec un score d'énergie",
    };
    const counts = result && result.counts ? Object.entries(result.counts) : [];
    const warnings = result && Array.isArray(result.warnings) ? result.warnings : [];
    const from = result && result.fromKey;
    const to = result && result.toKey;

    // Un résumé sans aucun compte ni avertissement n'a rien à dire, même s'il porte une
    // date : le stockage garde parfois un vestige d'un import interrompu, du genre
    // `{fromKey: "2024-06-01"}`. Annoncer « Import terminé » pour cela serait faux.
    if (counts.length === 0 && warnings.length === 0) {
      summaryBox.textContent = '';
      summaryBox.hidden = true;
      return;
    }

    const countsHtml = counts
      .map(([key, count]) => {
        const label = escapeHtml(countLabels[key] || key);
        const value = typeof count === 'number' ? count.toLocaleString('fr-FR') : escapeHtml(count);
        return `<li>${label} : ${value}</li>`;
      })
      .join('');
    const warningsHtml = warnings.length > 0
      ? `<h4>Avertissements</h4><ul class="warnings">${warnings.map((w) => `<li>${escapeHtml(w)}</li>`).join('')}</ul>`
      : '';
    const periodHtml = from && to
      ? `Import terminé. Période couverte : ${escapeHtml(from)} à ${escapeHtml(to)}.`
      : 'Import terminé.';

    summaryBox.innerHTML = `
      <p class="success">${periodHtml}</p>
      <ul class="counts">${countsHtml}</ul>
      ${warningsHtml}
    `;
    summaryBox.hidden = false;
  }

  // ======================================================================
  // Utilitaires
  // ======================================================================

  function escapeHtml(text) {
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ======================================================================
  // Initialisation
  // ======================================================================

  async function init() {
    initNavigation();
    initImportView();
    HA.reportUI.init();
    HA.chatUI.init();
    initSettingsView();

    allDays = await HA.db.loadAllDailySnapshots();
    const previousSummary = await HA.db.loadImportSummary();
    if (previousSummary) {
      try {
        renderImportSummary(previousSummary);
      } catch (error) {
        // Un résumé d'un ancien schéma (ou corrompu) ne doit pas empêcher le
        // reste de l'initialisation : le rapport doit s'afficher quand même.
        console.error("[import] résumé illisible, ignoré", error);
        const summaryBox = document.getElementById('import-summary');
        summaryBox.textContent = '';
        summaryBox.hidden = true;
      }
    }
    HA.reportUI.setHasData(allDays.length > 0);
    HA.reportUI.render();
    HA.chatUI.setHasData(allDays.length > 0);
    HA.chatUI.render();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
