/**
 * La vue de la conversation d'analyse, écrite une seule fois : la version web l'utilise
 * directement, la version Android la montre dans une WebView locale (voir `chat.html`).
 *
 * `HA.chat.render(host, conversation, model, options)` dessine la conversation entière
 * dans `host`. Une conversation est `{role: 'user'|'assistant', text: string, at: number}[]`
 * — `at` est facultatif pour le dessin (il ne sert qu'au tri et à la persistance côté
 * appelant) et `content` est accepté comme synonyme de `text` (voir `messageText`),
 * pour la coquille Android qui reçoit son JSON directement de Kotlin.
 *
 * Une conversation vide dessine elle-même l'amorce de premier usage (une phrase sur la
 * période, quatre questions d'exemple) plutôt que de laisser un cadre vide — l'écran
 * Android n'a pas d'autre endroit où l'écrire. `options.onSuggestion(text)`, facultatif,
 * est appelé quand une suggestion est cliquée ; sans lui, cliquer ne fait rien plutôt que
 * de planter. La version web y branche le remplissage du compositeur, `chat.html` y
 * branche `AndroidChatHost.onSuggestion`.
 *
 * Un message de l'assistant est découpé en segments par `parseAssistant` : du texte
 * Markdown d'un côté, des blocs ```healthchart de l'autre (voir `report/chart-spec.js`).
 * Chaque segment de texte passe par `HA.markdown.toHtml` (qui échappe déjà le HTML —
 * voir `lib/markdown.js`), chaque segment de graphique par `HA.chartSpec.render`. Un
 * éventuel bloc ```healthrange (voir `lib/chat.js`, `runConversationTurn`) est retiré
 * avant tout découpage : c'est un artefact interne de l'aller-retour de choix de
 * période, jamais un contenu à montrer.
 *
 * `model` est toujours le `ReportModel` de **tout** l'historique, jamais une fenêtre
 * réduite : un graphique se résout avec les points de son propre message
 * (`message.rangeFrom`/`rangeTo`, voir `messageRange`), filtrés depuis ce modèle complet
 * — jamais avec la fenêtre que le modèle examine au moment du dessin. Sans quoi le
 * graphique d'une ancienne réponse changerait de contenu après coup, dès que la
 * conversation change de période.
 *
 * Un bloc de graphique illisible (JSON invalide) ou dont le dessin échoue ne casse pas
 * le message : le texte reste affiché, une note discrète remplace le graphique. Un bloc
 * dont le JSON est valide mais que `chart-spec.js` refuse (référence inconnue, séries
 * incompatibles…) est déjà géré par `chart-spec.js` lui-même, qui dessine à la place un
 * message d'explication — rien à faire ici.
 *
 * Contrainte de sécurité : le texte vient d'un modèle de langage, donc jamais
 * `innerHTML` avec du texte brut. `HA.markdown.toHtml` échappe déjà tout, il peut être
 * posé tel quel ; partout ailleurs, `textContent`.
 */
