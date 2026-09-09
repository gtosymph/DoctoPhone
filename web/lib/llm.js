/**
 * Clients pour les trois fournisseurs de LLM. Chaque fonction envoie le prompt
 * système et un fil de conversation (`messages`), puis rend `{ text, usage }`.
 *
 * Un appelant qui ne passe qu'un `userPrompt` (analyse ponctuelle à un seul tour) continue de
 * fonctionner : il est transformé en fil d'un seul message utilisateur.
 *
 * Aucune clé API n'est écrite en dur ici : elle vient toujours des réglages
 * de l'utilisateur, saisie dans l'interface.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const PROVIDER_NAMES = {
    gemini: 'Google Gemini',
    anthropic: 'Anthropic',
    openai: 'OpenAI',
  };

  const DEFAULT_MODELS = {
    gemini: 'gemini-3.1-pro-preview',
    anthropic: 'claude-sonnet-5',
    openai: 'gpt-5.4',
  };

  /** Traduit une erreur HTTP ou réseau en message français exploitable par l'utilisateur. */
  function translateError(provider, status) {
    const name = PROVIDER_NAMES[provider] || provider;
    if (status === 401 || status === 403) {
      return `Clé API refusée par ${name}. Vérifiez la clé dans les réglages.`;
    }
    if (status === 429) {
      return `Débit limité par ${name}. Réessayez dans quelques instants.`;
    }
    if (status >= 500) {
      return `Erreur du fournisseur ${name}. Réessayez plus tard.`;
    }
    return `Erreur ${status} du fournisseur ${name}.`;
  }

  function translateNetworkError(provider) {
    const name = PROVIDER_NAMES[provider] || provider;
    return `Impossible de joindre ${name}. Vérifiez votre connexion internet.`;
  }

  async function fetchJson(provider, url, options) {
    let response;
    try {
      response = await fetch(url, options);
    } catch (networkError) {
      throw new Error(translateNetworkError(provider));
    }
    if (!response.ok) {
      throw new Error(translateError(provider, response.status));
    }
    return response.json();
  }

  /**
   * Accepte soit un fil `messages` déjà construit, soit l'ancien paramètre `userPrompt` (un
   * seul tour utilisateur), pour que l'appelant historique n'ait rien à changer.
   */
  function resolveMessages({ messages, userPrompt }) {
    if (Array.isArray(messages)) return messages;
    if (typeof userPrompt === 'string') return [{ role: 'user', content: userPrompt }];
    throw new Error('Il faut fournir "messages" ou "userPrompt".');
  }

  /**
   * Remet en forme un fil de conversation pour respecter la contrainte commune aux trois
   * fournisseurs : le premier message doit être de l'utilisateur, et les rôles doivent
   * alterner strictement. Fusionne les messages consécutifs de même rôle et enlève les
   * messages assistant en tête, plutôt que d'envoyer une requête que le fournisseur rejetterait.
   */
  function normalizeMessages(messages) {
    let trimmed = messages;
    while (trimmed.length > 0 && trimmed[0].role === 'assistant') {
      trimmed = trimmed.slice(1);
    }

    const merged = [];
    for (const message of trimmed) {
      const last = merged[merged.length - 1];
      if (last && last.role === message.role) {
        last.content = `${last.content}\n\n${message.content}`;
      } else {
        merged.push({ role: message.role, content: message.content });
      }
    }
    return merged;
  }

  async function callGemini({ apiKey, model, systemPrompt, ...params }) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
    // Gemini nomme "model" ce que les autres fournisseurs nomment "assistant".
    const body = {
      systemInstruction: { parts: [{ text: systemPrompt }] },
      contents: normalizeMessages(resolveMessages(params)).map((m) => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      })),
      generationConfig: { temperature: 0.3, maxOutputTokens: 4096 },
    };
    const json = await fetchJson('gemini', url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
      body: JSON.stringify(body),
    });
    const parts = (json.candidates && json.candidates[0] && json.candidates[0].content && json.candidates[0].content.parts) || [];
    const text = parts.map((p) => p.text || '').join('');
    return { text, usage: json.usageMetadata || null };
  }

  async function callAnthropic({ apiKey, model, systemPrompt, ...params }) {
    const url = 'https://api.anthropic.com/v1/messages';
    const body = {
      model,
      max_tokens: 4096,
      temperature: 0.3,
      system: systemPrompt,
      messages: normalizeMessages(resolveMessages(params)).map((m) => ({ role: m.role, content: m.content })),
    };
    const json = await fetchJson('anthropic', url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-dangerous-direct-browser-access': 'true',
      },
      body: JSON.stringify(body),
    });
    const text = (json.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
    return { text, usage: json.usage || null };
  }

  async function callOpenAi({ apiKey, model, systemPrompt, ...params }) {
    const url = 'https://api.openai.com/v1/chat/completions';
    const body = {
      model,
      temperature: 0.3,
      max_tokens: 4096,
      messages: [
        { role: 'system', content: systemPrompt },
        ...normalizeMessages(resolveMessages(params)).map((m) => ({ role: m.role, content: m.content })),
      ],
    };
    const json = await fetchJson('openai', url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify(body),
    });
    const text = (json.choices && json.choices[0] && json.choices[0].message && json.choices[0].message.content) || '';
    return { text, usage: json.usage || null };
  }

  async function analyze(provider, params) {
    if (provider === 'gemini') return callGemini(params);
    if (provider === 'anthropic') return callAnthropic(params);
    if (provider === 'openai') return callOpenAi(params);
    throw new Error(`Fournisseur inconnu : ${provider}.`);
  }

  root.HA.llm = {
    analyze,
    normalizeMessages,
    translateError,
    translateNetworkError,
    PROVIDER_NAMES,
    DEFAULT_MODELS,
  };
})();
