/**
 * Contrat de données du rapport de santé — version JavaScript.
 *
 * Ce fichier est le miroir exact de
 * `app/src/main/java/com/kmt/healthanalyzer/domain/report/ReportModel.kt` et
 * `ReportSections.kt`. Le moteur de graphiques (`web/report/`) lit ce modèle et rien
 * d'autre. Il est utilisé tel quel par la version web et par la WebView Android.
 *
 * Toute modification ici doit être reportée côté Kotlin, et l'inverse. Les deux
 * plateformes doivent produire le même JSON, sinon les deux versions divergent.
 *
 * Conventions :
 * - Une date est une chaîne `AAAA-MM-JJ`. Un mois est une chaîne `AAAA-MM`.
 * - Une heure relative est exprimée en heures décimales autour de minuit : `-1.5` vaut
 *   22h30, `2.25` vaut 2h15. Sans cette convention, l'axe se coupe à minuit.
 * - Une valeur absente vaut `null`, jamais zéro. Un zéro se dessine ; un trou ne se
 *   dessine pas.
 *
 * ATTENTION : ce modèle porte des mesures quotidiennes. Il ne doit jamais partir vers un
 * fournisseur de LLM. La seule porte de sortie reste `lib/prompt.js`, qui n'envoie que
 * des agrégats hebdomadaires.
 *
 * Conventions que les deux implémentations doivent partager, sous peine de montrer des
 * chiffres différents pour les mêmes données. Le test de parité les vérifie.
 *
 * - **Étiquette horaire** : l'heure en clair, sans zéro devant et sans suffixe, `"0"` à
 *   `"23"`. Le moteur la convertit en nombre ; un `"00h"` donnerait `NaN`.
 * - **Jour de semaine** : `"lun."` à `"dim."`, dans cet ordre.
 * - **Stades de sommeil** : le dénominateur est la somme des quatre stades du mois, pas
 *   la durée de sommeil. Les quatre parts totalisent donc exactement 100.
 * - **Pas et étages par mois** : des moyennes quotidiennes, pas des totaux.
 * - **Exercice par mois** : des totaux du mois, pas des moyennes par séance.
 * - **Écart-type** : divisé par `n`, jamais par `n - 1`. Le rapport décrit un ensemble
 *   observé, il n'estime pas une population.
 * - **Médiane** : sur un effectif pair, la moyenne des deux valeurs centrales.
 * - **Heures relatives** : calculées à partir de la seconde du jour. Les millisecondes
 *   sont écartées : elles n'ont aucun sens sur une heure de coucher et ne servent qu'à
 *   faire diverger deux calculs. Ne pas arrondir à la minute non plus.
 * - **Codes Samsung non documentés** (ECG, apnée) : jamais traduits en libellé clinique.
 *   La forme est `"Résultat <code> — à lire dans Samsung Health Monitor"`.
 * - **Tuiles** : `value` et `sub` sont les seuls champs déjà formatés. Typographie
 *   française — virgule décimale, espace fine insaisissable pour les milliers, `0h44`
 *   pour une heure, `5 h 48` pour une durée, `23 juin 2025` pour une date. `value` ne
 *   répète jamais l'unité : elle va dans `unit` — `"54"` avec `"bpm"`, `"131/82"` avec
 *   `"mmHg"`. `unit` ne vaut `null` que si la valeur porte déjà sa notation : une durée
 *   `5 h 48`, une heure `0h44`, un indice sans unité comme l'IMC.
 *
 * @typedef {{date: string, value: number|null}} DayValue
 * @typedef {{month: string, value: number|null}} MonthValue
 * @typedef {{label: string, value: number|null, count?: number|null}} LabelValue
 * @typedef {{label: string, r: number, n: number}} CorrelationItem
 *
 * @typedef {{key: string, label: string, value: string, unit?: string|null,
 *            sub: string, status: 'good'|'warn'|'serious'|'critical'|'neutral'}} ReportTile
 *
 * @typedef {{generatedAt: string, from: string|null, to: string|null, days: number,
 *            nights: number, heartRateSamples: number, hrvWindows: number,
 *            activeDays: number, timeZone: string, periodLabel: string,
 *            profile: {heightCm: number|null, weightKg: number|null}|null}} ReportMeta
 *
 * @typedef {{date: string, hours: number, score: number|null, bedRel: number|null,
 *            wakeRel: number|null, efficiencyPercent: number|null, sessions: number}} SleepNightPoint
 * @typedef {{month: string, meanHours: number, medianHours: number,
 *            meanScore: number|null, nights: number}} SleepMonthStat
 * @typedef {{month: string, deep: number, light: number, rem: number, awake: number}} SleepStageMonth
 * @typedef {{nightly: SleepNightPoint[], monthly: SleepMonthStat[], dayOfWeek: LabelValue[],
 *            distribution: LabelValue[], stagesMonthly: SleepStageMonth[], kpi: object}} SleepSection
 *
 * @typedef {{month: string, resting: number|null, average: number|null}} HeartMonth
 * @typedef {{date: string, systolic: number, diastolic: number, pulse: number|null}} BloodPressurePoint
 * @typedef {{date: string, meanHeartRate: number|null, classification: number|null,
 *            classificationLabel: string}} EcgPoint
 *
 * @typedef {{month: string, sessions: number, minutes: number, calories: number|null}} ExerciseMonth
 * @typedef {{date: string, weightKg: number, bodyFatPercent: number|null,
 *            skeletalMuscleKg: number|null, bodyMassIndex: number|null,
 *            basalMetabolicRate: number|null}} BodyPoint
 * @typedef {{month: string, mean: number, percentAbove60: number}} StressMonth
 * @typedef {{month: string, mean: number, min: number}} Spo2Month
 *
 * @typedef {{headline: string, verdict: string,
 *            sections: Object<string, {verdict: string, points: string[]}>,
 *            plan: {title: string, body: string}[]}} ReportNarrative
 */