(function (global) {
  'use strict';

  /** Un bloc de graphique est marqué ```healthchart … ``` sur ses propres lignes. */
  const CHART_BLOCK_RE = /```healthchart[ \t]*\r?\n([\s\S]*?)```/g;

  /**
   * Un bloc de demande de fenêtre, ```healthrange … ```. `HA.chatPrompt.runConversationTurn`
   * le résout toujours en interne (voir `lib/chat.js`) : un message affiché ne devrait
   * jamais en porter un. Filet de sécurité seulement, pour le cas où le modèle en écrirait
   * un troisième au-delà des deux demandes autorisées — la réponse de ce dernier appel est
   * rendue telle quelle par l'orchestrateur, sans repasser par lui. Retiré en silence,
   * jamais montré : ce n'est pas une erreur pour la personne qui lit la conversation.
   */
  const RANGE_BLOCK_RE = /```healthrange[ \t]*\r?\n[\s\S]*?```/g;

  /**
   * Découpe le texte d'un message de l'assistant en segments de texte et de graphique.
   *
   * @param {string} text le texte du message
   * @returns {Array<
   *   {type: 'text', text: string} |
   *   {type: 'chart', raw: string, spec: object|null, error: string|null}
   * >}
   */
  function parseAssistant(text) {
    const source = (typeof text === 'string' ? text : '').replace(RANGE_BLOCK_RE, '');
    const segments = [];
    let lastIndex = 0;
    let match;

    CHART_BLOCK_RE.lastIndex = 0;
    while ((match = CHART_BLOCK_RE.exec(source)) !== null) {
      const before = source.slice(lastIndex, match.index);
      if (before.trim().length > 0) segments.push({ type: 'text', text: before });
      segments.push(parseChartBlock(match[1]));
      lastIndex = match.index + match[0].length;
    }

    const rest = source.slice(lastIndex);
    if (rest.trim().length > 0 || segments.length === 0) segments.push({ type: 'text', text: rest });
    return segments;
  }

  /** Lit le JSON d'un bloc ```healthchart. Un JSON invalide ou non-objet rend `spec: null` et une `error` lisible. */
  function parseChartBlock(raw) {
    try {
      const spec = JSON.parse(raw);
      if (!spec || typeof spec !== 'object' || Array.isArray(spec)) {
        return { type: 'chart', raw, spec: null, error: "le graphique n'est pas un objet JSON" };
      }
      return { type: 'chart', raw, spec, error: null };
    } catch (error) {
      return { type: 'chart', raw, spec: null, error: `JSON illisible : ${error.message}` };
    }
  }

  function el(tag, className) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    return node;
  }

  /** Pose une note discrète, en `textContent` : elle peut reprendre un message d'erreur venu du modèle. */
  function appendNote(host, text) {
    const note = el('p', 'ha-note');
    note.textContent = text;
    host.appendChild(note);
  }

  /**
   * Dessine un segment de graphique dans une carte. N'appelle jamais `chart-spec.js` sur
   * un JSON illisible (rien à valider), et protège l'appel quand le JSON est lisible : le
   * moteur est indulgent mais reste du code, une exception ne doit pas faire disparaître
   * tout le message.
   *
   * @param {{from: string, to: string}} [range] la fenêtre du **message** qui porte ce
   *   graphique (voir `messageRange`), pas la fenêtre active de la conversation : un
   *   graphique déjà affiché garde ses données d'origine même si le modèle choisit une
   *   autre période ensuite pour répondre à une question suivante.
   */
  function renderChartSegment(segment, model, range) {
    const card = el('div', 'ha-card ha-chat-chart-card');

    if (!segment.spec) {
      appendNote(card, 'Graphique illisible, non affiché.');
      return card;
    }

    const title = typeof segment.spec.title === 'string' && segment.spec.title.trim()
      ? segment.spec.title.trim()
      : 'Graphique';
    const h = el('h3');
    h.textContent = title;
    card.appendChild(h);

    const chartHost = el('div', 'ha-chart');
    card.appendChild(chartHost);

    try {
      global.HA.chartSpec.render(chartHost, segment.spec, model, range);
    } catch (error) {
      chartHost.textContent = '';
      appendNote(card, 'Ce graphique n\'a pas pu être affiché.');
      if (global.console) global.console.error('[chat] échec du dessin du graphique', error);
    }

    return card;
  }

  /**
   * Le texte d'un message. La version web écrit `text` (voir `app-chat.js`) ; la
   * coquille Android reçoit un JSON construit côté Kotlin qui porte `content`
   * (même nom que le fil envoyé au LLM, voir `HA.chatPrompt.toLlmMessages`) et
   * aucun `text` — les deux formes sont donc acceptées ici plutôt que de figer
   * un nom de champ que les deux appelants devraient sans cesse resynchroniser.
   */
  function messageText(message) {
    if (typeof message.text === 'string') return message.text;
    if (typeof message.content === 'string') return message.content;
    return '';
  }

  /**
   * La fenêtre que le modèle examinait quand il a écrit ce message, si `healthrange` en a
   * choisi une pour ce tour (voir `lib/chat.js`, `runConversationTurn`) — `null` sinon, et
   * un graphique de ce message se résout alors avec tout l'historique, comme avant ce
   * mécanisme. Portée par le message lui-même, pas par un état de conversation courant :
   * un ancien message garde sa fenêtre même après que le modèle en a choisi une autre pour
   * répondre à une question suivante.
   */
  function messageRange(message) {
    if (typeof message.rangeFrom === 'string' && typeof message.rangeTo === 'string') {
      return { from: message.rangeFrom, to: message.rangeTo };
    }
    return null;
  }

  /** Dessine un message dans une bulle, texte simple pour l'utilisateur, segments pour l'assistant. */
  function renderMessage(message, model) {
    const isUser = message.role !== 'assistant';
    const row = el('div', `ha-chat-msg ha-chat-msg-${isUser ? 'user' : 'assistant'}`);
    const bubble = el('div', 'ha-chat-bubble');
    row.appendChild(bubble);

    if (isUser) {
      const p = el('div', 'ha-chat-text');
      p.textContent = messageText(message);
      bubble.appendChild(p);
      return row;
    }

    const range = messageRange(message);
    parseAssistant(messageText(message)).forEach((segment) => {
      if (segment.type === 'chart') {
        bubble.appendChild(renderChartSegment(segment, model, range));
        return;
      }
      if (segment.text.trim().length === 0) return;
      const div = el('div', 'ha-chat-text');
      div.innerHTML = global.HA.markdown.toHtml(segment.text);
      bubble.appendChild(div);
    });

    return row;
  }

  /**
   * Questions d'exemple de l'amorce de premier usage. Choisies pour montrer l'étendue de
   * ce qui est possible : une tendance sur plusieurs mois, une corrélation entre deux
   * mesures, un graphique explicite, une question ouverte.
   */
  const SUGGESTED_QUESTIONS = [
    'Comment mon sommeil a-t-il évolué ces trois derniers mois ?',
    'Mes pas influencent-ils ma fréquence cardiaque au repos ?',
    'Montre-moi mon heure de coucher nuit par nuit.',
    "Qu'est-ce qui mérite le plus mon attention ?",
  ];

  /** La phrase d'amorce : dit sur quelles données porte la conversation, quand le modèle les porte. */
  function introText(model) {
    const meta = (model && model.meta) || {};
    const period = meta.periodLabel ? ` sur ${meta.periodLabel}` : '';
    let nights = '';
    if (typeof meta.nights === 'number' && meta.nights > 0) {
      const plural = meta.nights > 1;
      nights = ` (${meta.nights} nuit${plural ? 's' : ''} de sommeil mesurée${plural ? 's' : ''})`;
    }
    return `Posez une question sur vos données de santé${period}${nights}. Le modèle peut `
      + 'répondre par du texte, et par un graphique quand une courbe éclaire mieux.';
  }

  /**
   * Dessine l'amorce de premier usage dans `host` (déjà vidé par `render`) : la phrase
   * d'introduction, puis les questions d'exemple cliquables.
   */
  function renderIntro(host, model, onSuggestion) {
    host.classList.add('ha-chat-empty');

    const text = el('p', 'ha-chat-intro-text');
    text.textContent = introText(model);
    host.appendChild(text);

    const list = el('div', 'ha-chat-suggestions');
    SUGGESTED_QUESTIONS.forEach((question) => {
      const button = el('button', 'ha-chat-suggestion');
      button.type = 'button';
      button.textContent = question;
      button.addEventListener('click', () => {
        if (typeof onSuggestion === 'function') onSuggestion(question);
      });
      list.appendChild(button);
    });
    host.appendChild(list);
  }

  /**
   * Dessine la conversation entière dans `host`, en le vidant d'abord. Une conversation
   * vide dessine l'amorce de premier usage à la place (voir `renderIntro`).
   *
   * @param {Element} host conteneur à remplir
   * @param {{role: string, text: string, at: number}[]} conversation
   * @param {object} model le `ReportModel` de la période, pour résoudre les graphiques
   *   et amorcer la phrase d'introduction
   * @param {{onSuggestion?: (text: string) => void}} [options]
   */
  function render(host, conversation, model, options) {
    host.textContent = '';
    host.classList.remove('ha-chat-empty');

    if (!conversation || conversation.length === 0) {
      renderIntro(host, model, options && options.onSuggestion);
      return;
    }

    conversation.forEach((message) => host.appendChild(renderMessage(message, model)));
  }

  global.HA = global.HA || {};
  global.HA.chat = { render, parseAssistant, introText, SUGGESTED_QUESTIONS };
}(typeof self !== 'undefined' ? self : this));
