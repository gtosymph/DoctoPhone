/**
 * Construit la section stress du rapport (`StressSection` / `StressKpi`).
 *
 * Hypothèses de forme des données brutes :
 * - `stress` : `{start, end, score, offsetMinutes}` (forme `StressMapper`).
 * - `stressAlerts` : un enregistrement par alerte ; seul le nombre compte ici.
 * - `energyScores` : `{date, total}` (forme `EnergyScoreMapper`) — la vitalité quotidienne.
 *
 * `percentAbove60` est calculé sur les mesures brutes (pas sur les moyennes
 * journalières) : c'est la proportion du temps mesuré passé au-dessus du seuil, pas
 * la proportion de jours dont la moyenne dépasse le seuil.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  const HIGH_STRESS_THRESHOLD = 60;

  function percentAbove(scores, threshold) {
    if (scores.length === 0) return null;
    return (scores.filter((s) => s > threshold).length / scores.length) * 100;
  }

  function buildMonthly(stress, defaultOffsetMinutes) {
    const byMonth = {};
    stress.forEach((s) => {
      const date = HA.aggregate.localDateKeyOf(s.start, util().resolveOffset(s, defaultOffsetMinutes));
      const month = date.slice(0, 7);
      (byMonth[month] = byMonth[month] || []).push(s.score);
    });
    return Object.keys(byMonth).sort().map((month) => ({
      month,
      mean: HA.stats.mean(byMonth[month]),
      percentAbove60: percentAbove(byMonth[month], HIGH_STRESS_THRESHOLD),
    }));
  }

  function buildPeakHour(hourly) {
    let peak = null;
    hourly.forEach((entry) => {
      if (entry.value === null) return;
      if (peak === null || entry.value > peak.value) peak = entry;
    });
    return peak ? Number(peak.label) : null;
  }

  /**
   * @param {object[]} stress
   * @param {object[]} stressAlerts
   * @param {object[]} energyScores
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildStressSection(stress, stressAlerts, energyScores, defaultOffsetMinutes) {
    const stressItems = stress || [];
    const byDay = util().groupByLocalDate(stressItems, (s) => s.start, defaultOffsetMinutes);
    const daily = util().toDayValues(byDay, (items) => HA.stats.mean(items.map((s) => s.score)));
    const hourly = HA.stats.byHour(stressItems.map((s) => ({
      hour: util().localHourOf(s.start, util().resolveOffset(s, defaultOffsetMinutes)),
      value: s.score,
    })));
    const vitalityByDate = util().groupByDate(energyScores || []);
    const vitalityDaily = util().toDayValues(vitalityByDate, (items) => items[0].total ?? null);

    const definedDaily = daily.map((d) => d.value).filter((v) => v !== null && v !== undefined);
    const allScores = stressItems.map((s) => s.score);
    const vitalityValues = vitalityDaily.map((d) => d.value).filter((v) => v !== null && v !== undefined);

    return {
      monthly: buildMonthly(stressItems, defaultOffsetMinutes),
      hourly,
      dayOfWeek: HA.stats.byDayOfWeek(daily),
      daily,
      vitalityDaily,
      kpi: {
        mean: definedDaily.length > 0 ? HA.stats.mean(definedDaily) : null,
        percentAbove60: percentAbove(allScores, HIGH_STRESS_THRESHOLD),
        alerts: (stressAlerts || []).length,
        peakHour: buildPeakHour(hourly),
        vitalityMean: vitalityValues.length > 0 ? HA.stats.mean(vitalityValues) : null,
        measuredDays: definedDaily.length,
      },
    };
  }

  root.HA.reportSections.buildStressSection = buildStressSection;
})();
