/**
 * Onglet Analyse — orchestration DOM de la conversation.
 *
 * Séparé de `app.js` pour la même raison que `app-report-ui.js` : rester sous
 * la limite de taille de fichier, et lire ses réglages (clé API, fournisseur)
 * directement dans `localStorage` plutôt que de partager l'état en mémoire de
 * `app.js` — une clé API modifiée dans Réglages doit être vue au prochain
 * envoi, sans recharger la page.
 *
 * Expose `HA.chatUI = { init, setHasData, render }` : `app.js` appelle `init()`
 * une fois, `setHasData()` chaque fois que le nombre de jours importés change,
 * et `render()` à chaque changement d'onglet.
 *
 * Persistance : la conversation vit dans sa propre base IndexedDB
 * (`health-analyzer-chat`), séparée de celle du reste de l'app (`lib/db.js`,
 * en cours de modification par un autre agent pendant l'écriture de ce
 * fichier) pour ne pas y toucher.
 *
 * Confidentialité : seuls des agrégats vont dans le prompt système (voir `lib/chat.js`,
 * `HA.chatPrompt.runConversationTurn`). Le modèle choisit lui-même la période qu'il
 * examine, par un bloc `healthrange` — voir `report/chat-prompt.txt`. Tant qu'il n'en a
 * choisi aucune, le prompt ne porte qu'un aperçu mensuel de tout l'historique
 * (`activeModel` reste `null`) ; une fois choisie, elle reste active pour les questions
 * suivantes (`activeModel`, gardé en mémoire, jamais persisté avec la conversation — seuls
 * le texte des messages et leur horodatage sont écrits dans IndexedDB).
 */
