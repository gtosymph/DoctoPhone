/**
 * Les graphiques de la section activité : pas, jours de semaine et exercice.
 *
 * Voir `report-model.js` pour la structure du modèle et les conventions.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;

  /** Objectif bas et haut de la bande cible de pas quotidiens. */
  const STEPS_TARGET_LOW = 7000;
  const STEPS_TARGET_HIGH = 8000;

  /** Au-delà de ce trou, la courbe se coupe. */
  const MAX_GAP_DAYS = 14;

  /** Les pas se lisent en milliers : « 10k » tient là où « 10 000 » déborde. */
  function kSteps(v) {
    return (Math.round(v / 100) / 10).toString().replace('.', ',') + 'k';
  }

  const charts = {};

  /** Pas quotidiens, en moyenne glissante sur 7 jours. */
  charts['activity-steps'] = function (host, model) {
    const rows = model.activity.stepsRolling7.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucun pas enregistré sur cette période.');

    // L'objectif entre dans l'étendue : une bande cible hors du cadre ne se lit pas.
    const s = E.scaleOf(rows.map(r => r.value).concat([STEPS_TARGET_HIGH]), {
      zero: true, fmt: kSteps,
    });
    const f = E.frame(host, {
      h: 260, gutter: s.gutter,
      label: 'Pas quotidiens, moyenne glissante sur 7 jours',
    });
    const sy = E.grid(f, s);
    E.targetBand(f, sy, STEPS_TARGET_LOW, STEPS_TARGET_HIGH, 'objectif 7 à 8 k pas');

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const aqua = E.cssVar('--s-aqua');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(r.value), t, row: r };
    });
    E.drawSeries(f, pts, {
      color: aqua, width: 2, maxGapMs: MAX_GAP_DAYS * E.DAY_MS, area: f.ih,
    });
    const last = pts[pts.length - 1];
    E.lastPoint(f, last, aqua, E.fmtInt(last.row.value));

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: [E.fmtInt(p.row.value) + ' pas par jour, moyenne sur 7 jours'],
    }));
  };

  /** Pas moyens selon le jour de la semaine. */
  charts['activity-steps-dow'] = function (host, model) {
    const rows = model.activity.stepsDayOfWeek.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host);

    const s = E.scaleOf(rows.map(r => r.value), { zero: true, fmt: kSteps });
    const f = E.frame(host, {
      h: 220, w: 460, gutter: s.gutter, label: 'Pas par jour de semaine',
    });
    const sy = E.grid(f, s);
    const bw = f.iw / rows.length;
    const aqua = E.cssVar('--s-aqua');

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .18, y: sy(r.value), width: bw * .64,
        height: Math.max(0, f.ih - sy(r.value)), rx: 4, fill: aqua, opacity: .85,
      }, f.g);
      const label = E.E('text', {
        x: i * bw + bw / 2, y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      label.textContent = r.label;
      E.hoverShape(rect, r.label, [
        E.fmtInt(r.value) + ' pas en moyenne',
        (r.count || 0) + ' jours mesurés',
      ]);
    });
  };

  /** Minutes d'exercice enregistrées, mois par mois. */
  charts['activity-exercise'] = function (host, model) {
    const rows = model.activity.exerciseMonthly.filter(r => r.minutes > 0);
    if (!rows.length) return E.drawEmpty(host, 'Aucune séance d\'exercice enregistrée.');

    const s = E.scaleOf(rows.map(r => r.minutes), {
      zero: true, fmt: v => E.fmtInt(v) + ' min',
    });
    const f = E.frame(host, {
      h: 220, w: 460, gutter: s.gutter, label: 'Minutes d\'exercice par mois',
    });
    const sy = E.grid(f, s);
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;
    const aqua = E.cssVar('--s-aqua');

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .18, y: sy(r.minutes), width: bw * .64,
        height: Math.max(0, f.ih - sy(r.minutes)), rx: 4, fill: aqua, opacity: .85,
      }, f.g);
      const lines = [
        r.sessions + (r.sessions > 1 ? ' séances' : ' séance'),
        E.fmtInt(r.minutes) + ' minutes',
      ];
      if (r.calories) lines.push(E.fmtInt(r.calories) + ' kcal');
      E.hoverShape(rect, E.fmtMonth(r.month), lines);
    });
  };

  /** Étages montés, mois par mois. */
  charts['activity-floors'] = function (host, model) {
    const rows = model.activity.floorsMonthly.filter(r => r.value !== null && r.value > 0);
    if (!rows.length) return E.drawEmpty(host, 'Aucun étage enregistré.');

    const s = E.scaleOf(rows.map(r => r.value), { zero: true, fmt: v => E.fmtInt(v) });
    const f = E.frame(host, {
      h: 200, w: 460, gutter: s.gutter, label: 'Étages montés par mois',
    });
    const sy = E.grid(f, s);
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;
    const yellow = E.cssVar('--s-yellow');

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .18, y: sy(r.value), width: bw * .64,
        height: Math.max(0, f.ih - sy(r.value)), rx: 4, fill: yellow, opacity: .8,
      }, f.g);
      E.hoverShape(rect, E.fmtMonth(r.month), [E.fmtInt(r.value) + ' étages en moyenne par jour']);
    });
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
