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

  const charts = {};

  /** Pas quotidiens, en moyenne glissante sur 7 jours. */
  charts['activity-steps'] = function (host, model) {
    const rows = model.activity.stepsRolling7.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucun pas enregistré sur cette période.');

    const f = E.frame(host, {
      h: 260, m: { l: 52, r: 14, t: 14, b: 30 },
      label: 'Pas quotidiens, moyenne glissante sur 7 jours',
    });
    const peak = Math.max(...rows.map(r => r.value), STEPS_TARGET_HIGH);
    const top = Math.ceil(peak / 2000) * 2000;
    const ticks = [];
    for (let v = 0; v <= top; v += Math.max(2000, Math.round(top / 4 / 1000) * 1000)) ticks.push(v);
    const sy = E.grid(f, 0, top, ticks, v => (v / 1000) + 'k');
    E.targetBand(f, sy, STEPS_TARGET_LOW, STEPS_TARGET_HIGH);

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const aqua = E.cssVar('--s-aqua');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(Math.min(r.value, top)), t, row: r };
    });

    for (const seg of E.segments(pts, MAX_GAP_DAYS * E.DAY_MS)) {
      if (seg.length < 2) continue;
      E.E('path', { d: E.areaPath(seg, f.ih), fill: aqua, opacity: .15 }, f.g);
      E.E('path', { d: E.linePath(seg), fill: 'none', stroke: aqua, 'stroke-width': 2 }, f.g);
    }

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: [E.fmtInt(p.row.value) + ' pas par jour, moyenne sur 7 jours'],
    }));
  };

  /** Pas moyens selon le jour de la semaine. */
  charts['activity-steps-dow'] = function (host, model) {
    const rows = model.activity.stepsDayOfWeek.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host);

    const f = E.frame(host, {
      h: 220, w: 460, m: { l: 46, r: 14, t: 14, b: 30 },
      label: 'Pas par jour de semaine',
    });
    const top = Math.ceil(Math.max(...rows.map(r => r.value)) / 2000) * 2000 || 2000;
    const ticks = [0, top / 2, top];
    const sy = E.grid(f, 0, top, ticks, v => (v / 1000) + 'k');
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

    const f = E.frame(host, { h: 220, w: 460, label: 'Minutes d\'exercice par mois' });
    const top = Math.ceil(Math.max(...rows.map(r => r.minutes)) / 50) * 50 || 50;
    const sy = E.grid(f, 0, top, [0, top / 2, top], v => Math.round(v) + ' min');
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

    const f = E.frame(host, { h: 200, w: 460, label: 'Étages montés par mois' });
    const top = Math.ceil(Math.max(...rows.map(r => r.value)) / 50) * 50 || 50;
    const sy = E.grid(f, 0, top, [0, top / 2, top], v => Math.round(v));
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
