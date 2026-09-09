/**
 * Mappeurs des 8 nouveaux types cliniques Samsung Health (tension, ECG, ronflement,
 * fréquence respiratoire, température cutanée, apnée, étages montés, alertes de
 * stress). Séparés de `mappers.js` pour garder chaque fichier sous les 400 lignes ;
 * même séparation que côté Android entre `HealthModels.kt` et `ClinicalModels.kt`.
 *
 * Mêmes conventions que `mappers.js` : un mappeur rend `null` quand la ligne manque
 * l'essentiel (identifiant, horodatage, valeur principale) ou sort de la plage
 * physiologique plausible. Noms de champs alignés sur `ClinicalModels.kt`.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  function millisToMinutes(ms) {
    return Math.round(ms / 60000);
  }

  function minutesBetween(startMs, endMs) {
    return Math.trunc((endMs - startMs) / 60000);
  }

  /**
   * Une prise de tension artérielle.
   *
   * Particularité relevée sur un export réel : bien que le type Samsung soit
   * `com.samsung.shealth.blood_pressure`, ses colonnes portent le préfixe
   * `com.samsung.health.blood_pressure.` — comme le sommeil ou l'exercice, mais
   * à la différence des autres types cliniques ci-dessous, qui ne sont pas préfixés.
   */
  const BloodPressureMapper = {
    PREFIX: 'com.samsung.health.blood_pressure.',
    SYSTOLIC_MIN: 60, SYSTOLIC_MAX: 250,
    DIASTOLIC_MIN: 30, DIASTOLIC_MAX: 150,
    map(row) {
      const id = row.string(`${this.PREFIX}datauuid`);
      if (id === null) return null;
      const offsetColumn = `${this.PREFIX}time_offset`;
      const time = row.instant(`${this.PREFIX}start_time`, offsetColumn);
      const systolic = row.int(`${this.PREFIX}systolic`);
      const diastolic = row.int(`${this.PREFIX}diastolic`);
      if (time === null || systolic === null || diastolic === null) return null;
      if (systolic < this.SYSTOLIC_MIN || systolic > this.SYSTOLIC_MAX) return null;
      if (diastolic < this.DIASTOLIC_MIN || diastolic > this.DIASTOLIC_MAX) return null;

      return {
        id,
        time,
        systolic,
        diastolic,
        pulse: row.int(`${this.PREFIX}pulse`),
        mean: row.int(`${this.PREFIX}mean`),
        offsetMinutes: row.zoneOffsetMinutes(offsetColumn),
      };
    },
  };

  /** Un enregistrement d'électrocardiogramme. L'app ne l'interprète pas médicalement. */
  const EcgMapper = {
    map(row) {
      const id = row.string('datauuid');
      if (id === null) return null;
      const time = row.instant('start_time', 'time_offset');
      if (time === null) return null;

      return {
        id,
        time,
        meanHeartRate: row.int('mean_heart_rate'),
        minHeartRate: row.int('min_heart_rate'),
        maxHeartRate: row.int('max_heart_rate'),
        classification: row.int('classification'),
        symptoms: row.string('symptoms'),
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  /** Un épisode de ronflement détecté pendant une nuit. */
  const SnoringMapper = {
    map(row) {
      const id = row.string('datauuid');
      const offsetColumn = 'time_offset';
      const start = row.instant('start_time', offsetColumn);
      const end = row.instant('end_time', offsetColumn);
      if (id === null || start === null || end === null) return null;
      const rawDuration = row.long('duration');
      const duration = rawDuration !== null ? millisToMinutes(rawDuration) : minutesBetween(start, end);
      if (duration <= 0) return null;

      return { id, start, end, durationMinutes: duration, offsetMinutes: row.zoneOffsetMinutes(offsetColumn) };
    },
  };

  /** La fréquence respiratoire moyenne d'une nuit, en cycles par minute. */
  const RespiratoryRateMapper = {
    PLAUSIBLE_MIN: 4, PLAUSIBLE_MAX: 40,
    map(row) {
      const id = row.string('datauuid');
      if (id === null) return null;
      const time = row.instant('start_time', 'time_offset');
      if (time === null) return null;
      const breathsPerMinute = row.float('average');
      if (breathsPerMinute === null) return null;
      if (breathsPerMinute < this.PLAUSIBLE_MIN || breathsPerMinute > this.PLAUSIBLE_MAX) return null;

      return {
        id,
        time,
        breathsPerMinute,
        min: row.float('lower_limit'),
        max: row.float('upper_limit'),
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  /** La température cutanée nocturne, en degrés Celsius, avec la référence personnelle de la montre. */
  const SkinTemperatureMapper = {
    PLAUSIBLE_MIN: 25, PLAUSIBLE_MAX: 45,
    map(row) {
      const id = row.string('datauuid');
      if (id === null) return null;
      const time = row.instant('start_time', 'time_offset');
      if (time === null) return null;
      const celsius = row.float('temperature');
      if (celsius === null) return null;
      if (celsius < this.PLAUSIBLE_MIN || celsius > this.PLAUSIBLE_MAX) return null;

      return {
        id,
        time,
        celsius,
        baseline: row.float('baseline'),
        min: row.float('min'),
        max: row.float('max'),
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  /** Le résultat d'un dépistage d'apnée du sommeil. */
  const SleepApneaMapper = {
    map(row) {
      const id = row.string('datauuid');
      if (id === null) return null;
      const time = row.instant('start_time', 'time_offset');
      const result = row.int('result');
      if (time === null || result === null) return null;

      return {
        id,
        time,
        result,
        averageBreathingDisturbance: row.float('average_bd'),
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  /** Une alerte de stress élevé émise par la montre. */
  const StressAlertMapper = {
    map(row) {
      const id = row.string('datauuid');
      const offsetColumn = 'time_offset';
      const start = row.instant('start_time', offsetColumn);
      const end = row.instant('end_time', offsetColumn);
      if (id === null || start === null || end === null) return null;

      return { id, start, end, offsetMinutes: row.zoneOffsetMinutes(offsetColumn) };
    },
  };

  /** Le nombre d'étages montés dans une journée. */
  const DailyFloorsMapper = {
    // Sur un export réel, `day_time` est une date-heure formatée (23 caractères),
    // pas un horodatage epoch brut : `row.localDate` la lit directement, comme
    // pour les pas quotidiens (`DailyStepsMapper`).
    map(row) {
      const date = row.localDate('day_time');
      const floors = row.int('floor_count');
      if (date === null || floors === null) return null;
      return { date, floors };
    },
  };

  root.HA.mappers = Object.assign(root.HA.mappers || {}, {
    BloodPressureMapper, EcgMapper, SnoringMapper, RespiratoryRateMapper,
    SkinTemperatureMapper, SleepApneaMapper, StressAlertMapper, DailyFloorsMapper,
  });
})();
