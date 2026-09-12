/**
 * Le petit langage de graphiques que le modèle peut écrire dans la conversation.
 *
 * Le vocabulaire est calqué sur Vega-Lite (`mark`, `series`, `title`) pour tirer parti
 * des réflexes que les modèles ont déjà sur ce format, mais il est volontairement
 * minuscule : quatre marques, une bande cible, et des séries qui **désignent** nos
 * données au lieu de les recopier (voir `chart-catalog.js`).
 *
 * Rien n'est évalué. La spécification est du JSON pur, lue champ par champ. Aucune
 * chaîne venue du modèle n'atteint `innerHTML` : le moteur de dessin pose tout en
 * `textContent`.
 *
 * Le validateur est délibérément indulgent : une réponse de modèle contient presque
 * toujours une petite erreur, et refuser le graphique entier pour un domaine mal choisi
 * serait pénible à l'usage. Il corrige ce qui se corrige, signale le reste, et ne rend
 * `null` que si plus rien ne peut être dessiné.
 */

(function (global) {
  'use strict';

  const MARKS = ['line', 'bar', 'point', 'area'];
  const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
  const MONTH_RE = /^\d{4}-\d{2}$/;

  /** Palette des séries, dans l'ordre d'attribution. */
  const COLORS = ['--s-violet', '--s-blue', '--s-aqua', '--s-orange', '--s-magenta', '--s-yellow'];

  /** Nombre maximal de séries dessinées ensemble : au-delà, le graphique devient illisible. */
  const MAX_SERIES = 4;

  const E = () => global.HA.engine;

  /**
   * Le schéma envoyé au modèle en sortie structurée.
   *
   * Il est volontairement fermé (`additionalProperties: false`) : un champ inventé est
   * plus souvent une erreur qu'une intention.
   */
  const SCHEMA = {
    type: 'object',
    additionalProperties: false,
    required: ['mark', 'title', 'series'],
    properties: {
      mark: { type: 'string', enum: MARKS },
      title: { type: 'string' },
      note: { type: 'string' },
      targetBand: {
        type: 'object',
        additionalProperties: false,
        required: ['low', 'high'],
        properties: { low: { type: 'number' }, high: { type: 'number' } },
      },
      series: {
        type: 'array',
        minItems: 1,
        maxItems: MAX_SERIES,
        items: {
          type: 'object',
          additionalProperties: false,
          properties: {
            label: { type: 'string' },
            ref: { type: 'string' },
            values: {
              type: 'array',
              items: {
                type: 'object',
                additionalProperties: false,
                required: ['x', 'y'],
                properties: { x: { type: 'string' }, y: { type: 'number' } },
              },
            },
          },
        },
      },
    },
  };

  /** Met en forme une valeur selon l'unité déclarée par le catalogue. */
  function formatValue(value, unit) {
    const e = E();
    if (value === null || value === undefined || !isFinite(value)) return '—';
    switch (unit) {
      case 'hours': return e.fmtHours(value);
      case 'clock': return e.fmtClock(value);
      case 'int': return e.fmtInt(value);
      case 'num': return e.fmtNum(value, 1);
      default: return e.fmtNum(value, 1) + ' ' + unit;
    }
  }

  /** Étiquette d'axe vertical : plus courte que l'infobulle, sans unité répétée partout. */
  function formatTick(value, unit) {
    const e = E();
    switch (unit) {
      case 'hours': return e.fmtNum(value, 0) + ' h';
      case 'clock': return e.fmtClock(value);
      case 'int': return Math.abs(value) >= 1000 ? (value / 1000) + 'k' : e.fmtInt(value);
      case 'num': return e.fmtNum(value, 0);
      default: return e.fmtNum(value, 0);
    }
  }

  /** Étiquette d'axe horizontal, selon la nature de l'axe. */
  function formatCategory(label, axis) {
    if (axis === 'month' && MONTH_RE.test(label)) return E().fmtMonth(label);
    if (axis === 'hour') return label + 'h';
    return label;
  }

  /** Choisit des graduations rondes qui encadrent les valeurs, sans jamais les écrêter. */
  function niceScale(values, targetBand) {
    const all = values.slice();
    if (targetBand) all.push(targetBand.low, targetBand.high);
    const clean = all.filter(v => v !== null && v !== undefined && isFinite(v));
    if (!clean.length) return null;

    // L'axe vient du moteur partagé, comme celui des dix-neuf graphiques du rapport :
    // un graphique demandé en conversation se lit exactement comme les autres.
    //
    // Seules les trois bornes sont gardées. La mise en forme d'une graduation dépend de
    // l'unité, connue au dessin, et une fonction ne survivrait pas à la sérialisation de
    // la spécification, qui est gravée sur le message auquel elle appartient.
    const s = E().scaleOf(clean);
    if (!s) return null;
    return { lo: s.lo, hi: s.hi, ticks: s.ticks };
  }

  /**
   * Vérifie et normalise une spécification venue du modèle.
   *
   * @param {object} raw la spécification telle que le modèle l'a écrite
   * @param {object} model le modèle de rapport, pour résoudre les références — toujours
   *   celui de tout l'historique, voir `range`
   * @param {{from: string, to: string}} [range] la fenêtre du message qui porte ce
   *   graphique, transmise telle quelle à `chartCatalog.resolve` — voir `chat-view.js`
   * @returns {{spec: object|null, problems: string[]}}
   */
  function validate(raw, model, range) {
    const problems = [];
    if (!raw || typeof raw !== 'object') {
      return { spec: null, problems: ['la spécification est absente'] };
    }

    const mark = MARKS.includes(raw.mark) ? raw.mark : 'line';
    if (!MARKS.includes(raw.mark)) problems.push(`marque inconnue « ${raw.mark} », remplacée par « line »`);

    const rawSeries = Array.isArray(raw.series) ? raw.series.slice(0, MAX_SERIES) : [];
    if (Array.isArray(raw.series) && raw.series.length > MAX_SERIES) {
      problems.push(`${raw.series.length} séries demandées, ${MAX_SERIES} gardées`);
    }

    const series = [];
    let unit = null;
    let axis = null;

    rawSeries.forEach((s, index) => {
      let points = null;
      let seriesUnit = null;
      let seriesAxis = null;
      let label = typeof s.label === 'string' ? s.label : null;

      if (typeof s.ref === 'string') {
        const resolved = global.HA.chartCatalog.resolve(s.ref, model, range);
        if (!resolved) { problems.push(`série inconnue : « ${s.ref} »`); return; }
        points = resolved.points;
        seriesUnit = resolved.unit;
        seriesAxis = resolved.axis;
        label = label || resolved.label;
      } else if (Array.isArray(s.values)) {
        const wellFormed = s.values.filter(p => p && typeof p.x === 'string' && isFinite(p.y));
        if (s.values.length !== wellFormed.length) {
          problems.push(`${s.values.length - wellFormed.length} point(s) illisible(s) écarté(s)`);
        }

        // Le modèle mélange les formats de date : il écrit `10/08/2026` au milieu de dates
        // ISO. Sans ce tri, la série bascule silencieusement d'un axe temporel vers des
        // catégories, et le graphique ment sans le dire. On décide donc l'axe à la
        // majorité, puis on écarte les points qui ne le respectent pas.
        const dates = wellFormed.filter(p => DATE_RE.test(p.x)).length;
        const months = wellFormed.filter(p => MONTH_RE.test(p.x)).length;
        const half = wellFormed.length / 2;
        seriesAxis = dates > half ? 'time' : months > half ? 'month' : 'band';

        const pattern = seriesAxis === 'time' ? DATE_RE : seriesAxis === 'month' ? MONTH_RE : null;
        const kept = pattern ? wellFormed.filter(p => pattern.test(p.x)) : wellFormed;
        if (kept.length !== wellFormed.length) {
          problems.push(`${wellFormed.length - kept.length} date(s) hors format ISO écartée(s)`);
        }

        points = kept.map(p => ({ x: p.x, y: Number(p.y) }));
        seriesUnit = 'num';
        label = label || `Série ${index + 1}`;
      } else {
        problems.push(`la série ${index + 1} n'a ni « ref » ni « values »`);
        return;
      }

      if (points.length < 2) { problems.push(`« ${label} » n'a pas assez de points`); return; }

      if (axis === null) { axis = seriesAxis; unit = seriesUnit; }
      else if (seriesAxis !== axis) { problems.push(`« ${label} » n'a pas le même axe que les autres séries`); return; }

      series.push({ label, points, color: COLORS[series.length % COLORS.length] });
    });

    if (!series.length) {
      problems.push('aucune série exploitable');
      return { spec: null, problems };
    }

    // La bande cible inversée dessine une hauteur négative, donc rien de visible et aucun
    // message : on remet les bornes dans l'ordre plutôt que de laisser un graphique muet.
    let targetBand = null;
    if (raw.targetBand && isFinite(raw.targetBand.low) && isFinite(raw.targetBand.high)) {
      const low = Math.min(raw.targetBand.low, raw.targetBand.high);
      const high = Math.max(raw.targetBand.low, raw.targetBand.high);
      if (low !== raw.targetBand.low) problems.push('bornes de la bande cible remises dans l\'ordre');
      targetBand = { low, high };
    }

    // L'échelle se calcule sur les données, jamais sur un domaine annoncé par le modèle :
    // il oublie souvent de l'ajuster, et la valeur hors domaine serait écrêtée en silence.
    const scale = niceScale(series.flatMap(s => s.points.map(p => p.y)), targetBand);
    if (!scale) { problems.push('aucune valeur numérique'); return { spec: null, problems }; }

    return {
      spec: {
        mark,
        title: typeof raw.title === 'string' && raw.title.trim() ? raw.title.trim() : 'Graphique',
        note: typeof raw.note === 'string' ? raw.note : null,
        unit: unit || 'num',
        axis: axis || 'band',
        targetBand,
        scale,
        series,
      },
      problems,
    };
  }

  /**
   * Le style de trait d'une série, pour que deux courbes se distinguent sans la couleur.
   *
   * Une barre ne porte pas de trait : son échantillon de légende reste un aplat.
   */
  function dashFor(mark, index) {
    if (mark === 'bar') return 'fill';
    const dashes = [null, E().DASH.dashed, E().DASH.dotted, E().DASH.dashDot];
    return dashes[index % dashes.length];
  }

  /** Dessine une spécification déjà validée. */
  function draw(host, spec) {
    const e = E();
    host.textContent = '';
    const compact = e.isCompact();
    const fmt = v => formatTick(v, spec.unit);
    let gutter = 0;
    for (const t of spec.scale.ticks) gutter = Math.max(gutter, e.textWidth(fmt(t)));

    const f = e.frame(host, {
      h: 250,
      w: compact ? 460 : 920,
      gutter: Math.ceil(gutter) + 12,
      label: spec.title,
    });

    const sy = e.grid(f, { lo: spec.scale.lo, hi: spec.scale.hi, ticks: spec.scale.ticks, fmt });
    if (spec.targetBand) e.targetBand(f, sy, spec.targetBand.low, spec.targetBand.high, 'cible');

    if (spec.series.length > 1) {
      e.legend(host, spec.series.map((s, i) => [s.label, e.cssVar(s.color), dashFor(spec.mark, i)]));
      host.insertBefore(host.lastChild, f.svg);
    }

    const first = spec.series[0].points;
    let sx;
    if (spec.axis === 'time') {
      const t0 = e.dateMs(first[0].x);
      const t1 = e.dateMs(first[first.length - 1].x);
      const scaleX = e.xTimeAxis(f, t0, t1 === t0 ? t1 + e.DAY_MS : t1);
      sx = point => scaleX(e.dateMs(point.x));
    } else {
      const labels = first.map(p => p.x);
      const band = e.xBandAxis(f, labels, l => formatCategory(l, spec.axis));
      const indexOf = new Map(labels.map((l, i) => [l, i]));
      sx = point => band(indexOf.has(point.x) ? indexOf.get(point.x) : 0);
    }

    const bandWidth = f.iw / Math.max(1, first.length);

    spec.series.forEach((s, seriesIndex) => {
      const color = e.cssVar(s.color);
      const pts = s.points.map(p => ({ x: sx(p), y: sy(p.y), row: p }));

      if (spec.mark === 'bar') {
        const slot = bandWidth / spec.series.length;
        pts.forEach((p, i) => {
          const height = Math.max(0, f.ih - p.y);
          const rect = e.E('rect', {
            x: p.x - bandWidth * 0.32 + seriesIndex * slot * 0.64,
            y: p.y, width: Math.max(2, slot * 0.6), height, rx: 3,
            fill: color, opacity: 0.85,
          }, f.g);
          e.hoverShape(rect, formatCategory(s.points[i].x, spec.axis), [
            s.label + ' : ' + formatValue(s.points[i].y, spec.unit),
          ]);
        });
        return;
      }

      if (spec.mark === 'point') {
        pts.forEach(p => e.E('circle', { cx: p.x, cy: p.y, r: 2.6, fill: color, opacity: 0.55 }, f.g));
      } else {
        if (spec.mark === 'area') {
          e.E('path', { d: e.areaPath(pts, f.ih), fill: color, opacity: 0.15 }, f.g);
        }
        e.E('path', {
          d: e.linePath(pts), fill: 'none', stroke: color,
          'stroke-width': 2.2, 'stroke-linejoin': 'round',
          'stroke-dasharray': dashFor(spec.mark, seriesIndex),
        }, f.g);
      }

      if (seriesIndex === 0) {
        e.hoverNearest(f, pts, p => ({
          title: spec.axis === 'time' ? e.fmtDate(p.row.x) : formatCategory(p.row.x, spec.axis),
          lines: spec.series.map(other => {
            const match = other.points.find(q => q.x === p.row.x);
            return other.label + ' : ' + formatValue(match ? match.y : null, spec.unit);
          }),
        }));
      }
    });
  }

  /**
   * Vérifie puis dessine une spécification dans le conteneur donné.
   *
   * @param {{from: string, to: string}} [range] la fenêtre du message qui porte ce
   *   graphique — voir `validate`
   * @returns {string[]} les problèmes rencontrés, vide si tout allait bien
   */
  function render(host, raw, model, range) {
    const { spec, problems } = validate(raw, model, range);
    if (!spec) {
      E().drawEmpty(host, 'Ce graphique n\'a pas pu être tracé.');
      return problems;
    }
    draw(host, spec);
    return problems;
  }

  global.HA = global.HA || {};
  global.HA.chartSpec = { SCHEMA, MARKS, MAX_SERIES, validate, draw, render, formatValue };
}(typeof self !== 'undefined' ? self : this));
