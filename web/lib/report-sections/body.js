/**
 * Construit la section corps du rapport (`BodySection` / `BodyKpi`).
 *
 * Hypothèse de forme des données brutes : `bodyComposition` au format
 * `BodyCompositionMapper` — `{time, weightKg, bodyFatPercent, skeletalMuscleMassKg,
 * basalMetabolicRate, bodyMassIndex, offsetMinutes}`.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  const MILLIS_PER_DAY = 86400000;

  function daysBetween(fromKey, toKey) {
    if (!fromKey || !toKey) return null;
    const [fy, fm, fd] = fromKey.split('-').map(Number);
    const [ty, tm, td] = toKey.split('-').map(Number);
    return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / MILLIS_PER_DAY);
  }

  function buildDaily(bodyComposition, defaultOffsetMinutes) {
    return (bodyComposition || [])
      .filter((m) => m.time !== null && m.time !== undefined)
      .map((m) => ({
        date: HA.aggregate.localDateKeyOf(m.time, util().resolveOffset(m, defaultOffsetMinutes)),
        weightKg: m.weightKg,
        bodyFatPercent: m.bodyFatPercent ?? null,
        skeletalMuscleKg: m.skeletalMuscleMassKg ?? null,
        bodyMassIndex: m.bodyMassIndex ?? null,
        basalMetabolicRate: m.basalMetabolicRate ?? null,
      }))
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  }

  /**
   * `deltaFatKg` : dernière mesure moins première mesure de la période, sur le champ
   * brut `bodyFatMassKg` (masse grasse en kg, déjà calculée par Samsung). Ne PAS
   * reconstituer cette masse depuis `bodyFatPercent * weightKg` : l'arrondi du
   * pourcentage stocké introduit un écart avec la valeur d'origine.
   */
  function deltaFatKgOf(bodyComposition) {
    const withFatMass = (bodyComposition || [])
      .filter((m) => m.time !== null && m.time !== undefined && m.bodyFatMassKg !== null && m.bodyFatMassKg !== undefined)
      .sort((a, b) => a.time - b.time);
    if (withFatMass.length === 0) return null;
    const first = withFatMass[0];
    const last = withFatMass[withFatMass.length - 1];
    return last.bodyFatMassKg - first.bodyFatMassKg;
  }

  function buildKpi(daily, bodyComposition, asOfDateKey) {
    if (daily.length === 0) {
      return {
        firstWeightKg: null, lastWeightKg: null, deltaKg: null, deltaMuscleKg: null,
        deltaFatKg: null, bodyMassIndex: null, bodyFatPercent: null, basalMetabolicRate: null,
        lastMeasuredOn: null, daysSinceLastMeasure: null, measures: 0,
      };
    }
    const first = daily[0];
    const last = daily[daily.length - 1];

    return {
      firstWeightKg: first.weightKg,
      lastWeightKg: last.weightKg,
      deltaKg: last.weightKg - first.weightKg,
      deltaMuscleKg: first.skeletalMuscleKg !== null && last.skeletalMuscleKg !== null
        ? last.skeletalMuscleKg - first.skeletalMuscleKg : null,
      deltaFatKg: deltaFatKgOf(bodyComposition),
      bodyMassIndex: last.bodyMassIndex,
      bodyFatPercent: last.bodyFatPercent,
      basalMetabolicRate: last.basalMetabolicRate,
      lastMeasuredOn: last.date,
      daysSinceLastMeasure: daysBetween(last.date, asOfDateKey),
      measures: daily.length,
    };
  }

  /**
   * @param {object[]} bodyComposition
   * @param {string|null} asOfDateKey date `AAAA-MM-JJ` de référence (fin de période) pour `daysSinceLastMeasure`
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildBodySection(bodyComposition, asOfDateKey, defaultOffsetMinutes) {
    const daily = buildDaily(bodyComposition, defaultOffsetMinutes);
    return { daily, kpi: buildKpi(daily, bodyComposition, asOfDateKey) };
  }

  root.HA.reportSections.buildBodySection = buildBodySection;
})();
