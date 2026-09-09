/**
 * Onglet Rapport — construit le modèle de rapport depuis IndexedDB et le
 * dessine avec le moteur SVG partagé (`report/`).
 *
 * Séparé de `app.js` pour rester dans la limite de taille de fichier (200 à
 * 400 lignes). Ce fichier ne contient aucune règle métier : il choisit quels
 * magasins charger selon la période, les passe à `HA.reportBuilder.buildReport`
 * (écrit par un autre agent, contrat décrit dans `lib/report-model.js`), puis
 * délègue le dessin à `HA.report.renderReport`.
 *
 * ATTENTION confidentialité : le modèle de rapport porte des mesures
 * quotidiennes. Il ne doit jamais être passé à `lib/prompt.js` ni à un appel
 * LLM — voir `HealthPromptBuilder` côté Android pour la même règle.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const MILLIS_PER_DAY = 86400000;

  /**
   * Magasins à clé `date` (série journalière) : lus par `HA.db.readDateRange`.
   * Doit rester synchronisé avec `DAILY_STORES` dans `lib/db.js`, qui ne
   * l'exporte pas. Tout autre magasin de `REPORT_STORES` est ponctuel (clé
   * `id`, index temporel epoch) et se lit par `HA.db.readRange`.
   */
  const DAILY_STORES = new Set(['dailySteps', 'dailyActivity', 'energyScores', 'dailyFloors']);

  /** Magasins IndexedDB lus pour construire le rapport (un tableau de lignes chacun). */
  const REPORT_STORES = [
    'sleepNights', 'sleepStages', 'heartRate', 'stress', 'stressAlerts', 'hrv',
    'spo2', 'exercise', 'bodyComposition', 'dailySteps', 'dailyActivity',
    'energyScores', 'bloodPressure', 'ecg', 'snoring', 'respiratory',
    'skinTemp', 'sleepApnea', 'dailyFloors',
  ];

  /** Formate une date locale en clé `AAAA-MM-JJ`, sans dérive de fuseau. */
  function dateKey(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  function startOfDayMillis(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0).getTime();
  }

  /**
   * Traduit la valeur du sélecteur de période en plage de dates et en plage
   * d'instants (epoch millisecondes).
   *
   * `from`/`fromMillis` valent `null` pour « tout l'historique » : cela signale
   * à `readSources` de lire chaque magasin en entier plutôt que par plage.
   *
   * `to` vaut aussi `null` pour « tout l'historique » : `HA.reportBuilder.buildReport`
   * doit alors détecter lui-même la vraie dernière date mesurée, plutôt que de
   * couper le rapport à aujourd'hui si le dernier import remonte à quelques jours.
   *
   * @param {string} periodValue '7' | '30' | '90' | '365' | 'all'
   * @param {Date} [now] horloge injectable pour les tests
   * @returns {{from: string|null, to: string|null, fromMillis: number|null, toMillis: number|null}}
   */
  function periodToRange(periodValue, now) {
    const toDate = now || new Date();
    if (periodValue === 'all') return { from: null, to: null, fromMillis: null, toMillis: null };
    const to = dateKey(toDate);
    const days = Number(periodValue);
    const fromDate = new Date(toDate.getTime() - (days - 1) * MILLIS_PER_DAY);
    return { from: dateKey(fromDate), to, fromMillis: startOfDayMillis(fromDate), toMillis: toDate.getTime() };
  }

  /**
   * Lit tous les magasins nécessaires au rapport.
   *
   * Lit par plage quand elle est connue (par date pour les séries journalières,
   * par instant pour les mesures ponctuelles) : cela évite de charger en
   * mémoire la totalité de la variabilité cardiaque (environ 144 000 mesures)
   * pour une période de quelques jours. Seul « tout l'historique » lit un
   * magasin en entier.
   *
   * @param {{from: string|null, to: string, fromMillis: number|null, toMillis: number}} range
   * @returns {Promise<object>} un objet `{ nomDuMagasin: lignes[] }`
   */
  async function readSources(range) {
    const entries = await Promise.all(REPORT_STORES.map(async (store) => {
      let rows;
      if (!range.from) {
        rows = await HA.db.readAll(store);
      } else if (DAILY_STORES.has(store)) {
        rows = await HA.db.readDateRange(store, range.from, range.to);
      } else {
        rows = await HA.db.readRange(store, range.fromMillis, range.toMillis);
      }
      return [store, rows];
    }));
    return Object.fromEntries(entries);
  }

  /**
   * Construit puis dessine le rapport dans `host`, ou signale un modèle
   * incomplet via `onEmpty` (magasin manquant, modèle mal formé) plutôt que de
   * dessiner un rapport cassé. Ne détecte PAS l'absence totale de données :
   * un historique vide reste un modèle valide côté contrat (voir
   * `HA.reportModel.emptyReportModel`), c'est à l'appelant de vérifier ça en
   * amont (par exemple via le nombre de jours déjà importés).
   *
   * `options` sert à la fois à borner la lecture IndexedDB (`from`, `to`,
   * `fromMillis`, `toMillis`, voir `periodToRange`) et à construire le modèle
   * (`from`, `to`, `zone`, `sleepTargetHours`, voir `lib/report-builder.js`).
   *
   * @param {Element} host conteneur du rapport, vidé et rempli par le moteur
   * @param {{from: string|null, to: string|null, fromMillis: number|null,
   *          toMillis: number|null, zone: string, sleepTargetHours: number}} options
   * @param {(problems: string[]) => void} onEmpty appelé avec les manques trouvés
   * @returns {Promise<object|null>} le modèle dessiné, ou `null` si `onEmpty` a été appelé
   */
  async function renderReportInto(host, options, onEmpty) {
    const sources = await readSources(options);
    const model = HA.reportBuilder.buildReport(sources, options);
    const problems = HA.reportModel.validateReportModel(model);
    if (problems.length > 0) {
      onEmpty(problems);
      return null;
    }
    HA.report.renderReport(host, model);
    return model;
  }

  root.HA.appReport = { REPORT_STORES, periodToRange, readSources, renderReportInto, dateKey };
})();
