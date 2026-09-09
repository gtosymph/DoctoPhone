/**
 * Modèle de rapport synthétique, pour l'essai du moteur.
 *
 * Aucune donnée réelle n'entre ici, et aucune ne doit y entrer : le dépôt ne contient
 * jamais de mesure de santé. Les valeurs sortent d'un générateur pseudo-aléatoire à
 * graine fixe, donc deux exécutions donnent le même rapport et une régression visuelle
 * se voit.
 */

(function (global) {
  'use strict';

  const DAYS = 420;
  const DAY_MS = 86400000;
  const DOW = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];

  /** Générateur à graine : le même rapport à chaque essai. */
  function rng(seed) {
    let s = seed;
    return function () {
      s = (s * 1664525 + 1013904223) % 4294967296;
      return s / 4294967296;
    };
  }

  function iso(ms) {
    return new Date(ms).toISOString().slice(0, 10);
  }

  function build() {
    const random = rng(20260908);
    const end = Date.UTC(2026, 8, 8);
    const start = end - DAYS * DAY_MS;

    const nightly = [];
    const stepsDaily = [];
    const stressDaily = [];
    const vitality = [];
    const restingDaily = [];
    const hrvDaily = [];
    const spo2Daily = [];
    const skinTemp = [];

    for (let i = 0; i < DAYS; i++) {
      const ms = start + i * DAY_MS;
      const date = iso(ms);
      const drift = i / DAYS;

      if (random() > 0.2) {
        const hours = 4.2 + random() * 4 + drift * 0.6;
        nightly.push({
          date,
          hours: Math.round(hours * 100) / 100,
          score: Math.round(45 + hours * 5 + random() * 10),
          bedRel: Math.round((-0.5 + random() * 3.4) * 100) / 100,
          wakeRel: Math.round((7 + random() * 2.5) * 100) / 100,
          efficiencyPercent: Math.round((86 + random() * 8) * 10) / 10,
          sessions: random() > 0.82 ? 2 : 1,
        });
        restingDaily.push({ date, value: Math.round(48 + random() * 10 - drift * 4) });
        hrvDaily.push({ date, value: Math.round(52 + drift * 18 + random() * 12) });
      }
      stepsDaily.push({ date, value: Math.round(2200 + drift * 4200 + random() * 3000) });
      stressDaily.push({ date, value: Math.round(48 - drift * 14 + random() * 16) });
      vitality.push({ date, value: Math.round(66 + drift * 12 + random() * 12) });
      if (random() > 0.55) spo2Daily.push({ date, value: Math.round((94 + random() * 4) * 10) / 10 });
      if (random() > 0.4) skinTemp.push({ date, value: Math.round((34.2 + random() * 1.1) * 10) / 10 });
    }

    const rolling = stepsDaily.map((d, i) => {
      const window = stepsDaily.slice(Math.max(0, i - 6), i + 1);
      return { date: d.date, value: Math.round(window.reduce((a, x) => a + x.value, 0) / window.length) };
    });

    const months = [];
    for (let m = 0; m < 14; m++) {
      const d = new Date(Date.UTC(2025, 7 + m, 1));
      months.push(d.toISOString().slice(0, 7));
    }

    const monthly = months.map((month, i) => ({
      month,
      meanHours: Math.round((5.4 + i * 0.06 + random() * 0.6) * 100) / 100,
      medianHours: Math.round((5.3 + i * 0.06 + random() * 0.5) * 100) / 100,
      meanScore: Math.round(58 + i + random() * 8),
      nights: 18 + Math.round(random() * 8),
    }));

    return {
      meta: {
        generatedAt: new Date().toISOString(),
        from: iso(start),
        to: iso(end),
        days: DAYS,
        nights: nightly.length,
        heartRateSamples: 12939,
        hrvWindows: 144471,
        activeDays: stepsDaily.length,
        timeZone: 'Europe/Paris',
        periodLabel: 'Tout l\'historique',
        profile: { heightCm: 169, weightKg: 84.2 },
      },
      tiles: [
        { key: 'sleep', label: 'Sommeil par nuit', value: '6h13', sub: 'médiane 6h00 · cible 7 à 9h', status: 'serious' },
        { key: 'bedtime', label: 'Coucher médian', value: '1h22', sub: '32 % après 2h du matin', status: 'warn' },
        { key: 'restingHeartRate', label: 'FC de repos', value: '48', unit: 'bpm', sub: 'p10 à p90 : 44 à 55', status: 'good' },
        { key: 'hrv', label: 'Variabilité (RMSSD)', value: '66', unit: 'ms', sub: 'très bon pour 30 ans', status: 'good' },
        { key: 'bodyMassIndex', label: 'IMC', value: '29,5', sub: '84,2 kg · masse grasse 29 %', status: 'serious' },
        { key: 'steps30', label: 'Pas sur 30 jours', value: '7 029', sub: 'contre 4 000 en moyenne globale', status: 'good' },
        { key: 'bloodPressure', label: 'Tension', value: '136/86', sub: 'deux mesures seulement', status: 'warn' },
        { key: 'stress', label: 'Stress moyen', value: '42', unit: '/100', sub: '21 % du temps au-dessus de 60', status: 'warn' },
      ],
      sleep: {
        nightly,
        monthly,
        dayOfWeek: DOW.map((label, i) => ({
          label, value: Math.round((5.3 + (i >= 5 ? 0.9 : 0) + random() * 0.5) * 100) / 100, count: 40 + Math.round(random() * 12),
        })),
        distribution: ['<4h', '4-5h', '5-6h', '6-7h', '7-8h', '8-9h', '>9h']
          .map((label, i) => ({ label, value: null, count: [12, 34, 61, 72, 54, 28, 9][i] })),
        stagesMonthly: months.map(month => {
          const deep = 14 + random() * 6;
          const rem = 17 + random() * 7;
          const awake = 6 + random() * 4;
          return {
            month,
            deep: Math.round(deep * 10) / 10,
            light: Math.round((100 - deep - rem - awake) * 10) / 10,
            rem: Math.round(rem * 10) / 10,
            awake: Math.round(awake * 10) / 10,
          };
        }),
        kpi: {
          nights: nightly.length, meanHours: 6.22, medianHours: 6.07,
          bedMedian: 1.37, wakeMedian: 8.15, bedSpreadHours: 1.76,
          pctAfterMidnight: 78, pctAfter2h: 32, pctUnder6h: 49, pctOver7h: 24,
          weekendCatchupHours: 0.9, debtHours: 318, targetHours: 7.5,
          efficiencyPercent: 90.2, latencyMinutes: 12,
          snoringNights: 39, snoringMeasuredNights: 45, snoringMedianMinutes: 25,
          apneaResult: 'Négatif (avril 2026)',
        },
      },
      heart: {
        monthly: months.map((month, i) => ({
          month,
          resting: Math.round(52 - i * 0.4 + random() * 3),
          average: Math.round(74 - i * 0.3 + random() * 4),
        })),
        restingDaily,
        hourly: Array.from({ length: 24 }, (unused, h) => ({
          label: h, value: Math.round(56 + Math.sin((h - 4) / 24 * Math.PI * 2) * 12 + random() * 3),
        })),
        hrvMonthly: months.map((month, i) => ({ month, value: Math.round(56 + i * 1.1 + random() * 5) })),
        hrvDaily,
        bloodPressure: [
          { date: '2026-02-20', systolic: 136, diastolic: 85, pulse: 73 },
          { date: '2026-03-19', systolic: 137, diastolic: 87, pulse: 75 },
        ],
        ecg: [
          { date: '2025-07-01', meanHeartRate: 82, classification: 1, classificationLabel: 'Rythme sinusal' },
          { date: '2025-10-12', meanHeartRate: 97, classification: 2, classificationLabel: 'Code 2 — à lire dans Samsung Health Monitor' },
          { date: '2026-03-30', meanHeartRate: 90, classification: 1, classificationLabel: 'Rythme sinusal' },
        ],
        kpi: {
          restingMean: 48, restingP10: 44, restingP90: 55, averageMean: 72,
          maxObserved: 200, hrvMedian: 66, measuredDays: restingDaily.length,
        },
      },
      activity: {
        stepsDaily,
        stepsRolling7: rolling,
        stepsMonthly: months.map(month => ({ month, value: Math.round(3200 + random() * 3600) })),
        stepsDayOfWeek: DOW.map((label, i) => ({
          label, value: Math.round(3300 + (i === 4 ? 1800 : 0) + random() * 1500), count: 58 + Math.round(random() * 6),
        })),
        exerciseMonthly: months.map(month => {
          const sessions = Math.round(4 + random() * 18);
          return { month, sessions, minutes: sessions * (12 + random() * 8), calories: sessions * (48 + random() * 30) };
        }),
        exerciseByKind: [
          { label: 'Marche', value: 145 }, { label: 'Vélo', value: 9 }, { label: 'Autre', value: 7 },
        ],
        floorsMonthly: months.map(month => ({ month, value: Math.round(4 + random() * 9) })),
        kpi: {
          meanSteps: 4180, meanSteps30: 7029, meanSteps90: 5994,
          bestSteps: 18079, bestStepsDate: '2025-08-10',
          pctDaysUnder3000: 46, pctDaysOver8000: 18,
          totalExerciseMinutes: 2140, exerciseSessions: 161, measuredDays: DAYS,
        },
      },
      body: {
        daily: Array.from({ length: 24 }, (unused, i) => {
          const date = iso(Date.UTC(2025, 6, 3) + i * 4 * DAY_MS);
          const weight = 91 - i * 0.24 + (random() - 0.5) * 0.5;
          return {
            date,
            weightKg: Math.round(weight * 10) / 10,
            bodyFatPercent: Math.round((30.4 - i * 0.06) * 10) / 10,
            skeletalMuscleKg: Math.round((36.8 - i * 0.07) * 10) / 10,
            bodyMassIndex: Math.round(weight / (1.69 * 1.69) * 10) / 10,
            basalMetabolicRate: Math.round(1741 - i * 2.7),
          };
        }),
        kpi: {
          firstWeightKg: 91, lastWeightKg: 85.6, deltaKg: -5.4, deltaMuscleKg: -1.7,
          deltaFatKg: -2.4, bodyMassIndex: 30, bodyFatPercent: 29,
          basalMetabolicRate: 1677, lastMeasuredOn: '2025-10-30',
          daysSinceLastMeasure: 313, measures: 24,
        },
      },
      stress: {
        monthly: months.map((month, i) => ({
          month,
          mean: Math.round(48 - i * 0.9 + random() * 6),
          percentAbove60: Math.round((28 - i * 1.4 + random() * 8) * 10) / 10,
        })),
        hourly: Array.from({ length: 24 }, (unused, h) => ({
          label: h, value: Math.round(28 + Math.max(0, Math.sin((h - 6) / 24 * Math.PI * 2)) * 26 + random() * 4),
        })),
        dayOfWeek: DOW.map(label => ({ label, value: Math.round(38 + random() * 10) })),
        daily: stressDaily,
        vitalityDaily: vitality,
        kpi: { mean: 42, percentAbove60: 21, alerts: 53, peakHour: 22, vitalityMean: 75, measuredDays: DAYS },
      },
      breathing: {
        spo2Monthly: months.map(month => ({
          month,
          mean: Math.round((95 + random() * 1.2) * 10) / 10,
          min: Math.round((89 + random() * 4) * 10) / 10,
        })),
        spo2Daily,
        respiratoryDaily: skinTemp.map(d => ({ date: d.date, value: Math.round((11.2 + random() * 1.6) * 10) / 10 })),
        skinTempDaily: skinTemp,
        kpi: {
          spo2Mean: 95.3, spo2Measures: 308, spo2Under90: 5,
          respiratoryMean: 11.7, skinTempMean: 34.6, skinTempStdDev: 0.5,
        },
      },
      correlations: [
        { label: 'Durée de sommeil et FC de repos, le même jour', r: -0.37, n: 248 },
        { label: 'Durée de sommeil et variabilité cardiaque', r: 0.24, n: 241 },
        { label: 'Pas de la veille vers la durée de sommeil', r: 0.11, n: 236 },
        { label: 'Pas de la veille vers le score de sommeil', r: 0.08, n: 231 },
        { label: 'Heure de coucher et durée de sommeil', r: -0.42, n: 248 },
        { label: 'Durée de sommeil vers le stress du lendemain', r: -0.19, n: 244 },
      ],
      narrative: {
        headline: 'Un cœur solide, un sommeil trop court',
        verdict: 'La machine cardiovasculaire tient très bien : une FC de repos à 48 bpm et une '
          + 'variabilité à 66 ms sont des valeurs de sujet entraîné. Le point faible est le sommeil, '
          + 'à 6h13 par nuit en moyenne, avec près d\'une nuit sur deux sous six heures.',
        sections: {
          sleep: {
            verdict: 'La durée est le seul indicateur qui ne progresse pas.',
            points: [
              'La qualité n\'est pas en cause : l\'efficacité tient à 90 % et l\'architecture des stades reste normale.',
              'L\'irrégularité du coucher pèse davantage que le décalage lui-même.',
            ],
          },
          heart: {
            verdict: 'Le profil cardiaque est excellent et il réagit visiblement au sommeil.',
            points: ['Chaque heure de sommeil gagnée accompagne une FC de repos plus basse.'],
          },
        },
        plan: [
          { title: 'Stabiliser l\'heure de coucher', body: 'Viser d\'abord une fenêtre de ±30 minutes, avant même d\'avancer l\'heure.' },
          { title: 'Ajouter deux séances de renforcement par semaine', body: 'La perte de poids de 2025 a coûté 1,7 kg de muscle.' },
          { title: 'Confirmer la tension sur sept jours', body: 'Deux mesures ne suffisent pas à conclure.' },
        ],
      },
    };
  }

  global.HA = global.HA || {};
  global.HA.smokeFixture = { build };
}(typeof self !== 'undefined' ? self : this));
