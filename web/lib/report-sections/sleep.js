/**
 * Construit la section sommeil du rapport (`SleepSection` / `SleepKpi` côté Kotlin).
 *
 * Hypothèse de forme des données brutes (magasin IndexedDB `sleepNights`) : une ligne
 * par SESSION, au format `SleepMapper` (voir `web/lib/mappers.js`). Cette fonction
 * fusionne d'abord les sessions fractionnées par nuit de réveil via
 * `HA.aggregate.aggregateSleepNights`, exactement comme `buildDailySnapshots`, pour
 * que les deux chemins de calcul restent d'accord sur la durée d'une nuit.
 *
 * L'heure locale du coucher vient de deux sources possibles, selon l'origine de la
 * nuit : `localBedTime` (chaîne `HH:MM:SS`, nuits venues de Health Connect côté
 * Android) ou `localBedSecondOfDay` (nombre, nuits venues de l'export Samsung via
 * `SleepMapper`). Quand aucune des deux n'est présente, l'heure locale est déduite de
 * `bedTime` (absolu) avec le décalage par défaut de la période (`defaultOffsetMinutes`,
 * voir `report-builder.js`), puis en dernier recours le fuseau du navigateur.
 *
 * `snoring` (forme `SnoringMapper`) : `{id, start, end, durationMinutes, offsetMinutes}`.
 * `sleepApnea` (forme `SleepApneaMapper`) : `{id, time, result, offsetMinutes}`. Voir
 * `mappers-clinical.js`.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  const STAGE_TARGET_FIELDS = { deep: 'deepMinutes', light: 'lightMinutes', rem: 'remMinutes', awake: 'awakeMinutes' };

  /** Nom de stade du modèle de domaine vers la clé du modèle de rapport. */
  const STAGE_KEYS = { DEEP: 'deep', LIGHT: 'light', REM: 'rem', AWAKE: 'awake' };
  // Libellés et bornes figés côté Kotlin (alignement de parité) : 5 tranches, la
  // dernière couvrant [8, +∞) même si son libellé affiché est « > 8 h ».
  const DEFAULT_DISTRIBUTION_BUCKETS = [
    { label: '< 5 h', max: 5 },
    { label: '5-6 h', min: 5, max: 6 },
    { label: '6-7 h', min: 6, max: 7 },
    { label: '7-8 h', min: 7, max: 8 },
    { label: '> 8 h', min: 8 },
  ];

  /**
   * Résout le décalage horaire à appliquer à `bedTime` ET `wakeTime`.
   *
   * Priorité au décalage exact de la nuit (`offsetMinutes`, porté par `SleepMapper`
   * sur un export Samsung) : c'est un nombre entier de minutes, appliqué directement à
   * l'horodatage absolu, donc sans perte de précision. La reconstruction depuis
   * `localBedTime`/`localBedSecondOfDay` (chaîne ou secondes déjà arrondies à la
   * seconde) n'est qu'un repli pour les nuits qui n'ont pas leur propre décalage —
   * elle peut introduire une erreur de quelques secondes, inévitable puisque
   * l'information exacte n'existe pas.
   */
  function resolveNightOffsetMinutes(night, defaultOffsetMinutes) {
    if (Number.isFinite(night.offsetMinutes)) return night.offsetMinutes;
    const explicit = util().parseHmsToSeconds(night.localBedTime);
    if (explicit !== null) return util().deriveOffsetFromLocalSeconds(night.bedTime, explicit);
    if (night.localBedSecondOfDay !== null && night.localBedSecondOfDay !== undefined) {
      return util().deriveOffsetFromLocalSeconds(night.bedTime, night.localBedSecondOfDay);
    }
    return defaultOffsetMinutes;
  }

  function buildNightPoint(night, defaultOffsetMinutes) {
    const offsetMinutes = resolveNightOffsetMinutes(night, defaultOffsetMinutes);
    const bedRel = util().relativeHoursOf(util().localSecondOfDayAt(night.bedTime, offsetMinutes));
    const wakeRel = night.wakeTime !== null && night.wakeTime !== undefined
      ? util().relativeHoursOf(util().localSecondOfDayAt(night.wakeTime, offsetMinutes))
      : null;
    return {
      date: night.date,
      hours: night.durationMinutes / 60,
      score: night.score,
      bedRel,
      wakeRel,
      efficiencyPercent: night.efficiencyPercent,
      sessions: night.sessions,
    };
  }

  function buildMonthly(nights) {
    const byMonth = {};
    nights.forEach((n) => { (byMonth[n.date.slice(0, 7)] = byMonth[n.date.slice(0, 7)] || []).push(n); });
    return Object.keys(byMonth).sort().map((month) => {
      const group = byMonth[month];
      const hours = group.map((n) => n.durationMinutes / 60);
      const scores = group.map((n) => n.score).filter((v) => v !== null && v !== undefined);
      return {
        month,
        meanHours: HA.stats.mean(hours),
        medianHours: HA.stats.median(hours),
        meanScore: scores.length > 0 ? HA.stats.mean(scores) : null,
        nights: group.length,
      };
    });
  }

  /**
   * Minutes de chaque stade, par jour de réveil.
   *
   * Les segments détaillés priment sur les champs de la nuit, parce qu'eux seuls portent
   * le sommeil profond et l'éveil : le fichier de nuits Samsung ne donne que
   * `total_rem_duration` et `total_light_duration`. Sans eux, la répartition ramenait le
   * léger et le paradoxal à 100 % et montrait de fausses proportions.
   *
   * Un segment porte l'identifiant de la SESSION d'origine, pas celui de la nuit
   * fusionnée : la table se construit donc sur les sessions brutes, sinon les segments des
   * nuits fractionnées seraient tous ignorés.
   */
  function stageMinutesByNight(rawSleepNights, segments) {
    const dateBySleepId = {};
    (rawSleepNights || []).forEach((n) => { if (n && n.id) dateBySleepId[n.id] = n.date; });
    const byDate = {};
    (segments || []).forEach((seg) => {
      const date = dateBySleepId[seg.sleepId];
      const stage = STAGE_KEYS[seg.stage];
      if (!date || !stage) return;
      const current = byDate[date] || { deep: 0, light: 0, rem: 0, awake: 0 };
      current[stage] += seg.durationMinutes || 0;
      byDate[date] = current;
    });
    return byDate;
  }

  function buildStagesMonthly(nights, rawSleepNights, segments) {
    const measured = stageMinutesByNight(rawSleepNights, segments);
    const byMonth = {};
    nights.forEach((n) => { (byMonth[n.date.slice(0, 7)] = byMonth[n.date.slice(0, 7)] || []).push(n); });
    return Object.keys(byMonth).sort().map((month) => {
      const group = byMonth[month];
      const sums = { deep: 0, light: 0, rem: 0, awake: 0 };
      group.forEach((n) => {
        const fromSegments = measured[n.date];
        if (fromSegments && (fromSegments.deep + fromSegments.light + fromSegments.rem + fromSegments.awake) > 0) {
          sums.deep += fromSegments.deep; sums.light += fromSegments.light;
          sums.rem += fromSegments.rem; sums.awake += fromSegments.awake;
          return;
        }
        // Repli : nuits venues de Health Connect, ou export sans segments.
        Object.keys(STAGE_TARGET_FIELDS).forEach((stage) => {
          const v = n[STAGE_TARGET_FIELDS[stage]];
          if (v !== null && v !== undefined) sums[stage] += v;
        });
      });
      const total = sums.deep + sums.light + sums.rem + sums.awake;
      if (total <= 0) return null;
      return {
        month,
        deep: (sums.deep / total) * 100,
        light: (sums.light / total) * 100,
        rem: (sums.rem / total) * 100,
        awake: (sums.awake / total) * 100,
      };
    }).filter((m) => m !== null);
  }

  /** La nuit (sa date) dont la fenêtre [bedTime, wakeTime] contient l'instant donné, sinon `null`. */
  function nightDateContaining(epochMs, nights) {
    for (const n of nights) {
      if (epochMs >= n.bedTime && epochMs <= n.wakeTime) return n.date;
    }
    return null;
  }

  /**
   * `snoringNights` compte les nuits DISTINCTES portant au moins un épisode de
   * ronflement (durée > 0), `snoringMeasuredNights` compte toutes les nuits de la
   * période. Un épisode est rattaché à la nuit dont il tombe dans la fenêtre
   * [coucher, réveil] — pas à la date calendaire de son `start`, qui peut tomber la
   * veille du jour de réveil. Un épisode hors de toute nuit mesurée (le jeu d'essai
   * omet volontairement une nuit sur onze) ne compte pas : le numérateur ne doit
   * jamais dépasser le dénominateur, sinon on peut afficher « 30 nuits sur 26 ».
   */
  function buildSnoringCounts(snoring, nights) {
    const nightsWithSnoring = new Set();
    (snoring || []).forEach((s) => {
      if (s.durationMinutes > 0) {
        const date = nightDateContaining(s.start, nights);
        if (date !== null) nightsWithSnoring.add(date);
      }
    });
    return { snoringNights: nightsWithSnoring.size, snoringMeasuredNights: nights.length };
  }

  function buildKpi(nights, nightly, targetHours, snoring, sleepApnea) {
    if (nights.length === 0) {
      return {
        nights: 0, meanHours: null, medianHours: null, bedMedian: null, wakeMedian: null,
        bedSpreadHours: null, pctAfterMidnight: null, pctAfter2h: null, pctUnder6h: null,
        pctOver7h: null, weekendCatchupHours: null, debtHours: null, targetHours,
        efficiencyPercent: null, latencyMinutes: null, snoringNights: 0,
        snoringMeasuredNights: 0, snoringMedianMinutes: null, apneaResult: null,
      };
    }
    const hours = nightly.map((n) => n.hours);
    const bedRelValues = nightly.map((n) => n.bedRel).filter((v) => v !== null && v !== undefined);
    const wakeRelValues = nightly.map((n) => n.wakeRel).filter((v) => v !== null && v !== undefined);
    const effValues = nights.map((n) => n.efficiencyPercent).filter((v) => v !== null && v !== undefined);
    const latValues = nights.map((n) => n.latencyMinutes).filter((v) => v !== null && v !== undefined);
    const targetMinutes = targetHours * 60;
    const snoringCounts = buildSnoringCounts(snoring, nights);

    const byWeekday = HA.stats.byDayOfWeek(nightly.map((n) => ({ date: n.date, value: n.hours })));
    // lun.=0 ... dim.=6 (voir HA.stats.WEEKDAY_LABELS) : week-end = sam. (5) et dim. (6).
    const weekdayMean = HA.stats.mean(byWeekday.slice(0, 5).filter((d) => d.value !== null).map((d) => d.value));
    const weekendMean = HA.stats.mean(byWeekday.slice(5).filter((d) => d.value !== null).map((d) => d.value));

    return {
      nights: nights.length,
      meanHours: HA.stats.mean(hours),
      medianHours: HA.stats.median(hours),
      bedMedian: bedRelValues.length > 0 ? util().relativeOfClockHours(HA.stats.circularMedian(bedRelValues)) : null,
      wakeMedian: wakeRelValues.length > 0 ? util().relativeOfClockHours(HA.stats.circularMedian(wakeRelValues)) : null,
      bedSpreadHours: bedRelValues.length > 0 ? HA.stats.circularStdDev(bedRelValues) : null,
      pctAfterMidnight: bedRelValues.length > 0 ? (bedRelValues.filter((v) => v > 0).length / bedRelValues.length) * 100 : null,
      pctAfter2h: bedRelValues.length > 0 ? (bedRelValues.filter((v) => v > 2).length / bedRelValues.length) * 100 : null,
      pctUnder6h: (hours.filter((h) => h < 6).length / hours.length) * 100,
      pctOver7h: (hours.filter((h) => h >= 7).length / hours.length) * 100,
      weekendCatchupHours: weekdayMean !== null && weekendMean !== null ? weekendMean - weekdayMean : null,
      debtHours: nights.reduce((sum, n) => sum + Math.max(targetMinutes - n.durationMinutes, 0), 0) / 60,
      targetHours,
      efficiencyPercent: effValues.length > 0 ? HA.stats.mean(effValues) : null,
      latencyMinutes: latValues.length > 0 ? HA.stats.mean(latValues) : null,
      snoringNights: snoringCounts.snoringNights,
      snoringMeasuredNights: snoringCounts.snoringMeasuredNights,
      snoringMedianMinutes: HA.stats.median((snoring || []).map((s) => s.durationMinutes).filter((v) => v > 0)),
      apneaResult: buildApneaResult(sleepApnea),
    };
  }

  // `sleepApnea` (forme `SleepApneaMapper`, voir `mappers-clinical.js`) n'a pas de champ
  // `date` : {id, time, result, averageBreathingDisturbance, offsetMinutes}.
  //
  // Le code `result` de Samsung n'est documenté nulle part de façon fiable : on ne le
  // traduit jamais en mot clinique (« apnée détectée »...), comme pour `classificationLabel`
  // côté ECG (voir `heart.js`). Renvoyer vers Samsung Health Monitor pour le libellé officiel.
  function buildApneaResult(sleepApnea) {
    if (!sleepApnea || sleepApnea.length === 0) return null;
    const last = sleepApnea.slice().sort((a, b) => a.time - b.time).pop();
    if (!last || last.result === null || last.result === undefined) return 'Résultat non enregistré';
    return `Résultat ${last.result} — à lire dans Samsung Health Monitor`;
  }

  /**
   * @param {object[]} rawSleepNights lignes brutes `sleepNights`, une par session
   * @param {object[]} snoring lignes brutes `snoring`
   * @param {object[]} sleepApnea lignes brutes `sleepApnea`
   * @param {{sleepTargetHours?: number}} options
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildSleepSection(rawSleepNights, snoring, sleepApnea, options, defaultOffsetMinutes, sleepStages) {
    const targetHours = (options && options.sleepTargetHours) || 7.5;
    const nights = HA.aggregate.aggregateSleepNights(rawSleepNights || [])
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
    const nightly = nights.map((n) => buildNightPoint(n, defaultOffsetMinutes));

    return {
      nightly,
      monthly: buildMonthly(nights),
      dayOfWeek: HA.stats.byDayOfWeek(nightly.map((n) => ({ date: n.date, value: n.hours }))),
      distribution: HA.stats.distribution(nightly.map((n) => n.hours), DEFAULT_DISTRIBUTION_BUCKETS),
      stagesMonthly: buildStagesMonthly(nights, rawSleepNights, sleepStages),
      kpi: buildKpi(nights, nightly, targetHours, snoring, sleepApnea),
    };
  }

  root.HA.reportSections.buildSleepSection = buildSleepSection;
})();
