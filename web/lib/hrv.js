/**
 * Lit un fichier `binning_data.json` de variabilité cardiaque.
 * Portage fidèle de `HrvBinningParser.kt`.
 *
 * Le fichier contient les mesures brutes d'une fenêtre d'environ une heure, une toutes
 * les 30 secondes. Le parseur en garde la médiane : elle résiste aux artefacts de
 * mouvement, fréquents la nuit, là où la moyenne se laisse tirer par quelques pics.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  function median(values) {
    if (values.length === 0) return null;
    const sorted = values.slice().sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 1) return sorted[middle];
    return (sorted[middle - 1] + sorted[middle]) / 2;
  }

  function parseHrvBinning(id, jsonText) {
    const bins = JSON.parse(jsonText);
    if (!Array.isArray(bins) || bins.length === 0) return null;

    const starts = bins.map((b) => b.start_time).filter((v) => typeof v === 'number');
    const ends = bins.map((b) => b.end_time).filter((v) => typeof v === 'number');
    if (starts.length === 0) return null;
    const start = Math.min(...starts);
    const end = ends.length > 0 ? Math.max(...ends) : start;

    const sdnnValues = bins.map((b) => b.sdnn).filter((v) => typeof v === 'number');
    const rmssdValues = bins.map((b) => b.rmssd).filter((v) => typeof v === 'number');

    return {
      id,
      time: Math.round((start + end) / 2),
      sdnnMillis: median(sdnnValues),
      rmssdMillis: median(rmssdValues),
    };
  }

  root.HA.hrv = { parseHrvBinning, median };
})();
