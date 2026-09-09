/**
 * Construit la vue consolidée d'une période à partir des mesures brutes.
 * Portage fidèle de `HealthAggregator.kt` + `HealthSnapshot.kt`.
 *
 * Toutes les fonctions sont pures : mêmes entrées, mêmes sorties, aucun accès
 * au DOM ni au réseau. C'est ce qui les rend vérifiables par `test.html`.
 *
 * Note d'implémentation web : Kotlin regroupe fréquence cardiaque, stress, HRV,
 * SpO2 et poids par jour via un `ZoneId` unique choisi par l'app (le fuseau de
 * l'appareil). Cette version web regroupe désormais chaque mesure avec le
 * décalage `offsetMinutes` porté par la mesure elle-même (colonne Samsung
 * `time_offset`), pour rester fidèle au fuseau d'origine de la mesure plutôt
 * qu'au fuseau du navigateur qui l'affiche. Une mesure ancienne sans
 * `offsetMinutes` retombe sur le fuseau du navigateur. Le sommeil, les pas et
 * le score d'énergie gardent, eux, la date déjà calculée par leur propre
 * mappeur (décalage porté par la ligne CSV).
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const DEFAULT_SLEEP_TARGET_MINUTES = 450; // 7 h 30
  const MIN_SAMPLES_FOR_RESTING = 20;
  const RESTING_PERCENTILE = 0.05;
  const TREND_WINDOW_DAYS = 14;
  const NOISE_BAND_PERCENT = 5.0;
  const SECONDS_PER_DAY = 86400;

  /** Les stades de sommeil sommés lors de la fusion des nuits fractionnées. */
  const SLEEP_STAGE_FIELDS = ['remMinutes', 'lightMinutes', 'deepMinutes', 'awakeMinutes'];

  /** Les mesures que l'app suit dans le temps. */
  const HEALTH_METRICS = [
    { key: 'STEPS', label: 'Pas', unit: 'pas/jour', lowerIsBetter: false, valueOf: (d) => d.steps },
    { key: 'ACTIVE_MINUTES', label: 'Temps actif', unit: 'min/jour', lowerIsBetter: false, valueOf: (d) => d.activeMinutes },
    { key: 'SLEEP_DURATION', label: 'Durée de sommeil', unit: 'min/nuit', lowerIsBetter: false, valueOf: (d) => d.sleepMinutes },
    { key: 'SLEEP_SCORE', label: 'Score de sommeil', unit: '/100', lowerIsBetter: false, valueOf: (d) => d.sleepScore },
    { key: 'RESTING_HEART_RATE', label: 'Fréquence cardiaque au repos', unit: 'bpm', lowerIsBetter: true, valueOf: (d) => d.restingHeartRate },
    { key: 'HRV', label: 'Variabilité cardiaque', unit: 'ms', lowerIsBetter: false, valueOf: (d) => d.hrvRmssd },
    { key: 'STRESS', label: 'Stress', unit: '/100', lowerIsBetter: true, valueOf: (d) => d.averageStress },
    { key: 'WEIGHT', label: 'Poids', unit: 'kg', lowerIsBetter: true, valueOf: (d) => d.weightKg },
    { key: 'ENERGY_SCORE', label: "Score d'énergie", unit: '/100', lowerIsBetter: false, valueOf: (d) => d.energyScore },
    { key: 'SPO2', label: 'Saturation en oxygène', unit: '%', lowerIsBetter: false, valueOf: (d) => d.spO2 },
  ];

  function averageOrNull(values) {
    if (values.length === 0) return null;
    return values.reduce((a, b) => a + b, 0) / values.length;
  }

  function parseDateKeyToUtcMs(key) {
    const [y, m, d] = key.split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  }

  function daysOf(fromKey, toKey) {
    const fromMs = parseDateKeyToUtcMs(fromKey);
    const toMs = parseDateKeyToUtcMs(toKey);
    const days = [];
    for (let ms = fromMs; ms <= toMs; ms += 86400000) {
      const dt = new Date(ms);
      days.push(HA.csv.dateKey(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate()));
    }
    return days;
  }

  /**
   * Résout le décalage horaire à utiliser, du plus précis au moins précis : celui de
   * la mesure elle-même (`offsetMinutes`), sinon le décalage par défaut de la période
   * (`defaultOffsetMinutes`, typiquement `options.zoneOffsetMinutes` d'un rapport —
   * utile pour les mesures qui n'en portent pas, comme certaines nuits Health
   * Connect), sinon `undefined` — auquel cas l'appelant retombe sur le fuseau du
   * navigateur plutôt que de planter.
   */
  function resolveOffsetMinutes(offsetMinutes, defaultOffsetMinutes) {
    if (Number.isFinite(offsetMinutes)) return offsetMinutes;
    if (Number.isFinite(defaultOffsetMinutes)) return defaultOffsetMinutes;
    return undefined;
  }

  /**
   * Regroupe un instant absolu (epoch ms) dans son jour local.
   *
   * Décalage résolu par [resolveOffsetMinutes] : celui de la mesure, sinon
   * `defaultOffsetMinutes`, sinon repli sur le fuseau du navigateur.
   */
  function localDateKeyOf(epochMs, offsetMinutes, defaultOffsetMinutes) {
    const resolved = resolveOffsetMinutes(offsetMinutes, defaultOffsetMinutes);
    if (resolved === undefined) {
      const d = new Date(epochMs);
      return HA.csv.dateKey(d.getFullYear(), d.getMonth() + 1, d.getDate());
    }
    const shifted = new Date(epochMs + resolved * 60000);
    return HA.csv.dateKey(shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate());
  }

  function groupBy(items, keyOf) {
    const map = {};
    items.forEach((item) => {
      const key = keyOf(item);
      if (key === null || key === undefined) return;
      (map[key] = map[key] || []).push(item);
    });
    return map;
  }

  function associateLastByDate(items) {
    const map = {};
    items.forEach((item) => { map[item.date] = item; });
    return map;
  }

  /** Somme les valeurs définies d'un champ ; rend `null` si aucune session ne le porte. */
  function sumDefined(sessions, field) {
    const defined = sessions.map((s) => s[field]).filter((v) => v !== null && v !== undefined);
    if (defined.length === 0) return null;
    return defined.reduce((a, b) => a + b, 0);
  }

  /**
   * Fusionne les sessions de sommeil fractionnées en une nuit par jour de réveil.
   *
   * Samsung écrit parfois plusieurs sessions pour une même nuit (réveil détecté au
   * milieu de la nuit, reprise du suivi ensuite). `SleepMapper` rattache déjà chaque
   * session au jour du réveil : il suffit donc de regrouper par cette date. Traiter
   * une session comme une nuit sous-compte le sommeil d'environ 1h10 par nuit sur les
   * nuits fractionnées (mesuré sur un export réel : 56 nuits sur 294).
   */
  function aggregateSleepNights(rawNights) {
    const byDate = groupBy(rawNights, (n) => n.date);
    return Object.keys(byDate).map((date) => {
      const sessions = byDate[date].slice().sort((a, b) => a.bedTime - b.bedTime);
      const first = sessions[0];
      const last = sessions[sessions.length - 1];
      const longest = sessions.reduce((best, s) => (s.durationMinutes > best.durationMinutes ? s : best), first);

      const merged = {
        id: first.id,
        date,
        bedTime: first.bedTime,
        wakeTime: last.wakeTime,
        durationMinutes: sessions.reduce((sum, s) => sum + s.durationMinutes, 0),
        score: longest.score,
        efficiencyPercent: longest.efficiencyPercent,
        latencyMinutes: longest.latencyMinutes,
        physicalRecovery: longest.physicalRecovery,
        mentalRecovery: longest.mentalRecovery,
        localBedSecondOfDay: first.localBedSecondOfDay,
        localBedTime: first.localBedTime,
        offsetMinutes: first.offsetMinutes,
        sessions: sessions.length,
      };
      SLEEP_STAGE_FIELDS.forEach((field) => { merged[field] = sumDefined(sessions, field); });
      return merged;
    });
  }

  /**
   * Estime la fréquence cardiaque au repos par le 5e centile des mesures du jour.
   *
   * La montre ne marque pas les mesures « au repos ». Le bas de la distribution
   * journalière en est le meilleur proxy. En dessous de [MIN_SAMPLES_FOR_RESTING]
   * mesures, la journée n'est pas assez couverte pour que l'estimation ait un sens.
   */
  function restingHeartRate(beats) {
    if (beats.length < MIN_SAMPLES_FOR_RESTING) return null;
    const sorted = beats.slice().sort((a, b) => a - b);
    const index = Math.min(
      Math.max(Math.trunc((sorted.length - 1) * RESTING_PERCENTILE), 0),
      sorted.length - 1
    );
    return sorted[index];
  }

  /**
   * Reporte la dernière pesée connue sur les jours sans mesure.
   *
   * L'utilisateur ne se pèse pas tous les jours, mais son poids de la veille reste
   * la meilleure estimation du jour. Les jours antérieurs à la première pesée
   * restent vides : rien ne permet de les remplir.
   */
  function lastKnownWeightPerDay(measurements, fromKey, toKey) {
    if (measurements.length === 0) return {};
    const sorted = measurements.slice().sort((a, b) => a.time - b.time);
    const byDay = {};
    sorted.forEach((m) => { byDay[localDateKeyOf(m.time, m.offsetMinutes)] = m.weightKg; });

    const result = {};
    let carried = null;
    daysOf(fromKey, toKey).forEach((date) => {
      if (byDay[date] !== undefined) carried = byDay[date];
      if (carried !== null) result[date] = carried;
    });
    return result;
  }

  function directionOf(changePercent) {
    if (changePercent === null) return 'STABLE';
    if (changePercent > NOISE_BAND_PERCENT) return 'UP';
    if (changePercent < -NOISE_BAND_PERCENT) return 'DOWN';
    return 'STABLE';
  }

  function isImprovementOf(metric, direction) {
    if (direction === 'STABLE') return null;
    if (direction === 'UP') return !metric.lowerIsBetter;
    return metric.lowerIsBetter;
  }

  /** Compare deux fenêtres consécutives de même longueur (14 jours maximum). */
  function buildTrends(days) {
    const windowSize = Math.max(Math.min(Math.floor(days.length / 2), TREND_WINDOW_DAYS), 1);
    const recent = days.slice(days.length - windowSize);
    const withoutRecent = days.slice(0, days.length - windowSize);
    const previous = withoutRecent.slice(Math.max(0, withoutRecent.length - windowSize));

    return HEALTH_METRICS.map((metric) => {
      const recentValues = recent.map(metric.valueOf).filter((v) => v !== null && v !== undefined);
      const recentAverage = averageOrNull(recentValues);
      if (recentAverage === null) return null;

      const previousValues = previous.map(metric.valueOf).filter((v) => v !== null && v !== undefined);
      const previousAverage = averageOrNull(previousValues);
      const change = previousAverage !== null && Math.abs(previousAverage) > 0
        ? ((recentAverage - previousAverage) / Math.abs(previousAverage)) * 100
        : null;
      const direction = directionOf(change);

      return {
        metric,
        recentAverage,
        previousAverage,
        direction,
        changePercent: change,
        isImprovement: isImprovementOf(metric, direction),
      };
    }).filter((t) => t !== null);
  }

  /**
   * Résume la régularité du sommeil.
   *
   * Les heures de coucher forment un cercle, pas une droite : 23 h et 1 h sont
   * distants de 2 heures. Le calcul passe donc par la moyenne circulaire de
   * `HA.stats` (voir `stats.js`), sinon une personne qui se couche autour de
   * minuit paraîtrait très irrégulière.
   */
  function buildSleepRegularity(days, targetMinutes) {
    const nights = days.filter((d) => d.sleepMinutes !== null && d.sleepMinutes !== undefined);
    if (nights.length === 0) return null;

    const bedtimes = nights
      .map((n) => n.bedTimeSecondOfDay)
      .filter((v) => v !== null && v !== undefined);
    const durations = nights.map((n) => n.sleepMinutes);
    const bedtimeHours = bedtimes.map((s) => s / 3600);

    const meanHours = HA.stats.circularMean(bedtimeHours);
    const spreadHours = HA.stats.circularStdDev(bedtimeHours);

    return {
      averageBedtimeSecondOfDay: meanHours !== null
        ? Math.round(meanHours * 3600) % SECONDS_PER_DAY
        : null,
      bedtimeSpreadHours: spreadHours,
      averageDurationMinutes: Math.round(averageOrNull(durations)),
      sleepDebtMinutes: durations.reduce((sum, d) => sum + Math.max(targetMinutes - d, 0), 0),
      targetMinutes,
      nightsMeasured: nights.length,
    };
  }

  function hasData(day) {
    return day.steps !== null || day.sleepMinutes !== null || day.restingHeartRate !== null ||
      day.averageStress !== null || day.hrvRmssd !== null || day.energyScore !== null ||
      day.activeMinutes !== null || day.spO2 !== null;
  }

  /**
   * Construit un `DailySnapshot` par jour de la période, à partir des mesures brutes.
   * C'est la seule fonction qui doit voir les mesures individuelles : son résultat,
   * un objet compact par jour, est ce qui est ensuite persisté dans IndexedDB.
   */
  function buildDailySnapshots(fromKey, toKey, inputs, sleepTargetMinutes) {
    const dailySteps = inputs.dailySteps || [];
    const dailyActivities = inputs.dailyActivities || [];
    const sleepNights = inputs.sleepNights || [];
    const heartRates = inputs.heartRates || [];
    const stress = inputs.stress || [];
    const hrv = inputs.hrv || [];
    const spO2 = inputs.spO2 || [];
    const bodyCompositions = inputs.bodyCompositions || [];
    const energyScores = inputs.energyScores || [];
    const targetMinutes = sleepTargetMinutes || DEFAULT_SLEEP_TARGET_MINUTES;

    const stepsByDay = associateLastByDate(dailySteps);
    const activityByDay = associateLastByDate(dailyActivities);
    const sleepByDay = associateLastByDate(aggregateSleepNights(sleepNights));
    const energyByDay = associateLastByDate(energyScores);
    const heartRateByDay = groupBy(heartRates, (h) => localDateKeyOf(h.time, h.offsetMinutes));
    const stressByDay = groupBy(stress, (s) => localDateKeyOf(s.start, s.offsetMinutes));
    const hrvByDay = groupBy(hrv, (h) => localDateKeyOf(h.time, h.offsetMinutes));
    const spO2ByDay = groupBy(spO2, (s) => localDateKeyOf(s.time, s.offsetMinutes));
    const weightByDay = lastKnownWeightPerDay(bodyCompositions, fromKey, toKey);

    return daysOf(fromKey, toKey).map((date) => {
      const beats = (heartRateByDay[date] || []).map((h) => h.beatsPerMinute);
      const night = sleepByDay[date];
      const stressScores = (stressByDay[date] || []).map((s) => s.score);
      const rmssdValues = (hrvByDay[date] || []).map((h) => h.rmssdMillis).filter((v) => v !== null);
      const spO2Values = (spO2ByDay[date] || []).map((s) => s.percent);
      const averageStress = averageOrNull(stressScores);
      const averageHeartRate = averageOrNull(beats);

      return {
        date,
        steps: (stepsByDay[date] && stepsByDay[date].steps) ?? (activityByDay[date] && activityByDay[date].steps) ?? null,
        activeMinutes: (activityByDay[date] && activityByDay[date].activeMinutes) ?? null,
        activeCalories: (activityByDay[date] && activityByDay[date].activeCalories) ?? null,
        restingHeartRate: restingHeartRate(beats),
        averageHeartRate: averageHeartRate !== null ? Math.round(averageHeartRate) : null,
        sleepMinutes: night ? night.durationMinutes : null,
        sleepScore: night ? night.score : null,
        bedTimeSecondOfDay: night ? night.localBedSecondOfDay : null,
        averageStress: averageStress !== null ? Math.round(averageStress) : null,
        hrvRmssd: averageOrNull(rmssdValues),
        spO2: averageOrNull(spO2Values),
        weightKg: weightByDay[date] !== undefined ? weightByDay[date] : null,
        energyScore: (energyByDay[date] && energyByDay[date].total) ?? null,
      };
    });
  }

  root.HA.aggregate = {
    HEALTH_METRICS,
    daysOf,
    localDateKeyOf,
    resolveOffsetMinutes,
    restingHeartRate,
    buildTrends,
    buildSleepRegularity,
    buildDailySnapshots,
    aggregateSleepNights,
    hasData,
    averageOrNull,
    DEFAULT_SLEEP_TARGET_MINUTES,
  };
})();
