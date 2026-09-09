/**
 * Les cinq graphiques de la section sommeil.
 *
 * Chaque fonction reçoit le conteneur et le modèle de rapport, et ne lit que la section
 * `sleep`. Voir `report-model.js` pour la structure exacte et les conventions.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;

  /** Fenêtre de lissage des nuits. Deux semaines effacent le bruit du week-end. */
  const SLEEP_SMOOTHING_NIGHTS = 14;

  /** Nombre minimal de nuits dans la fenêtre avant de tracer la moyenne glissante. */
  const SLEEP_SMOOTHING_MIN = 5;

  /** Au-delà de ce trou, la courbe se coupe : relier inventerait une tendance. */
  const MAX_GAP_DAYS = 21;

  /** Durée à partir de laquelle une nuit atteint la zone cible. */
  const TARGET_HOURS = 7;

  /**
   * Dit si une tranche de durée atteint la cible, à partir de son étiquette.
   *
   * Les étiquettes sont produites par le constructeur du modèle, sous la forme
   * `< 5 h`, `5-6 h`, `> 8 h`. La borne basse est le premier nombre lu, sauf pour une
   * tranche ouverte vers le bas, dont la borne basse vaut zéro.
   */
  function bucketReachesTarget(label) {
    const text = String(label);
    if (text.trim().startsWith('<')) return false;
    const match = text.match(/\d+/);
    return match ? Number(match[0]) >= TARGET_HOURS : false;
  }

  const charts = {};

  /**
   * Durée de sommeil, nuit par nuit.
   *
   * Les points sont les nuits réelles, la ligne leur moyenne glissante, la bande la
   * zone cible de 7 à 9 heures.
   */
  charts['sleep-nightly'] = function (host, model) {
    const rows = model.sleep.nightly;
    if (!rows.length) return E.drawEmpty(host, 'Aucune nuit enregistrée sur cette période.');

    const f = E.frame(host, { h: 280, label: 'Durée de sommeil par nuit' });
    const sy = E.grid(f, 0, 12, [0, 3, 6, 7, 9, 12], v => v + 'h');
    E.targetBand(f, sy, 7, 9);

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const violet = E.cssVar('--s-violet');

    const pts = [];
    for (const r of rows) {
      const t = E.dateMs(r.date);
      const x = sx(t);
      const y = sy(Math.min(r.hours, 12));
      E.E('circle', { cx: x, cy: y, r: 2.4, fill: violet, opacity: .38 }, f.g);
      pts.push({ x, y, t, row: r });
    }

    const smooth = [];
    for (let i = 0; i < rows.length; i++) {
      const window = rows.slice(Math.max(0, i - SLEEP_SMOOTHING_NIGHTS + 1), i + 1);
      if (window.length < SLEEP_SMOOTHING_MIN) continue;
      const mean = window.reduce((a, r) => a + r.hours, 0) / window.length;
      smooth.push({ x: sx(E.dateMs(rows[i].date)), y: sy(Math.min(mean, 12)), t: E.dateMs(rows[i].date) });
    }
    for (const seg of E.segments(smooth, MAX_GAP_DAYS * E.DAY_MS)) {
      if (seg.length < 2) continue;
      E.E('path', {
        d: E.linePath(seg), fill: 'none', stroke: violet,
        'stroke-width': 2.5, 'stroke-linejoin': 'round',
      }, f.g);
    }

    E.hoverNearest(f, pts, p => {
      const lines = [E.fmtHours(p.row.hours) + ' de sommeil'];
      if (p.row.score !== null && p.row.score !== undefined) lines.push('Score : ' + p.row.score + '/100');
      if (p.row.sessions > 1) lines.push(p.row.sessions + ' sessions agrégées');
      return { title: E.fmtDate(p.row.date), lines };
    });
  };

  /**
   * Heure de coucher, nuit par nuit.
   *
   * L'axe court de 20h à 7h du matin, sans coupure à minuit : le modèle fournit des
   * heures relatives, négatives avant minuit.
   */
  charts['sleep-bedtime'] = function (host, model) {
    const rows = model.sleep.nightly.filter(r => r.bedRel !== null && r.bedRel !== undefined);
    if (!rows.length) return E.drawEmpty(host, 'Aucune heure de coucher enregistrée.');

    const f = E.frame(host, { h: 260, label: 'Heure de coucher par nuit' });
    const ymin = -4;
    const ymax = 7;
    const sy = E.yScale(ymin, ymax, f.ih);

    for (const [value, label] of [[-4, '20h'], [-2, '22h'], [0, 'minuit'], [2, '2h'], [4, '4h'], [6, '6h']]) {
      const y = sy(value);
      E.E('line', {
        x1: 0, x2: f.iw, y1: y, y2: y,
        stroke: value === 0 ? E.cssVar('--axis') : E.cssVar('--grid'),
        'stroke-width': value === 0 ? 1.5 : 1,
      }, f.g);
      const t = E.E('text', {
        x: -8, y: y + 4, 'text-anchor': 'end', 'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = label;
    }

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const late = E.cssVar('--critical');
    const normal = E.cssVar('--s-violet');

    const pts = [];
    for (const r of rows) {
      const clamped = Math.max(ymin, Math.min(ymax, r.bedRel));
      const x = sx(E.dateMs(r.date));
      const y = sy(clamped);
      E.E('circle', {
        cx: x, cy: y, r: 2.6, fill: r.bedRel >= 2 ? late : normal, opacity: .5,
      }, f.g);
      pts.push({ x, y, row: r });
    }

    E.hoverNearest(f, pts, p => ({
      title: E.fmtDate(p.row.date),
      lines: [
        'Coucher à ' + E.fmtClock(p.row.bedRel),
        'Réveil à ' + E.fmtClock(p.row.wakeRel),
      ],
    }));
  };

  /** Distribution des durées de nuit, par tranche d'une heure. */
  charts['sleep-distribution'] = function (host, model) {
    const rows = model.sleep.distribution.filter(r => (r.count || r.value || 0) > 0);
    if (!rows.length) return E.drawEmpty(host);

    const f = E.frame(host, { h: 220, w: 460, label: 'Distribution des durées de sommeil' });
    const counts = rows.map(r => r.count !== null && r.count !== undefined ? r.count : r.value);
    const max = Math.max(...counts);
    const top = Math.max(10, Math.ceil(max / 10) * 10);
    const sy = E.grid(f, 0, top, [0, Math.round(top / 2), top], v => v);
    const bw = f.iw / rows.length;
    const good = E.cssVar('--good');
    const violet = E.cssVar('--s-violet');

    rows.forEach((r, i) => {
      const count = counts[i];
      const inTarget = bucketReachesTarget(r.label);
      const rect = E.E('rect', {
        x: i * bw + bw * .15, y: sy(count), width: bw * .7,
        height: Math.max(0, f.ih - sy(count)), rx: 4,
        fill: inTarget ? good : violet, opacity: inTarget ? .85 : .8,
      }, f.g);
      const t = E.E('text', {
        x: i * bw + bw / 2, y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = r.label;
      E.hoverShape(rect, r.label, [count + (count > 1 ? ' nuits' : ' nuit')]);
    });
  };

  /** Durée moyenne selon le jour de la semaine, au sens du soir où l'on se couche. */
  charts['sleep-dow'] = function (host, model) {
    const rows = model.sleep.dayOfWeek.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host);

    const f = E.frame(host, { h: 220, w: 460, label: 'Durée de sommeil par jour de semaine' });
    const sy = E.grid(f, 0, 9, [0, 3, 6, 9], v => v + 'h');
    E.targetBand(f, sy, 7, 9);
    const bw = f.iw / rows.length;
    const violet = E.cssVar('--s-violet');

    rows.forEach((r, i) => {
      const rect = E.E('rect', {
        x: i * bw + bw * .18, y: sy(r.value), width: bw * .64,
        height: Math.max(0, f.ih - sy(r.value)), rx: 4, fill: violet, opacity: .85,
      }, f.g);
      const label = E.E('text', {
        x: i * bw + bw / 2, y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      label.textContent = r.label;
      const value = E.E('text', {
        x: i * bw + bw / 2, y: sy(r.value) - 6, 'text-anchor': 'middle',
        'font-size': 11, 'font-weight': 600, fill: E.cssVar('--ink-2'),
        style: 'font-variant-numeric:tabular-nums',
      }, f.g);
      value.textContent = E.fmtHours(r.value);
      E.hoverShape(rect, r.label, [
        E.fmtHours(r.value) + ' en moyenne',
        (r.count || 0) + ' nuits mesurées',
      ]);
    });
  };

  /** Répartition des stades par mois, en pourcentage du temps au lit. */
  charts['sleep-stages'] = function (host, model) {
    const rows = model.sleep.stagesMonthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucun stade de sommeil enregistré.');

    // L'éveil prend `--muted` et non `--grid` : la couleur de la grille se confond avec le
    // fond en thème sombre, et le stade disparaissait de la barre empilée.
    const colors = [E.cssVar('--s-violet'), E.cssVar('--s-blue'), E.cssVar('--s-aqua'), E.cssVar('--muted')];
    const names = ['Profond', 'Léger', 'Paradoxal (REM)', 'Éveil'];
    E.legend(host, names.map((n, i) => [n, colors[i]]));

    const f = E.frame(host, { h: 250, label: 'Stades de sommeil par mois' });
    const sy = E.grid(f, 0, 100, [0, 25, 50, 75, 100], v => v + ' %');
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;

    rows.forEach((r, i) => {
      const values = [r.deep, r.light, r.rem, r.awake];
      let acc = 0;
      values.forEach((value, k) => {
        const v = value || 0;
        const y0 = sy(acc);
        const y1 = sy(acc + v);
        const rect = E.E('rect', {
          x: i * bw + bw * .12, y: y1, width: bw * .76,
          height: Math.max(0, y0 - y1 - 2), rx: 3, fill: colors[k],
        }, f.g);
        E.hoverShape(rect, E.fmtMonth(r.month), [names[k] + ' : ' + E.fmtNum(v) + ' %']);
        acc += v;
      });
    });
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
