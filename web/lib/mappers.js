/**
 * Convertit les lignes brutes d'un export Samsung Health en modèles du domaine.
 * Portage fidèle de `SamsungMappers.kt`.
 *
 * Chaque mappeur rend `null` quand la ligne ne porte pas les champs indispensables
 * ou quand une valeur sort de la plage physiologique.
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

  const SleepMapper = {
    PREFIX: 'com.samsung.health.sleep.',
    map(row) {
      const id = row.string(`${this.PREFIX}datauuid`);
      if (id === null) return null;
      const offsetColumn = `${this.PREFIX}time_offset`;
      const bedTime = row.instant(`${this.PREFIX}start_time`, offsetColumn);
      const wakeTime = row.instant(`${this.PREFIX}end_time`, offsetColumn);
      if (bedTime === null || wakeTime === null) return null;
      // Sans `sleep_duration`, la ligne décrit un objectif de coucher, pas une nuit mesurée.
      const duration = row.int('sleep_duration');
      if (duration === null || duration <= 0) return null;

      const offsetMinutes = row.zoneOffsetMinutes(offsetColumn) || 0;
      const localBedMillis = bedTime + offsetMinutes * 60000;
      const localBed = new Date(localBedMillis);
      const localWakeMillis = wakeTime + offsetMinutes * 60000;
      const localWake = new Date(localWakeMillis);

      return {
        id,
        // Une nuit appartient au jour du réveil : coucher à 23 h le 9 = nuit du 10.
        date: HA.csv.dateKey(localWake.getUTCFullYear(), localWake.getUTCMonth() + 1, localWake.getUTCDate()),
        bedTime,
        wakeTime,
        durationMinutes: duration,
        score: row.int('sleep_score'),
        efficiencyPercent: row.float('efficiency'),
        latencyMinutes: row.long('sleep_latency') !== null ? millisToMinutes(row.long('sleep_latency')) : null,
        physicalRecovery: row.double('physical_recovery') !== null ? Math.round(row.double('physical_recovery')) : null,
        mentalRecovery: row.double('mental_recovery') !== null ? Math.round(row.double('mental_recovery')) : null,
        remMinutes: row.int('total_rem_duration'),
        lightMinutes: row.int('total_light_duration'),
        // Secondes écoulées depuis minuit local, pour la moyenne circulaire des couchers.
        localBedSecondOfDay: localBed.getUTCHours() * 3600 + localBed.getUTCMinutes() * 60 + localBed.getUTCSeconds(),
        offsetMinutes,
      };
    },
  };

  const STAGE_CODES = { 40001: 'AWAKE', 40002: 'LIGHT', 40003: 'DEEP', 40004: 'REM' };

  const SleepStageMapper = {
    map(row) {
      const id = row.string('datauuid');
      const sleepId = row.string('sleep_id');
      const start = row.instant('start_time');
      const end = row.instant('end_time');
      const code = row.int('stage');
      if (id === null || sleepId === null || start === null || end === null || code === null) return null;

      return {
        id,
        sleepId,
        stage: STAGE_CODES[code] || 'UNKNOWN',
        start,
        end,
        durationMinutes: minutesBetween(start, end),
      };
    },
  };

  const HeartRateMapper = {
    PREFIX: 'com.samsung.health.heart_rate.',
    PLAUSIBLE_MIN: 25,
    PLAUSIBLE_MAX: 250,
    map(row) {
      const id = row.string(`${this.PREFIX}datauuid`);
      if (id === null) return null;
      const time = row.instant(`${this.PREFIX}start_time`, `${this.PREFIX}time_offset`);
      if (time === null) return null;
      const rawBpm = row.double(`${this.PREFIX}heart_rate`);
      if (rawBpm === null) return null;
      const bpm = Math.round(rawBpm);
      if (bpm < this.PLAUSIBLE_MIN || bpm > this.PLAUSIBLE_MAX) return null;

      return {
        id,
        time,
        beatsPerMinute: bpm,
        min: row.double(`${this.PREFIX}min`) !== null ? Math.round(row.double(`${this.PREFIX}min`)) : null,
        max: row.double(`${this.PREFIX}max`) !== null ? Math.round(row.double(`${this.PREFIX}max`)) : null,
        offsetMinutes: row.zoneOffsetMinutes(`${this.PREFIX}time_offset`),
      };
    },
  };

  const StressMapper = {
    map(row) {
      const id = row.string('datauuid');
      const start = row.instant('start_time');
      const end = row.instant('end_time');
      const rawScore = row.double('score');
      if (id === null || start === null || end === null || rawScore === null) return null;

      return {
        id,
        start,
        end,
        score: Math.round(rawScore),
        min: row.double('min') !== null ? Math.round(row.double('min')) : null,
        max: row.double('max') !== null ? Math.round(row.double('max')) : null,
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  const SpO2Mapper = {
    PREFIX: 'com.samsung.health.oxygen_saturation.',
    PLAUSIBLE_MIN: 50,
    PLAUSIBLE_MAX: 100,
    map(row) {
      const id = row.string(`${this.PREFIX}datauuid`);
      if (id === null) return null;
      const time = row.instant(`${this.PREFIX}start_time`, `${this.PREFIX}time_offset`);
      if (time === null) return null;
      const percent = row.float(`${this.PREFIX}spo2`);
      if (percent === null || percent < this.PLAUSIBLE_MIN || percent > this.PLAUSIBLE_MAX) return null;

      return {
        id,
        time,
        percent,
        min: row.float(`${this.PREFIX}min`),
        max: row.float(`${this.PREFIX}max`),
        offsetMinutes: row.zoneOffsetMinutes(`${this.PREFIX}time_offset`),
      };
    },
  };

  const BodyCompositionMapper = {
    map(row) {
      const id = row.string('datauuid');
      const time = row.instant('start_time');
      const weight = row.float('weight');
      if (id === null || time === null || weight === null || weight <= 0) return null;
      const heightRaw = row.float('height');
      const height = heightRaw !== null && heightRaw > 0 ? heightRaw : null;

      return {
        id,
        time,
        weightKg: weight,
        heightCm: height,
        bodyMassIndex: height !== null ? weight / ((height / 100) * (height / 100)) : null,
        bodyFatPercent: row.float('body_fat'),
        bodyFatMassKg: row.float('body_fat_mass'),
        skeletalMuscleMassKg: row.float('skeletal_muscle_mass'),
        fatFreeMassKg: row.float('fat_free_mass'),
        totalBodyWaterKg: row.float('total_body_water'),
        basalMetabolicRate: row.int('basal_metabolic_rate'),
        offsetMinutes: row.zoneOffsetMinutes('time_offset'),
      };
    },
  };

  const ExerciseMapper = {
    PREFIX: 'com.samsung.health.exercise.',
    // Codes d'activité Samsung Health les plus courants.
    KINDS: {
      1001: 'WALKING', 1002: 'RUNNING', 11007: 'CYCLING', 13001: 'HIKING',
      14001: 'SWIMMING', 15003: 'STRENGTH', 15005: 'STRENGTH',
      10004: 'ELLIPTICAL', 10005: 'ROWING', 9002: 'YOGA',
    },
    map(row) {
      const id = row.string(`${this.PREFIX}datauuid`);
      if (id === null) return null;
      const offsetColumn = `${this.PREFIX}time_offset`;
      const start = row.instant(`${this.PREFIX}start_time`, offsetColumn);
      const end = row.instant(`${this.PREFIX}end_time`, offsetColumn);
      if (start === null || end === null) return null;
      const code = row.int(`${this.PREFIX}exercise_type`);
      const rawDuration = row.long(`${this.PREFIX}duration`);
      const duration = rawDuration !== null ? millisToMinutes(rawDuration) : minutesBetween(start, end);
      if (duration <= 0) return null;

      return {
        id,
        kind: (code !== null && this.KINDS[code]) || 'OTHER',
        samsungTypeCode: code,
        start,
        end,
        durationMinutes: duration,
        calories: row.float(`${this.PREFIX}calorie`),
        distanceMeters: row.float(`${this.PREFIX}distance`),
        meanHeartRate: row.double(`${this.PREFIX}mean_heart_rate`) !== null ? Math.round(row.double(`${this.PREFIX}mean_heart_rate`)) : null,
        maxHeartRate: row.double(`${this.PREFIX}max_heart_rate`) !== null ? Math.round(row.double(`${this.PREFIX}max_heart_rate`)) : null,
        offsetMinutes: row.zoneOffsetMinutes(offsetColumn),
      };
    },
  };

  const DailyStepsMapper = {
    // Samsung écrit une ligne par source et par jour. Le code -2 porte l'agrégat
    // de toutes les sources. Garder les autres ferait compter les pas deux fois.
    AGGREGATED_SOURCE: -2,
    map(row) {
      if (row.int('source_type') !== this.AGGREGATED_SOURCE) return null;
      const date = row.localDate('day_time');
      const steps = row.int('count');
      if (date === null || steps === null) return null;

      return {
        date,
        steps,
        distanceMeters: row.float('distance'),
        calories: row.float('calorie'),
      };
    },
  };

  const DailyActivityMapper = {
    map(row) {
      const date = row.localDate('day_time');
      if (date === null) return null;

      return {
        date,
        steps: row.int('step_count'),
        activeMinutes: row.long('active_time') !== null ? millisToMinutes(row.long('active_time')) : null,
        exerciseMinutes: row.long('exercise_time') !== null ? millisToMinutes(row.long('exercise_time')) : null,
        activeCalories: row.double('calorie') !== null ? Math.round(row.double('calorie')) : null,
        distanceMeters: row.float('distance'),
        floors: row.int('floor_count'),
      };
    },
  };

  const EnergyScoreMapper = {
    map(row) {
      const date = row.localDate('day_time');
      const total = row.double('total_score');
      if (date === null || total === null) return null;

      return {
        date,
        total: Math.round(total),
        sleep: row.double('sleep_score') !== null ? Math.round(row.double('sleep_score')) : null,
        activity: row.double('activity_score') !== null ? Math.round(row.double('activity_score')) : null,
        nightHeartRate: row.double('shr_value') !== null ? Math.round(row.double('shr_value')) : null,
        nightHeartRateVariability: row.double('shrv_value') !== null ? Math.round(row.double('shrv_value')) : null,
        sleepDurationMinutes: row.long('sleep_duration') !== null ? millisToMinutes(row.long('sleep_duration')) : null,
      };
    },
  };

  root.HA.mappers = {
    SleepMapper, SleepStageMapper, HeartRateMapper, StressMapper, SpO2Mapper,
    BodyCompositionMapper, ExerciseMapper, DailyStepsMapper, DailyActivityMapper,
    EnergyScoreMapper, STAGE_CODES,
  };
})();
