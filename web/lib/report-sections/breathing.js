/**
 * Construit la section respiration du rapport (`BreathingSection` / `BreathingKpi`).
 *
 * Forme des données brutes :
 * - `spo2` : `{time, percent, offsetMinutes}` (forme `SpO2Mapper`).
 * - `respiratory` : `{time, breathsPerMinute, offsetMinutes}` (forme `RespiratoryRateMapper`).
 * - `skinTemp` : `{time, celsius, offsetMinutes}` (forme `SkinTemperatureMapper`).
 * Voir `mappers-clinical.js`. Les trois sont ponctuelles ; la date du point du
 * rapport est dérivée de `time` + `offsetMinutes`, comme pour `heartRate`.
 *
 * `spo2Under90` est un compte de mesures (pas un pourcentage), comme `BreathingKpi`
 * côté Kotlin : c'est le nombre de mesures individuelles sous 90 %, un repère brut
 * d'événements de désaturation plutôt qu'une proportion du temps mesuré.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  const SPO2_LOW_THRESHOLD = 90;

  function buildSpo2(spo2, defaultOffsetMinutes) {
    const items = spo2 || [];
    const byDay = util().groupByLocalDate(items, (s) => s.time, defaultOffsetMinutes);
    const daily = util().toDayValues(byDay, (rows) => HA.stats.mean(rows.map((r) => r.percent)));

    const byMonth = {};
    items.forEach((s) => {
      const date = HA.aggregate.localDateKeyOf(s.time, util().resolveOffset(s, defaultOffsetMinutes));
      const month = date.slice(0, 7);
      (byMonth[month] = byMonth[month] || []).push(s.percent);
    });
    const monthly = Object.keys(byMonth).sort().map((month) => ({
      month,
      mean: HA.stats.mean(byMonth[month]),
      min: Math.min(...byMonth[month]),
    }));

    const allPercents = items.map((s) => s.percent);
    return {
      daily,
      monthly,
      mean: allPercents.length > 0 ? HA.stats.mean(allPercents) : null,
      measures: allPercents.length,
      under90: allPercents.filter((p) => p < SPO2_LOW_THRESHOLD).length,
    };
  }

  /** Moyenne journalière d'une mesure ponctuelle (`respiratory`, `skinTemp`), un champ par store. */
  function buildDailyValues(items, valueOf, defaultOffsetMinutes) {
    const byDay = util().groupByLocalDate(items || [], (r) => r.time, defaultOffsetMinutes);
    return util().toDayValues(byDay, (rows) => HA.stats.mean(rows.map(valueOf)));
  }

  /**
   * @param {object[]} spo2
   * @param {object[]} respiratory
   * @param {object[]} skinTemp
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildBreathingSection(spo2, respiratory, skinTemp, defaultOffsetMinutes) {
    const spo2Result = buildSpo2(spo2, defaultOffsetMinutes);
    const respiratoryDaily = buildDailyValues(respiratory, (r) => r.breathsPerMinute, defaultOffsetMinutes);
    const skinTempDaily = buildDailyValues(skinTemp, (r) => r.celsius, defaultOffsetMinutes);

    const respiratoryValues = respiratoryDaily.map((d) => d.value).filter((v) => v !== null && v !== undefined);
    const skinTempValues = skinTempDaily.map((d) => d.value).filter((v) => v !== null && v !== undefined);

    return {
      spo2Monthly: spo2Result.monthly,
      spo2Daily: spo2Result.daily,
      respiratoryDaily,
      skinTempDaily,
      kpi: {
        spo2Mean: spo2Result.mean,
        spo2Measures: spo2Result.measures,
        spo2Under90: spo2Result.under90,
        respiratoryMean: respiratoryValues.length > 0 ? HA.stats.mean(respiratoryValues) : null,
        skinTempMean: skinTempValues.length > 0 ? HA.stats.mean(skinTempValues) : null,
        skinTempStdDev: skinTempValues.length > 0 ? HA.stats.stdDev(skinTempValues) : null,
      },
    };
  }

  root.HA.reportSections.buildBreathingSection = buildBreathingSection;
})();
