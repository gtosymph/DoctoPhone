/**
 * Construit la section activité du rapport (`ActivitySection` / `ActivityKpi`).
 *
 * Hypothèses de forme des données brutes :
 * - `dailySteps` : `{date, steps}` (forme `DailyStepsMapper`).
 * - `exercise` : `{kind, start, end, durationMinutes, calories, offsetMinutes}` (forme `ExerciseMapper`).
 * - `dailyFloors` : `{date, floors}`. `dailyActivity[].floors` sert de repli si ce magasin est vide.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  // Moyenne glissante sur 7 jours : au moins 4 jours mesurés dans la fenêtre pour éviter
  // qu'un début de période clairsemé ne produise une moyenne trompeuse.
  const ROLLING_WINDOW_DAYS = 7;
  const ROLLING_MIN_SAMPLES = 4;

  function buildStepsDaily(dailySteps) {
    const byDate = util().groupByDate(dailySteps || []);
    return util().toDayValues(byDate, (items) => items[0].steps ?? null);
  }

  function buildFloorsMonthly(dailyFloors, dailyActivity) {
    const source = (dailyFloors && dailyFloors.length > 0) ? dailyFloors : (dailyActivity || [])
      .filter((d) => d.floors !== null && d.floors !== undefined)
      .map((d) => ({ date: d.date, floors: d.floors }));
    const byDate = util().groupByDate(source);
    const dayValues = util().toDayValues(byDate, (items) => items[0].floors ?? null);
    return HA.stats.byMonth(dayValues);
  }

  function buildExerciseMonthly(exercise, defaultOffsetMinutes) {
    const byMonth = {};
    (exercise || []).forEach((session) => {
      if (session.start === null || session.start === undefined) return;
      const date = HA.aggregate.localDateKeyOf(session.start, util().resolveOffset(session, defaultOffsetMinutes));
      const month = date.slice(0, 7);
      (byMonth[month] = byMonth[month] || []).push(session);
    });
    return Object.keys(byMonth).sort().map((month) => {
      const sessions = byMonth[month];
      const calories = sessions.map((s) => s.calories).filter((v) => v !== null && v !== undefined);
      return {
        month,
        sessions: sessions.length,
        minutes: sessions.reduce((sum, s) => sum + (s.durationMinutes || 0), 0),
        calories: calories.length > 0 ? calories.reduce((a, b) => a + b, 0) : null,
      };
    });
  }

  // Traduction des codes `ExerciseMapper.KINDS` (voir mappers.js) : le rapport est en français.
  const KIND_LABELS_FR = {
    WALKING: 'Marche',
    RUNNING: 'Course',
    CYCLING: 'Vélo',
    HIKING: 'Randonnée',
    SWIMMING: 'Natation',
    STRENGTH: 'Musculation',
    ELLIPTICAL: 'Elliptique',
    ROWING: 'Aviron',
    YOGA: 'Yoga',
    OTHER: 'Autre',
  };

  function buildExerciseByKind(exercise) {
    const byKind = {};
    (exercise || []).forEach((session) => {
      const kind = session.kind || 'OTHER';
      byKind[kind] = byKind[kind] || { minutes: 0, count: 0 };
      byKind[kind].minutes += session.durationMinutes || 0;
      byKind[kind].count += 1;
    });
    return Object.keys(byKind).sort().map((kind) => ({
      label: KIND_LABELS_FR[kind] || KIND_LABELS_FR.OTHER,
      value: byKind[kind].minutes,
      count: byKind[kind].count,
    }));
  }

  function buildKpi(stepsDaily, exercise) {
    const definedSteps = stepsDaily.filter((d) => d.value !== null && d.value !== undefined);
    const values = definedSteps.map((d) => d.value);
    const last30 = definedSteps.slice(-30).map((d) => d.value);
    const last90 = definedSteps.slice(-90).map((d) => d.value);

    let best = null;
    definedSteps.forEach((d) => { if (best === null || d.value > best.value) best = d; });

    const totalExerciseMinutes = (exercise || []).reduce((sum, s) => sum + (s.durationMinutes || 0), 0);

    return {
      meanSteps: values.length > 0 ? HA.stats.mean(values) : null,
      meanSteps30: last30.length > 0 ? HA.stats.mean(last30) : null,
      meanSteps90: last90.length > 0 ? HA.stats.mean(last90) : null,
      bestSteps: best ? best.value : null,
      bestStepsDate: best ? best.date : null,
      pctDaysUnder3000: values.length > 0 ? (values.filter((v) => v < 3000).length / values.length) * 100 : null,
      pctDaysOver8000: values.length > 0 ? (values.filter((v) => v >= 8000).length / values.length) * 100 : null,
      totalExerciseMinutes: (exercise || []).length > 0 ? totalExerciseMinutes : null,
      exerciseSessions: (exercise || []).length,
      measuredDays: definedSteps.length,
    };
  }

  /**
   * @param {object[]} dailySteps
   * @param {object[]} exercise
   * @param {object[]} dailyFloors
   * @param {object[]} dailyActivity
   * @param {number|undefined} defaultOffsetMinutes décalage par défaut de la période
   */
  function buildActivitySection(dailySteps, exercise, dailyFloors, dailyActivity, defaultOffsetMinutes) {
    const stepsDaily = buildStepsDaily(dailySteps);

    return {
      stepsDaily,
      stepsRolling7: HA.stats.rollingMean(stepsDaily.map((d) => d.value), ROLLING_WINDOW_DAYS, ROLLING_MIN_SAMPLES)
        .map((value, index) => ({ date: stepsDaily[index].date, value })),
      stepsMonthly: HA.stats.byMonth(stepsDaily),
      stepsDayOfWeek: HA.stats.byDayOfWeek(stepsDaily),
      exerciseMonthly: buildExerciseMonthly(exercise, defaultOffsetMinutes),
      exerciseByKind: buildExerciseByKind(exercise),
      floorsMonthly: buildFloorsMonthly(dailyFloors, dailyActivity),
      kpi: buildKpi(stepsDaily, exercise),
    };
  }

  root.HA.reportSections.buildActivitySection = buildActivitySection;
})();
