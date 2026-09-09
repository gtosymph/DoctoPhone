/**
 * Assemble le `ReportModel` complet à partir des mesures brutes lues dans IndexedDB.
 *
 * Portage fidèle de `ReportBuilder.kt`. Le contrat de sortie est fixé par
 * `report-model.js` (et son miroir Kotlin `ReportModel.kt` / `ReportSections.kt`) :
 * les deux plateformes doivent produire le même JSON.
 *
 * ATTENTION CONFIDENTIALITÉ : ce modèle porte des mesures quotidiennes individuelles.
 * Il ne doit JAMAIS être envoyé à un fournisseur de LLM — `lib/prompt.js` reste la
 * seule porte de sortie, et il n'utilise pas ce module.
 *
 * `sources` : un objet portant, pour chaque magasin IndexedDB, le tableau des lignes
 * brutes. Les magasins sans mappeur existant (`bloodPressure`, `ecg`, `snoring`,
 * `respiratory`, `skinTemp`, `sleepApnea`, `stressAlerts`, `dailyFloors`) ont une
 * forme supposée, documentée dans le fichier de `report-sections/` qui les lit — à
 * confirmer avec l'agent qui construit le magasin IndexedDB correspondant.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  /**
   * Extrait la date locale `AAAA-MM-JJ` d'un enregistrement, pour filtrer par période.
   *
   * Les magasins cliniques (`bloodPressure`, `ecg`, `snoring`, `respiratory`,
   * `skinTemp`, `sleepApnea`, `stressAlerts`) sont ponctuels — `time` ou `start` en
   * epoch ms — comme `heartRate` ou `stress` : voir `mappers-clinical.js`. Aucun
   * n'est garanti de porter son propre `offsetMinutes` (le jeu d'essai de parité
   * n'en porte aucun) : chaque extracteur reçoit `defaultOffsetMinutes`, résolu une
   * fois par `buildReport` depuis `options.zoneOffsetMinutes`.
   */
  const DATE_OF = {
    sleepNights: (n) => n.date,
    sleepStages: (s, def) => (s.start !== null && s.start !== undefined ? HA.aggregate.localDateKeyOf(s.start, def) : null),
    heartRate: (h, def) => HA.aggregate.localDateKeyOf(h.time, HA.reportSections.util.resolveOffset(h, def)),
    stress: (s, def) => HA.aggregate.localDateKeyOf(s.start, HA.reportSections.util.resolveOffset(s, def)),
    stressAlerts: (s, def) => HA.aggregate.localDateKeyOf(s.start, HA.reportSections.util.resolveOffset(s, def)),
    hrv: (h, def) => HA.aggregate.localDateKeyOf(h.time, HA.reportSections.util.resolveOffset(h, def)),
    spo2: (s, def) => HA.aggregate.localDateKeyOf(s.time, HA.reportSections.util.resolveOffset(s, def)),
    exercise: (e, def) => HA.aggregate.localDateKeyOf(e.start, HA.reportSections.util.resolveOffset(e, def)),
    bodyComposition: (b, def) => HA.aggregate.localDateKeyOf(b.time, HA.reportSections.util.resolveOffset(b, def)),
    dailySteps: (d) => d.date,
    dailyActivity: (d) => d.date,
    energyScores: (d) => d.date,
    bloodPressure: (d, def) => HA.aggregate.localDateKeyOf(d.time, HA.reportSections.util.resolveOffset(d, def)),
    ecg: (d, def) => HA.aggregate.localDateKeyOf(d.time, HA.reportSections.util.resolveOffset(d, def)),
    snoring: (d, def) => HA.aggregate.localDateKeyOf(d.start, HA.reportSections.util.resolveOffset(d, def)),
    respiratory: (d, def) => HA.aggregate.localDateKeyOf(d.time, HA.reportSections.util.resolveOffset(d, def)),
    skinTemp: (d, def) => HA.aggregate.localDateKeyOf(d.time, HA.reportSections.util.resolveOffset(d, def)),
    sleepApnea: (d, def) => HA.aggregate.localDateKeyOf(d.time, HA.reportSections.util.resolveOffset(d, def)),
    dailyFloors: (d) => d.date,
  };

  const STORE_KEYS = Object.keys(DATE_OF);

  function detectRange(sources, defaultOffsetMinutes) {
    let min = null;
    let max = null;
    STORE_KEYS.forEach((key) => {
      const items = sources[key] || [];
      const dateOf = DATE_OF[key];
      items.forEach((item) => {
        const date = dateOf(item, defaultOffsetMinutes);
        if (date === null || date === undefined) return;
        if (min === null || date < min) min = date;
        if (max === null || date > max) max = date;
      });
    });
    return { from: min, to: max };
  }

  /** Recule une date `AAAA-MM-JJ` d'un jour. */
  function dayBefore(dateKey) {
    const d = new Date(dateKey + 'T12:00:00Z');
    d.setUTCDate(d.getUTCDate() - 1);
    return d.toISOString().slice(0, 10);
  }

  function filterSources(sources, from, to, defaultOffsetMinutes) {
    const util = HA.reportSections.util;
    const filtered = {};
    STORE_KEYS.forEach((key) => {
      // Les segments de stades appartiennent à leur nuit, pas à leur propre date : ceux de
      // la première nuit commencent la veille du début de période. Les filtrer au jour près
      // amputerait cette nuit. Le rattachement final se fait de toute façon par identifiant
      // de session, donc élargir d'un jour ne fait entrer aucun segment étranger.
      const start = key === 'sleepStages' ? dayBefore(from) : from;
      filtered[key] = util.withinPeriod(sources[key] || [], (item) => DATE_OF[key](item, defaultOffsetMinutes), start, to);
    });
    return filtered;
  }

  function buildMeta(sources, from, to, options) {
    const activeDates = new Set();
    (sources.dailySteps || []).forEach((d) => activeDates.add(d.date));
    (sources.dailyActivity || []).forEach((d) => activeDates.add(d.date));

    // `app-report.js` passe le fuseau sous la clé `zone` (voir sa signature de `renderReportInto`).
    const timeZone = (options && (options.zone || options.timeZone)) ||
      (typeof Intl !== 'undefined' && Intl.DateTimeFormat().resolvedOptions().timeZone) || 'UTC';

    const [fy, fm, fd] = (from || to || '1970-01-01').split('-').map(Number);
    const [ty, tm, td] = (to || from || '1970-01-01').split('-').map(Number);
    const days = from && to ? Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86400000) + 1 : 0;

    return {
      generatedAt: new Date().toISOString(),
      from: from || null,
      to: to || null,
      days,
      nights: 0, // complété par l'appelant après construction de la section sommeil (voir buildReport)
      heartRateSamples: (sources.heartRate || []).length,
      hrvWindows: (sources.hrv || []).length,
      activeDays: activeDates.size,
      timeZone,
      periodLabel: (options && options.periodLabel) || HA.reportSections.util.formatPeriodLabel(from, to),
      profile: (options && options.profile) || null,
    };
  }

  function dayValuesOf(nightly, field) {
    return nightly.map((n) => ({ date: n.date, value: n[field] }));
  }

  /** Les six corrélations du rapport, avec leur libellé exact et leur décalage. */
  function buildCorrelations(sleep, heart, activity, stress) {
    const sleepHours = dayValuesOf(sleep.nightly, 'hours');
    const sleepScore = dayValuesOf(sleep.nightly, 'score');
    const bedtime = dayValuesOf(sleep.nightly, 'bedRel');

    const specs = [
      { label: 'Durée de sommeil et FC de repos, le même jour', pairs: HA.correlation.paired(sleepHours, heart.restingDaily, 0) },
      { label: 'Durée de sommeil et variabilité cardiaque', pairs: HA.correlation.paired(sleepHours, heart.hrvDaily, 0) },
      { label: 'Pas de la veille vers la durée de sommeil', pairs: HA.correlation.paired(activity.stepsDaily, sleepHours, 1) },
      { label: 'Pas de la veille vers le score de sommeil', pairs: HA.correlation.paired(activity.stepsDaily, sleepScore, 1) },
      { label: 'Heure de coucher et durée de sommeil', pairs: HA.correlation.paired(bedtime, sleepHours, 0) },
      // Convention alignée sur Kotlin : le premier argument précède toujours le second de `lagDays` jours.
      { label: 'Durée de sommeil vers le stress du lendemain', pairs: HA.correlation.paired(sleepHours, stress.daily, 1) },
    ];

    return specs
      .map((spec) => ({ label: spec.label, r: HA.correlation.pearson(spec.pairs), n: spec.pairs.length }))
      .filter((item) => item.r !== null);
  }

  /**
   * @param {object} sources voir le contrat en tête de fichier
   * @param {{from?: string, to?: string, sleepTargetHours?: number, zone?: string,
   *          zoneOffsetMinutes?: number, periodLabel?: string,
   *          profile?: {heightCm: number|null, weightKg: number|null}}} [options]
   * @returns {object} un `ReportModel` conforme à `report-model.js`
   */
  function buildReport(sources, options) {
    const opts = options || {};
    // Décalage de repli pour toute mesure sans son propre `offsetMinutes` (voir
    // `HA.reportSections.util.resolveOffset`). `undefined` si absent : chaque
    // fonction retombe alors sur le fuseau du navigateur plutôt que de planter.
    const defaultOffsetMinutes = Number.isFinite(opts.zoneOffsetMinutes) ? opts.zoneOffsetMinutes : undefined;

    const detected = detectRange(sources, defaultOffsetMinutes);
    const from = opts.from || detected.from;
    const to = opts.to || detected.to;

    if (from === null || to === null) return HA.reportModel.emptyReportModel();

    const data = filterSources(sources, from, to, defaultOffsetMinutes);
    const sections = HA.reportSections;

    const sleep = sections.buildSleepSection(
      data.sleepNights, data.snoring, data.sleepApnea,
      { sleepTargetHours: opts.sleepTargetHours }, defaultOffsetMinutes, data.sleepStages
    );
    const heart = sections.buildHeartSection(data.heartRate, data.hrv, data.bloodPressure, data.ecg, defaultOffsetMinutes);
    const activity = sections.buildActivitySection(
      data.dailySteps, data.exercise, data.dailyFloors, data.dailyActivity, defaultOffsetMinutes
    );
    const body = sections.buildBodySection(data.bodyComposition, to, defaultOffsetMinutes);
    const stress = sections.buildStressSection(data.stress, data.stressAlerts, data.energyScores, defaultOffsetMinutes);
    const breathing = sections.buildBreathingSection(data.spo2, data.respiratory, data.skinTemp, defaultOffsetMinutes);

    const meta = buildMeta(data, from, to, opts);
    meta.nights = sleep.kpi.nights;

    const model = {
      meta,
      tiles: [],
      sleep,
      heart,
      activity,
      body,
      stress,
      breathing,
      correlations: buildCorrelations(sleep, heart, activity, stress),
      narrative: null,
    };
    model.tiles = sections.buildTiles(model);

    return model;
  }

  root.HA.reportBuilder = { buildReport };
})();
