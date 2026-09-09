/**
 * Assemble le prompt système de la conversation d'analyse, et met en forme
 * l'historique envoyé au LLM à chaque tour.
 *
 * Deux fonctions pures, testables sans DOM ni réseau :
 * - `assembleSystemPrompt` remplace les deux marqueurs du gabarit partagé
 *   `report/chat-prompt.txt` (`{{SERIES}}`, `{{AGGREGATES}}`).
 * - `toLlmMessages` borne l'historique aux `MAX_HISTORY_MESSAGES` derniers
 *   messages et le met sous la forme `{role, content}` attendue par `HA.llm.analyze`.
 *
 * `buildSystemPrompt` est la seule fonction impure du fichier : elle charge le
 * gabarit par `fetch`, une fois, puis délègue à `assembleSystemPrompt`.
 *
 * RÈGLE DE CONFIDENTIALITÉ (voir CLAUDE.md) : `{{AGGREGATES}}` est rempli par
 * `HA.prompt.narrativeUserPrompt(model)` telle quelle, jamais par une lecture
 * indépendante du modèle. Cette fonction est déjà auditée — elle n'envoie que
 * des agrégats, jamais une série quotidienne, un identifiant ou un horodatage
 * précis. En écrire une autre rouvrirait une porte de sortie à auditer de nouveau.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const SYSTEM_PROMPT_URL = 'report/chat-prompt.txt';

  /** Nombre maximal de messages envoyés au modèle à chaque tour, système à part. */
  const MAX_HISTORY_MESSAGES = 20;

  /** Mis en cache après le premier chargement : le gabarit ne change jamais en cours de session. */
  let cachedTemplate = null;

  /** Charge (et met en cache) le gabarit du prompt système, partagé avec Android. */
  async function loadTemplate() {
    if (cachedTemplate !== null) return cachedTemplate;
    const response = await fetch(SYSTEM_PROMPT_URL);
    if (!response.ok) {
      throw new Error(`Impossible de charger ${SYSTEM_PROMPT_URL} (HTTP ${response.status}).`);
    }
    cachedTemplate = await response.text();
    return cachedTemplate;
  }

  /**
   * Une ligne lisible par série disponible : son nom, son libellé, son unité,
   * son nombre de points. Le modèle désigne une série par ce nom — voir
   * `report/chart-catalog.js`.
   *
   * @param {object} model un `ReportModel`
   * @returns {string}
   */
  function formatSeriesList(model) {
    const series = HA.chartCatalog.availableSeries(model);
    if (series.length === 0) return 'Aucune série disponible sur cette période.';
    return series
      .map((s) => `- ${s.ref} — ${s.label} (${s.unit}, ${s.points} points)`)
      .join('\n');
  }

  /**
   * Remplace les deux marqueurs du gabarit par la liste des séries et les
   * agrégats de la période. Fonction pure : aucun accès réseau ni DOM.
   *
   * @param {string} template le texte de `report/chat-prompt.txt`
   * @param {object} model un `ReportModel`
   * @returns {string} le prompt système complet
   */
  function assembleSystemPrompt(template, model) {
    return String(template)
      .replace('{{SERIES}}', formatSeriesList(model))
      .replace('{{AGGREGATES}}', HA.prompt.narrativeUserPrompt(model));
  }

  /**
   * Construit le prompt système complet de la conversation.
   *
   * Les agrégats vont dans le prompt système, donc une seule fois pour toute
   * la conversation : l'appelant doit mettre son résultat en cache et ne le
   * reconstruire que si les données importées changent.
   *
   * @param {object} model un `ReportModel`
   * @returns {Promise<string>}
   */
  async function buildSystemPrompt(model) {
    const template = await loadTemplate();
    return assembleSystemPrompt(template, model);
  }

  // ========================================================================
  // healthrange — le modèle choisit lui-même la fenêtre qu'il examine
  // ========================================================================
  //
  // Exact symétrique de `report/chart-spec.js` pour `healthchart` (voir CLAUDE.md) :
  // le modèle écrit un bloc de code ```healthrange contenant du JSON, l'application le
  // lit et reconstruit le rapport sur la période demandée. Même esprit indulgent :
  // corrige ce qui se corrige (bornes inversées, fenêtre partiellement hors historique),
  // ne refuse que l'irrécupérable, avec un message que le modèle peut lire et comprendre.

  const RANGE_BLOCK_RE = /```healthrange[ \t]*\r?\n([\s\S]*?)```/;

  /** `AAAA-MM-JJ` strict. Une date malformée est refusée, jamais devinée au hasard. */
  const ISO_DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
  /** Tolère un format français glissé par erreur — un seul champ à la fois, pas d'ambiguïté de série. */
  const FRENCH_DATE_RE = /^(\d{2})\/(\d{2})\/(\d{4})$/;

  /** Analyse une date `healthrange` en clé ISO `AAAA-MM-JJ`, ou `null` si illisible. Ne valide pas le calendrier au-delà du format. */
  function parseRangeDate(raw) {
    const iso = ISO_DATE_RE.exec(raw);
    if (iso) return raw;
    const fr = FRENCH_DATE_RE.exec(raw);
    if (fr) return `${fr[3]}-${fr[2]}-${fr[1]}`;
    return null;
  }

  /** Un mois `AAAA-MM` de `overview.months` chevauche-t-il `[from, to]` (bornes ISO incluses) ? */
  function monthOverlaps(month, from, to) {
    const firstDay = `${month}-01`;
    // Le dernier jour du mois majore toujours toute date réelle de ce mois : comparer
    // des chaînes ISO suffit, sans construire un `Date` ni connaître le nombre de jours.
    const lastDay = `${month}-31`;
    return firstDay <= to && lastDay >= from;
  }

  /**
   * Lit et valide le bloc `healthrange` d'une réponse du modèle.
   *
   * @param {string} reply le texte brut de la réponse du modèle
   * @param {{earliest: string|null, latest: string|null, months: {month:string, daysWithData:number}[]}} overview
   * @returns {{type:'notFound'} | {type:'accepted', from:string, to:string, note:string|null} | {type:'rejected', reason:string}}
   */
  function parseHealthRange(reply, overview) {
    const match = RANGE_BLOCK_RE.exec(typeof reply === 'string' ? reply : '');
    if (!match) return { type: 'notFound' };

    let spec;
    try {
      spec = JSON.parse(match[1]);
    } catch (error) {
      return { type: 'rejected', reason: `le bloc healthrange n'est pas un JSON valide : ${error.message}` };
    }
    if (!spec || typeof spec !== 'object' || Array.isArray(spec)) {
      return { type: 'rejected', reason: "le bloc healthrange n'est pas un objet JSON." };
    }

    const fromRaw = typeof spec.from === 'string' ? spec.from.trim() : '';
    const toRaw = typeof spec.to === 'string' ? spec.to.trim() : '';
    if (!fromRaw || !toRaw) {
      return { type: 'rejected', reason: 'les champs "from" et "to" sont obligatoires, au format AAAA-MM-JJ.' };
    }

    const from = parseRangeDate(fromRaw);
    const to = parseRangeDate(toRaw);
    if (!from) return { type: 'rejected', reason: `la date "${fromRaw}" est illisible ; utilise le format AAAA-MM-JJ.` };
    if (!to) return { type: 'rejected', reason: `la date "${toRaw}" est illisible ; utilise le format AAAA-MM-JJ.` };

    const notes = [];
    let correctedFrom = from;
    let correctedTo = to;
    if (correctedFrom > correctedTo) {
      const swap = correctedFrom;
      correctedFrom = correctedTo;
      correctedTo = swap;
      notes.push('les bornes étaient inversées, elles ont été remises dans l\'ordre');
    }

    if (!overview.earliest || !overview.latest) {
      return { type: 'rejected', reason: "aucune donnée n'a encore été importée." };
    }
    if (correctedTo < overview.earliest || correctedFrom > overview.latest) {
      return {
        type: 'rejected',
        reason: `aucune mesure n'existe entre ${fromRaw} et ${toRaw} ; `
          + `l'historique disponible va de ${overview.earliest} à ${overview.latest}.`,
      };
    }
    if (correctedFrom < overview.earliest || correctedTo > overview.latest) {
      correctedFrom = correctedFrom < overview.earliest ? overview.earliest : correctedFrom;
      correctedTo = correctedTo > overview.latest ? overview.latest : correctedTo;
      notes.push(`la fenêtre a été ramenée à l'historique disponible (${overview.earliest} à ${overview.latest})`);
    }

    const touchesData = (overview.months || []).some((m) => monthOverlaps(m.month, correctedFrom, correctedTo));
    if (!touchesData) {
      return { type: 'rejected', reason: `aucune mesure n'existe entre ${fromRaw} et ${toRaw}.` };
    }

    return { type: 'accepted', from: correctedFrom, to: correctedTo, note: notes.length ? notes.join(', ') : null };
  }

  /** Au plus deux demandes de fenêtre par tour, plus l'appel qui les suit — voir `report/chat-prompt.txt`. */
  const MAX_RANGE_REQUESTS = 2;
  const MAX_LLM_CALLS = MAX_RANGE_REQUESTS + 1;

  const NO_WINDOW_CHOSEN_TEXT =
    "Aucune période n'est encore choisie. Demande une fenêtre avec `healthrange` avant de répondre avec des chiffres.";

  function rejectionNudge(reason) {
    return `La fenêtre demandée est refusée : ${reason} `
      + 'Corrige-la si tu peux, ou réponds avec ce que tu sais déjà si tu préfères.';
  }

  function dataNudge(outcome) {
    const correction = outcome.note ? ` (${outcome.note})` : '';
    return `Les données pour la période ${outcome.from} à ${outcome.to} sont maintenant disponibles${correction}. `
      + 'Réponds à la question précédente avec ces chiffres.';
  }

  /**
   * Mène un tour de conversation avec le mécanisme `healthrange` : demande une réponse au
   * modèle, et s'il demande une fenêtre de données, reconstruit le rapport sur cette
   * période et redemande une réponse — jusqu'à deux fois, voir `report/chat-prompt.txt`.
   *
   * Aller-retour invisible dans la conversation persistée : `messages` n'est jamais
   * modifié, les tours intermédiaires (demande de fenêtre, confirmation des données)
   * vivent dans une copie locale, le temps de cet appel seulement.
   *
   * @param {object} options
   * @param {string} options.template le gabarit `report/chat-prompt.txt`
   * @param {object} options.historyModel `ReportModel` sur tout l'historique
   * @param {object|null} options.activeModel `ReportModel` de la fenêtre déjà active, ou `null`
   * @param {{role:string, content:string}[]} options.messages fil déjà borné (voir `toLlmMessages`)
   * @param {(systemPrompt: string, messages: {role:string,content:string}[]) => Promise<{text:string}>} options.complete
   *   appelle le fournisseur LLM ; injecté pour rester testable sans réseau
   * @param {(from: string, to: string) => Promise<object>} options.buildReportForRange
   *   reconstruit un `ReportModel` sur la période demandée
   * @returns {Promise<{reply: string, activeModel: object|null, statusNote: string|null}>}
   */
  async function runConversationTurn(options) {
    const { template, historyModel, messages, complete, buildReportForRange } = options;
    const overview = HA.prompt.historyOverview(historyModel);

    let activeModel = options.activeModel || null;
    let statusNote = null;
    let workingMessages = messages.slice();

    for (let callIndex = 0; callIndex < MAX_LLM_CALLS; callIndex += 1) {
      const isLastAllowedCall = callIndex === MAX_LLM_CALLS - 1;

      const systemPrompt = String(template)
        .replace('{{SERIES}}', formatSeriesList(activeModel || historyModel))
        .replace('{{OVERVIEW}}', HA.prompt.describeHistoryOverview(overview))
        .replace('{{AGGREGATES}}', activeModel ? HA.prompt.narrativeUserPrompt(activeModel) : NO_WINDOW_CHOSEN_TEXT);

      // eslint-disable-next-line no-await-in-loop
      const response = await complete(systemPrompt, workingMessages);

      if (isLastAllowedCall) return { reply: response.text, activeModel, statusNote };

      const outcome = parseHealthRange(response.text, overview);
      if (outcome.type === 'notFound') return { reply: response.text, activeModel, statusNote };

      if (outcome.type === 'rejected') {
        workingMessages = workingMessages.concat([
          { role: 'assistant', content: response.text },
          { role: 'user', content: rejectionNudge(outcome.reason) },
        ]);
        continue; // eslint-disable-line no-continue
      }

      // eslint-disable-next-line no-await-in-loop
      activeModel = await buildReportForRange(outcome.from, outcome.to);
      statusNote = `Le modèle examine ${outcome.from} à ${outcome.to}.`;
      workingMessages = workingMessages.concat([
        { role: 'assistant', content: response.text },
        { role: 'user', content: dataNudge(outcome) },
      ]);
    }

    // Inatteignable : la dernière itération de la boucle rend toujours une réponse.
    throw new Error('la boucle de conversation s\'est arrêtée sans réponse.');
  }

  /**
   * Met une conversation sous la forme `{role, content}` attendue par
   * `HA.llm.analyze`, bornée aux `limit` derniers messages (par défaut
   * `MAX_HISTORY_MESSAGES`) pour que le coût ne dérive pas sur un long fil.
   *
   * @param {{role: string, text: string}[]} conversation
   * @param {number} [limit]
   * @returns {{role: string, content: string}[]}
   */
  function toLlmMessages(conversation, limit) {
    const n = limit === undefined ? MAX_HISTORY_MESSAGES : limit;
    return (conversation || [])
      .slice(-n)
      .map((m) => ({ role: m.role, content: m.text }));
  }

  root.HA.chatPrompt = {
    MAX_HISTORY_MESSAGES, loadTemplate, buildSystemPrompt, assembleSystemPrompt, formatSeriesList, toLlmMessages,
    parseHealthRange, runConversationTurn, MAX_RANGE_REQUESTS,
  };
})();
