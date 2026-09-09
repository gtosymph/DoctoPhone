/**
 * Orchestre l'import d'un export Samsung Health : reconnaît les fichiers utiles,
 * les fait passer par les mappeurs, puis calcule l'agrégat journalier complet.
 *
 * Cette fonction tourne dans le Web Worker (voir `worker.js`), loin de l'interface,
 * pour que le fil principal ne se fige jamais pendant l'import.
 *
 * 19 types Samsung sont reconnus : les 10 déjà consommés par le tableau de bord
 * (dont les stades de sommeil et les séances d'exercice, dont les mappeurs
 * existaient mais n'étaient pas branchés), plus 8 nouveaux types cliniques
 * (tension, ECG, ronflement, fréquence respiratoire, température cutanée,
 * apnée, étages montés, alertes de stress). `result.records` porte les mesures
 * brutes de chaque type, sous une clé identique au nom du magasin IndexedDB
 * correspondant (voir `db.js`) : c'est le contrat utilisé par `worker.js` pour
 * les persister via `HA.db.putAll`.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  /** Types Samsung reconnus, associés au nom du magasin IndexedDB qui les reçoit. */
  const CSV_TYPES = {
    'com.samsung.shealth.sleep': 'sleepNights',
    'com.samsung.health.sleep_stage': 'sleepStages',
    'com.samsung.shealth.tracker.heart_rate': 'heartRate',
    'com.samsung.shealth.stress': 'stress',
    'com.samsung.shealth.alerted_stress': 'stressAlerts',
    'com.samsung.health.hrv': 'hrv',
    'com.samsung.shealth.tracker.oxygen_saturation': 'spo2',
    'com.samsung.shealth.exercise': 'exercise',
    'com.samsung.health.weight': 'bodyComposition',
    'com.samsung.shealth.step_daily_trend': 'dailySteps',
    'com.samsung.shealth.activity.day_summary': 'dailyActivity',
    'com.samsung.shealth.vitality_score': 'energyScores',
    'com.samsung.shealth.blood_pressure': 'bloodPressure',
    'com.samsung.health.ecg': 'ecg',
    'com.samsung.shealth.sleep_snoring': 'snoring',
    'com.samsung.health.respiratory_rate': 'respiratory',
    'com.samsung.health.skin_temperature': 'skinTemp',
    'com.samsung.health.sleep_apnea': 'sleepApnea',
    'com.samsung.shealth.tracker.floors_day_summary': 'dailyFloors',
  };

  function escapeRegex(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  const CSV_PATTERNS = Object.keys(CSV_TYPES).map((dataType) => ({
    dataType,
    role: CSV_TYPES[dataType],
    regex: new RegExp(`^${escapeRegex(dataType)}\\.\\d+\\.csv$`),
  }));

  /** Détermine si un chemin de fichier de l'export est utile, et à quoi. */
  function classify(relativePath) {
    const segments = relativePath.split('/');
    const basename = segments[segments.length - 1];

    if (
      basename.endsWith('.binning_data.json') &&
      segments.includes('jsons') &&
      segments.includes('com.samsung.health.hrv')
    ) {
      return { kind: 'hrv-json', basename };
    }

    for (const pattern of CSV_PATTERNS) {
      if (pattern.regex.test(basename)) {
        return { kind: 'csv', dataType: pattern.dataType, role: pattern.role };
      }
    }
    return null;
  }

  function mapRows(rows, mapper) {
    const out = [];
    rows.forEach((row) => {
      const value = mapper(row);
      if (value !== null) out.push(value);
    });
    return out;
  }

  /** Collecte toutes les dates couvertes par l'import, pour borner la période agrégée. */
  function collectDateKeys(records) {
    const dates = [];
    const byDate = ['sleepNights', 'dailySteps', 'dailyActivity', 'energyScores', 'dailyFloors'];
    const byTime = ['heartRate', 'hrv', 'spo2', 'bodyComposition', 'bloodPressure', 'ecg', 'respiratory', 'skinTemp', 'sleepApnea'];
    const byStart = ['stress', 'stressAlerts', 'exercise', 'sleepStages', 'snoring'];
    byDate.forEach((key) => records[key].forEach((n) => dates.push(n.date)));
    byTime.forEach((key) => records[key].forEach((n) => dates.push(HA.aggregate.localDateKeyOf(n.time))));
    byStart.forEach((key) => records[key].forEach((n) => dates.push(HA.aggregate.localDateKeyOf(n.start))));
    return dates;
  }

  /**
   * @param {Array<{relativePath: string, getText: () => Promise<string>}>} entries
   * @param {(progress: {done: number, total: number, phase: string}) => void} onProgress
   * @param {number} sleepTargetMinutes
   */
  async function run(entries, onProgress, sleepTargetMinutes) {
    const classified = entries
      .map((entry) => ({ entry, meta: classify(entry.relativePath) }))
      .filter((x) => x.meta !== null);

    const csvJobs = classified.filter((x) => x.meta.kind === 'csv');
    const hrvJsonJobs = classified.filter((x) => x.meta.kind === 'hrv-json');
    const total = csvJobs.length + hrvJsonJobs.length;
    let done = 0;
    const report = (phase) => {
      done += 1;
      if (onProgress) onProgress({ done, total, phase });
    };

    const rowsByType = {};
    const readWarnings = [];
    for (const { entry, meta } of csvJobs) {
      try {
        const text = await entry.getText();
        const doc = HA.csv.parseSamsungCsv(text);
        (rowsByType[meta.dataType] = rowsByType[meta.dataType] || []).push(...doc.rows);
      } catch (error) {
        readWarnings.push(`Fichier illisible ignoré : ${entry.relativePath} (${error.message}).`);
      }
      report('csv');
    }

    const hrvJsonByName = {};
    for (const { entry, meta } of hrvJsonJobs) {
      try {
        hrvJsonByName[meta.basename] = await entry.getText();
      } catch (error) {
        readWarnings.push(`JSON HRV illisible ignoré : ${entry.relativePath}.`);
      }
      report('hrv-json');
    }

    const rows = (dataType) => rowsByType[dataType] || [];
    const sleepNights = mapRows(rows('com.samsung.shealth.sleep'), (r) => HA.mappers.SleepMapper.map(r));
    const sleepStages = mapRows(rows('com.samsung.health.sleep_stage'), (r) => HA.mappers.SleepStageMapper.map(r));
    const heartRate = mapRows(rows('com.samsung.shealth.tracker.heart_rate'), (r) => HA.mappers.HeartRateMapper.map(r));
    const stress = mapRows(rows('com.samsung.shealth.stress'), (r) => HA.mappers.StressMapper.map(r));
    const stressAlerts = mapRows(rows('com.samsung.shealth.alerted_stress'), (r) => HA.mappers.StressAlertMapper.map(r));
    const spo2 = mapRows(rows('com.samsung.shealth.tracker.oxygen_saturation'), (r) => HA.mappers.SpO2Mapper.map(r));
    const exercise = mapRows(rows('com.samsung.shealth.exercise'), (r) => HA.mappers.ExerciseMapper.map(r));
    const bodyComposition = mapRows(rows('com.samsung.health.weight'), (r) => HA.mappers.BodyCompositionMapper.map(r));
    const dailySteps = mapRows(rows('com.samsung.shealth.step_daily_trend'), (r) => HA.mappers.DailyStepsMapper.map(r));
    const dailyActivity = mapRows(rows('com.samsung.shealth.activity.day_summary'), (r) => HA.mappers.DailyActivityMapper.map(r));
    const energyScores = mapRows(rows('com.samsung.shealth.vitality_score'), (r) => HA.mappers.EnergyScoreMapper.map(r));
    const bloodPressure = mapRows(rows('com.samsung.shealth.blood_pressure'), (r) => HA.mappers.BloodPressureMapper.map(r));
    const ecg = mapRows(rows('com.samsung.health.ecg'), (r) => HA.mappers.EcgMapper.map(r));
    const snoring = mapRows(rows('com.samsung.shealth.sleep_snoring'), (r) => HA.mappers.SnoringMapper.map(r));
    const respiratory = mapRows(rows('com.samsung.health.respiratory_rate'), (r) => HA.mappers.RespiratoryRateMapper.map(r));
    const skinTemp = mapRows(rows('com.samsung.health.skin_temperature'), (r) => HA.mappers.SkinTemperatureMapper.map(r));
    const sleepApnea = mapRows(rows('com.samsung.health.sleep_apnea'), (r) => HA.mappers.SleepApneaMapper.map(r));
    const dailyFloors = mapRows(rows('com.samsung.shealth.tracker.floors_day_summary'), (r) => HA.mappers.DailyFloorsMapper.map(r));

    const hrv = [];
    let hrvMissingJson = 0;
    rows('com.samsung.health.hrv').forEach((row) => {
      const id = row.string('datauuid');
      const filename = row.string('binning_data');
      if (id === null || filename === null) return;
      const jsonText = hrvJsonByName[filename];
      if (jsonText === undefined) {
        hrvMissingJson += 1;
        return;
      }
      try {
        const sample = HA.hrv.parseHrvBinning(id, jsonText);
        if (sample !== null) {
          sample.offsetMinutes = row.zoneOffsetMinutes('time_offset');
          hrv.push(sample);
        }
      } catch (error) {
        hrvMissingJson += 1;
      }
    });

    // Clé par nom de magasin IndexedDB : `worker.js` boucle dessus pour persister
    // chaque type via `HA.db.putAll(storeName, items)` sans connaître le détail.
    const records = {
      sleepNights, sleepStages, heartRate, stress, stressAlerts, hrv, spo2, exercise,
      bodyComposition, dailySteps, dailyActivity, energyScores, bloodPressure, ecg,
      snoring, respiratory, skinTemp, sleepApnea, dailyFloors,
    };

    const dateKeys = collectDateKeys(records).sort();
    if (dateKeys.length === 0) {
      throw new Error("Aucune mesure reconnue dans les fichiers sélectionnés. Vérifiez qu'il s'agit bien d'un export Samsung Health.");
    }
    const fromKey = dateKeys[0];
    const toKey = dateKeys[dateKeys.length - 1];

    // `HA.aggregate.buildDailySnapshots` (non modifié) attend des noms de champs
    // hérités de l'implémentation d'origine, différents des noms de magasins ci-dessus.
    const aggregateInputs = {
      sleepNights, dailySteps, dailyActivities: dailyActivity, heartRates: heartRate,
      stress, hrv, spO2: spo2, bodyCompositions: bodyComposition, energyScores,
    };
    const days = HA.aggregate.buildDailySnapshots(fromKey, toKey, aggregateInputs, sleepTargetMinutes);

    const counts = Object.fromEntries(Object.entries(records).map(([key, list]) => [key, list.length]));

    const warnings = readWarnings.slice();
    if (hrvMissingJson > 0) {
      warnings.push(`${hrvMissingJson} mesure(s) de variabilité cardiaque sans fichier JSON correspondant, ignorées.`);
    }
    Object.entries(counts).forEach(([key, count]) => {
      if (count === 0) warnings.push(`Aucune donnée de type « ${key} » trouvée dans l'export.`);
    });
    if (total === 0) {
      warnings.push("Aucun fichier reconnu comme export Samsung Health n'a été trouvé dans la sélection.");
    }

    return { days, fromKey, toKey, counts, warnings, filesMatched: total, records };
  }

  root.HA.importer = { classify, run, CSV_TYPES };
})();
