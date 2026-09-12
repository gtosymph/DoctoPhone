/**
 * Les graphiques de la section cœur : fréquence cardiaque, variabilité, profil horaire
 * et corrélations croisées.
 *
 * Voir `report-model.js` pour la structure du modèle et les conventions.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;
  const MODEL = global.HA.reportModel;

  const charts = {};

  /** Fréquence cardiaque de repos et fréquence moyenne, mois par mois. */
  charts['heart-monthly'] = function (host, model) {
    const rows = model.heart.monthly.filter(r => r.resting !== null || r.average !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure cardiaque sur cette période.');

    const blue = E.cssVar('--s-blue');
    const magenta = E.cssVar('--s-magenta');
    // La FC moyenne est tiretée : deux traits pleins de teintes différentes deviennent
    // deux traits gris identiques sur une impression en niveaux de gris.
    E.legend(host, [
      ['FC de repos', blue, E.DASH.solid],
      ['FC moyenne', magenta, E.DASH.dashed],
    ]);

    const s = E.scaleOf(rows.flatMap(r => [r.resting, r.average]), { fmt: v => E.fmtInt(v) });
    if (!s) return E.drawEmpty(host);

    const f = E.frame(host, {
      h: 240, w: 460, gutter: s.gutter, label: 'Fréquence cardiaque par mois',
    });
    const sy = E.grid(f, s);
    const cx = E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);

    const resting = [];
    const average = [];
    rows.forEach((r, i) => {
      if (r.resting !== null && r.resting !== undefined) {
        resting.push({ x: cx(i), y: sy(r.resting), row: r });
      }
      if (r.average !== null && r.average !== undefined) {
        average.push({ x: cx(i), y: sy(r.average) });
      }
    });

    E.drawSeries(f, average, { color: magenta, width: 2, dash: E.DASH.dashed });
    E.drawSeries(f, resting, { color: blue, width: 2.5 });
    resting.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: blue }, f.g));

    const last = resting[resting.length - 1];
    if (last) E.lastPoint(f, last, blue, E.fmtInt(last.row.resting));

    E.hoverNearest(f, resting, p => ({
      title: E.fmtMonth(p.row.month),
      lines: [
        'FC de repos : ' + E.fmtInt(p.row.resting) + ' bpm',
        'FC moyenne : ' + E.fmtInt(p.row.average) + ' bpm',
      ],
    }));
  };

  /** Variabilité cardiaque RMSSD, médiane par mois. */
  charts['heart-hrv'] = function (host, model) {
    const rows = model.heart.hrvMonthly.filter(r => r.value !== null);
    if (!rows.length) return E.drawEmpty(host, 'Aucune mesure de variabilité cardiaque.');

    const s = E.scaleOf(rows.map(r => r.value), { fmt: v => E.fmtInt(v) + ' ms' });
    if (!s) return E.drawEmpty(host);

    const f = E.frame(host, {
      h: 240, w: 460, gutter: s.gutter, label: 'Variabilité cardiaque RMSSD par mois',
    });
    const sy = E.grid(f, s);
    const cx = E.xBandAxis(f, rows.map(r => r.month), E.fmtMonth);
    const aqua = E.cssVar('--s-aqua');

    const pts = rows.map((r, i) => ({ x: cx(i), y: sy(r.value), row: r }));
    E.drawSeries(f, pts, { color: aqua, width: 2.5 });
    pts.forEach(p => E.E('circle', { cx: p.x, cy: p.y, r: 3, fill: aqua }, f.g));

    const last = pts[pts.length - 1];
    E.lastPoint(f, last, aqua, E.fmtInt(last.row.value));

    E.hoverNearest(f, pts, p => ({
      title: E.fmtMonth(p.row.month),
      lines: ['RMSSD médian : ' + E.fmtInt(p.row.value) + ' ms'],
    }));
  };

  /**
   * Profil horaire de la fréquence cardiaque, moyenne sur toute la période.
   *
   * Pas de marque de dernier point : l'axe n'est pas le temps mais l'heure du jour, et
   * « 23 h » n'est pas plus récent que « 0 h ».
   */
  charts['heart-hourly'] = function (host, model) {
    const rows = model.heart.hourly.filter(r => r.value !== null);
    if (rows.length < 6) return E.drawEmpty(host, 'Pas assez de mesures pour un profil horaire.');

    const s = E.scaleOf(rows.map(r => r.value), { fmt: v => E.fmtInt(v) });
    if (!s) return E.drawEmpty(host);

    const f = E.frame(host, {
      h: 220, w: 460, gutter: s.gutter, label: 'Fréquence cardiaque par heure',
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

    const blue = E.cssVar('--s-blue');
    const pts = rows.map(r => ({ x: sx(r.label), y: sy(r.value), row: r }));
    E.drawSeries(f, pts, { color: blue, width: 2.5, area: f.ih, areaOpacity: .14 });

    E.hoverNearest(f, pts, p => ({
      title: p.row.label + 'h',
      lines: [E.fmtInt(p.row.value) + ' bpm en moyenne'],
    }));
  };

  /**
   * Corrélations croisées, mesurées sur les jours appariés.
   *
   * Une barre vers la droite marque une association positive. Le coefficient n'a de
   * sens qu'au-delà d'un certain nombre de jours appariés ; le constructeur du modèle a
   * déjà écarté les paires trop rares.
   *
   * Seul graphique à grille verticale, et pour une raison de fond : les barres sont
   * horizontales, donc la grandeur se lit sur l'axe des abscisses. Les traits qui la
   * jalonnent sont forcément verticaux.
   */
  charts['heart-correlations'] = function (host, model) {
    const rows = model.correlations.slice().sort((a, b) => Math.abs(b.r) - Math.abs(a.r));
    if (!rows.length) {
      return E.drawEmpty(host, 'Pas assez de jours appariés pour mesurer une association.');
    }

    // Un libellé SVG ne se coupe pas en plusieurs lignes. Sur écran étroit, il ne tient
    // donc pas à gauche de la barre : il passe au-dessus, et la barre prend la largeur.
    const compact = E.isCompact();
    const rowHeight = compact ? 54 : 40;
    const f = E.frame(host, {
      h: rows.length * rowHeight + 40,
      w: compact ? 460 : 920,
      m: { l: compact ? 8 : 300, r: 56, t: 10, b: 26 },
      label: 'Corrélations croisées',
    });

    // Ce graphique dessine son propre axe, donc aucune primitive ne remplit sa
    // description pour lui. Elle s'écrit ici, à la main.
    E.note(f, rows.length + ' associations mesurées, de −0,5 à +0,5. '
      + 'La plus forte : ' + rows[0].label + ', ' + E.fmtNum(rows[0].r, 2) + '.');

    const sx = v => (v + 0.5) * f.iw;
    E.E('line', {
      x1: sx(0), x2: sx(0), y1: 0, y2: f.ih,
      stroke: E.cssVar('--axis'), 'stroke-width': 1.5,
    }, f.g);

    for (const v of [-0.4, -0.2, 0.2, 0.4]) {
      E.E('line', { x1: sx(v), x2: sx(v), y1: 0, y2: f.ih, stroke: E.cssVar('--grid') }, f.g);
      const t = E.E('text', {
        x: sx(v), y: f.ih + 16, 'text-anchor': 'middle',
        'font-size': 10.5, fill: E.cssVar('--muted'),
      }, f.g);
      t.textContent = (v > 0 ? '+' : '') + v.toFixed(1).replace('.', ',');
    }

    const blue = E.cssVar('--s-blue');
    rows.forEach((r, i) => {
      const y = compact ? i * rowHeight + 22 : i * rowHeight + (rowHeight - 18) / 2;
      const width = Math.abs(sx(r.r) - sx(0));
      const x = r.r < 0 ? sx(r.r) : sx(0);
      const strong = Math.abs(r.r) >= MODEL.STRONG_CORRELATION;

      const rect = E.E('rect', {
        x, y, width: Math.max(width, 2), height: 18, rx: 4,
        fill: strong ? blue : E.cssVar('--axis'),
      }, f.g);
      const label = E.E('text', {
        x: compact ? 0 : -12,
        y: compact ? i * rowHeight + 14 : y + 13,
        'text-anchor': compact ? 'start' : 'end',
        'font-size': compact ? 11.5 : 12.5, fill: E.cssVar('--ink-2'),
      }, f.g);
      label.textContent = r.label;
      const value = E.E('text', {
        x: r.r < 0 ? x - 8 : x + width + 8, y: y + 13,
        'text-anchor': r.r < 0 ? 'end' : 'start', 'font-size': 12, 'font-weight': 650,
        fill: strong ? E.cssVar('--ink') : E.cssVar('--muted'),
        style: 'font-variant-numeric:tabular-nums',
      }, f.g);
      value.textContent = (r.r > 0 ? '+' : '') + r.r.toFixed(2).replace('.', ',');

      E.hoverShape(rect, r.label, [
        'r = ' + r.r.toFixed(2).replace('.', ','),
        'mesuré sur ' + r.n + ' jours appariés',
        strong ? 'Association nette' : 'Association faible',
      ]);
    });
  };

  global.HA = global.HA || {};
  global.HA.reportCharts = Object.assign(global.HA.reportCharts || {}, charts);
}(typeof self !== 'undefined' ? self : this));
