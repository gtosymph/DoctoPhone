/**
 * Prépare les messages envoyés au modèle de langage.
 * Portage fidèle de `HealthPromptBuilder.kt`.
 *
 * Deux règles gouvernent ce fichier.
 *
 * 1. Confidentialité. Seuls des agrégats quittent l'appareil : des moyennes par
 *    semaine et des tendances. Aucun identifiant Samsung, aucun horodatage précis,
 *    aucune mesure individuelle ne sort.
 * 2. Coût. Le résumé hebdomadaire tient dans quelques milliers de jetons, pour
 *    une analyse de qualité équivalente à des années de mesures brutes.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const MAX_WEEKS = 104; // Deux ans de données tiennent en 104 semaines.

  const SYSTEM_PROMPT = [
    "Tu es un assistant d'analyse de données de santé personnelles.",
    '',
    'Ton rôle :',
    '- Lis les agrégats de mesures que l\'utilisateur te donne.',
    '- Trouve les tendances réelles et les liens entre les mesures.',
    '- Nomme le point le plus important en premier, avec le chiffre qui le prouve.',
    '- Propose au plus trois actions concrètes et mesurables.',
    '',
    'Tes limites, à respecter strictement :',
    "- Tu n'es pas médecin. Tu ne poses aucun diagnostic et tu ne prescris rien.",
    "- Quand une mesure sort d'une plage habituelle, tu invites à consulter un",
    "  médecin plutôt que d'interpréter.",
    '- Tu ne conclus jamais au-delà des données fournies. Si une donnée manque,',
    '  tu le dis.',
    "- Tu distingues une corrélation d'une cause.",
    '',
    'Ta forme :',
    '- Réponds en français, en phrases courtes.',
    '- Structure la réponse en sections courtes avec des titres.',
    '- Cite toujours le chiffre qui appuie une affirmation.',
  ].join('\n');

  /** Met une durée en minutes sous la forme `5 h 09`. */
  function formatMinutes(minutes) {
    const hours = Math.floor(minutes / 60);
    const rest = String(minutes % 60).padStart(2, '0');
    return `${hours} h ${rest}`;
  }

  /** Formate un nombre décimal à la française (virgule), comme `String.format(Locale.FRANCE, ...)`. */
  function frFixed(value, decimals) {
    return value.toFixed(decimals).replace('.', ',');
  }

  function frSignedPercent(value) {
    const rounded = Math.round(value);
    const sign = rounded >= 0 ? '+' : '';
    return `${sign}${rounded}`;
  }

  function wording(direction) {
    if (direction === 'UP') return 'en hausse';
    if (direction === 'DOWN') return 'en baisse';
    return 'stable';
  }

  function isoWeekStartKey(dateKey) {
    const [y, m, d] = dateKey.split('-').map(Number);
    const dayMs = Date.UTC(y, m - 1, d);
    const isoDow = new Date(dayMs).getUTCDay() || 7; // Lundi = 1 ... Dimanche = 7
    const weekStartMs = dayMs - (isoDow - 1) * 86400000;
    const weekStart = new Date(weekStartMs);
    return HA.csv.dateKey(weekStart.getUTCFullYear(), weekStart.getUTCMonth() + 1, weekStart.getUTCDate());
  }

  function averageOf(days, select) {
    const values = days.map(select).filter((v) => v !== null && v !== undefined);
    return values.length === 0 ? null : HA.aggregate.averageOrNull(values);
  }

  function appendTrends(lines, snapshot) {
    if (snapshot.trends.length === 0) return;
    lines.push('## Tendances récentes');
    lines.push('Comparaison des deux dernières fenêtres de même longueur.');
    lines.push('');
    snapshot.trends.forEach((trend) => {
      const change = trend.changePercent !== null
        ? ` (${frSignedPercent(trend.changePercent)} %, ${wording(trend.direction)})`
        : '';
      lines.push(`- ${trend.metric.label} : ${frFixed(trend.recentAverage, 1)} ${trend.metric.unit}${change}`);
    });
    lines.push('');
  }

  function appendSleepRegularity(lines, snapshot) {
    const sleep = snapshot.sleepRegularity;
    if (!sleep) return;
    lines.push('## Sommeil');
    lines.push(`- Nuits mesurées : ${sleep.nightsMeasured}`);
    lines.push(`- Durée moyenne : ${formatMinutes(sleep.averageDurationMinutes)}`);
    lines.push(`- Objectif : ${formatMinutes(sleep.targetMinutes)}`);
    if (sleep.averageBedtimeSecondOfDay !== null) {
      const h = Math.floor(sleep.averageBedtimeSecondOfDay / 3600);
      const mi = Math.floor((sleep.averageBedtimeSecondOfDay % 3600) / 60);
      lines.push(`- Heure de coucher moyenne : ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`);
    }
    lines.push(`- Régularité du coucher : écart-type de ${frFixed(sleep.bedtimeSpreadHours, 1)} h`);
    lines.push(`- Dette de sommeil cumulée : ${formatMinutes(sleep.sleepDebtMinutes)}`);
    lines.push('');
  }

  /**
   * Résume la période en une ligne par semaine.
   * Une ligne par jour ferait exploser le coût sans rien apprendre au modèle.
   */
  function appendWeeklyTable(lines, snapshot) {
    const withData = snapshot.days.filter(HA.aggregate.hasData);
    const byWeek = {};
    withData.forEach((day) => {
      const weekStart = isoWeekStartKey(day.date);
      (byWeek[weekStart] = byWeek[weekStart] || []).push(day);
    });
    const weeks = Object.keys(byWeek).sort().slice(-MAX_WEEKS);
    if (weeks.length === 0) return;

    lines.push('## Moyennes par semaine');
    lines.push('');
    lines.push('| Semaine du | Pas | Sommeil | Score sommeil | FC repos | HRV | Stress | Poids | Énergie |');
    lines.push('|---|---|---|---|---|---|---|---|---|');
    weeks.forEach((weekStart) => {
      const days = byWeek[weekStart];
      const steps = averageOf(days, (d) => d.steps);
      const sleepMinutes = averageOf(days, (d) => d.sleepMinutes);
      const sleepScore = averageOf(days, (d) => d.sleepScore);
      const restingHr = averageOf(days, (d) => d.restingHeartRate);
      const hrv = averageOf(days, (d) => d.hrvRmssd);
      const stress = averageOf(days, (d) => d.averageStress);
      const weight = averageOf(days, (d) => d.weightKg);
      const energy = averageOf(days, (d) => d.energyScore);
      const cells = [
        weekStart,
        steps !== null ? String(Math.round(steps)) : '-',
        sleepMinutes !== null ? formatMinutes(Math.round(sleepMinutes)) : '-',
        sleepScore !== null ? String(Math.round(sleepScore)) : '-',
        restingHr !== null ? String(Math.round(restingHr)) : '-',
        hrv !== null ? String(Math.round(hrv)) : '-',
        stress !== null ? String(Math.round(stress)) : '-',
        weight !== null ? frFixed(weight, 1) : '-',
        energy !== null ? String(Math.round(energy)) : '-',
      ];
      lines.push(`| ${cells.join(' | ')} |`);
    });
  }

  function userPrompt(snapshot, question) {
    const lines = [];
    lines.push('# Données de santé');
    lines.push('');
    lines.push(`Période observée : ${snapshot.from} à ${snapshot.to}.`);
    lines.push(`Jours porteurs de mesures : ${snapshot.daysWithData} sur ${snapshot.days.length}.`);
    lines.push('');

    appendTrends(lines, snapshot);
    appendSleepRegularity(lines, snapshot);
    appendWeeklyTable(lines, snapshot);

    if (question && question.trim().length > 0) {
      lines.push('');
      lines.push("# Question de l'utilisateur");
      lines.push(question.trim());
    }

    return lines.join('\n');
  }

  // ========================================================================
  // narrativeUserPrompt — message utilisateur du récit de l'onglet Rapport
  // ========================================================================
  //
  // RÈGLE DE CONFIDENTIALITÉ (voir CLAUDE.md et `HealthPromptBuilder.kt`) : ne
  // sortent jamais un identifiant Samsung, un horodatage à la seconde ou à la
  // milliseconde, une mesure individuelle, une série quotidienne. Sortent et
  // doivent continuer de sortir les dates au jour près — la période observée,
  // le mois d'un agrégat, la date d'une mesure rare comme une pesée ou une prise
  // de tension : elles ne révèlent rien de plus que la période déjà envoyée.
  //
  // Chaque section ci-dessous liste explicitement ses champs autorisés
  // (moyennes mensuelles, profils par jour de semaine / par heure, indicateurs
  // `kpi`) et n'y touche jamais par accident : les séries quotidiennes ou
  // individuelles (`nightly`, `stepsDaily`, `stepsRolling7`, `restingDaily`,
  // `hrvDaily`, `bloodPressure`, `ecg`, `daily`, `vitalityDaily`, `spo2Daily`,
  // `respiratoryDaily`, `skinTempDaily`) ne sont lues NULLE PART dans ce bloc.
  // `bestStepsDate` (kpi activité) reste exclu malgré la règle ci-dessus : ce
  // n'est pas la date d'une mesure rare, mais celle du meilleur jour d'une série
  // quotidienne par ailleurs interdite — l'exposer reviendrait à sortir un point
  // précis de cette série. `redactPreciseTimestamps` est un filet de sécurité
  // sur tout texte libre (tuiles), contre un horodatage seconde/milliseconde qui
  // s'y glisserait par erreur — pas contre une date au jour près, qui reste permise.

  /** Efface un horodatage précis résiduel (secondes/millisecondes), jamais une date au jour près. Filet de sécurité, pas la règle elle-même. */
  function redactPreciseTimestamps(text) {
    return String(text)
      .replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?/g, '[horodatage]')
      .replace(/\b\d{10,}\b/g, '[horodatage]');
  }

  // Au-delà, un tableau mensuel grossirait le prompt sans ajouter d'information neuve
  // au LLM : deux ans d'historique suffisent à dégager une tendance. Même limite que
  // les semaines de `HealthPromptBuilder.kt`.
  const MAX_NARRATIVE_MONTHS = 24;

  const FULL_MONTHS_FR = [
    'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
    'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
  ];

  /** `2025-01` → `janvier 2025`. Le LLM lit mieux un mois en clair, et ne risque pas de recopier une clé technique. */
  function formatMonth(monthKey) {
    const [y, m] = String(monthKey).split('-').map(Number);
    if (!y || !m || m < 1 || m > 12) return String(monthKey);
    return `${FULL_MONTHS_FR[m - 1]} ${y}`;
  }

  /** Ne garde que les 24 derniers mois d'une série déjà triée chronologiquement. */
  function recentMonths(rows) {
    return (rows || []).slice(-MAX_NARRATIVE_MONTHS);
  }

  /**
   * Accord d'un nom au singulier ou au pluriel, comme en français : singulier pour
   * -1, 0 et 1, pluriel au-delà. Jamais de notation « (s) » — le LLM lit ce texte et
   * le recopie parfois tel quel dans sa prose.
   */
  function plural(n, singular, pluralForm) {
    return Math.abs(n) < 2 ? singular : (pluralForm || `${singular}s`);
  }

  /** Nombre à la française (virgule décimale, espace de groupement des milliers). */
  function fmtNum(value, decimals) {
    if (value === null || value === undefined || typeof value !== 'number' || Number.isNaN(value)) return '—';
    const digits = decimals === undefined ? 0 : decimals;
    return value.toLocaleString('fr-FR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }

  /** Heure relative à minuit (négative avant minuit, voir `report-model.js`) en `HH:MM`. */
  function formatRelativeClock(hours) {
    if (hours === null || hours === undefined) return '—';
    const clock = hours < 0 ? hours + 24 : hours;
    const h = Math.floor(clock);
    const m = Math.round((clock - h) * 60);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  function pushSection(lines, title) {
    lines.push('');
    lines.push(`## ${title}`);
  }

  function pushKpi(lines, kpi, fields) {
    if (!kpi) return;
    lines.push('Indicateurs :');
    fields.forEach(([key, label, formatter]) => {
      if (!(key in kpi)) return;
      lines.push(`- ${label} : ${formatter(kpi[key])}`);
    });
  }

  /**
   * Une ligne par entrée d'une série déjà triée chronologiquement (mois, tranche de
   * durée, type d'exercice…). Bornée aux 24 dernières entrées : sans effet sur les
   * séries courtes (tranches, types), essentiel sur les vraies séries mensuelles
   * (`month`) qui grandissent avec la période choisie.
   */
  function pushMonthlyRows(lines, title, rows, formatRow) {
    const kept = recentMonths(rows);
    if (kept.length === 0) return;
    lines.push(`${title} :`);
    kept.forEach((r) => lines.push(`- ${formatRow(r)}`));
  }

  function pushLabelValues(lines, title, rows, unit) {
    const defined = (rows || []).filter((r) => r.value !== null && r.value !== undefined);
    if (defined.length === 0) return;
    lines.push(`${title} :`);
    defined.forEach((r) => {
      const count = r.count !== null && r.count !== undefined ? ` (n=${r.count})` : '';
      lines.push(`- ${r.label} : ${fmtNum(r.value, 1)}${unit}${count}`);
    });
  }

  /**
   * Sous-texte d'une tuile pour le récit.
   *
   * La date d'une mesure rare (une pesée, une prise de tension) est permise telle
   * quelle — voir la règle de confidentialité ci-dessus — donc `tile.sub` part
   * inchangé. Deux tuiles reçoivent malgré tout un complément, en plus de leur
   * date, jamais à sa place : un délai relatif se lit mieux qu'une soustraction
   * de dates, et le LLM ne risque pas de se tromper de calcul.
   * - IMC : `body.kpi.daysSinceLastMeasure`, un délai déjà dans le contrat.
   * - Tension : le nombre de mesures de la période (`heart.bloodPressure.length` —
   *   un compte, jamais une valeur individuelle de cette liste, qui reste sinon
   *   entièrement hors de ce fichier).
   */
  function tileSubText(tile, model) {
    if (!tile.sub) return '';
    if (tile.key === 'bodyMassIndex') {
      const days = model.body && model.body.kpi ? model.body.kpi.daysSinceLastMeasure : null;
      if (days !== null && days !== undefined) return `${tile.sub}, il y a ${fmtNum(days, 0)} ${plural(days, 'jour')}`;
    }
    if (tile.key === 'bloodPressure') {
      const count = model.heart && Array.isArray(model.heart.bloodPressure) ? model.heart.bloodPressure.length : 0;
      if (count > 0) return `${tile.sub} (${fmtNum(count, 0)} ${plural(count, 'mesure')} sur la période)`;
    }
    return redactPreciseTimestamps(tile.sub);
  }

  function appendTiles(lines, model) {
    const tiles = model.tiles;
    if (!tiles || tiles.length === 0) return;
    pushSection(lines, 'Tuiles de synthèse');
    tiles.forEach((t) => {
      const unit = t.unit ? ` ${t.unit}` : '';
      const subText = tileSubText(t, model);
      const sub = subText ? ` — ${subText}` : '';
      lines.push(`- ${t.label} : ${t.value}${unit}${sub} [${t.status}]`);
    });
  }

  function appendSleep(lines, sleep) {
    pushSection(lines, 'Sommeil');
    pushMonthlyRows(lines, 'Moyennes mensuelles', sleep.monthly, (m) =>
      `${formatMonth(m.month)} : ${fmtNum(m.meanHours, 1)} h en moyenne (médiane ${fmtNum(m.medianHours, 1)} h), score moyen ${fmtNum(m.meanScore, 0)}, ${fmtNum(m.nights, 0)} nuits`);
    pushLabelValues(lines, 'Durée par jour de semaine', sleep.dayOfWeek, ' h');
    pushMonthlyRows(lines, 'Distribution des durées', sleep.distribution, (r) => `${r.label} : ${fmtNum(r.count, 0)} ${plural(r.count, 'nuit')}`);
    pushMonthlyRows(lines, 'Stades par mois', sleep.stagesMonthly, (m) =>
      `${formatMonth(m.month)} : profond ${fmtNum(m.deep, 0)} %, léger ${fmtNum(m.light, 0)} %, paradoxal ${fmtNum(m.rem, 0)} %, éveil ${fmtNum(m.awake, 0)} %`);
    pushKpi(lines, sleep.kpi, [
      ['nights', 'Nuits mesurées', (v) => fmtNum(v, 0)],
      ['meanHours', 'Durée moyenne', (v) => `${fmtNum(v, 1)} h`],
      ['medianHours', 'Durée médiane', (v) => `${fmtNum(v, 1)} h`],
      ['bedMedian', 'Coucher médian', (v) => formatRelativeClock(v)],
      ['wakeMedian', 'Réveil médian', (v) => formatRelativeClock(v)],
      ['bedSpreadHours', 'Régularité du coucher (écart-type)', (v) => `± ${fmtNum(v, 1)} h`],
      ['pctAfterMidnight', 'Nuits couchées après minuit', (v) => `${fmtNum(v, 0)} %`],
      ['pctAfter2h', 'Nuits couchées après 2 h', (v) => `${fmtNum(v, 0)} %`],
      ['pctUnder6h', 'Nuits sous 6 h', (v) => `${fmtNum(v, 0)} %`],
      ['pctOver7h', 'Nuits à 7 h ou plus', (v) => `${fmtNum(v, 0)} %`],
      ['weekendCatchupHours', 'Rattrapage le week-end', (v) => `${fmtNum(v, 1)} h`],
      ['debtHours', 'Dette de sommeil cumulée', (v) => `${fmtNum(v, 1)} h`],
      ['targetHours', 'Objectif', (v) => `${fmtNum(v, 1)} h`],
      ['efficiencyPercent', 'Efficacité moyenne', (v) => `${fmtNum(v, 0)} %`],
      ['latencyMinutes', 'Latence d\'endormissement', (v) => `${fmtNum(v, 0)} min`],
      ['snoringNights', 'Nuits avec ronflement', (v) => fmtNum(v, 0)],
      ['snoringMeasuredNights', 'Nuits avec mesure de ronflement', (v) => fmtNum(v, 0)],
      ['snoringMedianMinutes', 'Durée médiane de ronflement', (v) => `${fmtNum(v, 0)} min`],
      ['apneaResult', 'Dernier résultat apnée (code)', (v) => String(v)],
    ]);
  }

  function appendHeart(lines, heart) {
    pushSection(lines, 'Cœur');
    pushMonthlyRows(lines, 'Moyennes mensuelles', heart.monthly, (m) =>
      `${formatMonth(m.month)} : repos ${fmtNum(m.resting, 0)} bpm, moyenne ${fmtNum(m.average, 0)} bpm`);
    pushLabelValues(lines, 'Profil par heure (bpm)', heart.hourly, ' bpm');
    pushMonthlyRows(lines, 'Variabilité cardiaque par mois', heart.hrvMonthly, (m) => `${formatMonth(m.month)} : ${fmtNum(m.value, 0)} ms`);
    pushKpi(lines, heart.kpi, [
      ['restingMean', 'FC de repos moyenne', (v) => `${fmtNum(v, 0)} bpm`],
      ['restingP10', 'FC de repos, 10e centile', (v) => `${fmtNum(v, 0)} bpm`],
      ['restingP90', 'FC de repos, 90e centile', (v) => `${fmtNum(v, 0)} bpm`],
      ['averageMean', 'FC moyenne (hors repos)', (v) => `${fmtNum(v, 0)} bpm`],
      ['maxObserved', 'FC maximale observée', (v) => `${fmtNum(v, 0)} bpm`],
      ['hrvMedian', 'Variabilité cardiaque médiane', (v) => `${fmtNum(v, 0)} ms`],
      ['measuredDays', 'Jours mesurés', (v) => fmtNum(v, 0)],
    ]);
  }

  function appendActivity(lines, activity) {
    pushSection(lines, 'Activité');
    // `stepsMonthly`/`floorsMonthly` sont des `MonthValue` ({month, value}), pas des
    // `LabelValue` ({label, value}) : pushMonthlyRows, pas pushLabelValues.
    pushMonthlyRows(lines, 'Pas par mois', activity.stepsMonthly, (m) => `${formatMonth(m.month)} : ${fmtNum(m.value, 0)} pas`);
    pushLabelValues(lines, 'Pas par jour de semaine', activity.stepsDayOfWeek, ' pas');
    pushMonthlyRows(lines, 'Exercice par mois', activity.exerciseMonthly, (m) =>
      `${formatMonth(m.month)} : ${fmtNum(m.sessions, 0)} ${plural(m.sessions, 'séance')}, ${fmtNum(m.minutes, 0)} min${m.calories !== null ? `, ${fmtNum(m.calories, 0)} kcal` : ''}`);
    pushMonthlyRows(lines, 'Exercice par type', activity.exerciseByKind, (r) => `${r.label} : ${fmtNum(r.value, 0)} min (${fmtNum(r.count, 0)} ${plural(r.count, 'séance')})`);
    pushMonthlyRows(lines, 'Étages montés par mois', activity.floorsMonthly, (m) => `${formatMonth(m.month)} : ${fmtNum(m.value, 0)} étages`);
    pushKpi(lines, activity.kpi, [
      ['meanSteps', 'Pas moyens sur la période', (v) => `${fmtNum(v, 0)} pas`],
      ['meanSteps30', 'Pas moyens (30 derniers jours)', (v) => `${fmtNum(v, 0)} pas`],
      ['meanSteps90', 'Pas moyens (90 derniers jours)', (v) => `${fmtNum(v, 0)} pas`],
      ['bestSteps', 'Meilleur jour (nombre de pas, sans sa date)', (v) => `${fmtNum(v, 0)} pas`],
      ['pctDaysUnder3000', 'Jours sous 3000 pas', (v) => `${fmtNum(v, 0)} %`],
      ['pctDaysOver8000', 'Jours à 8000 pas ou plus', (v) => `${fmtNum(v, 0)} %`],
      ['totalExerciseMinutes', 'Minutes d\'exercice cumulées', (v) => `${fmtNum(v, 0)} min`],
      ['exerciseSessions', 'Séances d\'exercice', (v) => fmtNum(v, 0)],
      ['measuredDays', 'Jours avec un nombre de pas mesuré', (v) => fmtNum(v, 0)],
    ]);
  }

  function appendBody(lines, body) {
    pushSection(lines, 'Corps');
    // Pas de série mensuelle dans cette section du modèle : seul `kpi` est disponible.
    pushKpi(lines, body.kpi, [
      ['firstWeightKg', 'Premier poids mesuré sur la période', (v) => `${fmtNum(v, 1)} kg`],
      ['lastWeightKg', 'Dernier poids mesuré sur la période', (v) => `${fmtNum(v, 1)} kg`],
      ['deltaKg', 'Évolution du poids', (v) => `${fmtNum(v, 1)} kg`],
      ['deltaMuscleKg', 'Évolution de la masse musculaire', (v) => `${fmtNum(v, 1)} kg`],
      ['deltaFatKg', 'Évolution de la masse grasse', (v) => `${fmtNum(v, 1)} kg`],
      ['bodyMassIndex', 'IMC (dernière mesure)', (v) => fmtNum(v, 1)],
      ['bodyFatPercent', 'Masse grasse (dernière mesure)', (v) => `${fmtNum(v, 1)} %`],
      ['basalMetabolicRate', 'Métabolisme de base (dernière mesure)', (v) => `${fmtNum(v, 0)} kcal`],
      ['daysSinceLastMeasure', 'Jours depuis la dernière mesure', (v) => fmtNum(v, 0)],
      ['measures', 'Nombre de mesures sur la période', (v) => fmtNum(v, 0)],
    ]);
  }

  function appendStress(lines, stress) {
    pushSection(lines, 'Stress');
    pushMonthlyRows(lines, 'Moyennes mensuelles', stress.monthly, (m) =>
      `${formatMonth(m.month)} : moyenne ${fmtNum(m.mean, 0)}/100, ${fmtNum(m.percentAbove60, 0)} % du temps au-dessus de 60`);
    pushLabelValues(lines, 'Profil par heure', stress.hourly, '/100');
    pushLabelValues(lines, 'Profil par jour de semaine', stress.dayOfWeek, '/100');
    pushKpi(lines, stress.kpi, [
      ['mean', 'Stress moyen', (v) => `${fmtNum(v, 0)}/100`],
      ['percentAbove60', 'Temps au-dessus de 60', (v) => `${fmtNum(v, 0)} %`],
      ['alerts', 'Alertes de stress', (v) => fmtNum(v, 0)],
      ['peakHour', 'Heure de pic (0-23)', (v) => fmtNum(v, 0)],
      ['vitalityMean', 'Score de vitalité moyen', (v) => fmtNum(v, 0)],
      ['measuredDays', 'Jours mesurés', (v) => fmtNum(v, 0)],
    ]);
  }

  function appendBreathing(lines, breathing) {
    pushSection(lines, 'Respiration');
    pushMonthlyRows(lines, 'Oxygénation par mois', breathing.spo2Monthly, (m) =>
      `${formatMonth(m.month)} : moyenne ${fmtNum(m.mean, 1)} %, minimum ${fmtNum(m.min, 1)} %`);
    pushKpi(lines, breathing.kpi, [
      ['spo2Mean', 'Oxygénation moyenne', (v) => `${fmtNum(v, 1)} %`],
      ['spo2Measures', 'Mesures d\'oxygénation', (v) => fmtNum(v, 0)],
      ['spo2Under90', 'Mesures sous 90 %', (v) => fmtNum(v, 0)],
      ['respiratoryMean', 'Fréquence respiratoire moyenne', (v) => `${fmtNum(v, 1)} /min`],
      ['skinTempMean', 'Température cutanée moyenne', (v) => `${fmtNum(v, 1)} °C`],
      ['skinTempStdDev', 'Température cutanée, écart-type', (v) => `± ${fmtNum(v, 2)} °C`],
    ]);
  }

  function appendCorrelations(lines, correlations) {
    if (!correlations || correlations.length === 0) return;
    pushSection(lines, 'Corrélations (coefficient de Pearson sur jours appariés)');
    correlations.forEach((c) => lines.push(`- ${c.label} : r=${fmtNum(c.r, 2)} (n=${c.n})`));
  }

  /**
   * Construit le message utilisateur du récit de l'onglet Rapport, à partir du
   * `ReportModel` affiché. Ne lit QUE des agrégats — voir le commentaire de tête
   * de section pour la liste des champs jamais touchés.
   *
   * @param {object} model un `ReportModel` conforme à `report-model.js`
   * @returns {string} le message utilisateur, en français
   */
  function narrativeUserPrompt(model) {
    const lines = [];
    const meta = model.meta || {};
    lines.push('# Bilan de santé — agrégats de la période');
    lines.push(`Période : ${meta.periodLabel || '—'} (${fmtNum(meta.days, 0)} jours, ${fmtNum(meta.activeDays, 0)} jours actifs, ${fmtNum(meta.nights, 0)} nuits mesurées).`);
    if (meta.profile && (meta.profile.heightCm || meta.profile.weightKg)) {
      const height = meta.profile.heightCm !== null && meta.profile.heightCm !== undefined ? `${fmtNum(meta.profile.heightCm, 0)} cm` : null;
      const weight = meta.profile.weightKg !== null && meta.profile.weightKg !== undefined ? `${fmtNum(meta.profile.weightKg, 1)} kg` : null;
      lines.push(`Profil : ${[height, weight].filter(Boolean).join(', ')}.`);
    }

    appendTiles(lines, model);
    appendSleep(lines, model.sleep);
    appendHeart(lines, model.heart);
    appendActivity(lines, model.activity);
    appendBody(lines, model.body);
    appendStress(lines, model.stress);
    appendBreathing(lines, model.breathing);
    appendCorrelations(lines, model.correlations);

    return lines.join('\n');
  }

  // ========================================================================
  // historyOverview — aperçu mensuel de tout l'historique, pour {{OVERVIEW}}
  // ========================================================================
  //
  // Portage fidèle de `HistoryOverview.kt` côté Android. Ne sort qu'un compte de jours
  // par mois, jamais une date précise ni une valeur — le même genre d'agrégat que
  // `sleep.monthly.nights`, déjà toléré par la frontière de confidentialité (voir
  // CLAUDE.md, section « Confidentialité »). `model` lui-même ne quitte jamais l'appareil ;
  // seul le texte produit par `describeHistoryOverview` part dans le prompt système.

  /** Les séries quotidiennes/nocturnes dont l'union des dates définit un « jour actif ». */
  function dailySeriesForOverview(model) {
    return [
      (model.sleep && model.sleep.nightly) || [],
      (model.activity && model.activity.stepsDaily) || [],
      (model.heart && model.heart.restingDaily) || [],
      (model.heart && model.heart.hrvDaily) || [],
      (model.stress && model.stress.daily) || [],
      (model.stress && model.stress.vitalityDaily) || [],
      (model.body && model.body.daily) || [],
      (model.breathing && model.breathing.spo2Daily) || [],
      (model.breathing && model.breathing.respiratoryDaily) || [],
      (model.breathing && model.breathing.skinTempDaily) || [],
    ];
  }

  /**
   * Aperçu mensuel de tout l'historique porté par `model`, qui doit couvrir toute la
   * période importée — jamais une fenêtre restreinte, sinon l'aperçu mentirait sur
   * l'étendue réelle des données.
   *
   * @param {object} model un `ReportModel` sur tout l'historique
   * @returns {{earliest: string|null, latest: string|null, months: {month: string, daysWithData: number}[]}}
   */
  function historyOverview(model) {
    const dates = new Set();
    dailySeriesForOverview(model).forEach((rows) => rows.forEach((r) => {
      if (r && r.date) dates.add(r.date);
    }));

    const counts = {};
    dates.forEach((d) => {
      const month = d.slice(0, 7);
      counts[month] = (counts[month] || 0) + 1;
    });

    const months = Object.keys(counts).sort().map((month) => ({ month, daysWithData: counts[month] }));
    const sorted = Array.from(dates).sort();
    return {
      earliest: sorted.length ? sorted[0] : null,
      latest: sorted.length ? sorted[sorted.length - 1] : null,
      months,
    };
  }

  /**
   * Met `historyOverview(model)` en texte pour `{{OVERVIEW}}` — voir `report/chat-prompt.txt`.
   * C'est tout ce que le modèle sait de l'historique avant de choisir une fenêtre avec
   * `healthrange` : de quand à quand des données existent, et combien de jours par mois.
   */
  function describeHistoryOverview(overview) {
    if (!overview.months.length) return "Aucune donnée n'a encore été importée.";
    const lines = [];
    lines.push(`Historique disponible : du ${overview.earliest} au ${overview.latest}.`);
    lines.push('Jours avec au moins une mesure, par mois :');
    overview.months.forEach((m) => lines.push(`- ${m.month} : ${m.daysWithData} jours`));
    return lines.join('\n');
  }

  root.HA.prompt = {
    systemPrompt: () => SYSTEM_PROMPT, userPrompt, formatMinutes, frFixed, narrativeUserPrompt,
    historyOverview, describeHistoryOverview,
  };
})();
