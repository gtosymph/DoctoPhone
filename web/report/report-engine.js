/**
 * Moteur de graphiques du rapport de santé.
 *
 * Ce fichier ne connaît aucune métrique. Il fournit les primitives de dessin : cadre,
 * échelles, grille, axes, courbe, aire, barres et infobulle. Les 16 graphiques sont
 * écrits par-dessus, dans `report-charts.js`.
 *
 * Il est partagé par les deux versions : le web l'utilise directement, Android le
 * charge dans une WebView locale. Le dessin passe par SVG et un `viewBox`, donc un même
 * graphique tient sur un téléphone comme sur un écran large.
 *
 * Le pointeur est traité par les événements `pointer*`, jamais `mouse*` : sinon le
 * survol ne marche pas au doigt. La feuille de style pose `touch-action: pan-y` sur les
 * SVG, de sorte qu'un glissement vertical fasse défiler la page pendant qu'un
 * glissement horizontal parcourt la courbe.
 */

(function (global) {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';

  /** Au-dessous de cette largeur, le rapport passe en une colonne et allège ses axes. */
  const COMPACT_WIDTH = 720;

  const MONTHS_FR = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
    'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

  const DAY_MS = 86400000;

  /**
   * Crée un élément SVG.
   *
   * @param {string} tag le nom de la balise
   * @param {object} attrs les attributs à poser
   * @param {Element} [parent] le parent auquel rattacher l'élément
   * @returns {Element} l'élément créé
   */
  function E(tag, attrs, parent) {
    const el = document.createElementNS(NS, tag);
    for (const k in attrs) {
      if (attrs[k] !== null && attrs[k] !== undefined) el.setAttribute(k, attrs[k]);
    }
    if (parent) parent.appendChild(el);
    return el;
  }

  /** Lit une variable de thème. Le moteur redessine à chaque changement de thème. */
  function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  function isCompact() {
    return (global.innerWidth || 1024) < COMPACT_WIDTH;
  }

  // ---------------------------------------------------------------- formats

  function fmtMonth(m) {
    if (!m) return '';
    const [y, mo] = String(m).split('-');
    return MONTHS_FR[Number(mo) - 1] + ' ' + String(y).slice(2);
  }

  function fmtDate(d) {
    if (!d) return '';
    const dt = new Date(d + 'T12:00:00');
    return dt.getDate() + ' ' + MONTHS_FR[dt.getMonth()] + ' ' + String(dt.getFullYear()).slice(2);
  }

  /** Rend une durée décimale en heures sous la forme `6h07`. */
  function fmtHours(h) {
    if (h === null || h === undefined || !isFinite(h)) return '—';
    const total = Math.round(h * 60);
    return Math.floor(total / 60) + 'h' + String(total % 60).padStart(2, '0');
  }

  /**
   * Rend une heure relative à minuit sous la forme `23h40` ou `2h15`.
   *
   * L'entrée suit la convention du modèle : `-1.5` vaut 22h30, `2.25` vaut 2h15.
   */
  function fmtClock(rel) {
    if (rel === null || rel === undefined || !isFinite(rel)) return '—';
    const h = ((rel % 24) + 24) % 24;
    const total = Math.round(h * 60) % 1440;
    return Math.floor(total / 60) + 'h' + String(total % 60).padStart(2, '0');
  }

  function fmtInt(v) {
    if (v === null || v === undefined || !isFinite(v)) return '—';
    return Math.round(v).toLocaleString('fr-FR');
  }

  function fmtNum(v, digits) {
    if (v === null || v === undefined || !isFinite(v)) return '—';
    return v.toFixed(digits === undefined ? 1 : digits).replace('.', ',');
  }

  function dateMs(iso) {
    return new Date(iso + 'T12:00:00').getTime();
  }

  // ---------------------------------------------------------------- infobulle

  let tooltipEl = null;

  function tooltip() {
    if (!tooltipEl || !tooltipEl.isConnected) {
      tooltipEl = document.createElement('div');
      tooltipEl.className = 'ha-tooltip';
      document.body.appendChild(tooltipEl);
    }
    return tooltipEl;
  }

  /**
   * Montre l'infobulle au point donné.
   *
   * Le contenu est posé en `textContent`, jamais en `innerHTML`. Une partie des textes
   * vient du LLM ou de champs libres de l'export, comme les symptômes d'un ECG : les
   * traiter comme du HTML ouvrirait une injection de script.
   *
   * @param {string} title la première ligne, en gras
   * @param {string[]} lines les lignes suivantes, une par élément
   */
  function showTooltip(title, lines, x, y) {
    const tt = tooltip();
    tt.textContent = '';
    if (title) {
      const b = document.createElement('b');
      b.textContent = title;
      tt.appendChild(b);
    }
    for (const line of lines || []) {
      if (tt.childNodes.length) tt.appendChild(document.createElement('br'));
      tt.appendChild(document.createTextNode(line));
    }
    tt.style.opacity = '1';
    const w = tt.offsetWidth;
    const h = tt.offsetHeight;
    let left = x + 14;
    let top = y - h - 10;
    if (left + w > global.innerWidth - 8) left = x - w - 14;
    if (left < 8) left = 8;
    if (top < 8) top = y + 18;
    tt.style.left = left + 'px';
    tt.style.top = top + 'px';
  }

  function hideTooltip() {
    if (tooltipEl) tooltipEl.style.opacity = '0';
  }

  // ---------------------------------------------------------------- cadre

  /**
   * Prépare un SVG et son groupe intérieur.
   *
   * @param {Element} host le conteneur qui reçoit le SVG
   * @param {object} opts `w`, `h`, `m` (marges), `label` (titre d'accessibilité)
   * @returns {object} le cadre, avec `svg`, `g`, `iw`, `ih`, `W`, `H`, `m`
   */
  function frame(host, opts) {
    const o = opts || {};
    const W = o.w || 920;
    const H = o.h || 260;
    const m = Object.assign({ t: 14, r: 14, b: 30, l: 44 }, o.m || {});
    const svg = E('svg', {
      viewBox: '0 0 ' + W + ' ' + H,
      preserveAspectRatio: 'xMidYMid meet',
      role: 'img',
    }, host);
    if (o.label) {
      const t = E('title', {}, svg);
      t.textContent = o.label;
    }
    const g = E('g', { transform: 'translate(' + m.l + ',' + m.t + ')' }, svg);
    return { svg, g, iw: W - m.l - m.r, ih: H - m.t - m.b, W, H, m };
  }

  function yScale(min, max, ih) {
    const span = (max - min) || 1;
    return v => ih - (v - min) / span * ih;
  }

  /**
   * Trace la grille horizontale et l'axe des ordonnées.
   *
   * @returns {function} l'échelle verticale, réutilisable par l'appelant
   */
  function grid(f, ymin, ymax, ticks, fmt) {
    const sy = yScale(ymin, ymax, f.ih);
    for (const t of ticks) {
      const y = sy(t);
      E('line', {
        x1: 0, x2: f.iw, y1: y, y2: y,
        stroke: cssVar('--grid'), 'stroke-width': 1,
      }, f.g);
      const lb = E('text', {
        x: -8, y: y + 4, 'text-anchor': 'end', 'font-size': 11,
        fill: cssVar('--muted'), style: 'font-variant-numeric:tabular-nums',
      }, f.g);
      lb.textContent = fmt ? fmt(t) : String(t);
    }
    E('line', {
      x1: 0, x2: f.iw, y1: f.ih, y2: f.ih,
      stroke: cssVar('--axis'), 'stroke-width': 1,
    }, f.g);
    return sy;
  }

  /** Bande de valeur cible, dessinée sous les courbes. */
  function targetBand(f, sy, low, high) {
    E('rect', {
      x: 0, y: sy(high), width: f.iw, height: Math.max(0, sy(low) - sy(high)),
      fill: cssVar('--band'),
    }, f.g);
  }

  /**
   * Pose les étiquettes de mois sur un axe temporel continu.
   *
   * @returns {function} l'échelle horizontale, qui convertit un temps en abscisse
   */
  function xTimeAxis(f, t0, t1) {
    const span = (t1 - t0) || 1;
    const sx = t => (t - t0) / span * f.iw;
    const days = span / DAY_MS;
    const every = isCompact()
      ? (days > 400 ? 4 : days > 150 ? 2 : 1)
      : (days > 400 ? 2 : 1);
    const d = new Date(t0);
    d.setDate(1);
    d.setMonth(d.getMonth() + 1);
    let k = 0;
    while (d.getTime() < t1) {
      if (k % every === 0) {
        const x = sx(d.getTime());
        E('line', {
          x1: x, x2: x, y1: 0, y2: f.ih, stroke: cssVar('--grid'),
          'stroke-width': 1, 'stroke-dasharray': '2,4',
        }, f.g);
        const t = E('text', {
          x, y: f.ih + 18, 'text-anchor': 'middle', 'font-size': 11, fill: cssVar('--muted'),
        }, f.g);
        t.textContent = MONTHS_FR[d.getMonth()]
          + (d.getMonth() === 0 ? ' ' + String(d.getFullYear()).slice(2) : '');
      }
      d.setMonth(d.getMonth() + 1);
      k++;
    }
    return sx;
  }

  /**
   * Pose les étiquettes d'un axe à bandes régulières : mois, jours, tranches.
   *
   * @returns {function} le centre de la bande d'indice donné
   */
  function xBandAxis(f, labels, format) {
    const n = labels.length || 1;
    const step = f.iw / n;
    const every = Math.max(1, Math.ceil(n / (isCompact() ? 4 : 9)));
    labels.forEach((label, i) => {
      if (i % every !== 0) return;
      const t = E('text', {
        x: i * step + step / 2, y: f.ih + 18, 'text-anchor': 'middle',
        'font-size': 11, fill: cssVar('--muted'),
      }, f.g);
      t.textContent = format ? format(label) : String(label);
    });
    return i => i * step + step / 2;
  }

  function linePath(pts) {
    return pts.map((p, i) => (i ? 'L' : 'M') + p.x.toFixed(1) + ',' + p.y.toFixed(1)).join('');
  }

  function areaPath(pts, baseY) {
    if (!pts.length) return '';
    return linePath(pts)
      + 'L' + pts[pts.length - 1].x.toFixed(1) + ',' + baseY.toFixed(1)
      + 'L' + pts[0].x.toFixed(1) + ',' + baseY.toFixed(1) + 'Z';
  }

  /**
   * Découpe une série en tronçons de points consécutifs.
   *
   * Un trou coupe la ligne. Reliez deux points séparés par un mois sans mesure et vous
   * inventez une tendance qui n'existe pas.
   */
  function segments(points, maxGapMs) {
    const out = [];
    let current = [];
    for (const p of points) {
      if (current.length && p.t - current[current.length - 1].t > maxGapMs) {
        out.push(current);
        current = [];
      }
      current.push(p);
    }
    if (current.length) out.push(current);
    return out;
  }

  // ---------------------------------------------------------------- survol

  /**
   * Rend un graphique parcourable au doigt et à la souris.
   *
   * Le point le plus proche horizontalement est mis en avant, avec un trait de repère
   * et une infobulle. La zone sensible couvre tout le cadre : viser un point de 2 px au
   * doigt est impossible.
   *
   * @param {object} f le cadre rendu par [frame]
   * @param {Array} pts les points, portant `x`, `y` et les données à montrer
   * @param {function} format rend `{title, lines}` pour un point donné
   */
  function hoverNearest(f, pts, format) {
    if (!pts.length) return;
    const overlay = E('rect', {
      x: 0, y: 0, width: f.iw, height: f.ih, fill: 'transparent',
    }, f.g);
    const cross = E('line', {
      y1: 0, y2: f.ih, stroke: cssVar('--muted'), 'stroke-width': 1,
      'stroke-dasharray': '3,3', opacity: 0,
    }, f.g);
    const dot = E('circle', {
      r: 4.5, fill: cssVar('--accent'), stroke: cssVar('--surface'),
      'stroke-width': 2, opacity: 0,
    }, f.g);

    function move(ev) {
      const r = f.svg.getBoundingClientRect();
      if (!r.width) return;
      const mx = (ev.clientX - r.left) / r.width * f.W - f.m.l;
      let best = null;
      let bestDistance = Infinity;
      for (const p of pts) {
        const d = Math.abs(p.x - mx);
        if (d < bestDistance) { bestDistance = d; best = p; }
      }
      if (!best) return;
      cross.setAttribute('x1', best.x);
      cross.setAttribute('x2', best.x);
      cross.setAttribute('opacity', 1);
      dot.setAttribute('cx', best.x);
      dot.setAttribute('cy', best.y);
      dot.setAttribute('opacity', 1);
      const content = format(best);
      showTooltip(content.title, content.lines, ev.clientX, ev.clientY);
    }

    function leave() {
      cross.setAttribute('opacity', 0);
      dot.setAttribute('opacity', 0);
      hideTooltip();
    }

    overlay.addEventListener('pointermove', move);
    overlay.addEventListener('pointerdown', move);
    overlay.addEventListener('pointerleave', leave);
    overlay.addEventListener('pointercancel', leave);
  }

  /**
   * Attache une infobulle à une forme unique : barre, secteur, cellule.
   *
   * @param {Element} shape la forme sensible
   * @param {string} title la première ligne, en gras
   * @param {string[]} lines les lignes suivantes
   */
  function hoverShape(shape, title, lines) {
    const show = ev => showTooltip(title, lines, ev.clientX, ev.clientY);
    shape.addEventListener('pointermove', show);
    shape.addEventListener('pointerdown', show);
    shape.addEventListener('pointerleave', hideTooltip);
    shape.addEventListener('pointercancel', hideTooltip);
  }

  /** Montre un message à la place d'un graphique sans données. */
  function drawEmpty(host, message) {
    host.innerHTML = '';
    const p = document.createElement('p');
    p.className = 'ha-empty';
    p.textContent = message || 'Aucune mesure sur cette période.';
    host.appendChild(p);
  }

  /** Pose une légende de couleurs au-dessus d'un graphique. */
  function legend(host, entries) {
    const div = document.createElement('div');
    div.className = 'ha-legend';
    for (const [label, color] of entries) {
      const span = document.createElement('span');
      const i = document.createElement('i');
      i.style.background = color;
      span.appendChild(i);
      span.appendChild(document.createTextNode(label));
      div.appendChild(span);
    }
    host.appendChild(div);
  }

  global.HA = global.HA || {};
  global.HA.engine = {
    NS, COMPACT_WIDTH, MONTHS_FR, DAY_MS,
    E, cssVar, isCompact,
    fmtMonth, fmtDate, fmtHours, fmtClock, fmtInt, fmtNum, dateMs,
    showTooltip, hideTooltip,
    frame, yScale, grid, targetBand, xTimeAxis, xBandAxis,
    linePath, areaPath, segments,
    hoverNearest, hoverShape, drawEmpty, legend,
  };
}(typeof self !== 'undefined' ? self : this));
