/**
 * Récit écrit par le LLM (`model.narrative`, voir `report-model.js`).
 *
 * Deux responsabilités, séparées de `prompt.js` (qui construit le message utilisateur) :
 * 1. Charger le prompt système partagé avec Android — `report/narrative-prompt.txt`,
 *    un texte, pas un module JS. Ce fichier n'est jamais dupliqué ici.
 * 2. Lire la réponse du LLM. Le modèle rend un objet JSON, mais peut malgré la
 *    consigne l'entourer de texte ou d'un bloc de code Markdown : on extrait le
 *    premier objet JSON équilibré, puis on ne garde que ce qui respecte le contrat
 *    de `ReportNarrative`. Un texte inexploitable rend `null` avec un message clair
 *    plutôt que de faire planter l'appelant.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const SYSTEM_PROMPT_URL = 'report/narrative-prompt.txt';

  /** Mis en cache après le premier chargement : le texte ne change jamais en cours de session. */
  let cachedSystemPrompt = null;

  /** Charge (et met en cache) le prompt système du récit, partagé avec Android. */
  async function loadSystemPrompt() {
    if (cachedSystemPrompt !== null) return cachedSystemPrompt;
    const response = await fetch(SYSTEM_PROMPT_URL);
    if (!response.ok) {
      throw new Error(`Impossible de charger ${SYSTEM_PROMPT_URL} (HTTP ${response.status}).`);
    }
    cachedSystemPrompt = await response.text();
    return cachedSystemPrompt;
  }

  /**
   * Extrait le premier objet JSON équilibré d'un texte, en ignorant les accolades
   * qui apparaissent à l'intérieur d'une chaîne. Sans ce comptage, un guillemet ou
   * une accolade dans le texte d'un point du bilan couperait l'extraction au
   * mauvais endroit.
   *
   * @param {string} text
   * @returns {string|null} la sous-chaîne JSON, ou `null` si aucune accolade ouvrante n'existe
   */
  function extractFirstJsonObject(text) {
    const start = text.indexOf('{');
    if (start === -1) return null;

    let depth = 0;
    let inString = false;
    let escaped = false;

    for (let i = start; i < text.length; i += 1) {
      const ch = text[i];
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (ch === '\\') {
          escaped = true;
        } else if (ch === '"') {
          inString = false;
        }
        continue;
      }
      if (ch === '"') {
        inString = true;
      } else if (ch === '{') {
        depth += 1;
      } else if (ch === '}') {
        depth -= 1;
        if (depth === 0) return text.slice(start, i + 1);
      }
    }
    return null; // accolade ouvrante jamais refermée : réponse tronquée
  }

  function isNonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0;
  }

  /** Filtre les points d'une section : seules les chaînes non vides survivent. */
  function sanitizePoints(points) {
    if (!Array.isArray(points)) return [];
    return points.filter(isNonEmptyString);
  }

  /** Ne garde que les sections du contrat (`HA.reportModel.SECTION_KEYS`) au verdict exploitable. */
  function sanitizeSections(sections) {
    const result = {};
    if (!sections || typeof sections !== 'object') return result;
    HA.reportModel.SECTION_KEYS.forEach((key) => {
      const section = sections[key];
      if (!section || !isNonEmptyString(section.verdict)) return;
      result[key] = { verdict: section.verdict, points: sanitizePoints(section.points) };
    });
    return result;
  }

  /** Ne garde que les étapes de plan dont le titre et le corps sont des chaînes exploitables. */
  function sanitizePlan(plan) {
    if (!Array.isArray(plan)) return [];
    return plan
      .filter((step) => step && isNonEmptyString(step.title) && isNonEmptyString(step.body))
      .map((step) => ({ title: step.title, body: step.body }));
  }

  /**
   * Lit la réponse texte du LLM et rend un `ReportNarrative` conforme, ou `null`
   * avec un message d'erreur si rien d'exploitable n'a pu en être tiré.
   *
   * @param {string} text la réponse brute du LLM
   * @returns {{narrative: object|null, error: string|null}}
   */
  function parseNarrativeResponse(text) {
    if (typeof text !== 'string' || text.trim().length === 0) {
      return { narrative: null, error: 'Le modèle a renvoyé une réponse vide.' };
    }

    const jsonText = extractFirstJsonObject(text);
    if (jsonText === null) {
      return { narrative: null, error: "Aucun objet JSON n'a été trouvé dans la réponse du modèle." };
    }

    let parsed;
    try {
      parsed = JSON.parse(jsonText);
    } catch (error) {
      return { narrative: null, error: `La réponse du modèle n'est pas un JSON valide (${error.message}).` };
    }

    if (!parsed || typeof parsed !== 'object') {
      return { narrative: null, error: "La réponse du modèle n'est pas un objet." };
    }
    if (!isNonEmptyString(parsed.headline) || !isNonEmptyString(parsed.verdict)) {
      return { narrative: null, error: 'La réponse du modèle ne porte ni titre ni verdict exploitables.' };
    }

    return {
      narrative: {
        headline: parsed.headline,
        verdict: parsed.verdict,
        sections: sanitizeSections(parsed.sections),
        plan: sanitizePlan(parsed.plan),
      },
      error: null,
    };
  }

  root.HA.narrative = { loadSystemPrompt, parseNarrativeResponse };
})();