(() => {
  'use strict';

  // ======================================================================
  // Base IndexedDB de la conversation
  // ======================================================================

  const CHAT_DB_NAME = 'health-analyzer-chat';
  const CHAT_DB_VERSION = 1;
  const CHAT_STORE = 'chatMessages';
  const CHAT_TIME_INDEX = 'byTime';

  function openChatDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(CHAT_DB_NAME, CHAT_DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(CHAT_STORE)) {
          const store = db.createObjectStore(CHAT_STORE, { keyPath: 'id' });
          store.createIndex(CHAT_TIME_INDEX, 'at');
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  function txDone(transaction) {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  }

  async function loadStoredConversation() {
    const db = await openChatDb();
    const transaction = db.transaction([CHAT_STORE], 'readonly');
    const rows = await new Promise((resolve, reject) => {
      const request = transaction.objectStore(CHAT_STORE).index(CHAT_TIME_INDEX).getAll();
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    db.close();
    return rows;
  }

  async function persistMessage(message) {
    const db = await openChatDb();
    const transaction = db.transaction([CHAT_STORE], 'readwrite');
    transaction.objectStore(CHAT_STORE).put(message);
    await txDone(transaction);
    db.close();
  }

  async function clearStoredConversation() {
    const db = await openChatDb();
    const transaction = db.transaction([CHAT_STORE], 'readwrite');
    transaction.objectStore(CHAT_STORE).clear();
    await txDone(transaction);
    db.close();
  }

  function makeMessageId() {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  // ======================================================================
  // État
  // ======================================================================

  let hasImportedData = false;
  let conversation = [];
  let sending = false;

  // Modèle de rapport sur tout l'historique et gabarit du prompt système,
  // construits une seule fois par jeu de données importé — voir `setHasData`,
  // qui invalide ce cache. La conversation les réutilise à chaque tour.
  let contextPromise = null;

  // La fenêtre que le modèle a choisi d'examiner avec `healthrange`, ou `null` tant
  // qu'il n'en a demandé aucune — voir `HA.chatPrompt.runConversationTurn`. Gardée d'un
  // tour à l'autre pour qu'une question de suivi n'ait pas besoin de la redemander ;
  // remise à zéro par `setHasData` en même temps que le contexte, un nouvel import
  // pouvant changer ce que cette fenêtre contenait.
  let activeModel = null;

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
    };
  }

  // ======================================================================
  // Contexte : modèle de rapport sur tout l'historique + gabarit du prompt
  // ======================================================================

  const MILLIS_PER_DAY = 86400000;

  /** Construit le `ReportModel` sur tout l'historique importé, comme l'onglet Rapport en période « Tout l'historique ». */
  async function buildFullHistoryModel() {
    const range = HA.appReport.periodToRange('all', new Date());
    return buildModelForSourceRange(range);
  }

  /** La plage `{from, to, fromMillis, toMillis}` d'une fenêtre `healthrange` acceptée (bornes ISO incluses). */
  function rangeForDates(fromIso, toIso) {
    return {
      from: fromIso,
      to: toIso,
      fromMillis: new Date(`${fromIso}T00:00:00`).getTime(),
      toMillis: new Date(`${toIso}T00:00:00`).getTime() + MILLIS_PER_DAY - 1,
    };
  }

  /** Construit le `ReportModel` de la fenêtre `[fromIso, toIso]` choisie par le modèle — voir `runConversationTurn`. */
  async function buildModelForRange(fromIso, toIso) {
    return buildModelForSourceRange(rangeForDates(fromIso, toIso));
  }

  async function buildModelForSourceRange(range) {
    const sources = await HA.appReport.readSources(range);
    const settings = JSON.parse(localStorage.getItem('ha.settings') || '{}');
    return HA.reportBuilder.buildReport(sources, {
      ...range,
      zone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      sleepTargetHours: (settings.sleepTargetMinutes || HA.aggregate.DEFAULT_SLEEP_TARGET_MINUTES) / 60,
    });
  }

  /** Construit (une fois, en cache) le modèle sur tout l'historique et le gabarit du prompt système. */
  function ensureContext() {
    if (!contextPromise) {
      contextPromise = Promise.all([buildFullHistoryModel(), HA.chatPrompt.loadTemplate()])
        .then(([historyModel, template]) => ({ historyModel, template }));
      // Un échec ne doit pas rester en cache : le prochain appel doit pouvoir réessayer.
      contextPromise.catch(() => { contextPromise = null; });
    }
    return contextPromise;
  }

  // ======================================================================
  // États visibles
  // ======================================================================

  function showInlineStatus(kind, text) {
    const el = document.getElementById('chat-status');
    el.textContent = text;
    el.className = `report-inline-status ${kind}`;
    el.hidden = false;
  }

  function hideInlineStatus() {
    document.getElementById('chat-status').hidden = true;
  }

  function updateComposerState() {
    const hasKey = readSettings().apiKey.length > 0;
    document.getElementById('chat-input').disabled = sending;
    document.getElementById('chat-send').disabled = sending || !hasKey;
  }

  // ======================================================================
  // Rendu
  // ======================================================================

  /**
   * Remplit le compositeur avec une suggestion cliquée dans l'amorce de premier
   * usage (voir `HA.chat.render`, option `onSuggestion`). Ne l'envoie pas : la
   * personne garde la main sur sa question avant de l'envoyer elle-même.
   */
  function onSuggestion(question) {
    const input = document.getElementById('chat-input');
    input.value = question;
    input.focus();
  }

  async function render() {
    const empty = document.getElementById('chat-empty');
    const container = document.getElementById('chat-container');

    if (!hasImportedData) {
      empty.hidden = false;
      container.hidden = true;
      return;
    }
    empty.hidden = true;
    container.hidden = false;
    updateComposerState();

    const messagesHost = document.getElementById('chat-messages');
    // Le modèle qui résout les graphiques de la conversation : toujours tout l'historique.
    // Un graphique se résout avec la fenêtre de son propre message (`message.rangeFrom`/
    // `rangeTo`), jamais avec `activeModel` — sinon un ancien graphique changerait de
    // contenu dès que le modèle examine une autre période, voir `chat-view.js`.
    let model = null;
    try {
      model = (await ensureContext()).historyModel;
    } catch (error) {
      console.error('[chat] échec de préparation du contexte', error);
    }

    // Conversation vide : `HA.chat.render` dessine elle-même l'amorce de premier
    // usage (même vue partagée que la coquille Android) plutôt que de la
    // dupliquer ici — voir web/chat/chat-view.js.
    HA.chat.render(messagesHost, conversation, model || HA.reportModel.emptyReportModel(), { onSuggestion });
    messagesHost.scrollTop = messagesHost.scrollHeight;
  }

  // ======================================================================
  // Envoi
  // ======================================================================

  async function sendCurrentInput() {
    if (sending || !hasImportedData) return;
    const input = document.getElementById('chat-input');
    const text = input.value.trim();
    if (!text) return;

    const settings = readSettings();
    if (!settings.apiKey) {
      showInlineStatus('error', 'Renseignez une clé API dans les réglages avant d\'envoyer un message.');
      return;
    }

    let context;
    try {
      context = await ensureContext();
    } catch (error) {
      console.error('[chat] échec de préparation du contexte', error);
      showInlineStatus('error', "Le contexte de la conversation n'a pas pu être préparé. Réessayez.");
      return;
    }

    const userMessage = { id: makeMessageId(), role: 'user', text, at: Date.now() };
    conversation.push(userMessage);
    input.value = '';
    sending = true;
    showInlineStatus('loading', 'Réponse en cours…');
    await render();

    try {
      await persistMessage(userMessage);
      const result = await HA.chatPrompt.runConversationTurn({
        template: context.template,
        historyModel: context.historyModel,
        activeModel,
        messages: HA.chatPrompt.toLlmMessages(conversation),
        complete: (systemPrompt, messages) => HA.llm.analyze(settings.provider, {
          apiKey: settings.apiKey,
          model: settings.model,
          systemPrompt,
          messages,
        }),
        buildReportForRange: buildModelForRange,
      });
      activeModel = result.activeModel;
      // La fenêtre active au moment de CETTE réponse, gravée sur le message : voir
      // `chat-view.js`, `messageRange`. `null` tant qu'aucune `healthrange` n'a encore
      // été choisie — un graphique de ce message se résout alors sur tout l'historique.
      const activeMeta = activeModel && activeModel.meta;
      const assistantMessage = {
        id: makeMessageId(),
        role: 'assistant',
        text: result.reply,
        at: Date.now(),
        rangeFrom: activeMeta ? activeMeta.from : null,
        rangeTo: activeMeta ? activeMeta.to : null,
      };
      conversation.push(assistantMessage);
      await persistMessage(assistantMessage);
      if (result.statusNote) {
        showInlineStatus('info', result.statusNote);
      } else {
        hideInlineStatus();
      }
    } catch (error) {
      showInlineStatus('error', error.message);
    } finally {
      sending = false;
      await render();
    }
  }

  async function onClear() {
    if (conversation.length === 0) return;
    const confirmed = confirm('Effacer toute la conversation ? Cette action ne peut pas être annulée.');
    if (!confirmed) return;
    await clearConversation();
  }

  /**
   * Efface la conversation, sans confirmation : à l'appelant de la demander si
   * besoin. Utilisé par le bouton dédié (avec confirmation, voir `onClear`) et
   * par « Effacer toutes les données locales » des Réglages, qui a déjà la sienne.
   */
  async function clearConversation() {
    conversation = [];
    activeModel = null;
    hideInlineStatus();
    try {
      await clearStoredConversation();
    } catch (error) {
      console.error('[chat] échec de l\'effacement de la conversation', error);
    }
    await render();
  }

  // ======================================================================
  // Initialisation
  // ======================================================================

  function init() {
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-input');

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      sendCurrentInput();
    });

    // Entrée envoie, Maj+Entrée revient à la ligne — convention habituelle d'un
    // client de messagerie, plus rapide qu'aller chercher le bouton au clavier.
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendCurrentInput();
      }
    });

    document.getElementById('chat-clear').addEventListener('click', onClear);

    // Même geste que l'état vide du rapport (`dashboard-go-import`) : basculer
    // l'onglet en cliquant l'onglet lui-même plutôt que dupliquer la logique
    // de navigation de `app.js`.
    document.getElementById('chat-go-import').addEventListener('click', () => {
      const importTab = document.querySelector('.tab[data-view="import"]');
      if (importTab) importTab.click();
    });

    loadStoredConversation()
      .then((rows) => {
        conversation = rows;
        return render();
      })
      .catch((error) => {
        console.error('[chat] échec du chargement de la conversation', error);
      });
  }

  /** Invalide le contexte mis en cache : de nouvelles données changent les agrégats. */
  function setHasData(value) {
    hasImportedData = value;
    contextPromise = null;
    activeModel = null;
  }

  self.HA = self.HA || {};
  self.HA.chatUI = { init, setHasData, render, clearConversation };
})();
