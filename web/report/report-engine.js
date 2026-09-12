/**
 * Moteur de graphiques du rapport de santé.
 *
 * Ce fichier ne connaît aucune métrique. Il fournit les primitives de dessin : cadre,
 * échelles, grille, axes, courbe, aire, barres et infobulle. Les 19 graphiques sont
 * écrits par-dessus, dans `report-charts-*.js`.
 *
 * Il est partagé par les deux versions : le web l'utilise directement, Android le
 * charge dans une WebView locale. Le dessin passe par SVG et un `viewBox`, donc un même
 * graphique tient sur un téléphone comme sur un écran large.
 *
 * ## L'échelle décide, le graphique obéit
 *
 * Aucun graphique ne fixe ses propres bornes ni ses propres graduations. Il donne à
 * [scale] le minimum et le maximum de ses données, et reçoit un axe complet : bornes
 * rondes qui **couvrent** les mesures, graduations alignées sur un pas lisible, et la
 * largeur à réserver à gauche pour que l'étiquette la plus longue tienne dans la
 * `viewBox`.
 *
 * Cette règle vient de deux défauts réels. Le graphique du stress bornait son axe à 60
 * et rabattait sur cette ligne tout ce qui dépassait : une personne dont le score
 * moyen valait 78 lisait 60. Celui du sommeil graduait jusqu'à 12 h alors que la plus
 * longue nuit n'atteignait pas 10 h, ce qui écrasait toute la courbe dans le bas du
 * cadre. **Une échelle décidée à l'avance finit toujours par mentir sur les données.**
 *
 * ## Le noir et blanc
 *
 * Une synthèse remise à un médecin s'imprime, souvent en niveaux de gris. Deux courbes
 * qui ne se distinguent que par leur teinte y deviennent deux courbes grises
 * identiques. Toute série doublée d'une autre porte donc un style de trait ([DASH]) ou
 * une étiquette chiffrée, et la légende montre ce style plutôt qu'une pastille.
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
   * Les styles de trait qui doublent la couleur d'une série.
   *
   * `null` est le trait plein, toujours réservé à la série principale : c'est elle que
   * l'œil doit suivre en premier, et c'est le trait le plus lisible.
   */
  const DASH = {
    solid: null,
    dashed: '7,4',
    dotted: '2,3',
    dashDot: '9,3,2,3',
  };

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

  // ---------------------------------------------------------------- mesure du texte

  /**
   * Largeur approchée d'un texte, en pixels, pour la sans du rapport.
   *
   * Une mesure exacte demanderait un `getComputedTextLength()`, donc un élément déjà
   * posé dans le document et déjà mis en page. Or la marge gauche doit être connue
   * **avant** de créer le SVG, et le rapport se dessine parfois dans un conteneur encore
   * masqué, où toute mesure rend zéro.
   *
   * L'estimation est donc volontairement généreuse : réserver deux pixels de trop ne se
   * voit pas, en réserver deux de trop peu coupe le premier caractère de l'étiquette.
   */
  const CHAR_EM = { ' ': 0.28, '−': 0.6, '–': 0.6, '-': 0.36, '+': 0.6, '%': 0.9, '.': 0.3, ',': 0.3 };
  const NARROW = 'ijlt.,;:\'’|!';

  function textWidth(text, size) {
    const s = String(text === null || text === undefined ? '' : text);
    const px = size || 11;
    let em = 0;
    for (const ch of s) {
      if (CHAR_EM[ch] !== undefined) em += CHAR_EM[ch];
      else if (ch >= '0' && ch <= '9') em += 0.58;
      else if (NARROW.indexOf(ch) >= 0) em += 0.3;
      else if (ch === 'm' || ch === 'w' || ch === 'M' || ch === 'W') em += 0.88;
      else if (ch >= 'A' && ch <= 'Z') em += 0.68;
      else em += 0.56;
    }
    return em * px;
  }

  // ---------------------------------------------------------------- échelles

  /**
   * Les pas d'axe admis, à la puissance de dix près.
   *
   * `2,5` en est volontairement absent : une grille tous les 2,5 est correcte en
   * arithmétique et pénible à lire, surtout sur des heures et des kilogrammes.
   */
  const NICE_STEPS = [1, 2, 5, 10];

  /**
   * Choisit le pas qui approche au mieux le nombre de graduations voulu.
   *
   * L'écart est pénalisé plus lourdement vers le haut que vers le bas : une grille trop
   * dense coûte plus qu'une grille trop lâche. Le lecteur cherche un repère, pas un
   * quadrillage, et les étiquettes finissent par se toucher sur un graphique court.
   */
  const CROWDING_PENALTY = 1.3;

  function chooseStep(span, intervals) {
    const rough = span / Math.max(1, intervals);
    const power = Math.pow(10, Math.floor(Math.log10(rough)));
    let best = null;
    for (const p of [power, power * 10]) {
      for (const m of NICE_STEPS) {
        const step = m * p;
        if (!isFinite(step) || step <= 0) continue;
        const n = Math.ceil(span / step);
        const score = n > intervals
          ? (n - intervals) * CROWDING_PENALTY
          : (intervals - n);
        if (!best || score < best.score) best = { step, score };
      }
    }
    return best ? best.step : 1;
  }

  /** Nombre de décimales à garder pour un pas donné, afin d'écarter le bruit flottant. */
  function decimalsFor(step) {
    return Math.max(0, Math.ceil(-Math.log10(step)) + 1);
  }

  /**
   * Construit un axe complet à partir de l'étendue réelle des données.
   *
   * @param {number} min la plus petite valeur mesurée
   * @param {number} max la plus grande valeur mesurée
   * @param {object} [opts] `count` (nombre d'intervalles visé, 5 par défaut),
   *   `fmt` (mise en forme d'une graduation), `zero` (forcer le zéro comme base),
   *   `capHigh` / `capLow` (plafond et plancher naturels de la grandeur, comme 100 %)
   * @returns {object} `{ lo, hi, ticks, fmt, gutter }` — `gutter` est la largeur à
   *   réserver à gauche du cadre pour que la plus longue étiquette y tienne.
   */
  function scale(min, max, opts) {
    const o = opts || {};
    const fmt = o.fmt || (v => fmtNum(v, Number.isInteger(v) ? 0 : 1));

    let lo = Number(min);
    let hi = Number(max);
    if (!isFinite(lo) || !isFinite(hi)) { lo = 0; hi = 1; }
    if (lo > hi) { const swap = lo; lo = hi; hi = swap; }
    if (o.zero) { lo = Math.min(0, lo); hi = Math.max(0, hi); }

    if (hi - lo < 1e-9) {
      // Une série constante donnerait une étendue nulle, donc une division par zéro et
      // une courbe empilée sur une seule ligne. On ouvre un intervalle autour d'elle.
      const pad = Math.abs(hi) > 1 ? Math.abs(hi) * 0.05 : 0.5;
      lo -= pad;
      hi += pad;
      if (o.zero) lo = Math.min(0, lo);
    }

    // Cinq intervalles, et non quatre : avec quatre, un axe de poids allant de 35 à 90 kg
    // prenait un pas de 20 et s'étendait de 20 à 100, ce qui aplatissait les deux courbes
    // au milieu du cadre. Un pas plus fin colle davantage aux données.
    const step = chooseStep(hi - lo, Math.max(2, o.count || 5));
    const digits = decimalsFor(step);
    const first = Math.floor(lo / step + 1e-9) * step;
    const last = Math.ceil(hi / step - 1e-9) * step;

    const ticks = [];
    for (let k = 0; first + k * step <= last + step * 1e-6; k++) {
      let value = Number((first + k * step).toFixed(digits));
      if (o.capHigh !== undefined && value > o.capHigh) break;
      if (o.capLow !== undefined && value < o.capLow) continue;
      ticks.push(value);
    }
    if (ticks.length < 2) ticks.push(Number((first + step).toFixed(digits)));

    let gutter = 0;
    for (const t of ticks) gutter = Math.max(gutter, textWidth(fmt(t)));

    return {
      lo: ticks[0],
      hi: ticks[ticks.length - 1],
      ticks,
      fmt,
      // 8 px entre l'étiquette et l'axe, 4 px de garde contre l'approximation de mesure.
      gutter: Math.ceil(gutter) + 12,
    };
  }

  /**
   * Construit un axe à partir d'une liste de valeurs, en écartant les trous.
   *
   * @returns {object|null} `null` quand aucune valeur n'est exploitable — l'appelant
   *   doit alors montrer un état vide plutôt qu'un cadre sans données.
   */
  function scaleOf(values, opts) {
    const clean = [];
    for (const v of values) {
      const n = Number(v);
      if (v !== null && v !== undefined && isFinite(n)) clean.push(n);
    }
    if (!clean.length) return null;
    return scale(Math.min.apply(null, clean), Math.max.apply(null, clean), opts);
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
   * Montre l'infobulle au point donné, sans jamais sortir de la fenêtre.
   *
   * Le contenu est posé en `textContent`, jamais en `innerHTML`. Une partie des textes
   * vient du LLM ou de champs libres de l'export, comme les symptômes d'un ECG : les
   * traiter comme du HTML ouvrirait une injection de script.
   *
   * Le placement par défaut est **au-dessus** du point : au doigt, le pouce couvre la
   * zone touchée, et une infobulle posée dessous serait masquée par la main.
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

    const vw = global.innerWidth || 1024;
    const vh = global.innerHeight || 768;
    const w = tt.offsetWidth;
    const h = tt.offsetHeight;

    let left = x + 14;
    if (left + w > vw - 8) left = x - w - 14;
    if (left < 8) left = 8;

    let top = y - h - 12;
    if (top < 8) top = y + 20;
    // Le point peut se trouver tout en bas de la fenêtre : sans ce rabattement,
    // l'infobulle sort sous le bord et reste illisible.
    if (top + h > vh - 8) top = Math.max(8, vh - h - 8);

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
   * @param {object} opts `w`, `h`, `gutter` (marge gauche calculée par [scale]),
   *   `m` (marges explicites, qui l'emportent), `label` (titre d'accessibilité)
   * @returns {object} le cadre, avec `svg`, `g`, `iw`, `ih`, `W`, `H`, `m`
   */
  function frame(host, opts) {
    const o = opts || {};
    const W = o.w || 920;
    const H = o.h || 260;
    const m = Object.assign(
      { t: 14, r: 14, b: 30, l: Math.max(28, o.gutter || 44) },
      o.m || {},
    );
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
   * Trace la grille horizontale et l'axe des ordonnées, à partir d'un axe de [scale].
   *
   * La grille est horizontale seulement. Des traits verticaux pleine hauteur
   * quadrillent le fond et noient les marques qu'on est venu lire ; l'axe du temps pose
   * à la place de petites encoches sous la ligne de base.
   *
   * @param {object} f le cadre rendu par [frame]
   * @param {object} s l'axe rendu par [scale]
   * @returns {function} l'échelle verticale, réutilisable par l'appelant
   */
  function grid(f, s) {
    const sy = yScale(s.lo, s.hi, f.ih);
    for (const t of s.ticks) {
      const y = sy(t);
      E('line', {
        x1: 0, x2: f.iw, y1: y, y2: y,
        stroke: cssVar('--grid'), 'stroke-width': 1,
      }, f.g);
      const lb = E('text', {
        x: -8, y: y + 4, 'text-anchor': 'end', 'font-size': 11,
        fill: cssVar('--muted'), style: 'font-variant-numeric:tabular-nums',
      }, f.g);
      lb.textContent = s.fmt(t);
    }
    E('line', {
      x1: 0, x2: f.iw, y1: f.ih, y2: f.ih,
      stroke: cssVar('--axis'), 'stroke-width': 1,
    }, f.g);
    return sy;
  }

  /**
   * Bande de valeur cible, dessinée sous les courbes, avec son libellé dans la bande.
   *
   * Le libellé est dans la bande et non en légende : une bande grise sans explication
   * oblige à chercher ailleurs dans la page ce qu'elle représente, et à l'impression la
   * légende se retrouve parfois sur la page suivante.
   */
  function targetBand(f, sy, low, high, label) {
    const yTop = sy(high);
    const yBottom = sy(low);
    E('rect', {
      x: 0, y: yTop, width: f.iw, height: Math.max(0, yBottom - yTop),
      fill: cssVar('--band'),
    }, f.g);
    if (!label) return;

    const height = yBottom - yTop;
    const t = E('text', {
      x: f.iw - 6,
      // Une bande trop plate ne peut pas porter son texte : il se pose juste au-dessus.
      y: height >= 16 ? yTop + height / 2 + 3.5 : Math.max(9, yTop - 5),
      'text-anchor': 'end', 'font-size': 10.5, fill: cssVar('--muted'),
    }, f.g);
    t.textContent = label;
  }

  /**
   * Marque le dernier point d'une série et, au besoin, écrit sa valeur.
   *
   * C'est l'état d'aujourd'hui : l'œil le cherche en premier. Sans marque, il faut
   * suivre la courbe jusqu'au bord du cadre et deviner où elle s'arrête.
   *
   * @param {object} f le cadre
   * @param {object} pt le point, portant `x` et `y`
   * @param {string} color la couleur de la série
   * @param {string} [label] la valeur à écrire à côté du point
   */
  function lastPoint(f, pt, color, label) {
    if (!pt || !isFinite(pt.x) || !isFinite(pt.y)) return null;
    const g = E('g', { class: 'ha-last' }, f.g);
    E('circle', { cx: pt.x, cy: pt.y, r: 6.5, fill: color, opacity: .22 }, g);
    E('circle', {
      cx: pt.x, cy: pt.y, r: 3.6, fill: color,
      stroke: cssVar('--surface'), 'stroke-width': 1.6,
    }, g);
    if (!label) return g;

    // L'étiquette passe à gauche du point quand elle déborderait de la `viewBox`.
    const w = textWidth(label, 11);
    const toRight = pt.x + 10 + w <= f.iw + f.m.r - 4;
    // Le contour de la couleur du fond, peint sous le texte, détache l'étiquette de ce
    // qu'elle recouvre : sur une série dense, elle tombe sinon au milieu des points.
    const t = E('text', {
      x: toRight ? pt.x + 10 : pt.x - 10,
      y: Math.max(11, Math.min(f.ih - 2, pt.y + 4)),
      'text-anchor': toRight ? 'start' : 'end',
      'font-size': 11, 'font-weight': 600, fill: cssVar('--ink'),
      stroke: cssVar('--page'), 'stroke-width': 3, 'stroke-linejoin': 'round',
      'paint-order': 'stroke',
      style: 'font-variant-numeric:tabular-nums',
    }, g);
    t.textContent = label;
    return g;
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
          x1: x, x2: x, y1: f.ih, y2: f.ih + 4,
          stroke: cssVar('--axis'), 'stroke-width': 1,
        }, f.g);
        const text = MONTHS_FR[d.getMonth()]
          + (d.getMonth() === 0 ? ' ' + String(d.getFullYear()).slice(2) : '');
        const t = E('text', {
          x: clampLabelX(f, x, text), y: f.ih + 18,
          'text-anchor': 'middle', 'font-size': 11, fill: cssVar('--muted'),
        }, f.g);
        t.textContent = text;
      }
      d.setMonth(d.getMonth() + 1);
      k++;
    }
    return sx;
  }

  /**
   * Ramène une étiquette centrée à l'intérieur de la `viewBox`.
   *
   * Un texte SVG n'est pas rogné par le bord : il déborde, et le navigateur le coupe au
   * bord du cadre de vue. Une première et une dernière étiquette de mois y perdaient
   * leurs premières lettres.
   */
  function clampLabelX(f, x, text) {
    const half = textWidth(text) / 2;
    const left = half - f.m.l + 2;
    const right = f.iw + f.m.r - half - 2;
    return Math.max(left, Math.min(right, x));
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
      const text = format ? format(label) : String(label);
      const t = E('text', {
        x: clampLabelX(f, i * step + step / 2, text), y: f.ih + 18,
        'text-anchor': 'middle', 'font-size': 11, fill: cssVar('--muted'),
      }, f.g);
      t.textContent = text;
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

  /**
   * Trace une série en tronçons, chacun d'un seul trait.
   *
   * @param {object} f le cadre
   * @param {Array} pts les points, portant `x`, `y` et `t`
   * @param {object} opts `color`, `width`, `dash`, `maxGapMs`, `area` (ligne de base)
   */
  function drawSeries(f, pts, opts) {
    const o = opts || {};
    const gap = o.maxGapMs || Infinity;
    for (const seg of segments(pts, gap)) {
      if (seg.length < 2) continue;
      if (o.area !== undefined) {
        E('path', { d: areaPath(seg, o.area), fill: o.color, opacity: o.areaOpacity || .15 }, f.g);
      }
      E('path', {
        d: linePath(seg), fill: 'none', stroke: o.color,
        'stroke-width': o.width || 2, 'stroke-linejoin': 'round',
        'stroke-dasharray': o.dash || null,
      }, f.g);
    }
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
    overlay.addEventListener('pointerup', leave);
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
    shape.addEventListener('pointerup', hideTooltip);
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

  /**
   * Pose une légende au-dessus d'un graphique.
   *
   * Chaque entrée montre le **trait** de sa série, motif compris, et non une pastille
   * de couleur : imprimée en niveaux de gris, une pastille ne distingue plus rien.
   *
   * @param {Array} entries des triplets `[libellé, couleur, motif]`. Le motif vaut
   *   `null` pour un trait plein, une valeur de [DASH] pour un trait discontinu, ou
   *   la chaîne `'fill'` pour une série dessinée en aplat (barres, aires empilées).
   */
  function legend(host, entries) {
    const div = document.createElement('div');
    div.className = 'ha-legend';
    for (const [label, color, dash] of entries) {
      const span = document.createElement('span');
      const svg = E('svg', {
        viewBox: '0 0 22 10', width: 22, height: 10, 'aria-hidden': 'true',
      });
      if (dash === 'fill') {
        E('rect', { x: 1, y: 1, width: 20, height: 8, rx: 2, fill: color }, svg);
      } else {
        E('line', {
          x1: 0, x2: 22, y1: 5, y2: 5, stroke: color, 'stroke-width': 2.5,
          'stroke-dasharray': dash || null,
        }, svg);
      }
      span.appendChild(svg);
      span.appendChild(document.createTextNode(label));
      div.appendChild(span);
    }
    host.appendChild(div);
  }

  global.HA = global.HA || {};
  global.HA.engine = {
    NS, COMPACT_WIDTH, MONTHS_FR, DAY_MS, DASH,
    E, cssVar, isCompact, textWidth,
    fmtMonth, fmtDate, fmtHours, fmtClock, fmtInt, fmtNum, dateMs,
    showTooltip, hideTooltip,
    frame, yScale, scale, scaleOf, grid, targetBand, lastPoint,
    xTimeAxis, xBandAxis, clampLabelX,
    linePath, areaPath, segments, drawSeries,
    hoverNearest, hoverShape, drawEmpty, legend,
  };
}(typeof self !== 'undefined' ? self : this));
