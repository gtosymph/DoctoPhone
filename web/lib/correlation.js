/**
 * Corrélations de Pearson entre séries quotidiennes. Portage fidèle de `Correlation.kt`.
 *
 * Dépend de `HA.reportModel` (voir `report-model.js`) pour le seuil minimal de paires :
 * charger `report-model.js` avant ce fichier.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const MILLIS_PER_DAY = 86400000;

  function parseDateKeyToUtcMs(key) {
    const [y, m, d] = key.split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  }

  function addDays(dateKey, days) {
    const shifted = parseDateKeyToUtcMs(dateKey) + days * MILLIS_PER_DAY;
    const d = new Date(shifted);
    return HA.csv.dateKey(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  }

  /**
   * Apparie deux séries quotidiennes, avec un décalage optionnel.
   *
   * Convention alignée sur `Correlation.kt` : `a` précède toujours `b` de `lagDays`
   * jours. Pour chaque jour `d` de `a`, cherche la valeur de `b` au jour `d + lagDays`
   * (ex. « pas du jour d vers le sommeil du lendemain » avec `a` = pas, `b` = sommeil,
   * `lagDays = 1`). `lagDays = 0` compare le même jour.
   *
   * @param {{date: string, value: number|null}[]} a
   * @param {{date: string, value: number|null}[]} b
   * @param {number} [lagDays] défaut 0
   * @returns {{x: number, y: number}[]} les paires où les deux valeurs sont définies
   */
  function paired(a, b, lagDays) {
    const lag = lagDays || 0;
    const byDate = {};
    b.forEach((entry) => {
      if (entry.value !== null && entry.value !== undefined) byDate[entry.date] = entry.value;
    });

    const pairs = [];
    a.forEach((entry) => {
      if (entry.value === null || entry.value === undefined) return;
      const targetDate = lag === 0 ? entry.date : addDays(entry.date, lag);
      const bValue = byDate[targetDate];
      if (bValue !== undefined) pairs.push({ x: entry.value, y: bValue });
    });
    return pairs;
  }

  /**
   * Coefficient de corrélation de Pearson.
   *
   * Rend `null` en dessous de `MIN_CORRELATION_PAIRS` paires (le coefficient ne veut
   * rien dire sur trop peu de jours) ou si l'une des deux séries est constante
   * (écart-type nul : la corrélation n'est pas définie).
   *
   * @param {{x: number, y: number}[]} pairs
   * @returns {number|null}
   */
  function pearson(pairs) {
    const minPairs = HA.reportModel.MIN_CORRELATION_PAIRS;
    if (pairs.length < minPairs) return null;

    const xs = pairs.map((p) => p.x);
    const ys = pairs.map((p) => p.y);
    const meanX = HA.stats.mean(xs);
    const meanY = HA.stats.mean(ys);

    let covariance = 0;
    let varianceX = 0;
    let varianceY = 0;
    for (let i = 0; i < pairs.length; i += 1) {
      const dx = xs[i] - meanX;
      const dy = ys[i] - meanY;
      covariance += dx * dy;
      varianceX += dx * dx;
      varianceY += dy * dy;
    }
    if (varianceX <= 0 || varianceY <= 0) return null;
    return covariance / Math.sqrt(varianceX * varianceY);
  }

  root.HA.correlation = { paired, pearson, addDays };
})();
