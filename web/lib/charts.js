/**
 * Trace un graphique journalier en SVG, sans bibliothèque.
 *
 * Règle non négociable : un jour sans mesure est un TROU dans la ligne, jamais
 * un zéro, jamais un point relié au travers. La ligne se coupe et reprend.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const WIDTH = 720;
  const HEIGHT = 220;
  const PADDING_LEFT = 44;
  const PADDING_RIGHT = 12;
  const PADDING_TOP = 16;
  const PADDING_BOTTOM = 28;

  /** Découpe la série en tronçons de jours consécutifs porteurs de données. */
  function buildSegments(days, valueOf) {
    const segments = [];
    let current = [];
    days.forEach((day, index) => {
      const value = valueOf(day);
      if (value === null || value === undefined) {
        if (current.length > 0) segments.push(current);
        current = [];
      } else {
        current.push({ index, date: day.date, value });
      }
    });
    if (current.length > 0) segments.push(current);
    return segments;
  }

  function escapeAttr(text) {
    return String(text).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
  }

  /**
   * Rend un graphique en ligne dans l'élément SVG donné.
   * @param {SVGSVGElement} svg élément `<svg>` déjà présent dans le DOM.
   * @param {Array} days liste de `DailySnapshot`, dans l'ordre chronologique.
   * @param {Object} options `{ valueOf, color, formatValue, emptyMessage }`
   */
  function renderDailyLineChart(svg, days, options) {
    const { valueOf, color, formatValue, emptyMessage } = options;
    while (svg.firstChild) svg.removeChild(svg.firstChild);
    svg.setAttribute('viewBox', `0 0 ${WIDTH} ${HEIGHT}`);
    svg.setAttribute('preserveAspectRatio', 'none');

    const segments = buildSegments(days, valueOf);
    const allValues = segments.flat().map((p) => p.value);

    if (allValues.length === 0 || days.length < 2) {
      const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', String(WIDTH / 2));
      text.setAttribute('y', String(HEIGHT / 2));
      text.setAttribute('text-anchor', 'middle');
      text.setAttribute('class', 'chart-empty');
      text.textContent = emptyMessage || 'Pas assez de données sur cette période.';
      svg.appendChild(text);
      return;
    }

    const minValue = Math.min(...allValues);
    const maxValue = Math.max(...allValues);
    // Une plage plate (toutes les valeurs égales) doit rester lisible : on force un écart minimal.
    const span = maxValue - minValue || Math.max(maxValue * 0.1, 1);
    const lowValue = minValue - span * 0.08;
    const highValue = maxValue + span * 0.08;

    const usableWidth = WIDTH - PADDING_LEFT - PADDING_RIGHT;
    const usableHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;
    const lastIndex = Math.max(days.length - 1, 1);

    const xOf = (index) => PADDING_LEFT + (index / lastIndex) * usableWidth;
    const yOf = (value) => PADDING_TOP + usableHeight - ((value - lowValue) / (highValue - lowValue)) * usableHeight;

    const svgNs = 'http://www.w3.org/2000/svg';

    // Repères horizontaux : valeur haute, moyenne, basse.
    [lowValue, (lowValue + highValue) / 2, highValue].forEach((value) => {
      const y = yOf(value);
      const gridLine = document.createElementNS(svgNs, 'line');
      gridLine.setAttribute('x1', String(PADDING_LEFT));
      gridLine.setAttribute('x2', String(WIDTH - PADDING_RIGHT));
      gridLine.setAttribute('y1', String(y));
      gridLine.setAttribute('y2', String(y));
      gridLine.setAttribute('class', 'chart-grid');
      svg.appendChild(gridLine);

      const label = document.createElementNS(svgNs, 'text');
      label.setAttribute('x', String(PADDING_LEFT - 6));
      label.setAttribute('y', String(y + 4));
      label.setAttribute('text-anchor', 'end');
      label.setAttribute('class', 'chart-axis-label');
      label.textContent = formatValue ? formatValue(value) : Math.round(value).toString();
      svg.appendChild(label);
    });

    // Une ligne par tronçon : les trous entre segments ne sont jamais reliés.
    segments.forEach((segment) => {
      const points = segment.map((p) => `${xOf(p.index).toFixed(1)},${yOf(p.value).toFixed(1)}`).join(' ');
      const polyline = document.createElementNS(svgNs, 'polyline');
      polyline.setAttribute('points', points);
      polyline.setAttribute('class', 'chart-line');
      polyline.setAttribute('stroke', color || 'var(--accent)');
      svg.appendChild(polyline);

      if (segment.length === 1) {
        // Un tronçon d'un seul jour n'a pas de ligne : on montre un point.
        const point = segment[0];
        const dot = document.createElementNS(svgNs, 'circle');
        dot.setAttribute('cx', xOf(point.index).toFixed(1));
        dot.setAttribute('cy', yOf(point.value).toFixed(1));
        dot.setAttribute('r', '2.5');
        dot.setAttribute('fill', color || 'var(--accent)');
        svg.appendChild(dot);
      }

      segment.forEach((point) => {
        const title = document.createElementNS(svgNs, 'title');
        title.textContent = `${point.date} : ${formatValue ? formatValue(point.value) : point.value}`;
        const marker = document.createElementNS(svgNs, 'circle');
        marker.setAttribute('cx', xOf(point.index).toFixed(1));
        marker.setAttribute('cy', yOf(point.value).toFixed(1));
        marker.setAttribute('r', '2');
        marker.setAttribute('class', 'chart-point');
        marker.setAttribute('fill', color || 'var(--accent)');
        marker.appendChild(title);
        svg.appendChild(marker);
      });
    });

    // Étiquettes de dates : première et dernière journée de la période affichée.
    const first = document.createElementNS(svgNs, 'text');
    first.setAttribute('x', String(PADDING_LEFT));
    first.setAttribute('y', String(HEIGHT - 8));
    first.setAttribute('class', 'chart-axis-label');
    first.textContent = days[0].date;
    svg.appendChild(first);

    const last = document.createElementNS(svgNs, 'text');
    last.setAttribute('x', String(WIDTH - PADDING_RIGHT));
    last.setAttribute('y', String(HEIGHT - 8));
    last.setAttribute('text-anchor', 'end');
    last.setAttribute('class', 'chart-axis-label');
    last.textContent = days[days.length - 1].date;
    svg.appendChild(last);
  }

  root.HA.charts = { renderDailyLineChart, buildSegments };
})();
