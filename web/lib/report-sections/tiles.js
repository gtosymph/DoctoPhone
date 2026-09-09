/**
 * Construit les 8 tuiles de synthèse en tête du rapport.
 *
 * Les seuils sont des repères usuels de bien-être, pas un avis médical (voir
 * `PRIVACY.md` / le disclaimer de l'app). Ils sont documentés ici pour être ajustés
 * facilement si un utilisateur ou un professionnel de santé les juge inadaptés.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};
  const util = () => root.HA.reportSections.util;

  const NO_DATA = '—';

  // Espace fine insécable (U+202F) : séparateur de milliers français, distinct de
  // l'espace normale utilisée devant une unité (« 5 h 48 », « ± 1,0 h »).
  const THIN_SPACE = ' ';

  /**
   * Formate les champs `value`/`sub` des tuiles en français — seuls endroits du
   * modèle qui portent du texte déjà mis en forme (voir `report-model.js` : partout
   * ailleurs, le modèle garde des nombres bruts et c'est le moteur de dessin qui
   * formate). Rassemblé ici en un seul endroit pour qu'une correction future ne se
   * fasse qu'à un point : virgule décimale, espace fine pour les milliers, heure
   * d'horloge `0h44` (jamais `00:44`), date « 23 juin 2025 » (jamais ISO).
   */
  const TileFormat = {
    /** Nombre décimal français : virgule, espace fine tous les 3 chiffres. `null` si non fini. */
    number(value, decimals) {
      if (value === null || value === undefined || !Number.isFinite(value)) return null;
      const sign = value < 0 ? '-' : '';
      const fixed = Math.abs(value).toFixed(decimals);
      const [intPart, decPart] = fixed.split('.');
      const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, THIN_SPACE);
      return decPart !== undefined ? `${sign}${grouped},${decPart}` : `${sign}${grouped}`;
    },
    /** Durée : « 5 h 48 ». */
    duration(hours) {
      const totalMinutes = Math.round(hours * 60);
      const h = Math.floor(totalMinutes / 60);
      const m = totalMinutes % 60;
      return `${h} h ${String(m).padStart(2, '0')}`;
    },
    /** Heure relative à minuit en heure d'horloge : « 0h44 », « 23h10 » (jamais `00:44`). */
    clock(relativeHours) {
      let clock = relativeHours < 0 ? relativeHours + 24 : relativeHours;
      let h = Math.floor(clock);
      let m = Math.round((clock - h) * 60);
      if (m === 60) { m = 0; h += 1; }
      if (h === 24) h = 0;
      return `${h}h${String(m).padStart(2, '0')}`;
    },
    /** Date `AAAA-MM-JJ` en français lisible : « 23 juin 2025 ». */
    date(dateKey) {
      return dateKey ? util().formatDate(dateKey) : NO_DATA;
    },
  };

  function tierOf(value, thresholds) {
    // thresholds = [{max, status}], ordonnés du meilleur au pire ; le premier qui couvre `value` gagne.
    for (const t of thresholds) {
      if (t.max === undefined || value <= t.max) return t.status;
    }
    return 'critical';
  }

  function sleepTile(sleep) {
    const meanHours = sleep.kpi.meanHours;
    if (meanHours === null || meanHours === undefined) {
      return util().makeTile('sleep', 'Sommeil', NO_DATA, null, 'Aucune nuit mesurée', 'neutral');
    }
    // Repère usuel adulte : 7-9h recommandées (National Sleep Foundation).
    const status = tierOf(meanHours >= 7 && meanHours <= 9 ? 0 : Math.abs(meanHours - 8), [
      { max: 0, status: 'good' },
      { max: 1.5, status: 'warn' },
      { max: 2.5, status: 'serious' },
      { status: 'critical' },
    ]);
    return util().makeTile('sleep', 'Sommeil', TileFormat.duration(meanHours), null, `${TileFormat.number(sleep.kpi.nights, 0)} nuits mesurées`, status);
  }

  function bedtimeTile(sleep) {
    const spread = sleep.kpi.bedSpreadHours;
    const median = sleep.kpi.bedMedian;
    if (spread === null || spread === undefined || median === null || median === undefined) {
      return util().makeTile('bedtime', 'Coucher médian', NO_DATA, null, 'Aucune donnée', 'neutral');
    }
    // Écart-type circulaire du coucher : sous 1h très régulier, au-delà de 3h très irrégulier.
    const status = tierOf(spread, [
      { max: 1, status: 'good' },
      { max: 2, status: 'warn' },
      { max: 3, status: 'serious' },
      { status: 'critical' },
    ]);
    return util().makeTile('bedtime', 'Coucher médian', TileFormat.clock(median), null, `± ${TileFormat.number(spread, 1)} h`, status);
  }

  function restingHeartRateTile(heart) {
    const value = heart.kpi.restingMean;
    if (value === null || value === undefined) {
      return util().makeTile('restingHeartRate', 'FC de repos', NO_DATA, 'bpm', 'Aucune donnée', 'neutral');
    }
    // Repère usuel adulte au repos : 60-100 bpm normal, en dessous de 60 souvent le signe d'une bonne forme.
    const status = tierOf(value, [
      { max: 65, status: 'good' },
      { max: 75, status: 'warn' },
      { max: 90, status: 'serious' },
      { status: 'critical' },
    ]);
    return util().makeTile('restingHeartRate', 'FC de repos', TileFormat.number(value, 0), 'bpm', 'Moyenne sur la période', status);
  }

  function hrvTile(heart) {
    const value = heart.kpi.hrvMedian;
    if (value === null || value === undefined) {
      return util().makeTile('hrv', 'Variabilité cardiaque', NO_DATA, 'ms', 'Aucune donnée', 'neutral');
    }
    // Repère usuel RMSSD (très individuel, à lire en tendance plus qu'en valeur absolue) : au-delà de
    // 50ms confortable, sous 20ms signale une récupération réduite.
    const status = value >= 50 ? 'good' : value >= 30 ? 'warn' : value >= 20 ? 'serious' : 'critical';
    return util().makeTile('hrv', 'Variabilité cardiaque', TileFormat.number(value, 0), 'ms', 'Médiane RMSSD', status);
  }

  function bodyMassIndexTile(body) {
    const value = body.kpi.bodyMassIndex;
    if (value === null || value === undefined) {
      return util().makeTile('bodyMassIndex', 'IMC', NO_DATA, null, 'Aucune donnée', 'neutral');
    }
    // Catégories OMS.
    let status;
    if (value >= 18.5 && value < 25) status = 'good';
    else if ((value >= 17 && value < 18.5) || (value >= 25 && value < 30)) status = 'warn';
    else if ((value >= 16 && value < 17) || (value >= 30 && value < 35)) status = 'serious';
    else status = 'critical';
    return util().makeTile('bodyMassIndex', 'IMC', TileFormat.number(value, 1), null, `Dernière mesure : ${TileFormat.date(body.kpi.lastMeasuredOn)}`, status);
  }

  function steps30Tile(activity) {
    const value = activity.kpi.meanSteps30;
    if (value === null || value === undefined) {
      return util().makeTile('steps30', 'Pas (30 j)', NO_DATA, 'pas/j', 'Aucune donnée', 'neutral');
    }
    // Repère usuel : 8000-10000 pas/jour associés à un bon niveau d'activité, sous 3000 très sédentaire.
    const status = value >= 8000 ? 'good' : value >= 5000 ? 'warn' : value >= 3000 ? 'serious' : 'critical';
    return util().makeTile('steps30', 'Pas (30 j)', TileFormat.number(value, 0), 'pas/j', 'Moyenne des 30 derniers jours', status);
  }

  function bloodPressureTile(heart) {
    const readings = heart.bloodPressure;
    if (!readings || readings.length === 0) {
      return util().makeTile('bloodPressure', 'Tension artérielle', NO_DATA, 'mmHg', 'Aucune donnée', 'neutral');
    }
    const last = readings[readings.length - 1];
    // Catégories usuelles (American Heart Association) : systolique/diastolique.
    let status;
    if (last.systolic < 120 && last.diastolic < 80) status = 'good';
    else if (last.systolic < 140 && last.diastolic < 90) status = 'warn';
    else if (last.systolic < 180 && last.diastolic < 120) status = 'serious';
    else status = 'critical';
    return util().makeTile(
      'bloodPressure', 'Tension artérielle',
      `${TileFormat.number(last.systolic, 0)}/${TileFormat.number(last.diastolic, 0)}`,
      'mmHg', `Dernière mesure : ${TileFormat.date(last.date)}`, status
    );
  }

  function stressTile(stress) {
    const value = stress.kpi.mean;
    if (value === null || value === undefined) {
      return util().makeTile('stress', 'Stress', NO_DATA, '/100', 'Aucune donnée', 'neutral');
    }
    const status = value <= 30 ? 'good' : value <= 45 ? 'warn' : value <= 60 ? 'serious' : 'critical';
    return util().makeTile('stress', 'Stress', TileFormat.number(value, 0), '/100', 'Moyenne sur la période', status);
  }

  function buildTiles(model) {
    return [
      sleepTile(model.sleep),
      bedtimeTile(model.sleep),
      restingHeartRateTile(model.heart),
      hrvTile(model.heart),
      bodyMassIndexTile(model.body),
      steps30Tile(model.activity),
      bloodPressureTile(model.heart),
      stressTile(model.stress),
    ];
  }

  root.HA.reportSections.buildTiles = buildTiles;
})();
