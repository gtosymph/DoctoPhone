/**
 * Petit lanceur de tests maison (aucun framework), pour les fonctions pures
 * du parseur et de l'agrégation. Chaque test est indépendant et synchrone.
 */
(() => {
  'use strict';
  const results = [];
  // Les tests sont enregistrés puis lancés un par un, dans l'ordre, par `runAll()`.
  // Nécessaire pour tester db.js (IndexedDB, asynchrone) sans que deux tests ne se
  // marchent dessus en accédant en parallèle aux mêmes magasins.
  const registered = [];

  function test(name, fn) {
    registered.push({ name, fn });
  }

  function assert(condition, message) {
    if (!condition) throw new Error(message || 'Assertion échouée');
  }

  function assertEqual(actual, expected, message) {
    const a = JSON.stringify(actual);
    const e = JSON.stringify(expected);
    if (a !== e) {
      throw new Error(`${message || 'assertEqual'} — attendu ${e}, obtenu ${a}`);
    }
  }

  function assertClose(actual, expected, tolerance, message) {
    if (actual === null || Math.abs(actual - expected) > tolerance) {
      throw new Error(`${message || 'assertClose'} — attendu ≈ ${expected} (± ${tolerance}), obtenu ${actual}`);
    }
  }

  // ======================================================================
  // csv.js — lecture RFC 4180, BOM, colonne parasite, décalage horaire
  // ======================================================================

  test('parseSamsungCsv retire le BOM et découpe le descripteur', () => {
    const text = '﻿com.samsung.shealth.test,7006011,11\nid,note,\n1,bonjour,\n';
    const doc = HA.csv.parseSamsungCsv(text);
    assertEqual(doc.descriptor.dataType, 'com.samsung.shealth.test');
    assertEqual(doc.descriptor.appVersion, '7006011');
    assertEqual(doc.descriptor.schemaVersion, '11');
  });

  test('parseSamsungCsv retire la colonne finale parasite (virgule de fin de ligne)', () => {
    const text = 'com.samsung.shealth.test,1,1\nid,note,\n1,bonjour,\n';
    const doc = HA.csv.parseSamsungCsv(text);
    // La ligne de colonnes se termine par une virgule : elle ne doit pas créer de 3e colonne.
    assertEqual(doc.columns, ['id', 'note']);
    assertEqual(doc.rows[0].string('note'), 'bonjour');
  });

  test('parseSamsungCsv respecte la RFC 4180 : guillemets et virgules internes', () => {
    const text = 'com.samsung.shealth.test,1,1\nid,note,\n1,"bonjour, ""le monde""",\n';
    const doc = HA.csv.parseSamsungCsv(text);
    assertEqual(doc.rows.length, 1);
    assertEqual(doc.rows[0].string('note'), 'bonjour, "le monde"');
  });

  test('SamsungCsvRow.instant applique le décalage UTC+0200 de la colonne time_offset', () => {
    const text = 'com.samsung.shealth.test,1,1\nstart_time,time_offset,\n2024-06-01 10:00:00.000,UTC+0200,\n';
    const doc = HA.csv.parseSamsungCsv(text);
    const row = doc.rows[0];
    const expected = Date.UTC(2024, 5, 1, 10, 0, 0) - 120 * 60000; // 10h locales à UTC+2 = 08h UTC
    assertEqual(row.instant('start_time'), expected, "l'instant ne tient pas compte du décalage UTC+0200");
    assertEqual(row.zoneOffsetMinutes('time_offset'), 120);
  });

  test('SamsungCsvRow.instant applique un décalage négatif (UTC-0500)', () => {
    const text = 'com.samsung.shealth.test,1,1\nstart_time,time_offset,\n2024-06-01 10:00:00.000,UTC-0500,\n';
    const doc = HA.csv.parseSamsungCsv(text);
    const row = doc.rows[0];
    const expected = Date.UTC(2024, 5, 1, 10, 0, 0) + 300 * 60000;
    assertEqual(row.instant('start_time'), expected);
  });

  // ======================================================================
  // mappers.js — filtres et rejets métier
  // ======================================================================

  test('DailyStepsMapper ne garde que source_type == -2', () => {
    const text = [
      'com.samsung.shealth.step_daily_trend,1,1',
      'day_time,source_type,count,',
      '2024-06-01 00:00:00.000,-2,5000,',
      '2024-06-01 00:00:00.000,0,3000,',
      '2024-06-01 00:00:00.000,1,2000,',
      '',
    ].join('\n');
    const doc = HA.csv.parseSamsungCsv(text);
    const mapped = doc.rows.map((r) => HA.mappers.DailyStepsMapper.map(r));
    assertEqual(mapped[0].steps, 5000, "la ligne source_type=-2 doit être gardée");
    assertEqual(mapped[1], null, 'la ligne source_type=0 doit être rejetée (double comptage)');
    assertEqual(mapped[2], null, 'la ligne source_type=1 doit être rejetée (double comptage)');
  });

  test('SleepMapper rejette une nuit sans sleep_duration (objectif de coucher)', () => {
    const withDuration = [
      'com.samsung.shealth.sleep,1,1',
      'com.samsung.health.sleep.datauuid,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset,sleep_duration,',
      'abc,2024-06-01 23:00:00.000,2024-06-02 07:00:00.000,UTC+0200,480,',
      '',
    ].join('\n');
    const withoutDuration = withDuration.replace(',480,', ',,');
    const mappedWith = HA.mappers.SleepMapper.map(HA.csv.parseSamsungCsv(withDuration).rows[0]);
    const mappedWithout = HA.mappers.SleepMapper.map(HA.csv.parseSamsungCsv(withoutDuration).rows[0]);
    assert(mappedWith !== null, 'une nuit avec sleep_duration doit être acceptée');
    assertEqual(mappedWithout, null, 'une nuit sans sleep_duration doit être rejetée');
  });

  test('SleepMapper rattache la nuit au jour du réveil, pas du coucher', () => {
    const text = [
      'com.samsung.shealth.sleep,1,1',
      'com.samsung.health.sleep.datauuid,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset,sleep_duration,',
      'abc,2024-06-09 23:00:00.000,2024-06-10 07:00:00.000,UTC+0200,480,',
      '',
    ].join('\n');
    const night = HA.mappers.SleepMapper.map(HA.csv.parseSamsungCsv(text).rows[0]);
    assertEqual(night.date, '2024-06-10', 'la nuit doit porter la date du réveil (10), pas du coucher (9)');
  });

  test('SleepStageMapper traduit les codes de stades Samsung', () => {
    const text = [
      'com.samsung.health.sleep_stage,1,1',
      'datauuid,sleep_id,start_time,end_time,stage,',
      'a,s,2024-06-01 00:00:00.000,2024-06-01 00:10:00.000,40001,',
      'b,s,2024-06-01 00:10:00.000,2024-06-01 00:20:00.000,40002,',
      'c,s,2024-06-01 00:20:00.000,2024-06-01 00:30:00.000,40003,',
      'd,s,2024-06-01 00:30:00.000,2024-06-01 00:40:00.000,40004,',
      'e,s,2024-06-01 00:40:00.000,2024-06-01 00:50:00.000,99999,',
      '',
    ].join('\n');
    const rows = HA.csv.parseSamsungCsv(text).rows;
    const stages = rows.map((r) => HA.mappers.SleepStageMapper.map(r).stage);
    assertEqual(stages, ['AWAKE', 'LIGHT', 'DEEP', 'REM', 'UNKNOWN']);
  });

  // ======================================================================
  // aggregate.js — FC de repos, moyenne circulaire, sens des tendances
  // ======================================================================

  test('restingHeartRate utilise le 5e centile, index (n-1)*0,05', () => {
    const beats = Array.from({ length: 20 }, (_, i) => 60 + i); // 60..79
    // n=20 -> index = trunc((20-1)*0.05) = trunc(0.95) = 0 -> plus basse valeur triée
    assertEqual(HA.aggregate.restingHeartRate(beats), 60);
  });

  test('restingHeartRate rend null avec moins de 20 mesures', () => {
    const beats = Array.from({ length: 19 }, (_, i) => 60 + i);
    assertEqual(HA.aggregate.restingHeartRate(beats), null);
  });

  test('buildSleepRegularity moyenne les couchers en cercle, pas en droite', () => {
    // 23h00 et 01h00 sont distants de 2h, pas de 22h : la moyenne doit être ~minuit,
    // jamais ~midi (ce que donnerait une moyenne arithmétique naïve des secondes du jour).
    const days = [
      { sleepMinutes: 480, bedTimeSecondOfDay: 23 * 3600 },
      { sleepMinutes: 480, bedTimeSecondOfDay: 1 * 3600 },
    ];
    const regularity = HA.aggregate.buildSleepRegularity(days, 450);
    const midnightDistance = Math.min(
      Math.abs(regularity.averageBedtimeSecondOfDay - 0),
      Math.abs(regularity.averageBedtimeSecondOfDay - 86400)
    );
    assert(midnightDistance < 60, `la moyenne circulaire doit tomber près de minuit, obtenu ${regularity.averageBedtimeSecondOfDay}s`);
    assertClose(regularity.bedtimeSpreadHours, 1.0, 0.2, "l'écart-type circulaire doit rester proche de 1h pour ±1h autour de minuit");
  });

  test("isImprovement dépend du sens de la mesure : la FC de repos qui baisse est une amélioration", () => {
    const days = [
      { steps: 8000, restingHeartRate: 60 },
      { steps: 8000, restingHeartRate: 60 },
      { steps: 6000, restingHeartRate: 50 },
      { steps: 6000, restingHeartRate: 50 },
    ];
    const trends = HA.aggregate.buildTrends(days);
    const hr = trends.find((t) => t.metric.key === 'RESTING_HEART_RATE');
    const steps = trends.find((t) => t.metric.key === 'STEPS');
    assertEqual(hr.direction, 'DOWN', 'la FC de repos baisse de 60 à 50');
    assertEqual(hr.isImprovement, true, 'une FC de repos qui baisse est une AMÉLIORATION (lowerIsBetter)');
    assertEqual(steps.direction, 'DOWN', 'les pas baissent de 8000 à 6000');
    assertEqual(steps.isImprovement, false, 'moins de pas est une DÉGRADATION (pas lowerIsBetter)');
  });

  test('buildTrends reste STABLE dans la bande de bruit de 5 %', () => {
    const days = [
      { steps: 10000 }, { steps: 10000 },
      { steps: 10300 }, { steps: 10300 }, // +3 % : dans la bande de bruit
    ];
    const trends = HA.aggregate.buildTrends(days);
    const steps = trends.find((t) => t.metric.key === 'STEPS');
    assertEqual(steps.direction, 'STABLE');
    assertEqual(steps.isImprovement, null);
  });

  // ======================================================================
  // hrv.js — médiane des fenêtres
  // ======================================================================

  test('HrvBinningParser garde la médiane, résistante aux artefacts', () => {
    const bins = JSON.stringify([
      { start_time: 1000, end_time: 2000, sdnn: 50, rmssd: 60 },
      { start_time: 2000, end_time: 3000, sdnn: 52, rmssd: 62 },
      { start_time: 3000, end_time: 4000, sdnn: 999, rmssd: 999 }, // artefact
    ]);
    const sample = HA.hrv.parseHrvBinning('id', bins);
    assertEqual(sample.sdnnMillis, 52, "la médiane de 3 valeurs triées [50,52,999] est 52");
    assertEqual(sample.rmssdMillis, 62);
  });

  // ======================================================================
  // mappers.js — offsetMinutes (fuseau explicite, indépendant du navigateur)
  // ======================================================================

  test('HeartRateMapper porte offsetMinutes tiré de time_offset', () => {
    const text = [
      'com.samsung.shealth.tracker.heart_rate,1,1',
      'com.samsung.health.heart_rate.datauuid,com.samsung.health.heart_rate.start_time,com.samsung.health.heart_rate.time_offset,com.samsung.health.heart_rate.heart_rate,',
      'h1,2024-06-01 08:00:00.000,UTC+0200,65,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const beat = HA.mappers.HeartRateMapper.map(row);
    assertEqual(beat.offsetMinutes, 120, "l'offset UTC+0200 doit donner 120 minutes");
  });

  test('SleepMapper porte offsetMinutes tiré de time_offset', () => {
    const text = [
      'com.samsung.shealth.sleep,1,1',
      'com.samsung.health.sleep.datauuid,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset,sleep_duration,',
      'abc,2024-06-01 23:00:00.000,2024-06-02 07:00:00.000,UTC+0200,480,',
      '',
    ].join('\n');
    const night = HA.mappers.SleepMapper.map(HA.csv.parseSamsungCsv(text).rows[0]);
    assertEqual(night.offsetMinutes, 120);
  });

  // ======================================================================
  // mappers-clinical.js — les 8 nouveaux types cliniques
  // ======================================================================

  test('BloodPressureMapper lit les colonnes préfixées com.samsung.health.blood_pressure.* (constat sur export réel)', () => {
    const text = [
      'com.samsung.shealth.blood_pressure,1,1',
      'com.samsung.health.blood_pressure.datauuid,com.samsung.health.blood_pressure.start_time,com.samsung.health.blood_pressure.time_offset,com.samsung.health.blood_pressure.systolic,com.samsung.health.blood_pressure.diastolic,com.samsung.health.blood_pressure.pulse,com.samsung.health.blood_pressure.mean,',
      'abc,2024-06-01 08:00:00.000,UTC+0200,120,80,65,93,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const reading = HA.mappers.BloodPressureMapper.map(row);
    assert(reading !== null, 'une ligne complète doit être acceptée');
    assertEqual(reading.systolic, 120);
    assertEqual(reading.diastolic, 80);
    assertEqual(reading.pulse, 65);
    assertEqual(reading.mean, 93);
    assertEqual(reading.offsetMinutes, 120);
  });

  test('BloodPressureMapper rejette une systolique hors plage plausible (60..250)', () => {
    const text = [
      'com.samsung.shealth.blood_pressure,1,1',
      'com.samsung.health.blood_pressure.datauuid,com.samsung.health.blood_pressure.start_time,com.samsung.health.blood_pressure.time_offset,com.samsung.health.blood_pressure.systolic,com.samsung.health.blood_pressure.diastolic,',
      'abc,2024-06-01 08:00:00.000,UTC+0200,300,80,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.BloodPressureMapper.map(row), null, 'une systolique de 300 doit être rejetée');
  });

  test('BloodPressureMapper rejette une diastolique hors plage plausible (30..150)', () => {
    const text = [
      'com.samsung.shealth.blood_pressure,1,1',
      'com.samsung.health.blood_pressure.datauuid,com.samsung.health.blood_pressure.start_time,com.samsung.health.blood_pressure.time_offset,com.samsung.health.blood_pressure.systolic,com.samsung.health.blood_pressure.diastolic,',
      'abc,2024-06-01 08:00:00.000,UTC+0200,120,10,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.BloodPressureMapper.map(row), null, 'une diastolique de 10 doit être rejetée');
  });

  test('EcgMapper mappe la fréquence cardiaque, la classification et les symptômes', () => {
    const text = [
      'com.samsung.health.ecg,1,1',
      'datauuid,start_time,time_offset,mean_heart_rate,min_heart_rate,max_heart_rate,classification,symptoms,',
      'xyz,2024-06-01 09:00:00.000,UTC+0200,72,68,80,1,aucun,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const record = HA.mappers.EcgMapper.map(row);
    assert(record !== null);
    assertEqual(record.meanHeartRate, 72);
    assertEqual(record.classification, 1);
    assertEqual(record.symptoms, 'aucun');
  });

  test('EcgMapper rend null sans horodatage de départ', () => {
    const text = [
      'com.samsung.health.ecg,1,1',
      'datauuid,start_time,time_offset,mean_heart_rate,',
      'xyz,,UTC+0200,72,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.EcgMapper.map(row), null);
  });

  test('SnoringMapper calcule la durée depuis start/end quand duration est absente', () => {
    const text = [
      'com.samsung.shealth.sleep_snoring,1,1',
      'duration,datauuid,start_time,end_time,time_offset,',
      ',s1,2024-06-01 23:00:00.000,2024-06-01 23:12:00.000,UTC+0200,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const episode = HA.mappers.SnoringMapper.map(row);
    assert(episode !== null);
    assertEqual(episode.durationMinutes, 12);
  });

  test('RespiratoryRateMapper rejette une fréquence hors 4..40 respirations/min', () => {
    const text = [
      'com.samsung.health.respiratory_rate,1,1',
      'datauuid,start_time,time_offset,average,lower_limit,upper_limit,',
      'r1,2024-06-01 03:00:00.000,UTC+0200,60,50,70,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.RespiratoryRateMapper.map(row), null, '60 respirations/min est hors plage plausible');
  });

  test('RespiratoryRateMapper accepte une fréquence plausible', () => {
    const text = [
      'com.samsung.health.respiratory_rate,1,1',
      'datauuid,start_time,time_offset,average,lower_limit,upper_limit,',
      'r1,2024-06-01 03:00:00.000,UTC+0200,14,12,17,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const sample = HA.mappers.RespiratoryRateMapper.map(row);
    assert(sample !== null);
    assertEqual(sample.breathsPerMinute, 14);
  });

  test('SkinTemperatureMapper rejette une température hors 25..45 °C', () => {
    const text = [
      'com.samsung.health.skin_temperature,1,1',
      'datauuid,start_time,time_offset,temperature,baseline,',
      't1,2024-06-01 02:00:00.000,UTC+0200,50,33.2,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.SkinTemperatureMapper.map(row), null);
  });

  test('SkinTemperatureMapper accepte une température plausible avec sa référence', () => {
    const text = [
      'com.samsung.health.skin_temperature,1,1',
      'datauuid,start_time,time_offset,temperature,baseline,',
      't1,2024-06-01 02:00:00.000,UTC+0200,33.8,33.2,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const sample = HA.mappers.SkinTemperatureMapper.map(row);
    assert(sample !== null);
    assertClose(sample.celsius, 33.8, 0.001);
    assertClose(sample.baseline, 33.2, 0.001);
  });

  test('SleepApneaMapper mappe le résultat et le trouble respiratoire moyen', () => {
    const text = [
      'com.samsung.health.sleep_apnea,1,1',
      'datauuid,start_time,time_offset,result,average_bd,',
      'a1,2024-06-01 01:00:00.000,UTC+0200,2,3.5,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const apnea = HA.mappers.SleepApneaMapper.map(row);
    assert(apnea !== null);
    assertEqual(apnea.result, 2);
    assertClose(apnea.averageBreathingDisturbance, 3.5, 0.001);
  });

  test('StressAlertMapper rend null sans horodatage de fin', () => {
    const text = [
      'com.samsung.shealth.alerted_stress,1,1',
      'datauuid,start_time,end_time,time_offset,',
      'sa1,2024-06-01 10:00:00.000,,UTC+0200,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    assertEqual(HA.mappers.StressAlertMapper.map(row), null);
  });

  test('DailyFloorsMapper lit day_time comme une date-heure formatée, comme les pas quotidiens', () => {
    const text = [
      'com.samsung.shealth.tracker.floors_day_summary,1,1',
      'datauuid,day_time,floor_count,',
      'f1,2024-06-01 00:00:00.000,7,',
      '',
    ].join('\n');
    const row = HA.csv.parseSamsungCsv(text).rows[0];
    const floors = HA.mappers.DailyFloorsMapper.map(row);
    assert(floors !== null);
    assertEqual(floors.date, '2024-06-01');
    assertEqual(floors.floors, 7);
  });

  // ======================================================================
  // importer.js — 19 types reconnus, bilan complet, records par magasin
  // ======================================================================

  test('HA.importer.CSV_TYPES reconnaît les 19 types Samsung (10 déjà connus + 8 nouveaux + stades/exercice)', () => {
    const expected = [
      'com.samsung.shealth.sleep', 'com.samsung.health.sleep_stage',
      'com.samsung.shealth.tracker.heart_rate', 'com.samsung.shealth.stress',
      'com.samsung.shealth.alerted_stress', 'com.samsung.health.hrv',
      'com.samsung.shealth.tracker.oxygen_saturation', 'com.samsung.shealth.exercise',
      'com.samsung.health.weight', 'com.samsung.shealth.step_daily_trend',
      'com.samsung.shealth.activity.day_summary', 'com.samsung.shealth.vitality_score',
      'com.samsung.shealth.blood_pressure', 'com.samsung.health.ecg',
      'com.samsung.shealth.sleep_snoring', 'com.samsung.health.respiratory_rate',
      'com.samsung.health.skin_temperature', 'com.samsung.health.sleep_apnea',
      'com.samsung.shealth.tracker.floors_day_summary',
    ];
    expected.forEach((type) => assert(type in HA.importer.CSV_TYPES, `type manquant : ${type}`));
    assertEqual(Object.keys(HA.importer.CSV_TYPES).length, expected.length, 'aucun type en trop ni en moins');
  });

  test('HA.importer.classify reconnaît un fichier de tension artérielle', () => {
    const meta = HA.importer.classify('com.samsung.shealth.blood_pressure.20240601000000.csv');
    assert(meta !== null);
    assertEqual(meta.kind, 'csv');
    assertEqual(meta.dataType, 'com.samsung.shealth.blood_pressure');
  });

  test('HA.importer.run persiste les records par magasin et compte tous les types, y compris les vides', async () => {
    const bpText = [
      'com.samsung.shealth.blood_pressure,1,1',
      'com.samsung.health.blood_pressure.datauuid,com.samsung.health.blood_pressure.start_time,com.samsung.health.blood_pressure.time_offset,com.samsung.health.blood_pressure.systolic,com.samsung.health.blood_pressure.diastolic,',
      'bp1,2024-06-01 08:00:00.000,UTC+0200,118,76,',
      '',
    ].join('\n');
    const stepsText = [
      'com.samsung.shealth.step_daily_trend,1,1',
      'day_time,source_type,count,',
      '2024-06-01 00:00:00.000,-2,4200,',
      '',
    ].join('\n');
    const entries = [
      { relativePath: 'com.samsung.shealth.blood_pressure.20240601000000.csv', getText: async () => bpText },
      { relativePath: 'com.samsung.shealth.step_daily_trend.20240601000000.csv', getText: async () => stepsText },
    ];
    const result = await HA.importer.run(entries, null, 450);
    assertEqual(result.records.bloodPressure.length, 1, 'la tension importée doit apparaître dans records.bloodPressure');
    assertEqual(result.counts.bloodPressure, 1);
    assertEqual(result.counts.dailySteps, 1);
    const expectedKeys = [
      'sleepNights', 'sleepStages', 'heartRate', 'stress', 'stressAlerts', 'hrv', 'spo2',
      'exercise', 'bodyComposition', 'dailySteps', 'dailyActivity', 'energyScores',
      'bloodPressure', 'ecg', 'snoring', 'respiratory', 'skinTemp', 'sleepApnea', 'dailyFloors',
    ];
    expectedKeys.forEach((key) => assert(Array.isArray(result.records[key]), `records.${key} doit être un tableau, même vide`));
  });

  // ======================================================================
  // db.js — schéma v2 : mesures brutes, écriture par lots, plages
  // ======================================================================

  test('HA.db.putAll + readAll conservent les mesures d\'un magasin ponctuel', async () => {
    await HA.db.clearAll();
    const items = [
      { id: 'hr-1', time: 1717200000000, beatsPerMinute: 60 },
      { id: 'hr-2', time: 1717203600000, beatsPerMinute: 62 },
    ];
    await HA.db.putAll('heartRate', items);
    const rows = await HA.db.readAll('heartRate');
    assertEqual(rows.length, 2, 'les deux mesures doivent être présentes après putAll');
  });

  test("HA.db.putAll écrit par lots d'environ 500 objets, dans des transactions séparées", async () => {
    await HA.db.clearAll();
    const items = Array.from({ length: 1200 }, (_, i) => ({ id: `hr-${i}`, time: 1717200000000 + i * 60000 }));
    let transactionCount = 0;
    const originalTransaction = IDBDatabase.prototype.transaction;
    IDBDatabase.prototype.transaction = function patchedTransaction(...args) {
      transactionCount += 1;
      return originalTransaction.apply(this, args);
    };
    try {
      await HA.db.putAll('heartRate', items);
    } finally {
      IDBDatabase.prototype.transaction = originalTransaction;
    }
    assertEqual(transactionCount, 3, '1200 objets par lots de 500 doivent ouvrir 3 transactions séparées');
    const rows = await HA.db.readAll('heartRate');
    assertEqual(rows.length, 1200, "tous les objets doivent survivre à l'écriture par lots");
  });

  test("HA.db.readRange filtre un magasin ponctuel par son index temporel", async () => {
    await HA.db.clearAll();
    await HA.db.putAll('heartRate', [
      { id: 'hr-a', time: 1000 },
      { id: 'hr-b', time: 2000 },
      { id: 'hr-c', time: 3000 },
    ]);
    const rows = await HA.db.readRange('heartRate', 1500, 2500);
    assertEqual(rows.map((r) => r.id), ['hr-b'], 'seule la mesure à time=2000 doit être dans la plage');
  });

  test('HA.db.readDateRange filtre une série journalière par date', async () => {
    await HA.db.clearAll();
    await HA.db.putAll('dailySteps', [
      { date: '2024-06-01', steps: 4000 },
      { date: '2024-06-02', steps: 5000 },
      { date: '2024-06-03', steps: 6000 },
    ]);
    const rows = await HA.db.readDateRange('dailySteps', '2024-06-02', '2024-06-03');
    assertEqual(rows.map((r) => r.date), ['2024-06-02', '2024-06-03']);
  });

  test('HA.db.clearAll vide aussi les nouveaux magasins', async () => {
    await HA.db.putAll('bloodPressure', [{ id: 'bp-1', time: 1000, systolic: 120, diastolic: 80 }]);
    await HA.db.clearAll();
    const rows = await HA.db.readAll('bloodPressure');
    assertEqual(rows.length, 0);
  });

  test("HA.db.counts compte les objets par magasin, y compris les stades et l'exercice", async () => {
    await HA.db.clearAll();
    await HA.db.putAll('sleepStages', [{ id: 's1', sleepId: 'n1', start: 1000, end: 2000 }]);
    await HA.db.putAll('exercise', [{ id: 'e1', start: 1000, end: 2000 }]);
    const counts = await HA.db.counts();
    assertEqual(counts.sleepStages, 1);
    assertEqual(counts.exercise, 1);
    assertEqual(counts.hrv, 0, 'un magasin vide doit compter 0, pas être absent');
  });

  test('HA.db.saveDailySnapshots et loadAllDailySnapshots restent fonctionnels en v2', async () => {
    await HA.db.clearAll();
    await HA.db.saveDailySnapshots([{ date: '2024-06-01', steps: 1000 }], { fromKey: '2024-06-01' });
    const days = await HA.db.loadAllDailySnapshots();
    assertEqual(days.length, 1);
    assertEqual(days[0].steps, 1000);
    const summary = await HA.db.loadImportSummary();
    assertEqual(summary.fromKey, '2024-06-01');
  });

  // ======================================================================
  // app-report.js — plage de dates de l'onglet Rapport, lecture des
  // magasins, orchestration construction + dessin
  // ======================================================================

  test('periodToRange calcule une plage de 7 jours se terminant à la date donnée', () => {
    const now = new Date(2024, 5, 10); // 10 juin 2024, horloge locale
    const range = HA.appReport.periodToRange('7', now);
    assertEqual(range.to, '2024-06-10');
    assertEqual(range.from, '2024-06-04', '7 jours incluant le jour courant doivent démarrer le 4');
    assertEqual(range.toMillis, now.getTime());
  });

  test("periodToRange rend toute la plage nulle pour « tout l'historique »", () => {
    // `to` doit aussi rester null : sinon HA.reportBuilder.buildReport couperait le
    // rapport à aujourd'hui au lieu de détecter la vraie dernière mesure importée.
    const now = new Date(2024, 5, 10);
    const range = HA.appReport.periodToRange('all', now);
    assertEqual(range, { from: null, to: null, fromMillis: null, toMillis: null });
  });

  test('readSources lit les séries journalières par date et les mesures ponctuelles par instant', async () => {
    const originalDb = HA.db;
    const calls = { readAll: [], readDateRange: [], readRange: [] };
    HA.db = {
      readAll: async (store) => { calls.readAll.push(store); return [{ store, all: true }]; },
      readDateRange: async (store, from, to) => { calls.readDateRange.push([store, from, to]); return [{ store, from, to }]; },
      readRange: async (store, fromMillis, toMillis) => { calls.readRange.push([store, fromMillis, toMillis]); return [{ store, fromMillis, toMillis }]; },
    };
    try {
      const range = { from: '2024-06-01', to: '2024-06-10', fromMillis: 1000, toMillis: 2000 };
      const sources = await HA.appReport.readSources(range);
      assertEqual(calls.readAll.length, 0, 'une plage connue ne doit jamais lire un magasin en entier');
      assertEqual(calls.readDateRange.map((c) => c[0]).sort(), ['dailyActivity', 'dailyFloors', 'dailySteps', 'energyScores']);
      assertEqual(calls.readRange.length, HA.appReport.REPORT_STORES.length - 4, 'les 15 magasins ponctuels doivent passer par readRange');
      assertEqual(sources.dailySteps, [{ store: 'dailySteps', from: '2024-06-01', to: '2024-06-10' }]);
      assertEqual(sources.hrv, [{ store: 'hrv', fromMillis: 1000, toMillis: 2000 }]);
    } finally {
      HA.db = originalDb;
    }
  });

  test("readSources lit chaque magasin en entier pour « tout l'historique »", async () => {
    const originalDb = HA.db;
    const calls = { readAll: [] };
    HA.db = {
      readAll: async (store) => { calls.readAll.push(store); return [{ store, all: true }]; },
      readDateRange: async () => { throw new Error('ne doit pas être appelé'); },
      readRange: async () => { throw new Error('ne doit pas être appelé'); },
    };
    try {
      const sources = await HA.appReport.readSources({ from: null, to: '2024-06-10', fromMillis: null, toMillis: 2000 });
      assertEqual(calls.readAll.length, HA.appReport.REPORT_STORES.length);
      assertEqual(sources.hrv, [{ store: 'hrv', all: true }]);
    } finally {
      HA.db = originalDb;
    }
  });

  test('renderReportInto dessine le rapport quand le modèle est valide', async () => {
    const originalDb = HA.db;
    const originalBuilder = HA.reportBuilder;
    const originalReport = HA.report;
    const model = HA.reportModel.emptyReportModel();
    HA.db = { readAll: async () => [], readDateRange: async () => [], readRange: async () => [] };
    let builtWith = null;
    HA.reportBuilder = { buildReport: (sources, options) => { builtWith = { sources, options }; return model; } };
    let renderedWith = null;
    HA.report = { renderReport: (host, m) => { renderedWith = { host, m }; } };
    const host = { marker: 'host' };
    const options = { from: '2024-06-01', to: '2024-06-10', zone: 'Europe/Paris', sleepTargetHours: 7.5 };
    let emptyCalled = false;
    try {
      await HA.appReport.renderReportInto(host, options, () => { emptyCalled = true; });
      assert(!emptyCalled, "un modèle valide ne doit pas déclencher l'état vide");
      assert(renderedWith !== null, 'HA.report.renderReport doit être appelé');
      assertEqual(renderedWith.host, host);
      assertEqual(renderedWith.m, model);
      assertEqual(builtWith.options, options, 'les options doivent être transmises telles quelles à buildReport');
    } finally {
      HA.db = originalDb;
      HA.reportBuilder = originalBuilder;
      HA.report = originalReport;
    }
  });

  test("renderReportInto signale l'état vide quand le modèle est incomplet, sans dessiner", async () => {
    const originalDb = HA.db;
    const originalBuilder = HA.reportBuilder;
    const originalReport = HA.report;
    HA.db = { readAll: async () => [], readDateRange: async () => [], readRange: async () => [] };
    HA.reportBuilder = { buildReport: () => ({}) }; // modèle non conforme au contrat
    let renderCalled = false;
    HA.report = { renderReport: () => { renderCalled = true; } };
    const options = { from: null, to: '2024-06-10', zone: 'UTC', sleepTargetHours: 7.5 };
    let problems = null;
    try {
      await HA.appReport.renderReportInto({}, options, (p) => { problems = p; });
      assert(!renderCalled, 'un modèle incomplet ne doit jamais être dessiné');
      assert(Array.isArray(problems) && problems.length > 0, 'les manques doivent être transmis à onEmpty');
    } finally {
      HA.db = originalDb;
      HA.reportBuilder = originalBuilder;
      HA.report = originalReport;
    }
  });

  // ======================================================================
  // report-render.js / report-sections.js — onglets par domaine
  //
  // Seuls ces tests chargent le vrai moteur de rapport (report/report-*.js, voir
  // test.html) : les autres tests de renderReportInto plus haut le mockent. Le modèle
  // vient de report/smoke-fixture.js, le même générateur reproductible que l'essai
  // visuel de report/smoke.html — aucune donnée de santé réelle n'y entre.
  // ======================================================================

  /** Le nombre de graphiques configurés dans SECTIONS + la carte des corrélations. */
  function countConfiguredCharts() {
    let n = 1; // CORRELATIONS_CARD, posée dans l'onglet Synthèse.
    for (const section of HA.reportSections.SECTIONS) {
      for (const card of section.cards) if (card.chart) n += 1;
    }
    return n;
  }

  /**
   * Le nombre de graphiques dessinés AVANT tout dépliage.
   *
   * Depuis la mise en hiérarchie, seules les cartes marquées `essential` sortent tout de
   * suite ; le reste attend un clic sur « Voir le détail ». Un SVG construit dans un
   * conteneur masqué n'a aucune largeur à mesurer, donc le dessin est différé, pas caché.
   */
  function countEssentialCharts() {
    let n = 1; // CORRELATIONS_CARD, toujours dessinée dans Synthèse.
    for (const section of HA.reportSections.SECTIONS) {
      const essential = section.cards.filter(c => c.essential);
      // Une section sans carte essentielle montre tout : mieux vaut une section longue
      // qu'une section entièrement cachée derrière un bouton.
      const shown = essential.length ? essential : section.cards;
      for (const card of shown) if (card.chart) n += 1;
    }
    return n;
  }

  /** Déplie tous les blocs de détail d'un panneau, comme le ferait un lecteur curieux. */
  function expandAllDetails(root) {
    for (const button of root.querySelectorAll('.ha-detail-toggle')) button.click();
  }

  test('renderReport construit sept panneaux d\'onglets, un seul visible à la fois', () => {
    const host = document.createElement('div');
    HA.report.renderReport(host, HA.smokeFixture.build());
    const panels = host.querySelectorAll('.ha-tabpanel');
    assertEqual(panels.length, 7, 'un panneau par onglet : Synthèse, Sommeil, Cœur, Activité, Corps, Vitalité, Tout');
    const visible = Array.from(panels).filter((p) => !p.hidden);
    assertEqual(visible.length, 1, 'un seul panneau doit être visible à la fois');
    assertEqual(visible[0].dataset.tab, 'synthese', 'Synthèse est l\'onglet par défaut');
  });

  test('l\'onglet Tout construit ses graphiques seulement à sa première activation', () => {
    const host = document.createElement('div');
    HA.report.renderReport(host, HA.smokeFixture.build());
    const toutPanel = host.querySelector('.ha-tabpanel[data-tab="tout"]');
    const expected = countEssentialCharts();
    assertEqual(toutPanel.children.length, 0, 'Tout doit rester vide tant qu\'il n\'a pas été ouvert (paresse)');
    const svgBefore = host.querySelectorAll('svg').length;
    assertEqual(svgBefore, expected, 'chaque graphique essentiel n\'est encore dessiné qu\'une fois, dans son propre onglet de domaine');

    host.querySelector('#ha-tab-tout').click();

    assertEqual(toutPanel.querySelectorAll('svg').length, expected,
      'Tout redessine chaque graphique essentiel une seconde fois, une fois ouvert');
    assertEqual(host.querySelectorAll('svg').length, svgBefore + expected,
      'ouvrir Tout ne doit pas redessiner les six autres panneaux, seulement construire le sien');
  });

  test('déplier le détail rend tous les graphiques configurés, aucun ne disparaît', () => {
    const host = document.createElement('div');
    HA.report.renderReport(host, HA.smokeFixture.build());
    host.querySelector('#ha-tab-tout').click();
    const toutPanel = host.querySelector('.ha-tabpanel[data-tab="tout"]');

    const avant = toutPanel.querySelectorAll('svg').length;
    expandAllDetails(toutPanel);
    const apres = toutPanel.querySelectorAll('svg').length;

    // La hiérarchie range, elle n'enlève rien. C'est la promesse faite dans les specs :
    // « l'essentiel visible, le détail replié, rien ne disparaît ».
    assert(apres > avant, 'déplier n\'ajoute aucun graphique');
    assertEqual(apres, countConfiguredCharts(),
      'un graphique configuré reste introuvable même après dépliage');
  });

  test('le détail se dessine à la première ouverture, pas avant', () => {
    const host = document.createElement('div');
    HA.report.renderReport(host, HA.smokeFixture.build());
    host.querySelector('#ha-tab-sleep').click();
    const panel = host.querySelector('.ha-tabpanel[data-tab="sleep"]');
    const body = panel.querySelector('.ha-detail-body');
    const button = panel.querySelector('.ha-detail-toggle');

    // Un SVG construit dans un conteneur masqué sort à zéro pixel de large : le dessin est
    // donc différé, pas simplement caché.
    assertEqual(body.querySelectorAll('.ha-card').length, 0, 'le détail est dessiné avant son ouverture');
    assert(body.hidden, 'le détail doit partir replié');

    button.click();
    assert(!body.hidden, 'le bouton n\'ouvre pas le bloc');
    assert(body.querySelectorAll('.ha-card').length > 0, 'le bloc ouvert reste vide');
    assertEqual(button.getAttribute('aria-expanded'), 'true', 'l\'état du bouton n\'est pas annoncé');

    button.click();
    assert(body.hidden, 'le second clic ne referme pas le bloc');
    assertEqual(button.getAttribute('aria-expanded'), 'false');
  });

  test('l\'onglet actif survit à un redessin complet (thème, période)', () => {
    const host = document.createElement('div');
    const model = HA.smokeFixture.build();
    HA.report.renderReport(host, model);
    host.querySelector('#ha-tab-heart').click();

    HA.report.renderReport(host, model); // un redessin complet, pas un clic sur l'onglet

    const visible = Array.from(host.querySelectorAll('.ha-tabpanel')).filter((p) => !p.hidden);
    assertEqual(visible.length, 1);
    assertEqual(visible[0].dataset.tab, 'heart', 'Cœur doit rester actif après le redessin');
  });

  test('l\'onglet Tout, resté ouvert pendant un redessin, se reconstruit tout de suite', () => {
    const host = document.createElement('div');
    const model = HA.smokeFixture.build();
    HA.report.renderReport(host, model);
    host.querySelector('#ha-tab-tout').click();

    HA.report.renderReport(host, model); // redessin complet, l'onglet Tout est déjà actif

    const toutPanel = host.querySelector('.ha-tabpanel[data-tab="tout"]');
    assert(!toutPanel.hidden, 'Tout doit rester l\'onglet visible après le redessin');
    assertEqual(toutPanel.querySelectorAll('svg').length, countEssentialCharts(),
      'Tout doit être reconstruit tout de suite, pas au prochain clic');
  });

  /**
   * Ces deux tests ont besoin d'une vraie disposition (largeur, `getBoundingClientRect`,
   * `overflow-x` calculé) : contrairement aux autres tests de ce fichier, l'hôte doit
   * donc être attaché au document — hors champ, pour ne pas perturber la page de
   * résultats pendant l'exécution — puis détaché en fin de test.
   */
  function withAttachedHost(widthPx, fn) {
    const host = document.createElement('div');
    host.style.cssText = `position:absolute; left:-9999px; top:0; width:${widthPx}px;`;
    document.body.appendChild(host);
    try {
      fn(host);
    } finally {
      document.body.removeChild(host);
    }
  }

  test('la barre d\'onglets défile plutôt que de passer à la ligne, à largeur étroite (360px)', () => {
    withAttachedHost(360, (host) => {
      HA.report.renderReport(host, HA.smokeFixture.build());
      const bar = host.querySelector('.ha-tabbar');
      const tops = new Set(Array.from(bar.querySelectorAll('.ha-tab')).map((b) => Math.round(b.getBoundingClientRect().top)));
      assert(bar.scrollWidth > bar.clientWidth, 'à 360px, sept onglets doivent déborder — sinon le test ne prouve rien');
      assertEqual(tops.size, 1, 'les sept onglets doivent rester sur une seule ligne, jamais passer à la ligne');
      assertEqual(getComputedStyle(bar).overflowX, 'auto',
        'le débordement doit défiler à l\'horizontale, pas se couper ni passer à la ligne');
    });
  });

  test('l\'onglet actif est ramené dans le champ de vision de la piste quand il est hors écran', () => {
    withAttachedHost(360, (host) => {
      HA.report.renderReport(host, HA.smokeFixture.build());
      const bar = host.querySelector('.ha-tabbar');
      const toutBtn = host.querySelector('#ha-tab-tout');

      toutBtn.click(); // « Tout » est le dernier onglet, hors champ à 360px de large

      const toutRect = toutBtn.getBoundingClientRect();
      const barRect = bar.getBoundingClientRect();
      assert(toutRect.left >= barRect.left - 1 && toutRect.right <= barRect.right + 1,
        'l\'onglet activé doit être entièrement visible dans la piste après le clic');
    });
  });

  // ======================================================================
  // aggregate.js — nuits fractionnées et fuseau horaire par mesure (tâches 1 et 2)
  // ======================================================================

  test("aggregateSleepNights fusionne les sessions d'une même nuit de réveil", () => {
    const sessions = [
      {
        id: 'a', date: '2024-06-10', bedTime: Date.UTC(2024, 5, 9, 21, 0), wakeTime: Date.UTC(2024, 5, 9, 23, 0),
        durationMinutes: 90, score: 70, efficiencyPercent: 80, latencyMinutes: 10,
        physicalRecovery: 50, mentalRecovery: 50, remMinutes: 20, lightMinutes: 60,
        localBedSecondOfDay: 21 * 3600,
      },
      {
        id: 'b', date: '2024-06-10', bedTime: Date.UTC(2024, 5, 10, 1, 0), wakeTime: Date.UTC(2024, 5, 10, 5, 0),
        durationMinutes: 240, score: 90, efficiencyPercent: 95, latencyMinutes: 5,
        physicalRecovery: 80, mentalRecovery: 85, remMinutes: 40, lightMinutes: 150,
        localBedSecondOfDay: 1 * 3600,
      },
    ];
    const merged = HA.aggregate.aggregateSleepNights(sessions);
    assertEqual(merged.length, 1, 'les deux sessions doivent fusionner en une seule nuit');
    const night = merged[0];
    assertEqual(night.durationMinutes, 330, 'la durée fusionnée est la somme des sessions (90+240)');
    assertEqual(night.bedTime, sessions[0].bedTime, 'le coucher est celui de la première session');
    assertEqual(night.wakeTime, sessions[1].wakeTime, 'le réveil est celui de la dernière session');
    assertEqual(night.score, 90, 'le score retenu est celui de la session la plus longue (session b, 240 min)');
    assertEqual(night.efficiencyPercent, 95, 'efficacité de la session la plus longue');
    assertEqual(night.remMinutes, 60, 'REM sommé (20+40)');
    assertEqual(night.lightMinutes, 210, 'léger sommé (60+150)');
    assertEqual(night.sessions, 2);
  });

  test('aggregateSleepNights ignore les valeurs de stade absentes lors de la somme (jamais 0)', () => {
    const sessions = [
      {
        id: 'a', date: '2024-06-11', bedTime: 1000, wakeTime: 2000, durationMinutes: 60, score: 50,
        efficiencyPercent: null, latencyMinutes: null, physicalRecovery: null, mentalRecovery: null,
        remMinutes: null, lightMinutes: 30, localBedSecondOfDay: 0,
      },
      {
        id: 'b', date: '2024-06-11', bedTime: 3000, wakeTime: 4000, durationMinutes: 60, score: 50,
        efficiencyPercent: null, latencyMinutes: null, physicalRecovery: null, mentalRecovery: null,
        remMinutes: 15, lightMinutes: null, localBedSecondOfDay: 0,
      },
    ];
    const night = HA.aggregate.aggregateSleepNights(sessions)[0];
    assertEqual(night.remMinutes, 15, "REM : une seule session le porte, la somme ignore le null de l'autre");
    assertEqual(night.lightMinutes, 30, 'léger : symétrique');
    assertEqual(night.deepMinutes, null, 'profond : aucune session ne le porte -> null, jamais 0');
  });

  test("buildDailySnapshots regroupe une mesure par son propre offsetMinutes, pas le fuseau du navigateur", () => {
    // 2024-07-01T13:30:00Z vaut 2024-07-01 23h30 à UTC+10 (le même jour), mais aussi
    // 2024-07-02 03h30 à... non : UTC+10 -> 23h30 le 1er. Choisir UTC+13 pour franchir minuit :
    // 13:30 UTC + 13h = 02:30 le 2 juillet, local à UTC+13.
    const instant = Date.UTC(2024, 6, 1, 13, 30);
    const inputs = { heartRates: [{ time: instant, beatsPerMinute: 70, offsetMinutes: 13 * 60 }] };
    const days = HA.aggregate.buildDailySnapshots('2024-07-01', '2024-07-02', inputs, 450);
    const day1 = days.find((d) => d.date === '2024-07-01');
    const day2 = days.find((d) => d.date === '2024-07-02');
    assertEqual(day1.averageHeartRate, null, "la mesure appartient au 2 juillet local (UTC+13), pas au 1er (UTC)");
    assertEqual(day2.averageHeartRate, 70, 'la mesure doit être comptée le 2 juillet, jour local à UTC+13');
  });

  test("buildDailySnapshots retombe sur le fuseau du navigateur sans planter quand offsetMinutes manque", () => {
    const now = Date.now();
    const date = HA.aggregate.localDateKeyOf(now);
    const inputs = { heartRates: [{ time: now, beatsPerMinute: 65 }] }; // pas d'offsetMinutes : mesure ancienne
    const days = HA.aggregate.buildDailySnapshots(date, date, inputs, 450);
    assertEqual(days.length, 1);
    assertEqual(days[0].averageHeartRate, 65, "repli sur le fuseau du navigateur, aucune exception levée");
  });

  // ======================================================================
  // stats.js — statistiques de série et circulaires
  // ======================================================================

  test('HA.stats.percentile utilise le même indice trunc((n-1)*p) que restingHeartRate', () => {
    const values = Array.from({ length: 20 }, (_, i) => i); // 0..19
    assertEqual(HA.stats.percentile(values, 0.05), 0);
    assertEqual(HA.stats.percentile(values, 0.5), 9); // trunc(19*0.5) = trunc(9.5) = 9
  });

  test('HA.stats.mean et HA.stats.median rendent null sur une série vide', () => {
    assertEqual(HA.stats.mean([1, 2, 3, 4]), 2.5);
    assertEqual(HA.stats.median([1, 2, 3, 4]), 2.5);
    assertEqual(HA.stats.median([1, 3, 2]), 2);
    assertEqual(HA.stats.mean([]), null);
    assertEqual(HA.stats.median([]), null);
  });

  test("HA.stats.stdDev calcule l'écart-type de population (division par n)", () => {
    assertClose(HA.stats.stdDev([2, 4, 4, 4, 5, 5, 7, 9]), 2.0, 0.001);
  });

  test('HA.stats.rollingMean : moyenne glissante simple sans trou', () => {
    const rolled = HA.stats.rollingMean([10, 20, 30, 40, 50], 3, 3);
    assertEqual(rolled, [null, null, 20, 30, 40]);
  });

  test('HA.stats.rollingMean : un trou ne bloque pas la fenêtre tant que minSamples est atteint', () => {
    const rolled = HA.stats.rollingMean([1, 2, null, 4, 5], 3, 2);
    assertEqual(rolled, [null, 1.5, 1.5, 3, 4.5]);
  });

  test('HA.stats.byDayOfWeek regroupe par jour ISO, lun. en premier', () => {
    const days = [
      { date: '2024-06-03', value: 1 }, // lundi
      { date: '2024-06-04', value: 2 }, // mardi
      { date: '2024-06-05', value: 3 }, // mercredi
      { date: '2024-06-06', value: 4 }, // jeudi
      { date: '2024-06-07', value: 5 }, // vendredi
      { date: '2024-06-08', value: 6 }, // samedi
      { date: '2024-06-09', value: 7 }, // dimanche
    ];
    const byWeekday = HA.stats.byDayOfWeek(days);
    assertEqual(byWeekday.map((d) => d.label), ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.']);
    assertEqual(byWeekday.map((d) => d.value), [1, 2, 3, 4, 5, 6, 7]);
  });

  test('HA.stats.byMonth regroupe par AAAA-MM, trié chronologiquement', () => {
    const days = [
      { date: '2024-07-01', value: 10 },
      { date: '2024-06-15', value: 20 },
      { date: '2024-06-20', value: 30 },
    ];
    assertEqual(HA.stats.byMonth(days), [{ month: '2024-06', value: 25 }, { month: '2024-07', value: 10 }]);
  });

  test('HA.stats.byHour moyenne par heure locale (0 à 23)', () => {
    const items = [{ hour: 3, value: 10 }, { hour: 3, value: 20 }, { hour: 22, value: 5 }];
    const byHour = HA.stats.byHour(items);
    assertEqual(byHour[3].value, 15);
    assertEqual(byHour[3].count, 2);
    assertEqual(byHour[22].value, 5);
    assertEqual(byHour[0].value, null, 'une heure sans mesure vaut null, jamais 0');
  });

  test("HA.stats.distribution compte par tranche fournie par l'appelant", () => {
    const buckets = [{ label: '< 5', max: 5 }, { label: '5-8', min: 5, max: 8 }, { label: '>= 8', min: 8 }];
    const dist = HA.stats.distribution([1, 4, 5, 6, 7, 8, 9], buckets);
    assertEqual(dist, [
      { label: '< 5', value: 2, count: 2 },
      { label: '5-8', value: 3, count: 3 },
      { label: '>= 8', value: 2, count: 2 },
    ]);
  });

  test('HA.stats.circularMean moyenne les heures en cercle, pas en droite', () => {
    const mean = HA.stats.circularMean([23, 1]);
    const distanceFromMidnight = Math.min(mean, 24 - mean);
    assert(distanceFromMidnight < 0.05, `la moyenne circulaire de 23h et 1h doit être proche de minuit, obtenu ${mean}`);
  });

  test('HA.stats.circularStdDev suit sqrt(-2 * ln(R)) / (2π) * 24', () => {
    assertClose(HA.stats.circularStdDev([23, 1]), 1.006, 0.01);
  });

  test("HA.stats.circularMedian recentre autour de 18h pour ne pas séparer les couchers autour de minuit", () => {
    // 23h, 23h30, 0h30 : une médiane arithmétique naïve donnerait ~7h55 (fausse).
    const median = HA.stats.circularMedian([23, 23.5, 0.5]);
    assertClose(median, 23.5, 0.01, 'la médiane doit rester proche de 23h-0h, pas sauter à ~8h');
  });

  // ======================================================================
  // correlation.js — corrélation de Pearson et appariement décalé
  // ======================================================================

  test('HA.correlation.pearson rend null en dessous du seuil minimal de paires (30)', () => {
    const pairs = Array.from({ length: 29 }, (_, i) => ({ x: i, y: i }));
    assertEqual(HA.correlation.pearson(pairs), null);
  });

  test('HA.correlation.pearson rend 1 pour une relation linéaire parfaite, au-dessus du seuil', () => {
    const pairs = Array.from({ length: 30 }, (_, i) => ({ x: i, y: 2 * i + 3 }));
    assertClose(HA.correlation.pearson(pairs), 1, 0.0001);
  });

  test("HA.correlation.pearson rend null si une série est constante (écart-type nul)", () => {
    const pairs = Array.from({ length: 30 }, (_, i) => ({ x: i, y: 5 }));
    assertEqual(HA.correlation.pearson(pairs), null);
  });

  test('HA.correlation.paired apparie deux séries quotidiennes avec un décalage de jours', () => {
    const a = [{ date: '2024-06-01', value: 10 }, { date: '2024-06-02', value: 20 }];
    const b = [{ date: '2024-06-02', value: 100 }, { date: '2024-06-03', value: 200 }];
    const pairs = HA.correlation.paired(a, b, 1); // b daté un jour après a
    assertEqual(pairs, [{ x: 10, y: 100 }, { x: 20, y: 200 }]);
  });

  test('HA.correlation.paired ignore les jours sans correspondance dans les deux séries', () => {
    const a = [{ date: '2024-06-01', value: 10 }];
    const b = [{ date: '2024-06-05', value: 100 }];
    assertEqual(HA.correlation.paired(a, b, 0), []);
  });

  // ======================================================================
  // report-builder.js — assemblage du ReportModel complet
  // ======================================================================

  function dateAt(offsetDays) {
    const ms = Date.UTC(2024, 0, 1) + offsetDays * 86400000;
    const d = new Date(ms);
    return HA.csv.dateKey(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  }

  /** Jeu de données synthétique, en UTC (offsetMinutes = 0) pour rester facile à vérifier à la main. */
  function buildSyntheticSources(dayCount) {
    const sleepNights = [];
    const heartRate = [];
    const hrv = [];
    const dailySteps = [];
    const stress = [];
    const bodyComposition = [];

    for (let i = 0; i < dayCount; i += 1) {
      const date = dateAt(i);
      const hours = 6 + (i % 4) * 0.5; // 6, 6.5, 7, 7.5
      const bedTime = Date.UTC(2024, 0, 1) + (i - 1) * 86400000 + 23 * 3600000;
      const wakeTime = bedTime + hours * 3600000;
      sleepNights.push({
        id: `sleep-${i}`, date, bedTime, wakeTime, durationMinutes: hours * 60,
        score: 60 + (i % 30), efficiencyPercent: 85, latencyMinutes: 12,
        physicalRecovery: 50, mentalRecovery: 50,
        remMinutes: hours * 15, lightMinutes: hours * 30,
        localBedSecondOfDay: 23 * 3600, offsetMinutes: 0,
      });

      // Plus de sommeil -> FC de repos plus basse (corrélation négative construite).
      const restingTarget = Math.round(70 - hours * 2);
      for (let k = 0; k < 20; k += 1) {
        heartRate.push({ time: Date.UTC(2024, 0, 1 + i, 10, k), beatsPerMinute: restingTarget + k, offsetMinutes: 0 });
      }

      hrv.push({ time: Date.UTC(2024, 0, 1 + i, 3, 0), rmssdMillis: 30 + (i % 10), offsetMinutes: 0 });
      dailySteps.push({ date, steps: 4000 + (i % 7) * 800 });
      stress.push({ start: Date.UTC(2024, 0, 1 + i, 14, 0), end: Date.UTC(2024, 0, 1 + i, 14, 30), score: 20 + (i % 50), offsetMinutes: 0 });
      if (i % 5 === 0) {
        bodyComposition.push({
          time: Date.UTC(2024, 0, 1 + i, 7, 0), weightKg: 70 - i * 0.02, bodyFatPercent: 20,
          skeletalMuscleMassKg: 30, basalMetabolicRate: 1600, bodyMassIndex: 22, offsetMinutes: 0,
        });
      }
    }

    return {
      sleepNights, sleepStages: [], heartRate, stress, stressAlerts: [], hrv, spo2: [],
      exercise: [], bodyComposition, dailySteps, dailyActivity: [], energyScores: [],
      bloodPressure: [], ecg: [], snoring: [], respiratory: [], skinTemp: [], sleepApnea: [], dailyFloors: [],
    };
  }

  test('HA.reportBuilder.buildReport produit un modèle conforme au contrat de report-model.js', () => {
    const sources = buildSyntheticSources(35);
    const model = HA.reportBuilder.buildReport(sources, { sleepTargetHours: 7.5 });
    const problems = HA.reportModel.validateReportModel(model);
    assertEqual(problems, [], `le modèle doit être valide : ${problems.join('; ')}`);
    assertEqual(model.sleep.nightly.length, 35, 'une nuit par jour (une seule session chacune, aucune fusion)');
    assertEqual(model.meta.nights, 35);
    assertEqual(model.tiles.length, 8, 'les 8 tuiles attendues');
  });

  test('HA.reportBuilder.buildReport calcule une corrélation négative entre sommeil et FC de repos', () => {
    const sources = buildSyntheticSources(35);
    const model = HA.reportBuilder.buildReport(sources, { sleepTargetHours: 7.5 });
    const item = model.correlations.find((c) => c.label === 'Durée de sommeil et FC de repos, le même jour');
    assert(item !== undefined, 'la corrélation doit apparaître (35 paires >= seuil de 30)');
    assert(item.r < -0.5, `attendu une corrélation négative marquée, obtenu ${item.r}`);
    assertEqual(item.n, 35);
  });

  test('HA.reportBuilder.buildReport rend un modèle vide et valide sans aucune donnée', () => {
    const model = HA.reportBuilder.buildReport({}, {});
    assertEqual(HA.reportModel.validateReportModel(model), []);
    assertEqual(model.tiles.length, 0);
  });

  // ======================================================================
  // report-builder.js — décalage par défaut de la période (zoneOffsetMinutes),
  // pour les jeux d'essai sans offsetMinutes par mesure (voir shared-fixtures/)
  // ======================================================================

  test("buildSleepSection lit l'heure de coucher depuis localBedTime (chaîne HH:MM:SS) quand elle est présente", () => {
    const night = {
      id: 'n1', date: '2025-01-02', bedTime: Date.UTC(2025, 0, 1, 21, 51, 0), wakeTime: Date.UTC(2025, 0, 2, 4, 30, 0),
      durationMinutes: 279, score: 60, efficiencyPercent: 90, latencyMinutes: 8,
      localBedTime: '23:51:00', // UTC+2 : 21h51 UTC = 23h51 locale
    };
    const section = HA.reportSections.buildSleepSection([night], [], [], {}, undefined);
    const point = section.nightly[0];
    assertClose(point.bedRel, -0.15, 0.001, "23h51 doit donner un bedRel proche de -0,15 (heures relatives à minuit)");
    assertClose(point.wakeRel, 6.5, 0.001, "le réveil doit utiliser le même décalage (UTC+2) que localBedTime : 6h30 locale");
  });

  test("buildSleepSection déduit l'heure locale de bedTime avec zoneOffsetMinutes quand localBedTime manque (cas Health Connect)", () => {
    const night = {
      id: 'n1', date: '2025-01-02', bedTime: Date.UTC(2025, 0, 1, 21, 51, 0), wakeTime: Date.UTC(2025, 0, 2, 4, 30, 0),
      durationMinutes: 279, score: 60, efficiencyPercent: 90, latencyMinutes: 8,
      // pas de localBedTime, pas de localBedSecondOfDay, pas d'offsetMinutes
    };
    const defaultOffsetMinutes = 120; // UTC+2, comme `zoneOffsetMinutes` du jeu d'essai de parité
    const section = HA.reportSections.buildSleepSection([night], [], [], {}, defaultOffsetMinutes);
    const point = section.nightly[0];
    assertClose(point.bedRel, -0.15, 0.001, 'sans localBedTime, bedRel doit se déduire de bedTime + zoneOffsetMinutes');
    assertClose(point.wakeRel, 6.5, 0.001);
  });

  test('HA.reportBuilder.buildReport propage options.zoneOffsetMinutes jusqu\'aux sections (bedMedian non nul)', () => {
    const sources = buildSyntheticSources(35); // offsetMinutes: 0 sur chaque mesure dans ce jeu d'essai
    // On retire offsetMinutes de chaque nuit pour simuler un jeu d'essai façon parity-input.json.
    sources.sleepNights.forEach((n) => { delete n.offsetMinutes; });
    const model = HA.reportBuilder.buildReport(sources, { sleepTargetHours: 7.5, zoneOffsetMinutes: 0 });
    assert(model.sleep.kpi.bedMedian !== null, 'bedMedian doit être calculé grâce au décalage par défaut de la période');
    assert(model.sleep.nightly.every((n) => n.bedRel !== null), 'toutes les nuits doivent porter un bedRel');
  });

  // ======================================================================
  // prompt.js — narrativeUserPrompt : confidentialité du récit LLM
  // ======================================================================

  /**
   * Modèle de rapport truffé de pièges : chaque champ INTERDIT porte une valeur
   * sentinelle (999 / 999999) ou une date bien formée (2025-12-25 / 2025-12-31),
   * pour qu'un oubli dans le filtre de confidentialité se voie immédiatement dans
   * le texte produit.
   */
  function buildNarrativeFixtureModel() {
    return {
      meta: {
        generatedAt: '2026-01-01T00:00:00.000Z', from: '2025-12-01', to: '2025-12-31',
        days: 31, nights: 20, heartRateSamples: 500, hrvWindows: 50, activeDays: 25,
        timeZone: 'Europe/Paris', periodLabel: '1 déc. 2025 – 31 déc. 2025',
        profile: { heightCm: 180, weightKg: 75 },
      },
      // `sub` porte déjà une date au jour près formatée en français par tiles.js
      // (`TileFormat.date`) — jamais une clé ISO ni un horodatage précis.
      tiles: [
        { key: 'sleep', label: 'Sommeil', value: '7 h 10', unit: null, sub: '20 nuits mesurées', status: 'good' },
        { key: 'bodyMassIndex', label: 'IMC', value: '23.1', unit: null, sub: 'Dernière mesure : 31 décembre 2025', status: 'good' },
        { key: 'bloodPressure', label: 'Tension artérielle', value: '120/80', unit: 'mmHg', sub: 'Dernière mesure : 31 décembre 2025', status: 'good' },
      ],
      sleep: {
        nightly: [{ date: '2025-12-31', hours: 6.5, score: 80, bedRel: -1.5, wakeRel: 6, efficiencyPercent: 90, sessions: 1 }],
        monthly: [{ month: '2025-12', meanHours: 7.1, medianHours: 7.2, meanScore: 78, nights: 20 }],
        dayOfWeek: [{ label: 'lun.', value: 7.0 }],
        distribution: [{ label: '6-7 h', value: null, count: 5 }],
        stagesMonthly: [{ month: '2025-12', deep: 15, light: 55, rem: 20, awake: 10 }],
        kpi: {
          nights: 20, meanHours: 7.1, medianHours: 7.2, bedMedian: -1.5, wakeMedian: 6.2,
          bedSpreadHours: 0.8, pctAfterMidnight: 30, pctAfter2h: 5, pctUnder6h: 10, pctOver7h: 60,
          weekendCatchupHours: 0.5, debtHours: 2.3, targetHours: 7.5, efficiencyPercent: 89,
          latencyMinutes: 12, snoringNights: 3, snoringMeasuredNights: 20, snoringMedianMinutes: 15,
          apneaResult: '1',
        },
      },
      heart: {
        monthly: [{ month: '2025-12', resting: 55, average: 68 }],
        restingDaily: [{ date: '2025-12-31', value: 999 }],
        hourly: [{ label: '7', value: 62 }],
        hrvMonthly: [{ month: '2025-12', value: 45 }],
        hrvDaily: [{ date: '2025-12-31', value: 999 }],
        bloodPressure: [{ date: '2025-12-31', systolic: 999, diastolic: 999, pulse: 60 }],
        ecg: [{ date: '2025-12-31', meanHeartRate: 999, classification: 1, classificationLabel: 'Rythme sinusal normal' }],
        kpi: { restingMean: 55.4, restingP10: 50, restingP90: 62, averageMean: 68, maxObserved: 90, hrvMedian: 45, measuredDays: 25 },
      },
      activity: {
        stepsDaily: [{ date: '2025-12-31', value: 999999 }],
        stepsRolling7: [{ date: '2025-12-31', value: 999999 }],
        stepsMonthly: [{ month: '2025-12', value: 8500 }],
        stepsDayOfWeek: [{ label: 'lun.', value: 9000 }],
        exerciseMonthly: [{ month: '2025-12', sessions: 8, minutes: 240, calories: 1800 }],
        exerciseByKind: [{ label: 'RUNNING', value: 120, count: 4 }],
        floorsMonthly: [{ month: '2025-12', value: 6 }],
        kpi: {
          meanSteps: 8200, meanSteps30: 8500, meanSteps90: 8000, bestSteps: 15000,
          bestStepsDate: '2025-12-25', pctDaysUnder3000: 5, pctDaysOver8000: 55,
          totalExerciseMinutes: 240, exerciseSessions: 8, measuredDays: 25,
        },
      },
      body: {
        daily: [{ date: '2025-12-31', weightKg: 999 }],
        kpi: {
          firstWeightKg: 76, lastWeightKg: 75, deltaKg: -1, deltaMuscleKg: 0.2, deltaFatKg: -0.5,
          bodyMassIndex: 23.1, bodyFatPercent: 18.2, basalMetabolicRate: 1700,
          lastMeasuredOn: '2025-12-31', daysSinceLastMeasure: 78, measures: 4,
        },
      },
      stress: {
        monthly: [{ month: '2025-12', mean: 32, percentAbove60: 8 }],
        hourly: [{ label: '14', value: 40 }],
        dayOfWeek: [{ label: 'lun.', value: 35 }],
        daily: [{ date: '2025-12-31', value: 999 }],
        vitalityDaily: [{ date: '2025-12-31', value: 999 }],
        kpi: { mean: 32, percentAbove60: 8, alerts: 2, peakHour: 14, vitalityMean: 70, measuredDays: 25 },
      },
      breathing: {
        spo2Monthly: [{ month: '2025-12', mean: 96, min: 91 }],
        spo2Daily: [{ date: '2025-12-31', value: 999 }],
        respiratoryDaily: [{ date: '2025-12-31', value: 999 }],
        skinTempDaily: [{ date: '2025-12-31', value: 999 }],
        kpi: { spo2Mean: 96.2, spo2Measures: 300, spo2Under90: 4, respiratoryMean: 14.2, skinTempMean: 34.5, skinTempStdDev: 0.4 },
      },
      correlations: [{ label: 'Durée de sommeil et FC de repos, le même jour', r: -0.35, n: 200 }],
      narrative: null,
    };
  }

  test('narrativeUserPrompt garde les dates au jour près (règle du projet : elles sont permises)', () => {
    // La date d'une mesure rare (pesée, tension) ne révèle rien de plus que la période
    // déjà envoyée : elle reste dans le message, telle que fournie par la tuile.
    const text = HA.prompt.narrativeUserPrompt(buildNarrativeFixtureModel());
    assert(text.includes('31 décembre 2025'), 'la date de la tuile IMC/tension doit survivre, pas être masquée');
  });

  test('narrativeUserPrompt efface un horodatage précis (heure) résiduel, filet de sécurité', () => {
    const model = buildNarrativeFixtureModel();
    // Simule une tuile qui embarquerait par erreur un horodatage complet, au lieu
    // d'une date déjà formatée par tiles.js : redactPreciseTimestamps doit l'effacer.
    model.tiles.push({
      key: 'autreChose', label: 'Test', value: '1', unit: null,
      sub: 'Mesuré le 2025-12-31T08:15:00.000Z (epoch 1735632900000)', status: 'neutral',
    });
    const text = HA.prompt.narrativeUserPrompt(model);
    assert(!text.includes('2025-12-31T08:15:00'), "l'horodatage ISO complet (avec l'heure) ne doit pas survivre");
    assert(!text.includes('1735632900000'), "l'epoch en millisecondes ne doit pas survivre");
    assert(text.includes('[horodatage]'), "le filet de sécurité doit poser un repère neutre à la place");
  });

  test('narrativeUserPrompt ne porte jamais les séries quotidiennes ou individuelles (sentinelles 999 absentes)', () => {
    const text = HA.prompt.narrativeUserPrompt(buildNarrativeFixtureModel());
    assert(!/\b999\b/.test(text), 'une mesure individuelle (sentinelle 999 : restingDaily/hrvDaily/bloodPressure/ecg/daily/vitalityDaily/spo2Daily/respiratoryDaily/skinTempDaily) a fuité');
    assert(!/\b999999\b/.test(text), 'une mesure individuelle (sentinelle 999999 : stepsDaily/stepsRolling7) a fuité');
  });

  test('narrativeUserPrompt porte bien les agrégats autorisés (mensuel, tuiles, kpi filtré, corrélations)', () => {
    const text = HA.prompt.narrativeUserPrompt(buildNarrativeFixtureModel());
    assert(text.includes('7,1'), 'la moyenne mensuelle de sommeil doit apparaître, à la française');
    assert(/15[^0-9]?000/.test(text), 'bestSteps (sans sa date) doit apparaître, éventuellement groupé par milliers');
    assert(text.includes('Sommeil'), 'le libellé de tuile doit apparaître');
    assert(text.includes('Durée de sommeil et FC de repos'), 'le libellé de corrélation doit apparaître');
    assert(text.includes('200'), 'le n de la corrélation doit apparaître');
  });

  test('narrativeUserPrompt écrit les nombres à la française et les mois en toutes lettres', () => {
    const text = HA.prompt.narrativeUserPrompt(buildNarrativeFixtureModel());
    assert(text.includes('décembre 2025'), 'un mois AAAA-MM doit devenir son nom complet en français');
    assert(!/\d{4}-\d{2}/.test(text), "aucune clé de mois technique (AAAA-MM) ne doit survivre à l'affichage");
    assert(!text.includes('7.1'), 'la virgule décimale doit remplacer le point partout, pas seulement dans les tuiles');
  });

  test('narrativeUserPrompt ajoute un délai relatif ou un compte aux tuiles IMC et tension, EN PLUS de leur date', () => {
    const text = HA.prompt.narrativeUserPrompt(buildNarrativeFixtureModel());
    assert(text.includes('Dernière mesure : 31 décembre 2025, il y a 78 jours'), 'la tuile IMC doit garder sa date et ajouter le délai relatif, au pluriel correct (78 jours)');
    assert(text.includes('Dernière mesure : 31 décembre 2025 (1 mesure sur la période)'), "la tuile tension doit garder sa date, ajouter le compte de heart.bloodPressure au singulier correct (jamais une valeur individuelle de cette liste), et jamais de notation « (s) »");
  });

  test('narrativeUserPrompt accorde correctement le singulier et le pluriel, jamais de notation « (s) »', () => {
    const model = buildNarrativeFixtureModel();
    model.body.kpi.daysSinceLastMeasure = 1; // singulier : « il y a 1 jour », pas « 1 jour(s) »
    model.heart.bloodPressure = [
      { date: '2025-12-30', systolic: 118, diastolic: 78, pulse: 60 },
      { date: '2025-12-31', systolic: 120, diastolic: 80, pulse: 60 },
    ]; // pluriel : « 2 mesures », un compte, jamais les valeurs elles-mêmes
    const text = HA.prompt.narrativeUserPrompt(model);
    assert(!text.includes('(s)'), "aucune notation « (s) » ne doit jamais apparaître : le LLM lit ce texte et le recopie parfois tel quel");
    assert(/il y a 1 jour(?!s)/.test(text), 'un seul jour doit rester au singulier, jamais « 1 jours »');
    assert(text.includes('(2 mesures sur la période)'), 'deux mesures doivent être écrites au pluriel');
  });

  test('narrativeUserPrompt borne les séries mensuelles aux 24 derniers mois', () => {
    const model = buildNarrativeFixtureModel();
    // 30 mois de moyennes de sommeil : seuls les 24 derniers doivent apparaître.
    model.sleep.monthly = Array.from({ length: 30 }, (_, i) => {
      const month = String(1 + (i % 12)).padStart(2, '0');
      const year = 2023 + Math.floor(i / 12);
      return { month: `${year}-${month}`, meanHours: 7, medianHours: 7, meanScore: 70, nights: 20 };
    });
    const text = HA.prompt.narrativeUserPrompt(model);
    // ", 20 nuits" n'apparaît que dans les lignes générées par ce test (seule section
    // portée à 30 mois) : compter cette sous-chaîne isole la série de sommeil des
    // cinq autres sections mensuelles, qui gardent leur unique mois de la fixture.
    // La ligne d'en-tête (« ... 20 nuits mesurées ») porte la même sous-chaîne par
    // coïncidence : ne compter que les lignes de liste, qui commencent par « - ».
    const monthLines = text.split('\n').filter((line) => line.startsWith('- ') && line.includes(', 20 nuits'));
    assertEqual(monthLines.length, 24, 'seuls les 24 derniers mois doivent être écrits, pour ne pas laisser le prompt grossir sans fin');
  });

  // ======================================================================
  // prompt.js — historyOverview / describeHistoryOverview : aperçu {{OVERVIEW}}
  // ======================================================================

  test('historyOverview regroupe les jours actifs par mois, sans double compte', () => {
    const model = {
      sleep: { nightly: [{ date: '2025-01-05' }, { date: '2025-01-06' }] },
      // Même jour que la première nuit : ne doit pas compter une seconde fois.
      activity: { stepsDaily: [{ date: '2025-01-05' }] },
      heart: { restingDaily: [{ date: '2025-02-01' }] },
    };

    const overview = HA.prompt.historyOverview(model);

    assertEqual(overview.earliest, '2025-01-05');
    assertEqual(overview.latest, '2025-02-01');
    assertEqual(overview.months, [
      { month: '2025-01', daysWithData: 2 },
      { month: '2025-02', daysWithData: 1 },
    ]);
  });

  test('historyOverview sur un modèle vide rend un aperçu vide', () => {
    const overview = HA.prompt.historyOverview({});
    assertEqual(overview, { earliest: null, latest: null, months: [] });
  });

  test('describeHistoryOverview ne porte qu\'un compte de jours, jamais une valeur', () => {
    const model = { sleep: { nightly: [{ date: '2025-01-05', hours: 7, score: 88 }] } };
    const text = HA.prompt.describeHistoryOverview(HA.prompt.historyOverview(model));
    assert(text.includes('Historique disponible : du 2025-01-05 au 2025-01-05.'), 'bornes de l\'historique');
    assert(text.includes('- 2025-01 : 1 jours'), 'compte de jours du mois');
    assert(!text.includes('88'), 'le score ne doit jamais sortir dans l\'aperçu');
  });

  test('describeHistoryOverview dit explicitement l\'absence de donnée', () => {
    const text = HA.prompt.describeHistoryOverview(HA.prompt.historyOverview({}));
    assert(text.includes('Aucune donnée'));
  });

  // ======================================================================
  // narrative.js — extraction et validation de la réponse du LLM
  // ======================================================================

  const MINIMAL_NARRATIVE_JSON = JSON.stringify({
    headline: 'Titre bref',
    verdict: 'Un verdict de deux phrases.',
    sections: { sleep: { verdict: 'Ça dort.', points: ['Un point.'] } },
    plan: [{ title: 'Faire X', body: 'Pour telle raison.' }],
  });

  test('parseNarrativeResponse lit un JSON strict', () => {
    const { narrative, error } = HA.narrative.parseNarrativeResponse(MINIMAL_NARRATIVE_JSON);
    assertEqual(error, null);
    assertEqual(narrative.headline, 'Titre bref');
    assertEqual(narrative.sections.sleep.points, ['Un point.']);
  });

  test('parseNarrativeResponse extrait le JSON entouré de prose', () => {
    const text = `Voici le bilan demandé :\n${MINIMAL_NARRATIVE_JSON}\nJ'espère que cela aide.`;
    const { narrative, error } = HA.narrative.parseNarrativeResponse(text);
    assertEqual(error, null);
    assertEqual(narrative.headline, 'Titre bref');
  });

  test('parseNarrativeResponse extrait le JSON entouré d\'un bloc de code Markdown', () => {
    const text = '```json\n' + MINIMAL_NARRATIVE_JSON + '\n```';
    const { narrative, error } = HA.narrative.parseNarrativeResponse(text);
    assertEqual(error, null);
    assertEqual(narrative.verdict, 'Un verdict de deux phrases.');
  });

  test('parseNarrativeResponse ignore les accolades internes aux chaînes en comptant la profondeur', () => {
    const withBraces = JSON.stringify({
      headline: 'Titre { avec accolade }',
      verdict: 'Verdict.',
      sections: {},
      plan: [],
    });
    const text = `Avant\n${withBraces}\nAprès`;
    const { narrative, error } = HA.narrative.parseNarrativeResponse(text);
    assertEqual(error, null);
    assertEqual(narrative.headline, 'Titre { avec accolade }');
  });

  test('parseNarrativeResponse rend narrative:null et un message clair sur un texte sans JSON exploitable', () => {
    const { narrative, error } = HA.narrative.parseNarrativeResponse('Désolé, je ne peux pas répondre.');
    assertEqual(narrative, null);
    assert(typeof error === 'string' && error.length > 0, 'un message d\'erreur doit être fourni');
  });

  test('parseNarrativeResponse rend narrative:null quand headline ou verdict manque', () => {
    const { narrative, error } = HA.narrative.parseNarrativeResponse(JSON.stringify({ sections: {}, plan: [] }));
    assertEqual(narrative, null);
    assert(typeof error === 'string' && error.length > 0);
  });

  test('parseNarrativeResponse enlève les clés de section inconnues et les points non-textuels sans planter', () => {
    const text = JSON.stringify({
      headline: 'Titre',
      verdict: 'Verdict.',
      sections: {
        sleep: { verdict: 'ok', points: ['bien', 42, null] },
        inconnue: { verdict: 'ne doit pas apparaître', points: [] },
      },
      plan: [{ title: 'A', body: 'B' }, { title: 42, body: 'invalide' }],
    });
    const { narrative, error } = HA.narrative.parseNarrativeResponse(text);
    assertEqual(error, null);
    assertEqual(narrative.sections.sleep.points, ['bien']);
    assert(!('inconnue' in narrative.sections), 'une clé de section hors contrat ne doit pas survivre');
    assertEqual(narrative.plan.length, 1, 'une étape de plan mal formée doit être filtrée, pas planter');
  });

  // ======================================================================
  // report-export.js — échappement JSON, gabarit autonome
  // ======================================================================

  test('escapeJsonForScript retire tout caractère < (protège contre une balise de fermeture cachée)', () => {
    const dangerous = JSON.stringify({ text: '</script><script>alert(1)</script>' });
    const escaped = HA.reportExport.escapeJsonForScript(dangerous);
    assert(!escaped.includes('<'), 'aucun caractère < ne doit survivre à l\'échappement');
    const restored = JSON.parse(escaped.replace(/\\u003c/g, '<'));
    assertEqual(restored.text, '</script><script>alert(1)</script>', 'le JSON rééchappé doit redonner le texte original telle quelle');
  });

  test('buildExportHtml produit un document autonome où le modèle survit intact malgré une tentative de coupure', async () => {
    const model = buildNarrativeFixtureModel();
    model.narrative = {
      headline: 'Titre </script><script>evil()</script> piégé',
      verdict: 'V', sections: {}, plan: [],
    };
    const html = await HA.reportExport.buildExportHtml(model);
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const scripts = doc.querySelectorAll('script');
    assertEqual(scripts.length, 3, 'le document ne doit porter que les 3 <script> du gabarit, jamais un 4e injecté');
    const modelScript = doc.getElementById('ha-model');
    assert(modelScript !== null, 'le script #ha-model doit exister');
    const restoredModel = JSON.parse(modelScript.textContent);
    assertEqual(restoredModel.narrative.headline, model.narrative.headline, 'le titre piégé doit survivre intact au lieu de couper le document');
    assert(html.includes('HA.report'), 'le moteur de rendu doit être inclus dans le document exporté');
  });

  // ======================================================================
  // Rendu des résultats
  // ======================================================================

  function render() {
    const passCount = results.filter((r) => r.pass).length;
    const summary = document.getElementById('summary');
    summary.textContent = `${passCount} / ${results.length} tests réussis`;
    summary.className = passCount === results.length ? 'summary ok' : 'summary ko';

    const list = document.getElementById('results');
    list.innerHTML = results.map((r) => `
      <li class="${r.pass ? 'pass' : 'fail'}">
        <span class="icon">${r.pass ? '✓' : '✗'}</span>
        <span class="name">${r.name}</span>
        ${r.pass ? '' : `<div class="error">${r.error}</div>`}
      </li>
    `).join('');
  }

  // ======================================================================
  // llm.js — conversation multi-tours, compatibilité, corps de requête
  // ======================================================================

  /**
   * Remplace `self.fetch` le temps d'exécuter `run`, capture l'URL et les options du
   * premier appel, puis restaure la fonction d'origine même en cas d'erreur.
   */
  async function withMockedFetch(responseBody, run) {
    const originalFetch = self.fetch;
    let captured = null;
    self.fetch = async (url, options) => {
      captured = { url, options };
      return { ok: true, status: 200, json: async () => responseBody };
    };
    try {
      await run();
    } finally {
      self.fetch = originalFetch;
    }
    return captured;
  }

  test('normalizeMessages garde un fil deja alterne inchange', () => {
    const messages = [
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Bonjour, que puis-je faire ?' },
      { role: 'user', content: 'Analyse mon sommeil.' },
    ];
    assertEqual(HA.llm.normalizeMessages(messages), messages);
  });

  test('normalizeMessages fusionne deux messages utilisateur consecutifs', () => {
    const result = HA.llm.normalizeMessages([
      { role: 'user', content: 'Premier message.' },
      { role: 'user', content: 'Deuxième message.' },
    ]);
    assertEqual(result, [{ role: 'user', content: 'Premier message.\n\nDeuxième message.' }]);
  });

  test('normalizeMessages fusionne deux messages assistant consecutifs', () => {
    const result = HA.llm.normalizeMessages([
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Première partie.' },
      { role: 'assistant', content: 'Seconde partie.' },
    ]);
    assertEqual(result, [
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Première partie.\n\nSeconde partie.' },
    ]);
  });

  test('normalizeMessages enleve un message assistant en tete', () => {
    const result = HA.llm.normalizeMessages([
      { role: 'assistant', content: 'Je ne devrais pas être en tête.' },
      { role: 'user', content: 'Analyse mon sommeil.' },
    ]);
    assertEqual(result, [{ role: 'user', content: 'Analyse mon sommeil.' }]);
  });

  test('analyze accepte encore un userPrompt simple (compatibilite)', async () => {
    const captured = await withMockedFetch({ content: [{ type: 'text', text: 'ok' }] }, () => HA.llm.analyze('anthropic', {
      apiKey: 'sk-test',
      model: 'claude-sonnet-5',
      systemPrompt: 'Tu es un assistant santé.',
      userPrompt: 'Analyse mes données de sommeil.',
    }));
    const body = JSON.parse(captured.options.body);
    assertEqual(body.messages, [{ role: 'user', content: 'Analyse mes données de sommeil.' }]);
  });

  test('analyze envoie le fil complet pour anthropic dans l ordre', async () => {
    const captured = await withMockedFetch({ content: [{ type: 'text', text: 'ok' }] }, () => HA.llm.analyze('anthropic', {
      apiKey: 'sk-test',
      model: 'claude-sonnet-5',
      systemPrompt: 'Tu es un assistant santé.',
      messages: [
        { role: 'user', content: 'Bonjour' },
        { role: 'assistant', content: 'Bonjour, que puis-je faire ?' },
        { role: 'user', content: 'Analyse mon sommeil.' },
      ],
    }));
    const body = JSON.parse(captured.options.body);
    assertEqual(body.messages, [
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Bonjour, que puis-je faire ?' },
      { role: 'user', content: 'Analyse mon sommeil.' },
    ]);
    assertEqual(body.system, 'Tu es un assistant santé.');
  });

  test('analyze garde le systeme en tete pour openai puis le fil de conversation', async () => {
    const captured = await withMockedFetch({ choices: [{ message: { role: 'assistant', content: 'ok' } }] }, () => HA.llm.analyze('openai', {
      apiKey: 'sk-test',
      model: 'gpt-5.4',
      systemPrompt: 'Tu es un assistant santé.',
      messages: [
        { role: 'user', content: 'Bonjour' },
        { role: 'assistant', content: 'Salut' },
      ],
    }));
    const body = JSON.parse(captured.options.body);
    assertEqual(body.messages, [
      { role: 'system', content: 'Tu es un assistant santé.' },
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Salut' },
    ]);
  });

  test('analyze utilise le role model pour l assistant chez gemini', async () => {
    const captured = await withMockedFetch({ candidates: [{ content: { parts: [{ text: 'ok' }] } }] }, () => HA.llm.analyze('gemini', {
      apiKey: 'gem-test',
      model: 'gemini-3.1-pro-preview',
      systemPrompt: 'Tu es un assistant santé.',
      messages: [
        { role: 'user', content: 'Bonjour' },
        { role: 'assistant', content: 'Salut' },
      ],
    }));
    const body = JSON.parse(captured.options.body);
    assertEqual(body.contents, [
      { role: 'user', parts: [{ text: 'Bonjour' }] },
      { role: 'model', parts: [{ text: 'Salut' }] },
    ]);
    assert(!('role' in body.systemInstruction), 'systemInstruction ne doit pas porter de role');
  });

  test('analyze fusionne et corrige un fil invalide avant de l envoyer (openai)', async () => {
    const captured = await withMockedFetch({ choices: [{ message: { role: 'assistant', content: 'ok' } }] }, () => HA.llm.analyze('openai', {
      apiKey: 'sk-test',
      model: 'gpt-5.4',
      systemPrompt: 'Tu es un assistant santé.',
      messages: [
        { role: 'assistant', content: 'Residu en tete.' },
        { role: 'user', content: 'Premier message.' },
        { role: 'user', content: 'Deuxième message.' },
      ],
    }));
    const body = JSON.parse(captured.options.body);
    assertEqual(body.messages, [
      { role: 'system', content: 'Tu es un assistant santé.' },
      { role: 'user', content: 'Premier message.\n\nDeuxième message.' },
    ]);
  });

  // ======================================================================
  // chat/chat-view.js — parseAssistant (découpage texte / graphique)
  // ======================================================================

  test('parseAssistant rend un seul segment de texte sans bloc healthchart', () => {
    const segments = HA.chat.parseAssistant('Votre sommeil est stable ce mois-ci.');
    assertEqual(segments, [{ type: 'text', text: 'Votre sommeil est stable ce mois-ci.' }]);
  });

  test('parseAssistant rend un texte vide comme un unique segment de texte vide', () => {
    assertEqual(HA.chat.parseAssistant(''), [{ type: 'text', text: '' }]);
    assertEqual(HA.chat.parseAssistant(undefined), [{ type: 'text', text: '' }]);
  });

  test('parseAssistant sépare texte avant, graphique, texte après', () => {
    const text = [
      'Avant le graphique.',
      '',
      '```healthchart',
      '{ "mark": "line", "title": "Sommeil", "series": [{ "ref": "sleep.nightly.hours" }] }',
      '```',
      '',
      'Après le graphique.',
    ].join('\n');
    const segments = HA.chat.parseAssistant(text);
    assertEqual(segments.length, 3, 'trois segments attendus');
    assertEqual(segments[0].type, 'text');
    assert(segments[0].text.includes('Avant le graphique.'), 'le texte avant doit être gardé');
    assertEqual(segments[1].type, 'chart');
    assertEqual(segments[1].spec, { mark: 'line', title: 'Sommeil', series: [{ ref: 'sleep.nightly.hours' }] });
    assertEqual(segments[1].error, null);
    assertEqual(segments[2].type, 'text');
    assert(segments[2].text.includes('Après le graphique.'), 'le texte après doit être gardé');
  });

  test('parseAssistant ne garde pas de segment de texte vide entre deux graphiques', () => {
    const text = [
      '```healthchart',
      '{ "mark": "line", "title": "A", "series": [{ "ref": "sleep.nightly.hours" }] }',
      '```',
      '```healthchart',
      '{ "mark": "bar", "title": "B", "series": [{ "ref": "activity.stepsDaily" }] }',
      '```',
    ].join('\n');
    const segments = HA.chat.parseAssistant(text);
    assertEqual(segments.map((s) => s.type), ['chart', 'chart'], 'aucun segment de texte parasite entre les deux blocs');
  });

  test('parseAssistant signale un bloc JSON illisible sans lever d\'exception', () => {
    const text = '```healthchart\n{ mark: line, title: "cassé" series: [] }\n```';
    const segments = HA.chat.parseAssistant(text);
    assertEqual(segments.length, 1);
    assertEqual(segments[0].type, 'chart');
    assertEqual(segments[0].spec, null);
    assert(typeof segments[0].error === 'string' && segments[0].error.length > 0, 'une erreur lisible doit être fournie');
  });

  test('parseAssistant refuse un bloc dont le JSON n\'est pas un objet', () => {
    const segments = HA.chat.parseAssistant('```healthchart\n[1, 2, 3]\n```');
    assertEqual(segments[0].spec, null);
    assert(segments[0].error.length > 0, 'un tableau JSON ne doit pas passer pour une spécification');
  });

  test('parseAssistant retire un bloc healthrange resté dans la réponse (filet de sécurité)', () => {
    // `runConversationTurn` résout toujours healthrange en interne — voir lib/chat.js.
    // Ce test couvre le cas résiduel où le modèle en écrirait un troisième, non traité.
    const segments = HA.chat.parseAssistant(
      'Sans cette période, voici ce que je peux dire.\n\n```healthrange\n{ "from": "2025-01-01", "to": "2025-01-31" }\n```',
    );
    assertEqual(segments.length, 1);
    assertEqual(segments[0].type, 'text');
    assert(!segments[0].text.includes('healthrange'), 'le bloc healthrange ne doit jamais être montré');
  });

  // ======================================================================
  // lib/chat.js — assemblage du prompt système et bornage de l'historique
  // ======================================================================

  /** Un `ReportModel` minimal mais valide, avec deux nuits pour peupler une série. */
  function buildChatFixtureModel() {
    const model = HA.reportModel.emptyReportModel();
    model.meta.periodLabel = '1 au 2 janvier 2026';
    model.sleep.nightly = [
      { date: '2026-01-01', hours: 7, score: 80, bedRel: -1, efficiencyPercent: 90 },
      { date: '2026-01-02', hours: 6.5, score: 75, bedRel: -0.5, efficiencyPercent: 88 },
    ];
    return model;
  }

  test('formatSeriesList liste les séries disponibles avec leur unité et leur nombre de points', () => {
    const model = buildChatFixtureModel();
    const list = HA.chatPrompt.formatSeriesList(model);
    assert(list.includes('sleep.nightly.hours'), 'la référence de série doit apparaître');
    assert(list.includes('Durée de sommeil par nuit'), 'le libellé doit apparaître');
    assert(list.includes('2 points'), 'le nombre de points doit apparaître');
  });

  test('formatSeriesList signale l\'absence de série exploitable', () => {
    const model = HA.reportModel.emptyReportModel();
    assertEqual(HA.chatPrompt.formatSeriesList(model), 'Aucune série disponible sur cette période.');
  });

  test('assembleSystemPrompt remplace les deux marqueurs sans laisser de trace', () => {
    const model = buildChatFixtureModel();
    const template = 'Intro.\n\nSÉRIES :\n{{SERIES}}\n\nAGRÉGATS :\n{{AGGREGATES}}\n\nFin.';
    const prompt = HA.chatPrompt.assembleSystemPrompt(template, model);
    assert(!prompt.includes('{{SERIES}}'), 'le marqueur SERIES doit disparaître');
    assert(!prompt.includes('{{AGGREGATES}}'), 'le marqueur AGGREGATES doit disparaître');
    assert(prompt.includes('sleep.nightly.hours'), 'la liste des séries doit être insérée');
    assert(prompt.includes(HA.prompt.narrativeUserPrompt(model)), 'les agrégats doivent venir de narrativeUserPrompt telle quelle');
  });

  test('assembleSystemPrompt n\'envoie que des agrégats, jamais une date au format ISO seul (série quotidienne)', () => {
    // Régression de confidentialité : le prompt système ne doit jamais recopier
    // une ligne brute type `2026-01-01,7` — seuls des agrégats et libellés de
    // série (jamais leurs valeurs quotidiennes) doivent apparaître.
    const model = buildChatFixtureModel();
    const prompt = HA.chatPrompt.assembleSystemPrompt('{{SERIES}}\n{{AGGREGATES}}', model);
    assert(!prompt.includes('2026-01-01'), 'aucune date de mesure quotidienne ne doit fuiter dans le prompt système');
  });

  test('toLlmMessages met la conversation sous la forme {role, content}', () => {
    const conversation = [
      { role: 'user', text: 'Bonjour', at: 1 },
      { role: 'assistant', text: 'Salut', at: 2 },
    ];
    assertEqual(HA.chatPrompt.toLlmMessages(conversation), [
      { role: 'user', content: 'Bonjour' },
      { role: 'assistant', content: 'Salut' },
    ]);
  });

  test('toLlmMessages borne l\'historique aux 20 derniers messages par défaut', () => {
    const conversation = [];
    for (let i = 0; i < 25; i += 1) {
      conversation.push({ role: i % 2 === 0 ? 'user' : 'assistant', text: `message ${i}`, at: i });
    }
    const result = HA.chatPrompt.toLlmMessages(conversation);
    assertEqual(result.length, 20, 'seuls les 20 derniers messages doivent être gardés');
    assertEqual(result[0].content, 'message 5', 'le premier message gardé doit être le 6e (index 5)');
    assertEqual(result[result.length - 1].content, 'message 24', 'le dernier message doit être le plus récent');
  });

  test('toLlmMessages accepte une borne explicite', () => {
    const conversation = [
      { role: 'user', text: 'a', at: 1 },
      { role: 'assistant', text: 'b', at: 2 },
      { role: 'user', text: 'c', at: 3 },
    ];
    assertEqual(HA.chatPrompt.toLlmMessages(conversation, 2), [
      { role: 'assistant', content: 'b' },
      { role: 'user', content: 'c' },
    ]);
  });

  // ======================================================================
  // lib/chat.js — healthrange : le modèle choisit lui-même sa fenêtre
  // ======================================================================

  /** L'aperçu d'historique utilisé par les tests de `parseHealthRange` ci-dessous. */
  function buildRangeOverviewFixture() {
    return {
      earliest: '2024-06-01',
      latest: '2025-08-31',
      months: [
        { month: '2024-06', daysWithData: 20 },
        { month: '2024-07', daysWithData: 25 },
        // Août 2024 : un trou dans l'historique de test, volontairement absent.
        { month: '2024-09', daysWithData: 10 },
        { month: '2025-01', daysWithData: 28 },
        { month: '2025-08', daysWithData: 5 },
      ],
    };
  }

  test('parseHealthRange sans bloc healthrange ne trouve rien', () => {
    const outcome = HA.chatPrompt.parseHealthRange('Votre sommeil va bien.', buildRangeOverviewFixture());
    assertEqual(outcome, { type: 'notFound' });
  });

  test('parseHealthRange accepte une fenêtre valide dans l\'historique', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2025-01-01", "to": "2025-01-31" }\n```',
      buildRangeOverviewFixture(),
    );
    assertEqual(outcome, { type: 'accepted', from: '2025-01-01', to: '2025-01-31', note: null });
  });

  test('parseHealthRange remet dans l\'ordre des bornes inversées', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2025-01-31", "to": "2025-01-01" }\n```',
      buildRangeOverviewFixture(),
    );
    assert(outcome.type === 'accepted' && outcome.from === '2025-01-01' && outcome.to === '2025-01-31');
    assert(outcome.note.includes('inversées'), 'une note doit dire que les bornes ont été corrigées');
  });

  test('parseHealthRange tolère une date au format français', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "01/01/2025", "to": "31/01/2025" }\n```',
      buildRangeOverviewFixture(),
    );
    assertEqual(outcome, { type: 'accepted', from: '2025-01-01', to: '2025-01-31', note: null });
  });

  test('parseHealthRange refuse un JSON illisible', () => {
    const outcome = HA.chatPrompt.parseHealthRange('```healthrange\n{ pas du json\n```', buildRangeOverviewFixture());
    assert(outcome.type === 'rejected' && outcome.reason.includes('JSON'));
  });

  test('parseHealthRange refuse des champs from/to manquants', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2025-01-01" }\n```',
      buildRangeOverviewFixture(),
    );
    assert(outcome.type === 'rejected' && outcome.reason.includes('obligatoires'));
  });

  test('parseHealthRange refuse une période entièrement hors historique', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2019-01-01", "to": "2019-12-31" }\n```',
      buildRangeOverviewFixture(),
    );
    assert(outcome.type === 'rejected' && outcome.reason.includes('2024-06-01'));
  });

  test('parseHealthRange ramène une période partiellement hors historique aux bornes disponibles', () => {
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2025-08-01", "to": "2025-12-31" }\n```',
      buildRangeOverviewFixture(),
    );
    assert(outcome.type === 'accepted' && outcome.from === '2025-08-01' && outcome.to === '2025-08-31');
    assert(outcome.note.includes('ramenée'));
  });

  test('parseHealthRange refuse une période sans aucune mesure dans les mois couverts', () => {
    // Août 2024 est le trou volontaire de `buildRangeOverviewFixture`.
    const outcome = HA.chatPrompt.parseHealthRange(
      '```healthrange\n{ "from": "2024-08-01", "to": "2024-08-31" }\n```',
      buildRangeOverviewFixture(),
    );
    assert(outcome.type === 'rejected' && outcome.reason.includes('aucune mesure'));
  });

  /** Un `ReportModel` couvrant 24 mois (2024-2025), pour que les fenêtres testées ci-dessous soient acceptées. */
  function buildBroadHistoryModel() {
    const model = HA.reportModel.emptyReportModel();
    model.activity.stepsDaily = Array.from({ length: 24 }, (_, i) => {
      const year = 2024 + Math.floor(i / 12);
      const month = String((i % 12) + 1).padStart(2, '0');
      return { date: `${year}-${month}-15`, value: 8000 };
    });
    return model;
  }

  test('runConversationTurn sans bloc healthrange rend la réponse telle quelle, sans fenêtre choisie', async () => {
    const historyModel = buildBroadHistoryModel();
    const requests = [];
    const result = await HA.chatPrompt.runConversationTurn({
      template: 'SERIES:{{SERIES}}\nOVERVIEW:{{OVERVIEW}}\nAGGREGATES:{{AGGREGATES}}',
      historyModel,
      activeModel: null,
      messages: [{ role: 'user', content: 'Comment je dors ?' }],
      complete: async (systemPrompt) => {
        requests.push(systemPrompt);
        return { text: 'Votre sommeil va bien.' };
      },
      buildReportForRange: async () => { throw new Error('ne doit pas être appelé sans healthrange'); },
    });

    assertEqual(result.reply, 'Votre sommeil va bien.');
    assertEqual(result.activeModel, null);
    assertEqual(result.statusNote, null);
    assertEqual(requests.length, 1);
    assert(requests[0].includes('Aucune période n\'est encore choisie'), 'sans fenêtre, le prompt ne doit porter aucun chiffre détaillé');
  });

  test('runConversationTurn accorde une fenêtre valide et reconstruit le rapport', async () => {
    const historyModel = buildBroadHistoryModel();
    const rebuilt = buildBroadHistoryModel();
    const requests = [];
    let buildCalls = 0;

    const result = await HA.chatPrompt.runConversationTurn({
      template: 'SERIES:{{SERIES}}\nOVERVIEW:{{OVERVIEW}}\nAGGREGATES:{{AGGREGATES}}',
      historyModel,
      activeModel: null,
      messages: [{ role: 'user', content: 'Et en mars 2025 ?' }],
      complete: async (systemPrompt) => {
        requests.push(systemPrompt);
        if (requests.length === 1) return { text: '```healthrange\n{ "from": "2025-03-01", "to": "2025-03-31" }\n```' };
        return { text: 'En mars 2025, tout va bien.' };
      },
      buildReportForRange: async (from, to) => {
        buildCalls += 1;
        assertEqual([from, to], ['2025-03-01', '2025-03-31']);
        return rebuilt;
      },
    });

    assertEqual(result.reply, 'En mars 2025, tout va bien.');
    assertEqual(result.activeModel, rebuilt);
    assert(!!result.statusNote && result.statusNote.includes('2025-03-01'));
    assertEqual(buildCalls, 1);
    assertEqual(requests.length, 2);
    assert(!requests[1].includes('Aucune période n\'est encore choisie'), 'le second appel doit porter les vrais agrégats');
  });

  test('runConversationTurn s\'arrête après deux demandes de fenêtre et répond avec ce qu\'elle a', async () => {
    const historyModel = buildBroadHistoryModel();
    let callCount = 0;
    let buildCalls = 0;

    const result = await HA.chatPrompt.runConversationTurn({
      template: 'SERIES:{{SERIES}}\nOVERVIEW:{{OVERVIEW}}\nAGGREGATES:{{AGGREGATES}}',
      historyModel,
      activeModel: null,
      messages: [{ role: 'user', content: 'Compare janvier, février et mars 2025' }],
      complete: async () => {
        callCount += 1;
        const month = String(callCount).padStart(2, '0');
        return { text: `\`\`\`healthrange\n{ "from": "2025-${month}-01", "to": "2025-${month}-28" }\n\`\`\`` };
      },
      buildReportForRange: async () => { buildCalls += 1; return historyModel; },
    });

    assertEqual(callCount, 3, 'au plus deux demandes de fenêtre, plus l\'appel qui les suit');
    assertEqual(buildCalls, 2, 'seules les deux premières demandes sont honorées');
    assert(result.reply.includes('healthrange'), 'la réponse du dernier appel part telle quelle (filtrée à l\'affichage, voir chat-view.js)');
  });

  test('un healthchart de la réponse porte la fenêtre demandée, à graver sur le message', () => {
    // Bout en bout : le modèle demande février, `activeModel.meta` porte alors février —
    // c'est cette paire {from, to} que l'appelant (`app-chat.js`) grave sur le message
    // (`rangeFrom`/`rangeTo`), pour que son graphique se résolve toujours avec elle. Le
    // modèle complet, lui, reste inchangé : voir le test suivant pour la résolution.
    const historyModel = HA.reportModel.emptyReportModel();
    historyModel.sleep.nightly = [
      { date: '2025-01-10', hours: 6.0 },
      { date: '2025-02-05', hours: 7.0 },
      { date: '2025-02-20', hours: 7.5 },
      { date: '2025-03-15', hours: 8.0 },
    ];

    return HA.chatPrompt.runConversationTurn({
      template: 'SERIES:{{SERIES}}\nOVERVIEW:{{OVERVIEW}}\nAGGREGATES:{{AGGREGATES}}',
      historyModel,
      activeModel: null,
      messages: [{ role: 'user', content: 'Et en février ?' }],
      complete: async () => ({ text: '```healthrange\n{ "from": "2025-02-01", "to": "2025-02-28" }\n```' }),
      buildReportForRange: async (from, to) => {
        // Simule ce que fait `HealthRepository.buildReport` / `readSources` : `meta.from`/
        // `meta.to` portent la fenêtre demandée, comme `ReportBuilder.kt` le fait aussi.
        const nightly = historyModel.sleep.nightly.filter((n) => n.date >= from && n.date <= to);
        return { ...historyModel, meta: { ...historyModel.meta, from, to }, sleep: { ...historyModel.sleep, nightly } };
      },
    }).then((result) => {
      assertEqual(result.activeModel.meta.from, '2025-02-01');
      assertEqual(result.activeModel.meta.to, '2025-02-28');

      // Résolu comme `chat-view.js` le fait : contre le modèle COMPLET, filtré par la
      // fenêtre du message — jamais contre `result.activeModel` (rebâti et donc borné).
      const range = { from: result.activeModel.meta.from, to: result.activeModel.meta.to };
      const resolved = HA.chartCatalog.resolve('sleep.nightly.hours', historyModel, range);
      assertEqual(resolved.points, [{ x: '2025-02-05', y: 7 }, { x: '2025-02-20', y: 7.5 }]);
    });
  });

  test('chartCatalog.resolve filtre les points d\'axe temporel par la fenêtre donnée', () => {
    const model = HA.reportModel.emptyReportModel();
    model.sleep.nightly = [
      { date: '2025-01-10', hours: 6.0 },
      { date: '2025-02-05', hours: 7.0 },
      { date: '2025-03-15', hours: 8.0 },
    ];

    const resolved = HA.chartCatalog.resolve('sleep.nightly.hours', model, { from: '2025-02-01', to: '2025-02-28' });

    assertEqual(resolved.points, [{ x: '2025-02-05', y: 7 }]);
  });

  test('chartCatalog.resolve sans fenêtre rend tout l\'historique', () => {
    const model = HA.reportModel.emptyReportModel();
    model.sleep.nightly = [
      { date: '2025-01-10', hours: 6.0 },
      { date: '2025-02-05', hours: 7.0 },
    ];

    const resolved = HA.chartCatalog.resolve('sleep.nightly.hours', model);

    assertEqual(resolved.points.length, 2);
  });

  test('chartCatalog.resolve n\'applique pas la fenêtre à un axe par bandes (jour de semaine)', () => {
    const model = HA.reportModel.emptyReportModel();
    model.sleep.dayOfWeek = [{ label: 'lun.', value: 7 }, { label: 'mar.', value: 6.5 }];

    // Un axe `band` n'a pas de date par point : une fenêtre ne changerait rien à ce qu'il
    // montre, le filtrer serait donc une erreur silencieuse (tout disparaîtrait).
    const resolved = HA.chartCatalog.resolve('sleep.dayOfWeek', model, { from: '2025-02-01', to: '2025-02-28' });

    assertEqual(resolved.points.length, 2);
  });

  test('deux messages avec des fenêtres différentes gardent chacun leurs propres données', () => {
    // C'est le défaut de justesse à éviter : un graphique dont le titre annonce une
    // période et dont la courbe en montre une autre. Le modèle complet est unique
    // (`historyModel`) ; seule la fenêtre gravée sur chaque message doit changer sa
    // résolution — jamais une fenêtre « active » partagée par toute la conversation.
    const historyModel = HA.reportModel.emptyReportModel();
    historyModel.sleep.nightly = [
      { date: '2025-01-08', hours: 6.5 },
      { date: '2025-01-10', hours: 6.0 },
      { date: '2025-03-12', hours: 7.5 },
      { date: '2025-03-15', hours: 8.0 },
    ];

    const chartSpec = { mark: 'line', title: 'Sommeil', series: [{ ref: 'sleep.nightly.hours' }] };

    const firstMessageRange = { from: '2025-01-01', to: '2025-01-31' };
    const secondMessageRange = { from: '2025-03-01', to: '2025-03-31' };

    const firstResolved = HA.chartSpec.validate(chartSpec, historyModel, firstMessageRange).spec;
    const secondResolved = HA.chartSpec.validate(chartSpec, historyModel, secondMessageRange).spec;

    assertEqual(firstResolved.series[0].points, [{ x: '2025-01-08', y: 6.5 }, { x: '2025-01-10', y: 6 }]);
    assertEqual(secondResolved.series[0].points, [{ x: '2025-03-12', y: 7.5 }, { x: '2025-03-15', y: 8 }]);

    // Re-résoudre le premier message après le second ne doit rien avoir changé : sa
    // fenêtre lui appartient, elle ne dérive pas avec la conversation.
    const firstResolvedAgain = HA.chartSpec.validate(chartSpec, historyModel, firstMessageRange).spec;
    assertEqual(firstResolvedAgain.series[0].points, [{ x: '2025-01-08', y: 6.5 }, { x: '2025-01-10', y: 6 }]);
  });

  test('HA.chat.render dessine un healthchart avec la fenêtre gravée sur le message (rangeFrom/rangeTo)', () => {
    const historyModel = HA.reportModel.emptyReportModel();
    historyModel.sleep.nightly = [
      { date: '2025-01-10', hours: 6.0 },
      { date: '2025-02-05', hours: 7.0 },
      { date: '2025-02-20', hours: 7.5 },
    ];
    const chartBlock = '```healthchart\n{ "mark": "line", "title": "Sommeil", "series": [{ "ref": "sleep.nightly.hours" }] }\n```';
    const message = { role: 'assistant', text: chartBlock, rangeFrom: '2025-02-01', rangeTo: '2025-02-28' };

    const host = document.createElement('div');
    HA.chat.render(host, [message], historyModel);

    const card = host.querySelector('.ha-chat-chart-card');
    assert(card, 'la carte de graphique doit exister');
    assert(!card.querySelector('.ha-note'), 'aucune note d\'échec ne doit apparaître');
    assert(card.querySelector('svg'), 'le graphique doit être dessiné');
  });

  // ======================================================================
  // chat/chat-view.js — amorce de premier usage (conversation vide)
  // ======================================================================

  test('introText mentionne la période et accorde « nuit(s) » au pluriel', () => {
    const text = HA.chat.introText({ meta: { periodLabel: '1er janvier au 31 mars 2026', nights: 42 } });
    assert(text.includes('1er janvier au 31 mars 2026'), 'la période doit apparaître');
    assert(text.includes('42 nuits de sommeil mesurées'), 'le pluriel doit être utilisé au-delà de 1');
  });

  test('introText accorde « nuit » et « mesurée » au singulier pour une seule nuit', () => {
    const text = HA.chat.introText({ meta: { periodLabel: 'hier', nights: 1 } });
    assert(text.includes('1 nuit de sommeil mesurée)'), 'le nom et le participe doivent tous deux rester au singulier pour une seule nuit');
    assert(!text.includes('mesurées'), 'le participe ne doit pas rester au pluriel quand le nom repasse au singulier');
  });

  test('introText reste une phrase correcte sans période ni nuit connues', () => {
    const text = HA.chat.introText({ meta: {} });
    assert(text.startsWith('Posez une question sur vos données de santé.'), 'aucun « sur » ni parenthèse vide ne doit traîner');
    assert(!text.includes('  '), 'aucune double espace ne doit apparaître quand période et nuits manquent');
  });

  test('introText tolère un modèle absent', () => {
    const text = HA.chat.introText(null);
    assert(typeof text === 'string' && text.length > 0);
  });

  test('SUGGESTED_QUESTIONS porte quatre questions non vides, dont une qui appelle un graphique', () => {
    assertEqual(HA.chat.SUGGESTED_QUESTIONS.length, 4);
    HA.chat.SUGGESTED_QUESTIONS.forEach((q) => assert(typeof q === 'string' && q.trim().length > 0));
    assert(HA.chat.SUGGESTED_QUESTIONS.some((q) => /montre|graphique/i.test(q)), 'au moins une suggestion doit inviter un graphique');
  });

  test('render dessine l\'amorce de premier usage sur une conversation vide, avec les quatre suggestions', () => {
    const host = document.createElement('div');
    HA.chat.render(host, [], { meta: { periodLabel: 'mars 2026', nights: 5 } });
    assert(host.classList.contains('ha-chat-empty'), 'le conteneur doit porter le modificateur ha-chat-empty');
    assert(host.querySelector('.ha-chat-intro-text') !== null, 'la phrase d\'amorce doit être présente');
    assertEqual(host.querySelectorAll('.ha-chat-suggestion').length, 4);
    assertEqual(host.querySelectorAll('.ha-chat-msg').length, 0, 'aucune bulle de message sur une conversation vide');
  });

  test('render appelle onSuggestion avec le texte cliqué, et ne plante pas sans rappel', () => {
    const host = document.createElement('div');
    let clicked = null;
    HA.chat.render(host, [], {}, { onSuggestion: (text) => { clicked = text; } });
    const firstButton = host.querySelector('.ha-chat-suggestion');
    firstButton.click();
    assertEqual(clicked, HA.chat.SUGGESTED_QUESTIONS[0]);

    const hostNoCallback = document.createElement('div');
    HA.chat.render(hostNoCallback, [], {});
    hostNoCallback.querySelector('.ha-chat-suggestion').click(); // ne doit lever aucune exception
  });

  test('render retire le modificateur ha-chat-empty dès qu\'il y a des messages', () => {
    const host = document.createElement('div');
    HA.chat.render(host, [], {});
    assert(host.classList.contains('ha-chat-empty'));
    HA.chat.render(host, [{ role: 'user', text: 'Bonjour', at: 1 }], {});
    assert(!host.classList.contains('ha-chat-empty'), 'le modificateur doit disparaître une fois la conversation non vide');
    assertEqual(host.querySelectorAll('.ha-chat-suggestion').length, 0);
  });

  // ---------------------------------------------------------------- phrases de lecture
  //
  // Chaque section du rapport porte une phrase calculée à partir de `ReportModel`, qui dit
  // ce que VOS données montrent — à côté de la note fixe, qui explique la méthode.
  //
  // Ces phrases ne passent jamais par le LLM et ne lui sont jamais montrées. C'est la même
  // règle que le catalogue de séries : le modèle désigne, il ne recopie aucun chiffre, donc
  // il ne peut pas en inventer un. Sur une donnée de santé, une phrase absente coûte moins
  // cher qu'une phrase fausse.

  /** Un `ReportModel` réduit aux sections que la phrase testée lit. */
  function insightModel(overrides) {
    const base = {
      meta: {}, tiles: [],
      sleep: { nightly: [], monthly: [], dayOfWeek: [], distribution: [], stagesMonthly: [], kpi: {} },
      heart: { monthly: [], restingDaily: [], hourly: [], hrvMonthly: [], hrvDaily: [], bloodPressure: [], ecg: [], kpi: {} },
      activity: { stepsDaily: [], stepsRolling7: [], stepsMonthly: [], stepsDayOfWeek: [], exerciseMonthly: [], exerciseByKind: [], floorsMonthly: [], kpi: {} },
      body: { daily: [], kpi: {} },
      stress: { monthly: [], hourly: [], dayOfWeek: [], daily: [], vitalityDaily: [], kpi: {} },
      breathing: { spo2Monthly: [], spo2Daily: [], respiratoryDaily: [], skinTempDaily: [], kpi: {} },
      correlations: [], narrative: null,
    };
    return Object.assign(base, overrides || {});
  }

  function insightOf(key, model) {
    const section = HA.reportSections.SECTIONS.find(s => s.key === key);
    assert(section, 'section introuvable : ' + key);
    assert(typeof section.insight === 'function', 'la section ' + key + ' n a pas de phrase de lecture');
    return section.insight(model);
  }

  test('la phrase du sommeil cite le nombre de nuits et la part sous 6 h', () => {
    const model = insightModel({
      sleep: { nightly: [], monthly: [], dayOfWeek: [], distribution: [], stagesMonthly: [],
        kpi: { nights: 294, pctUnder6h: 14, bedSpreadHours: 1.4 } },
    });
    const phrase = insightOf('sleep', model);
    assert(phrase.includes('294'), 'le nombre de nuits manque : ' + phrase);
    assert(phrase.includes('14'), 'la part sous 6 h manque : ' + phrase);
    // Le même format que l'indicateur « irrégularité du coucher » de la section, pas un
    // nombre décimal : la même grandeur ne se lit pas en deux formats sur un même écran.
    assert(phrase.includes('1h24'), 'la dispersion du coucher manque ou change de format : ' + phrase);
  });

  test('la phrase du coeur nomme huit jours sur dix, pas neuf', () => {
    const model = insightModel({
      heart: { monthly: [], restingDaily: [], hourly: [], hrvMonthly: [], hrvDaily: [],
        bloodPressure: [], ecg: [], kpi: { restingP10: 44, restingP90: 55, measuredDays: 341 } },
    });
    const phrase = insightOf('heart', model);
    // p10 a p90 couvre 80 % des jours. « neuf jours sur dix » serait une surpromesse
    // silencieuse, et c'est exactement le genre d'erreur qu'une phrase ecrite a la main
    // laisse passer.
    assert(phrase.includes('huit jours sur dix'), 'la couverture annoncee est fausse : ' + phrase);
    assert(phrase.includes('44') && phrase.includes('55'), phrase);
  });

  test('chaque phrase rend null quand ses chiffres manquent', () => {
    const empty = insightModel();
    for (const key of ['sleep', 'heart', 'activity', 'body', 'stress', 'breathing']) {
      assertEqual(insightOf(key, empty), null, 'la section ' + key + ' invente une phrase sans donnees');
    }
  });

  test('la phrase du corps porte le signe de la variation', () => {
    const gain = insightModel({ body: { daily: [], kpi: { deltaKg: 1.8, deltaMuscleKg: 0.4 } } });
    const loss = insightModel({ body: { daily: [], kpi: { deltaKg: -2.4, deltaMuscleKg: -0.3 } } });
    assert(insightOf('body', gain).includes('+1,8'), insightOf('body', gain));
    // Le signe moins est un vrai signe moins typographique (U+2212), pas un trait d'union :
    // c'est la convention du reste du rapport, et il s'aligne sur la meme chasse que le plus.
    assert(insightOf('body', loss).includes('−2,4'), insightOf('body', loss));
  });

  test('une phrase partielle vaut mieux que pas de phrase du tout', () => {
    // Le poids est connu, la masse musculaire non : la phrase doit dire ce qu'elle sait.
    const model = insightModel({ body: { daily: [], kpi: { deltaKg: -2.4, deltaMuscleKg: null } } });
    const phrase = insightOf('body', model);
    assert(phrase !== null, 'la phrase disparait alors que le poids est connu');
    assert(!phrase.includes('muscle'), 'la phrase parle du muscle sans le connaitre : ' + phrase);
  });

  test('aucune phrase ne juge ni ne conseille', () => {
    const model = insightModel({
      sleep: { nightly: [], monthly: [], dayOfWeek: [], distribution: [], stagesMonthly: [],
        kpi: { nights: 294, pctUnder6h: 62, bedSpreadHours: 3.1 } },
      stress: { monthly: [], hourly: [], dayOfWeek: [], daily: [], vitalityDaily: [],
        kpi: { percentAbove60: 71, peakHour: 15 } },
    });
    // Le rapport decrit, il ne diagnostique pas. Le vocabulaire d'alerte appartient au
    // medecin, pas a une app qui lit une montre.
    const interdits = ['inquietant', 'grave', 'dangereux', 'anormal', 'devriez', 'il faut', 'risque'];
    for (const key of ['sleep', 'stress']) {
      const phrase = (insightOf(key, model) || '').toLowerCase();
      for (const mot of interdits) {
        assert(!phrase.includes(mot), 'la phrase de ' + key + ' porte un jugement : ' + phrase);
      }
    }
  });

  async function runAll() {
    for (const { name, fn } of registered) {
      try {
        await fn();
        results.push({ name, pass: true });
      } catch (error) {
        results.push({ name, pass: false, error: error.message });
      }
    }
    render();
  }

  // Régression : la longueur du vecteur résultant dépassait 1 par arrondi flottant dès
  // six angles identiques, ce qui rendait NaN et cassait la sérialisation du modèle.
  test('circularStdDev ne rend jamais NaN sur des heures identiques', () => {
    for (const n of [2, 6, 7, 10, 100, 730]) {
      for (const hour of [0, 1.5, 3, 12, 22.5]) {
        const value = HA.stats.circularStdDev(new Array(n).fill(hour));
        assert(Number.isFinite(value), `n=${n} heure=${hour} rend ${value}`);
        assert(value >= 0, `n=${n} heure=${hour} rend une dispersion négative`);
      }
    }
  });

  runAll();
})();
