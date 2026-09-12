/**
 * Les cinq graphiques de la section sommeil.
 *
 * Chaque fonction reçoit le conteneur et le modèle de rapport, et ne lit que la section
 * `sleep`. Voir `report-model.js` pour la structure exacte et les conventions.
 *
 * Aucun de ces graphiques ne fixe ses propres bornes : `E.scale` les déduit des mesures.
 * Un axe décidé à l'avance finit par écrêter une valeur réelle ou par graduer une plage
 * que personne n'a atteinte.
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

  /** Borne haute de la zone cible. */
  const TARGET_HOURS_HIGH = 9;

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

    // La cible entre dans l'étendue de l'axe : une bande à moitié hors du cadre ne se
    // lit pas, même quand aucune nuit ne l'atteint.
    const s = E.scaleOf(rows.map(r => r.hours).concat([TARGET_HOURS_HIGH]), {
      zero: true, fmt: v => v + 'h',
    });
    if (!s) return E.drawEmpty(host, 'Aucune nuit enregistrée sur cette période.');

    const f = E.frame(host, { h: 280, gutter: s.gutter, label: 'Durée de sommeil par nuit' });
    const sy = E.grid(f, s);
    E.targetBand(f, sy, TARGET_HOURS, TARGET_HOURS_HIGH, 'cible 7–9 h');

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const violet = E.cssVar('--s-violet');

    const pts = [];
    for (const r of rows) {
      const t = E.dateMs(r.date);
      const x = sx(t);
      const y = sy(r.hours);
      E.E('circle', { cx: x, cy: y, r: 2.4, fill: violet, opacity: .38 }, f.g);
      pts.push({ x, y, t, row: r });
    }

    const smooth = [];
    for (let i = 0; i < rows.length; i++) {
      const window = rows.slice(Math.max(0, i - SLEEP_SMOOTHING_NIGHTS + 1), i + 1);
      if (window.length < SLEEP_SMOOTHING_MIN) continue;
      const mean = window.reduce((a, r) => a + r.hours, 0) / window.length;
      const t = E.dateMs(rows[i].date);
      smooth.push({ x: sx(t), y: sy(mean), t, mean });
    }
    E.drawSeries(f, smooth, {
      color: violet, width: 2.5, maxGapMs: MAX_GAP_DAYS * E.DAY_MS,
    });
    const last = smooth[smooth.length - 1];
    if (last) E.lastPoint(f, last, violet, E.fmtHours(last.mean));

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
   * L'axe court du soir au matin, sans coupure à minuit : le modèle fournit des heures
   * relatives, négatives avant minuit. Les bornes suivent les données — un coucher à
   * 19 h ne doit pas être rabattu sur 20 h.
   */
  charts['sleep-bedtime'] = function (host, model) {
    const rows = model.sleep.nightly.filter(r => r.bedRel !== null && r.bedRel !== undefined);
    if (!rows.length) return E.drawEmpty(host, 'Aucune heure de coucher enregistrée.');

    const values = rows.map(r => r.bedRel);
    const ymin = Math.min(-4, Math.floor(Math.min.apply(null, values) / 2) * 2);
    const ymax = Math.max(7, Math.ceil(Math.max.apply(null, values) / 2) * 2);

    const labels = [];
    for (let v = Math.ceil(ymin / 2) * 2; v <= ymax; v += 2) labels.push(v);
    const gutter = Math.max.apply(null, labels.map(v => E.textWidth(v === 0 ? 'minuit' : E.fmtClock(v)))) + 12;

    const f = E.frame(host, { h: 260, gutter, label: 'Heure de coucher par nuit' });
    const sy = E.yScale(ymin, ymax, f.ih);

    for (const value of labels) {
      const y = sy(value);
      E.E('line', {
        x1: 0, x2: f.iw, y1: y, y2: y,
        stroke: value === 0 ? E.cssVar('--axis') : E.cssVar('--grid'),
        'stroke-width': value === 0 ? 1.5 : 1,
      }, f.g);
      const t = E.E('text', {
        x: -8, y: y + 4, 'text-anchor': 'end', 'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = value === 0 ? 'minuit' : E.fmtClock(value);
    }

    const t0 = E.dateMs(rows[0].date);
    const t1 = E.dateMs(rows[rows.length - 1].date);
    const sx = E.xTimeAxis(f, t0, t1);
    const late = E.cssVar('--critical');
    const normal = E.cssVar('--s-violet');

    const pts = [];
    for (const r of rows) {
      const x = sx(E.dateMs(r.date));
      const y = sy(r.bedRel);
      E.E('circle', {
        cx: x, cy: y, r: 2.6, fill: r.bedRel >= 2 ? late : normal, opacity: .5,
      }, f.g);
      pts.push({ x, y, row: r });
    }
    const last = pts[pts.length - 1];
    E.lastPoint(f, last, last.row.bedRel >= 2 ? late : normal, E.fmtClock(last.row.bedRel));

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

    const counts = rows.map(r => r.count !== null && r.count !== undefined ? r.count : r.value);
    const s = E.scaleOf(counts, { zero: true, fmt: v => E.fmtInt(v) });
    const f = E.frame(host, {
      h: 220, w: 460, gutter: s.gutter, label: 'Distribution des durées de sommeil',
    });
    const sy = E.grid(f, s);
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
        x: E.clampLabelX(f, i * bw + bw / 2, r.label), y: f.ih + 18,
        'text-anchor': 'middle', 'font-size': 11, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = r.label;
      E.hoverShape(rect, r.label, [count + (count > 1 ? ' nuits' : ' nuit')]);
    });
  };

  /** Durée moyenne selon le jour de la semaine, au sens du soir où l'on se couche. */
  charts['sleep-dow'] = function (host, model) {
    const rows = model.sleep.dayOfWeek.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host);

    const s = E.scaleOf(rows.map(r => r.value).concat([TARGET_HOURS_HIGH]), {
      zero: true, fmt: v => v + 'h',
    });
    const f = E.frame(host, {
      h: 220, w: 460, gutter: s.gutter, label: 'Durée de sommeil par jour de semaine',
    });
    const sy = E.grid(f, s);
    E.targetBand(f, sy, TARGET_HOURS, TARGET_HOURS_HIGH, 'cible');
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

  /**
   * Répartition des stades par mois, en pourcentage du temps au lit.
   *
   * Quatre couleurs empilées et rien d'autre : à l'impression en niveaux de gris, les
   * quatre stades deviennent quatre gris voisins. Chaque segment assez haut porte donc
   * son pourcentage, seule marque qui survive au noir et blanc.
   */
  charts['sleep-stages'] = function (host, model) {
    const rows = model.sleep.stagesMonthly;
    if (!rows.length) return E.drawEmpty(host, 'Aucun stade de sommeil enregistré.');

    // L'éveil prend `--muted` et non `--grid` : la couleur de la grille se confond avec le
    // fond en thème sombre, et le stade disparaissait de la barre empilée.
    const colors = [E.cssVar('--s-violet'), E.cssVar('--s-blue'), E.cssVar('--s-aqua'), E.cssVar('--muted')];
    const names = ['Profond', 'Léger', 'Paradoxal (REM)', 'Éveil'];
    E.legend(host, names.map((n, i) => [n, colors[i], 'fill']));

    // Les quatre parts d'un même mois font 100 % : l'axe est celui de la grandeur, pas
    // celui des données, et il ne varie pas d'un rapport à l'autre.
    const s = E.scale(0, 100, { zero: true, capHigh: 100, fmt: v => v + ' %' });
    const f = E.frame(host, { h: 250, gutter: s.gutter, label: 'Stades de sommeil par mois' });
    const sy = E.grid(f, s);
    E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const bw = f.iw / rows.length;

    /** En deçà de cette hauteur, le chiffre déborderait de son segment. */
    const MIN_LABEL_HEIGHT = 14;

    rows.forEach((r, i) => {
      const values = [r.deep, r.light, r.rem, r.awake];
      let acc = 0;
      values.forEach((value, k) => {
        const v = value || 0;
        const y0 = sy(acc);
        const y1 = sy(acc + v);
        const height = Math.max(0, y0 - y1 - 2);
        const rect = E.E('rect', {
          x: i * bw + bw * .12, y: y1, width: bw * .76,
          height, rx: 3, fill: colors[k],
        }, f.g);
        if (height >= MIN_LABEL_HEIGHT && bw > 34) {
          const t = E.E('text', {
            x: i * bw + bw / 2, y: y1 + height / 2 + 3.5, 'text-anchor': 'middle',
            class: 'ha-stage-label', 'font-size': 10, 'font-weight': 600,
            fill: E.cssVar('--page'), style: 'font-variant-numeric:tabular-nums',
          }, f.g);
          t.textContent = Math.round(v) + ' %';
        }
        E.hoverShape(rect, E.fmtMonth(r.month), [names[k] + ' : ' + E.fmtNum(v) + ' %']);
        acc += v;
      });
    });
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
