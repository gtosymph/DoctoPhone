/**
 * Statistiques de série pour le rapport de santé. Portage fidèle de `Stats.kt`.
 *
 * Toutes les fonctions sont pures et travaillent sur des tableaux déjà filtrés
 * (sans `null`) sauf mention contraire. Une série vide rend `null`, jamais `0` :
 * un zéro se dessine, un trou ne se dessine pas (voir `report-model.js`).
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const TWO_PI = 2 * Math.PI;
  const HOURS_PER_DAY = 24;

  /** Jours de semaine en français court, dans l'ordre où le rapport les montre. */
  const WEEKDAY_LABELS = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];

  function mean(values) {
    if (values.length === 0) return null;
    return values.reduce((a, b) => a + b, 0) / values.length;
  }

  function median(values) {
    if (values.length === 0) return null;
    const sorted = values.slice().sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 1) return sorted[middle];
    return (sorted[middle - 1] + sorted[middle]) / 2;
  }

  /** Écart-type de population (division par n, pas n-1) : la série entière est connue. */
  function stdDev(values) {
    if (values.length === 0) return null;
    const m = mean(values);
    const variance = mean(values.map((v) => (v - m) * (v - m)));
    return Math.sqrt(variance);
  }

  /**
   * Rang `p` (0 à 1) d'une série, par l'index `trunc((n-1) * p)`.
   *
   * Convention déjà utilisée par `restingHeartRate` (voir `aggregate.js`) : ne pas la
   * changer, sinon la FC de repos et les autres percentiles du rapport divergeraient.
   */
  function percentile(values, p) {
    if (values.length === 0) return null;
    const sorted = values.slice().sort((a, b) => a - b);
    const index = Math.min(Math.max(Math.trunc((sorted.length - 1) * p), 0), sorted.length - 1);
    return sorted[index];
  }

  /**
   * Moyenne glissante sur `window` points, `null` tant que la fenêtre contient
   * moins de `minSamples` valeurs définies (début de série, ou trou de données).
   *
   * @param {(number|null)[]} values série ordonnée, un point par jour
   * @param {number} window taille de la fenêtre glissante (jours), fenêtre arrière incluant le jour courant
   * @param {number} minSamples nombre minimal de valeurs définies dans la fenêtre pour rendre une moyenne
   * @returns {(number|null)[]} une valeur par jour, alignée sur `values`
   */
  function rollingMean(values, window, minSamples) {
    return values.map((_, index) => {
      const start = Math.max(0, index - window + 1);
      const windowValues = values.slice(start, index + 1).filter((v) => v !== null && v !== undefined);
      if (windowValues.length < minSamples) return null;
      return mean(windowValues);
    });
  }

  /** Découpe une date `AAAA-MM-JJ` en jour ISO (0 = lundi ... 6 = dimanche), sans dépendre du fuseau du navigateur. */
  function isoWeekdayIndexOf(dateKey) {
    const [y, m, d] = dateKey.split('-').map(Number);
    const jsDay = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = dimanche ... 6 = samedi
    return (jsDay + 6) % 7;
  }

  /**
   * Moyenne par jour de semaine.
   * @param {{date: string, value: number|null}[]} dayValues
   * @returns {{label: string, value: number|null, count: number}[]} les 7 jours, dans l'ordre lun. → dim.
   */
  function byDayOfWeek(dayValues) {
    const buckets = WEEKDAY_LABELS.map(() => []);
    dayValues.forEach((entry) => {
      if (entry.value === null || entry.value === undefined) return;
      buckets[isoWeekdayIndexOf(entry.date)].push(entry.value);
    });
    return WEEKDAY_LABELS.map((label, index) => ({
      label,
      value: buckets[index].length > 0 ? mean(buckets[index]) : null,
      count: buckets[index].length,
    }));
  }

  /**
   * Moyenne par mois `AAAA-MM`, triée par ordre chronologique.
   * @param {{date: string, value: number|null}[]} dayValues
   * @returns {{month: string, value: number|null}[]}
   */
  function byMonth(dayValues) {
    const buckets = {};
    dayValues.forEach((entry) => {
      if (entry.value === null || entry.value === undefined) return;
      const month = entry.date.slice(0, 7);
      (buckets[month] = buckets[month] || []).push(entry.value);
    });
    return Object.keys(buckets).sort().map((month) => ({ month, value: mean(buckets[month]) }));
  }

  /**
   * Moyenne par heure locale (0 à 23). L'appelant calcule l'heure locale de chaque
   * mesure (avec son `offsetMinutes`) : cette fonction ne connaît que des heures déjà résolues.
   * @param {{hour: number, value: number|null}[]} hourValues
   * @returns {{label: string, value: number|null, count: number}[]} 24 tranches, 0 → 23
   */
  function byHour(hourValues) {
    const buckets = Array.from({ length: 24 }, () => []);
    hourValues.forEach((entry) => {
      if (entry.value === null || entry.value === undefined) return;
      if (entry.hour < 0 || entry.hour > 23) return;
      buckets[entry.hour].push(entry.value);
    });
    return buckets.map((values, hour) => ({
      label: String(hour),
      value: values.length > 0 ? mean(values) : null,
      count: values.length,
    }));
  }

  /**
   * Répartition d'une série en tranches fournies par l'appelant.
   * @param {number[]} values
   * @param {{label: string, min?: number, max?: number}[]} buckets tranches ordonnées, `min` inclus, `max` exclu
   * @returns {{label: string, value: number, count: number}[]} un compte par tranche (0 si vide, jamais `null` : le compte est toujours connu)
   */
  function distribution(values, buckets) {
    return buckets.map((bucket) => {
      const count = values.filter((v) => {
        const aboveMin = bucket.min === undefined || bucket.min === null || v >= bucket.min;
        const belowMax = bucket.max === undefined || bucket.max === null || v < bucket.max;
        return aboveMin && belowMax;
      }).length;
      return { label: bucket.label, value: count, count };
    });
  }

  /** Longueur du vecteur résultant R : proche de 1 si les heures sont concentrées, proche de 0 si dispersées. */
  function resultantVector(hours) {
    let sinSum = 0;
    let cosSum = 0;
    hours.forEach((h) => {
      const angle = (h / HOURS_PER_DAY) * TWO_PI;
      sinSum += Math.sin(angle);
      cosSum += Math.cos(angle);
    });
    const n = hours.length;
    return { meanSin: sinSum / n, meanCos: cosSum / n };
  }

  /**
   * Moyenne circulaire d'heures d'horloge (0 à 24, cadran de 24h).
   *
   * Une moyenne arithmétique naïve de 23h et 1h donnerait midi ; la moyenne circulaire
   * donne minuit, ce qui est le sens réel de « se coucher entre 23h et 1h ».
   * @param {number[]} hours
   * @returns {number|null} heure moyenne dans [0, 24), ou `null` si `hours` est vide
   */
  function circularMean(hours) {
    if (hours.length === 0) return null;
    const { meanSin, meanCos } = resultantVector(hours);
    let angle = Math.atan2(meanSin, meanCos);
    if (angle < 0) angle += TWO_PI;
    return (angle / TWO_PI) * HOURS_PER_DAY;
  }

  /**
   * Écart-type circulaire, en heures : `sqrt(-2 * ln(R)) / (2π) * 24`.
   *
   * Rend 12h (dispersion maximale) quand R vaut 0 (heures uniformément réparties sur
   * le cadran, aucune direction privilégiée).
   * @param {number[]} hours
   * @returns {number|null} `null` si `hours` est vide
   */
  function circularStdDev(hours) {
    if (hours.length === 0) return null;
    const { meanSin, meanCos } = resultantVector(hours);
    // La longueur du vecteur résultant est une moyenne de vecteurs unitaires : elle ne
    // peut mathématiquement pas dépasser 1. L'arrondi flottant, lui, la pousse parfois à
    // 1,0000000000000002 quand les angles sont tous égaux — il suffit de six couchers
    // identiques. Le logarithme devient alors positif et la racine rend NaN, qui casse la
    // sérialisation du modèle. Le plafond supprime ce cas.
    const resultantLength = Math.min(1, Math.sqrt(meanSin * meanSin + meanCos * meanCos));
    if (resultantLength <= 0) return HOURS_PER_DAY / 2;
    return (Math.sqrt(-2 * Math.log(resultantLength)) / TWO_PI) * HOURS_PER_DAY;
  }

  /**
   * Médiane circulaire d'heures d'horloge, recentrée autour de 18h.
   *
   * Une médiane arithmétique naïve séparerait à tort les couchers juste avant et
   * juste après minuit (23h30 et 0h30 se retrouveraient aux deux extrémités du tri).
   * Décaler les heures antérieures à 18h de +24h les ramène dans le même cadran que
   * les couchers du soir, sans discontinuité au milieu de la nuit : 18h est l'heure
   * la plus éloignée d'un coucher typique, donc le point de coupure le plus sûr.
   * @param {number[]} hours
   * @returns {number|null} `null` si `hours` est vide
   */
  function circularMedian(hours) {
    if (hours.length === 0) return null;
    const shifted = hours.map((h) => (h < 18 ? h + HOURS_PER_DAY : h)).sort((a, b) => a - b);
    const middle = Math.floor(shifted.length / 2);
    const med = shifted.length % 2 === 1
      ? shifted[middle]
      : (shifted[middle - 1] + shifted[middle]) / 2;
    return med >= HOURS_PER_DAY ? med - HOURS_PER_DAY : med;
  }

  root.HA.stats = {
    mean,
    median,
    stdDev,
    percentile,
    rollingMean,
    byDayOfWeek,
    byMonth,
    byHour,
    distribution,
    circularMean,
    circularStdDev,
    circularMedian,
    WEEKDAY_LABELS,
  };
})();
