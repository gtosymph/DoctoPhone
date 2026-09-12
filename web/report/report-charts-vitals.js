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

  /** Au-delà de ce trou, une courbe quotidienne se coupe. */
  const MAX_GAP_DAYS = 14;

  const charts = {};

  /** Poids et masse musculaire, mesure par mesure. */
  charts['body-weight'] = function (host, model) {
    const rows = model.body.daily;
    if (!rows.length) return E.drawEmpty(host, 'Aucune pesée enregistrée sur cette période.');

    const yellow = E.cssVar('--s-yellow');
    const aqua = E.cssVar('--s-aqua');
    const hasMuscle = rows.some(r => r.skeletalMuscleKg !== null && r.skeletalMuscleKg !== undefined);
    // Le muscle est tireté : imprimées en niveaux de gris, deux courbes pleines de
    // teintes différentes ne se distinguent plus.
    E.legend(host, hasMuscle
      ? [['Poids (kg)', yellow, E.DASH.solid], ['Muscle squelettique (kg)', aqua, E.DASH.dashed]]
      : [['Poids (kg)', yellow, E.DASH.solid]]);

    const s = E.scaleOf(rows.flatMap(r => [r.weightKg, r.skeletalMuscleKg]), {
      fmt: v => E.fmtNum(v, Number.isInteger(v) ? 0 : 1) + ' kg',
    });
    if (!s) return E.drawEmpty(host, 'Aucune pesée enregistrée sur cette période.');

    const f = E.frame(host, {
      h: 260, gutter: s.gutter, label: 'Poids et composition corporelle',
    });
    const sy = E.grid(f, s);

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0 === t1 ? t0 - E.DAY_MS : t0, t0 === t1 ? t1 + E.DAY_MS : t1);

    const weight = rows.map(r => ({ x: sx(E.dateMs(r.date)), y: sy(r.weightKg), row: r }));
    const muscle = rows
      .filter(r => r.skeletalMuscleKg !== null && r.skeletalMuscleKg !== undefined)
      .map(r => ({ x: sx(E.dateMs(r.date)), y: sy(r.skeletalMuscleKg) }));

    E.drawSeries(f, weight, { color: yellow, width: 2.5 });
    weight.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: yellow }, f.g));
    E.drawSeries(f, muscle, { color: aqua, width: 2, dash: E.DASH.dashed });

    const last = weight[weight.length - 1];
    E.lastPoint(f, last, yellow, E.fmtNum(last.row.weightKg) + ' kg');

    E.hoverNearest(f, weight, p => {
      const lines = ['Poids : ' + E.fmtNum(p.row.weightKg) + ' kg'];
      if (p.row.bodyMassIndex) lines.push('IMC : ' + E.fmtNum(p.row.bodyMassIndex));
      if (p.row.skeletalMuscleKg) lines.push('Muscle : ' + E.fmtNum(p.row.skeletalMuscleKg) + ' kg');
      if (p.row.bodyFatPercent) lines.push('Masse grasse : ' + E.fmtNum(p.row.bodyFatPercent) + ' %');
      return { title: E.fmtDate(p.row.date), lines };
    });
  };

  /**
   * Score de stress moyen par mois, avec la part du temps passé en zone élevée.
   *
   * L'axe couvre les deux séries. Il était borné à 60, et tout ce qui dépassait était
   * rabattu sur cette ligne : une part de 91 % du temps au-dessus de 60 se lisait 60.
   * Les deux grandeurs vont de 0 à 100 et la légende dit laquelle est laquelle, mais
   * l'axe ne porte aucune unité, précisément parce qu'il en sert deux.
   */
  charts['stress-monthly'] = function (host, model) {
    const rows = model.stress.monthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure de stress sur cette période.');

    const orange = E.cssVar('--s-orange');
    const yellow = E.cssVar('--s-yellow');
    E.legend(host, [
      ['Score moyen', orange, E.DASH.solid],
      ['Part du temps au-dessus de ' + STRESS_HIGH, yellow, 'fill'],
    ]);

    const s = E.scaleOf(rows.flatMap(r => [r.mean, r.percentAbove60]), {
      zero: true, capHigh: 100, fmt: v => E.fmtInt(v),
    });
    if (!s) return E.drawEmpty(host, 'Aucune mesure de stress sur cette période.');

    const f = E.frame(host, { h: 240, w: 460, gutter: s.gutter, label: 'Stress par mois' });
    const sy = E.grid(f, s);
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .3, y: sy(r.percentAbove60), width: bw * .4,
        height: Math.max(0, f.ih - sy(r.percentAbove60)), rx: 3,
        fill: yellow, opacity: .55,
      }, f.g);
      E.hoverShape(rect, E.fmtMonth(r.month), [
        E.fmtNum(r.percentAbove60) + ' % du temps au-dessus de ' + STRESS_HIGH,
      ]);
    });

    const pts = rows.map((r, i) => ({ x: i * bw + bw / 2, y: sy(r.mean), row: r }));
    E.drawSeries(f, pts, { color: orange, width: 2.5 });
    pts.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: orange }, f.g));

    const last = pts[pts.length - 1];
    E.lastPoint(f, last, orange, E.fmtInt(last.row.mean));

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

    const s = E.scaleOf(rows.map(r => r.value), { zero: true, fmt: v => E.fmtInt(v) });
    const f = E.frame(host, {
      h: 240, w: 460, gutter: s.gutter, label: 'Stress par heure de la journée',
    });
    const sy = E.grid(f, s);
    const sx = h => Number(h) / 23 * f.iw;

    for (const h of [0, 6, 12, 18, 23]) {
      const t = E.E('text', {
        x: E.clampLabelX(f, sx(h), h + 'h'), y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = h + 'h';
    }

    const orange = E.cssVar('--s-orange');
    const pts = rows.map(r => ({ x: sx(r.label), y: sy(r.value), row: r }));
    E.drawSeries(f, pts, { color: orange, width: 2.5, area: f.ih, areaOpacity: .14 });

    E.hoverNearest(f, pts, p => ({
      title: p.row.label + 'h',
      lines: ['Score moyen : ' + E.fmtInt(p.row.value)],
    }));
  };

  /** Score de vitalité Samsung, jour par jour. */
  charts['stress-vitality'] = function (host, model) {
    const rows = model.stress.vitalityDaily.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucun score de vitalité enregistré.');

    const s = E.scaleOf(rows.map(r => r.value), { capHigh: 100, fmt: v => E.fmtInt(v) });
    const f = E.frame(host, {
      h: 230, gutter: s.gutter, label: 'Score de vitalité quotidien',
    });
    const sy = E.grid(f, s);
    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const accent = E.cssVar('--accent');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(r.value), t, row: r };
    });
    E.drawSeries(f, pts, { color: accent, width: 1.6, maxGapMs: MAX_GAP_DAYS * E.DAY_MS });

    const last = pts[pts.length - 1];
    E.lastPoint(f, last, accent, E.fmtInt(last.row.value));

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: ['Vitalité : ' + E.fmtInt(p.row.value) + '/100'],
    }));
  };

  /**
   * Saturation en oxygène, moyenne et minimum de chaque mois.
   *
   * Le minimum est dessiné en cercle creux et la moyenne en trait plein : deux marques
   * de formes différentes, donc lisibles sans la couleur.
   */
  charts['breathing-spo2'] = function (host, model) {
    const rows = model.breathing.spo2Monthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure d\'oxygénation.');

    const blue = E.cssVar('--s-blue');
    const magenta = E.cssVar('--s-magenta');
    E.legend(host, [
      ['Moyenne du mois', blue, E.DASH.solid],
      ['Minimum du mois', magenta, E.DASH.dotted],
    ]);

    // Une saturation ne dépasse pas 100 % : l'axe s'y arrête, mais il descend aussi bas
    // que le plus faible minimum mesuré. Il était borné à 88 et y rabattait tout.
    const s = E.scaleOf(rows.flatMap(r => [r.mean, r.min]), {
      capHigh: 100, fmt: v => E.fmtInt(v) + ' %',
    });
    if (!s) return E.drawEmpty(host, 'Aucune mesure d\'oxygénation.');

    const f = E.frame(host, {
      h: 230, gutter: s.gutter, label: 'Saturation en oxygène par mois',
    });
    const sy = E.grid(f, s);
    const cx = E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);

    const pts = rows.map((r, i) => ({ x: cx(i), y: sy(r.mean), row: r }));
    E.drawSeries(f, pts, { color: blue, width: 2.5 });
    pts.forEach((p, i) => {
      E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: blue }, f.g);
      E.E('circle', {
        cx: p.x, cy: sy(rows[i].min), r: 3,
        fill: 'none', stroke: magenta, 'stroke-width': 1.8,
      }, f.g);
    });

    const last = pts[pts.length - 1];
    E.lastPoint(f, last, blue, E.fmtNum(last.row.mean) + ' %');

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

    const s = E.scaleOf(rows.map(r => r.value), { fmt: v => E.fmtNum(v) + ' °C' });
    const f = E.frame(host, {
      h: 200, gutter: s.gutter, label: 'Température cutanée nocturne',
    });
    const sy = E.grid(f, s);
    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const magenta = E.cssVar('--s-magenta');

    const pts = rows.map(r => {
      const t = E.dateMs(r.date);
      return { x: sx(t), y: sy(r.value), t, row: r };
    });
    E.drawSeries(f, pts, { color: magenta, width: 1.6, maxGapMs: MAX_GAP_DAYS * E.DAY_MS });

    const last = pts[pts.length - 1];
    E.lastPoint(f, last, magenta, E.fmtNum(last.row.value) + ' °C');

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: [E.fmtNum(p.row.value) + ' °C au poignet'],
    }));
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
