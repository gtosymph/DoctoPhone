/**
 * Construit la section coeur du rapport (`HeartSection` / `HeartKpi` côté Kotlin).
 *
 * Forme des données brutes :
 * - `heartRate` : `{time, beatsPerMinute, offsetMinutes}` (forme `HeartRateMapper`).
 * - `hrv` : `{time, rmssdMillis, offsetMinutes}` (forme `HrvBinningParser`).
 * - `bloodPressure` : `{time, systolic, diastolic, pulse, offsetMinutes}` (forme `BloodPressureMapper`,
 *   voir `mappers-clinical.js`) — la date du point est dérivée de `time` + `offsetMinutes`.
 * - `ecg` : `{time, meanHeartRate, classification, offsetMinutes}` (forme `EcgMapper`) — code
 *   Samsung numérique, non documenté publiquement. `classificationLabel` ne traduit PAS
 *   ce code en diagnostic (voir `classificationLabelOf`) : l'app n'est pas un dispositif
 *   médical, et un libellé deviné pourrait annoncer à tort un trouble du rythme grave.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  /**
   * Forme prudente du libellé de classification ECG : le code Samsung n'est documenté
   * nulle part de façon fiable, donc on ne le traduit jamais en mot clinique (« rythme
   * sinusal », « fibrillation »...). Renvoyer vers Samsung Health Monitor, qui affiche
   * le libellé officiel du fabricant.
   */
  function classificationLabelOf(code) {
    if (code === null || code === undefined) return 'Résultat non enregistré';
    return `Résultat ${code} — à lire dans Samsung Health Monitor`;
  }

  function buildDailyHeartStats(heartRate, defaultOffsetMinutes) {
    const byDay = util().groupByLocalDate(heartRate, (h) => h.time, defaultOffsetMinutes);
    const result = {};
    Object.keys(byDay).forEach((date) => {
      const beats = byDay[date].map((h) => h.beatsPerMinute);
      result[date] = {
        resting: HA.aggregate.restingHeartRate(beats),
        average: HA.stats.mean(beats),
      };
    });
    return result;
  }

  function buildMonthly(dailyStats) {
    const byMonth = {};
    Object.keys(dailyStats).forEach((date) => {
      const month = date.slice(0, 7);
      (byMonth[month] = byMonth[month] || []).push(dailyStats[date]);
    });
    return Object.keys(byMonth).sort().map((month) => {
      const resting = byMonth[month].map((d) => d.resting).filter((v) => v !== null && v !== undefined);
      const average = byMonth[month].map((d) => d.average).filter((v) => v !== null && v !== undefined);
      return {
        month,
        resting: resting.length > 0 ? HA.stats.mean(resting) : null,
        average: average.length > 0 ? HA.stats.mean(average) : null,
      };
    });
  }

  function buildHourly(heartRate, defaultOffsetMinutes) {
    const hourValues = heartRate.map((h) => ({
      hour: util().localHourOf(h.time, util().resolveOffset(h, defaultOffsetMinutes)),
      value: h.beatsPerMinute,
    }));
    return HA.stats.byHour(hourValues);
  }

  function buildHrv(hrv, defaultOffsetMinutes) {
    const byDay = util().groupByLocalDate(hrv, (h) => h.time, defaultOffsetMinutes);
    const daily = util().toDayValues(byDay, (items) => {
      const values = items.map((h) => h.rmssdMillis).filter((v) => v !== null && v !== undefined);
      return values.length > 0 ? HA.stats.mean(values) : null;
    });
    const monthly = HA.stats.byMonth(daily).map((entry) => ({ month: entry.month, value: entry.value }));
    return { daily, monthly };
  }

  function buildBloodPressure(bloodPressure, defaultOffsetMinutes) {
    return (bloodPressure || [])
      .map((bp) => ({
        date: HA.aggregate.localDateKeyOf(bp.time, util().resolveOffset(bp, defaultOffsetMinutes)),
        systolic: bp.systolic,
        diastolic: bp.diastolic,
        pulse: bp.pulse ?? null,
      }))
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  }

  function buildEcg(ecg, defaultOffsetMinutes) {
    return (ecg || [])
      .map((e) => ({
        date: HA.aggregate.localDateKeyOf(e.time, util().resolveOffset(e, defaultOffsetMinutes)),
        meanHeartRate: e.meanHeartRate ?? null,
        classification: e.classification ?? null,
        classificationLabel: classificationLabelOf(e.classification),
      }))
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  }

  function buildKpi(dailyStats, hrvDaily, heartRate) {
    const restingValues = Object.values(dailyStats).map((d) => d.resting).filter((v) => v !== null && v !== undefined);
    const averageValues = Object.values(dailyStats).map((d) => d.average).filter((v) => v !== null && v !== undefined);
    const hrvValues = hrvDaily.map((d) => d.value).filter((v) => v !== null && v !== undefined);
    // Le maximum observé porte sur les mesures individuelles, pas sur des moyennes ou
    // des FC de repos journalières : une pointe d'effort ne doit pas être lissée.
    const beats = (heartRate || []).map((h) => h.beatsPerMinute);

    return {
      restingMean: restingValues.length > 0 ? HA.stats.mean(restingValues) : null,
      restingP10: restingValues.length > 0 ? HA.stats.percentile(restingValues, 0.10) : null,
      restingP90: restingValues.length > 0 ? HA.stats.percentile(restingValues, 0.90) : null,
      averageMean: averageValues.length > 0 ? HA.stats.mean(averageValues) : null,
      maxObserved: beats.length > 0 ? Math.max(...beats) : null,
      hrvMedian: hrvValues.length > 0 ? HA.stats.median(hrvValues) : null,
      measuredDays: Object.keys(dailyStats).length,
    };
  }

  /**
   * @param {object[]} heartRate mesures brutes de fréquence cardiaque
   * @param {object[]} hrv mesures brutes de variabilité cardiaque
   * @param {object[]} bloodPressure mesures brutes de tension artérielle
   * @param {object[]} ecg enregistrements ECG
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildHeartSection(heartRate, hrv, bloodPressure, ecg, defaultOffsetMinutes) {
    const dailyStats = buildDailyHeartStats(heartRate || [], defaultOffsetMinutes);
    const restingDaily = util().toDayValues(dailyStats, (d) => d.resting);
    const hrvSection = buildHrv(hrv || [], defaultOffsetMinutes);

    return {
      monthly: buildMonthly(dailyStats),
      restingDaily,
      hourly: buildHourly(heartRate || [], defaultOffsetMinutes),
      hrvMonthly: hrvSection.monthly,
      hrvDaily: hrvSection.daily,
      bloodPressure: buildBloodPressure(bloodPressure, defaultOffsetMinutes),
      ecg: buildEcg(ecg, defaultOffsetMinutes),
      kpi: buildKpi(dailyStats, hrvSection.daily, heartRate),
    };
  }

  root.HA.reportSections.buildHeartSection = buildHeartSection;
})();
