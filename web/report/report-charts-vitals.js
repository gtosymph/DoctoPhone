/**
 * Les graphiques des sections corps, stress et respiration.
 *
 * Voir `report-model.js` pour la structure du modèle et les conventions.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;

  /** Seuil au-delà duquel Samsung considère le stress comme élevé. */
  const STRESS_HIGH = 60;

  const charts = {};

  /** Poids et masse musculaire, mesure par mesure. */
  charts['body-weight'] = function (host, model) {
    const rows = model.body.daily;
    if (!rows.length) return E.drawEmpty(host, 'Aucune pesée enregistrée sur cette période.');

    const yellow = E.cssVar('--s-yellow');
    const aqua = E.cssVar('--s-aqua');
    const hasMuscle = rows.some(r => r.skeletalMuscleKg !== null && r.skeletalMuscleKg !== undefined);
    E.legend(host, hasMuscle
      ? [['Poids (kg)', yellow], ['Muscle squelettique (kg)', aqua]]
      : [['Poids (kg)', yellow]]);

    const f = E.frame(host, { h: 260, label: 'Poids et composition corporelle' });
    const values = rows.flatMap(r => [r.weightKg, r.skeletalMuscleKg])
      .filter(v => v !== null && v !== undefined && isFinite(v));
    const lo = Math.floor((Math.min(...values) - 3) / 10) * 10;
    const hi = Math.ceil((Math.max(...values) + 3) / 10) * 10;
    const ticks = [];
    for (let v = lo; v <= hi; v += Math.max(10, Math.round((hi - lo) / 4 / 10) * 10)) ticks.push(v);
    const sy = E.grid(f, lo, hi, ticks, v => v + ' kg');

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0 === t1 ? t0 - E.DAY_MS : t0, t0 === t1 ? t1 + E.DAY_MS : t1);

    const weight = rows.map(r => ({ x: sx(E.dateMs(r.date)), y: sy(r.weightKg), row: r }));
    const muscle = rows
      .filter(r => r.skeletalMuscleKg !== null && r.skeletalMuscleKg !== undefined)
      .map(r => ({ x: sx(E.dateMs(r.date)), y: sy(r.skeletalMuscleKg) }));

    if (weight.length > 1) {
      E.E('path', { d: E.linePath(weight), fill: 'none', stroke: yellow, 'stroke-width': 2.5 }, f.g);
    }
    weight.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: yellow }, f.g));
    if (muscle.length > 1) {
      E.E('path', { d: E.linePath(muscle), fill: 'none', stroke: aqua, 'stroke-width': 2 }, f.g);
    }

    E.hoverNearest(f, weight, p => {
      const lines = ['Poids : ' + E.fmtNum(p.row.weightKg) + ' kg'];
      if (p.row.bodyMassIndex) lines.push('IMC : ' + E.fmtNum(p.row.bodyMassIndex));
      if (p.row.skeletalMuscleKg) lines.push('Muscle : ' + E.fmtNum(p.row.skeletalMuscleKg) + ' kg');
      if (p.row.bodyFatPercent) lines.push('Masse grasse : ' + E.fmtNum(p.row.bodyFatPercent) + ' %');
      return { title: E.fmtDate(p.row.date), lines };
    });
  };

  /** Score de stress moyen par mois, avec la part du temps passé en zone élevée. */
  charts['stress-monthly'] = function (host, model) {
    const rows = model.stress.monthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure de stress sur cette période.');

    const orange = E.cssVar('--s-orange');
    const yellow = E.cssVar('--s-yellow');
    E.legend(host, [['Score moyen', orange], ['Part du temps au-dessus de 60', yellow]]);

    const f = E.frame(host, { h: 240, w: 460, label: 'Stress par mois' });
    const sy = E.grid(f, 0, 60, [0, 20, 40, 60], v => v);
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .3, y: sy(Math.min(r.percentAbove60, 60)), width: bw * .4,
        height: Math.max(0, f.ih - sy(Math.min(r.percentAbove60, 60))), rx: 3,
        fill: yellow, opacity: .55,
      }, f.g);
      E.hoverShape(rect, E.fmtMonth(r.month), [
        E.fmtNum(r.percentAbove60) + ' % du temps au-dessus de ' + STRESS_HIGH,
      ]);
    });

    const pts = rows.map((r, i) => ({ x: i * bw + bw / 2, y: sy(Math.min(r.mean, 60)), row: r }));
    if (pts.length > 1) {
      E.E('path', { d: E.linePath(pts), fill: 'none', stroke: orange, 'stroke-width': 2.5 }, f.g);
    }
    pts.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: orange }, f.g));

    E.hoverNearest(f, pts, p => ({
      title: E.fmtMonth(p.row.month),
      lines: [
        'Score moyen : ' + E.fmtInt(p.row.mean),
        E.fmtNum(p.row.percentAbove60) + ' % du temps au-dessus de ' + STRESS_HIGH,
      ],
    }));
  };

  /** Profil horaire du stress, moyenne sur toute la période. */
  charts['stress-hourly'] = function (host, model) {
    const rows = model.stress.hourly.filter(r => r.value !== null);
    if (rows.length < 6) return E.drawEmpty(host, 'Pas assez de mesures pour un profil horaire.');

    const f = E.frame(host, { h: 240, w: 460, label: 'Stress par heure de la journée' });
    const top = Math.max(60, Math.ceil(Math.max(...rows.map(r => r.value)) / 20) * 20);
    const sy = E.grid(f, 0, top, [0, top / 3, top * 2 / 3, top], v => Math.round(v));
    const sx = h => Number(h) / 23 * f.iw;

    for (const h of [0, 6, 12, 18, 23]) {
      const t = E.E('text', {
        x: sx(h), y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = h + 'h';
    }

    const orange = E.cssVar('--s-orange');
    const pts = rows.map(r => ({ x: sx(r.label), y: sy(r.value), row: r }));
    E.E('path', { d: E.areaPath(pts, f.ih), fill: orange, opacity: .14 }, f.g);
    E.E('path', { d: E.linePath(pts), fill: 'none', stroke: orange, 'stroke-width': 2.5 }, f.g);

    E.hoverNearest(f, pts, p => ({
      title: p.row.label + 'h',
      lines: ['Score moyen : ' + E.fmtInt(p.row.value)],
    }));
  };

  /** Score de vitalité Samsung, jour par jour. */
  charts['stress-vitality'] = function (host, model) {
    const rows = model.stress.vitalityDaily.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucun score de vitalité enregistré.');

    const f = E.frame(host, { h: 230, label: 'Score de vitalité quotidien' });
    const sy = E.grid(f, 30, 100, [30, 50, 75, 100], v => v);
    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const accent = E.cssVar('--accent');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(Math.max(30, Math.min(100, r.value))), t, row: r };
    });
    for (const seg of E.segments(pts, 14 * E.DAY_MS)) {
      if (seg.length < 2) continue;
      E.E('path', {
        d: E.linePath(seg), fill: 'none', stroke: accent, 'stroke-width': 1.6, opacity: .9,
      }, f.g);
    }

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: ['Vitalité : ' + E.fmtInt(p.row.value) + '/100'],
    }));
  };

  /** Saturation en oxygène, moyenne et minimum de chaque mois. */
  charts['breathing-spo2'] = function (host, model) {
    const rows = model.breathing.spo2Monthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure d\'oxygénation.');

    const blue = E.cssVar('--s-blue');
    const magenta = E.cssVar('--s-magenta');
    E.legend(host, [['Moyenne du mois', blue], ['Minimum du mois', magenta]]);

    const f = E.frame(host, { h: 230, label: 'Saturation en oxygène par mois' });
    const sy = E.grid(f, 88, 100, [90, 95, 100], v => v + ' %');
    const cx = E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);

    const pts = rows.map((r, i) => ({ x: cx(i), y: sy(Math.max(88, r.mean)), row: r }));
    if (pts.length > 1) {
      E.E('path', { d: E.linePath(pts), fill: 'none', stroke: blue, 'stroke-width': 2.5 }, f.g);
    }
    pts.forEach((p, i) => {
      E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: blue }, f.g);
      E.E('circle', {
        cx: p.x, cy: sy(Math.max(88, rows[i].min)), r: 3,
        fill: 'none', stroke: magenta, 'stroke-width': 1.8,
      }, f.g);
    });

    E.hoverNearest(f, pts, p => {
      const lines = [
        'Moyenne : ' + E.fmtNum(p.row.mean) + ' %',
        'Minimum : ' + E.fmtNum(p.row.min) + ' %',
      ];
      // Une valeur diurne isolée sous 90 % vient presque toujours d'un mauvais contact
      // du capteur, pas d'une désaturation réelle.
      if (p.row.min < 90) lines.push('Artefact de mesure probable');
      return { title: E.fmtMonth(p.row.month), lines };
    });
  };

  /** Température cutanée nocturne, nuit par nuit. */
  charts['breathing-skintemp'] = function (host, model) {
    const rows = model.breathing.skinTempDaily.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure de température cutanée.');

    const f = E.frame(host, { h: 200, label: 'Température cutanée nocturne' });
    const values = rows.map(r => r.value);
    const lo = Math.floor(Math.min(...values) - 0.5);
    const hi = Math.ceil(Math.max(...values) + 0.5);
    const sy = E.grid(f, lo, hi, [lo, (lo + hi) / 2, hi], v => E.fmtNum(v) + ' °C');
    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const magenta = E.cssVar('--s-magenta');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(r.value), t, row: r };
    });
    for (const seg of E.segments(pts, 14 * E.DAY_MS)) {
      if (seg.length < 2) continue;
      E.E('path', {
        d: E.linePath(seg), fill: 'none', stroke: magenta, 'stroke-width': 1.6, opacity: .9,
      }, f.g);
    }

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: [E.fmtNum(p.row.value) + ' °C au poignet'],
    }));
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