(function (global) {
  'use strict';

  /** Nombre minimal de jours appariés en dessous duquel une corrélation ne veut rien dire. */
  const MIN_CORRELATION_PAIRS = 30;

  /** Au-delà de ce coefficient, l'association mérite d'être signalée. */
  const STRONG_CORRELATION = 0.3;

  /** Les six sections du rapport, dans l'ordre où elles sont montrées. */
  const SECTION_KEYS = ['sleep', 'heart', 'activity', 'body', 'stress', 'breathing'];

  /** Les états admis pour la pastille d'une tuile. Toute autre valeur retombe sur `neutral`. */
  const TILE_STATUS = ['good', 'warn', 'serious', 'critical', 'neutral'];

  /**
   * Crée un modèle vide et valide.
   *
   * Le moteur de dessin sait montrer un rapport vide : il enlève les sections sans
   * données au lieu de dessiner des axes sans courbe.
   *
   * @returns {object} un modèle de rapport sans aucune mesure
   */
  function emptyReportModel() {
    return {
      meta: {
        generatedAt: new Date().toISOString(),
        from: null,
        to: null,
        days: 0,
        nights: 0,
        heartRateSamples: 0,
        hrvWindows: 0,
        activeDays: 0,
        timeZone: 'UTC',
        periodLabel: '',
        profile: null,
      },
      tiles: [],
      sleep: {
        nightly: [], monthly: [], dayOfWeek: [], distribution: [], stagesMonthly: [],
        kpi: { nights: 0, targetHours: 7.5, snoringNights: 0, snoringMeasuredNights: 0 },
      },
      heart: {
        monthly: [], restingDaily: [], hourly: [], hrvMonthly: [], hrvDaily: [],
        bloodPressure: [], ecg: [], kpi: { measuredDays: 0 },
      },
      activity: {
        stepsDaily: [], stepsRolling7: [], stepsMonthly: [], stepsDayOfWeek: [],
        exerciseMonthly: [], exerciseByKind: [], floorsMonthly: [],
        kpi: { exerciseSessions: 0, measuredDays: 0 },
      },
      body: { daily: [], kpi: { measures: 0 } },
      stress: {
        monthly: [], hourly: [], dayOfWeek: [], daily: [], vitalityDaily: [],
        kpi: { alerts: 0, measuredDays: 0 },
      },
      breathing: {
        spo2Monthly: [], spo2Daily: [], respiratoryDaily: [], skinTempDaily: [],
        kpi: { spo2Measures: 0, spo2Under90: 0 },
      },
      correlations: [],
      narrative: null,
    };
  }

  /**
   * Vérifie qu'un objet respecte le contrat, et renvoie la liste des manques.
   *
   * Cette fonction sert aux tests et au chargement d'un rapport exporté. Elle ne corrige
   * rien : elle nomme ce qui manque pour que l'erreur soit lisible.
   *
   * @param {object} model le modèle à vérifier
   * @returns {string[]} les problèmes trouvés, vide si le modèle est conforme
   */
  function validateReportModel(model) {
    const problems = [];
    if (!model || typeof model !== 'object') {
      return ['le modèle est absent ou n\'est pas un objet'];
    }
    if (!model.meta || typeof model.meta.generatedAt !== 'string') {
      problems.push('meta.generatedAt manque');
    }
    if (!Array.isArray(model.tiles)) {
      problems.push('tiles n\'est pas un tableau');
    }
    for (const key of SECTION_KEYS) {
      if (!model[key] || typeof model[key] !== 'object') {
        problems.push(`la section ${key} manque`);
      }
    }
    if (!Array.isArray(model.correlations)) {
      problems.push('correlations n\'est pas un tableau');
    }
    for (const tile of model.tiles || []) {
      if (!TILE_STATUS.includes(tile.status)) {
        problems.push(`la tuile ${tile.key} porte un état inconnu : ${tile.status}`);
      }
    }
    return problems;
  }

  global.HA = global.HA || {};
  global.HA.reportModel = {
    MIN_CORRELATION_PAIRS,
    STRONG_CORRELATION,
    SECTION_KEYS,
    TILE_STATUS,
    emptyReportModel,
    validateReportModel,
  };
}(typeof self !== 'undefined' ? self : this));
