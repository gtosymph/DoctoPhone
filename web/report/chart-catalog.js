/**
 * Catalogue des séries que le modèle peut demander à tracer.
 *
 * Le principe, et c'est le point important : **le modèle ne recopie pas vos chiffres, il
 * désigne une série**. Il écrit `"ref": "sleep.nightly.hours"`, l'application résout la
 * référence dans le rapport réel et dessine. Trois conséquences :
 *
 * - le modèle ne peut pas inventer une valeur, puisqu'il n'en transmet aucune ;
 * - le coût en jetons reste négligeable, même sur deux ans de mesures ;
 * - le graphique de la conversation a exactement l'aspect de celui du rapport.
 *
 * Il reste possible de fournir des valeurs en clair, pour quelque chose que le modèle a
 * calculé lui-même. Voir `chart-spec.js`.
 *
 * Chaque entrée décrit une série : son libellé lisible, son unité d'affichage, et la
 * nature de son axe horizontal — `time` pour une série datée, `band` pour des catégories
 * régulières comme les mois, les jours de semaine ou les heures.
 */

(function (global) {
  'use strict';

  /** Lit un chemin `a.b.c` dans un objet, sans lever d'exception sur un trou. */
  function at(root, path) {
    return path.split('.').reduce((node, key) => (node == null ? null : node[key]), root);
  }

  /** Série datée `{date, value}` prête pour un axe temporel. */
  function fromDayValues(path, field) {
    return model => {
      const rows = at(model, path);
      if (!Array.isArray(rows)) return [];
      return rows
        .map(r => ({ x: r.date, y: field ? r[field] : r.value }))
        .filter(p => p.x && p.y !== null && p.y !== undefined);
    };
  }

  /** Série à bandes `{label, value}` : jours de semaine, heures, tranches. */
  function fromLabelValues(path, field) {
    return model => {
      const rows = at(model, path);
      if (!Array.isArray(rows)) return [];
      return rows
        .map(r => ({ x: String(r.label), y: field ? r[field] : r.value }))
        .filter(p => p.y !== null && p.y !== undefined);
    };
  }

  /** Série mensuelle `{month, …}` : l'étiquette est le mois, mise en forme au dessin. */
  function fromMonthValues(path, field) {
    return model => {
      const rows = at(model, path);
      if (!Array.isArray(rows)) return [];
      return rows
        .map(r => ({ x: r.month, y: field ? r[field] : r.value }))
        .filter(p => p.x && p.y !== null && p.y !== undefined);
    };
  }

  /**
   * Le catalogue.
   *
   * `unit` pilote le format d'affichage : `hours` rend `6 h 12`, `clock` rend `1h19`,
   * `int` rend `5 450`, `num` rend `29,4`, et tout autre texte s'ajoute tel quel après
   * la valeur.
   */
  const CATALOG = {
    // ---------------------------------------------------------------- sommeil
    'sleep.nightly.hours': {
      label: 'Durée de sommeil par nuit', unit: 'hours', axis: 'time',
      read: fromDayValues('sleep.nightly', 'hours'),
    },
    'sleep.nightly.score': {
      label: 'Score de sommeil par nuit', unit: '/100', axis: 'time',
      read: fromDayValues('sleep.nightly', 'score'),
    },
    'sleep.nightly.bedRel': {
      label: 'Heure de coucher par nuit', unit: 'clock', axis: 'time',
      read: fromDayValues('sleep.nightly', 'bedRel'),
    },
    'sleep.nightly.efficiency': {
      label: 'Efficacité du sommeil par nuit', unit: '%', axis: 'time',
      read: fromDayValues('sleep.nightly', 'efficiencyPercent'),
    },
    'sleep.monthly.meanHours': {
      label: 'Durée moyenne de sommeil par mois', unit: 'hours', axis: 'month',
      read: fromMonthValues('sleep.monthly', 'meanHours'),
    },
    'sleep.monthly.meanScore': {
      label: 'Score de sommeil moyen par mois', unit: '/100', axis: 'month',
      read: fromMonthValues('sleep.monthly', 'meanScore'),
    },
    'sleep.dayOfWeek': {
      label: 'Durée de sommeil par jour de semaine', unit: 'hours', axis: 'band',
      read: fromLabelValues('sleep.dayOfWeek'),
    },
    'sleep.distribution': {
      label: 'Nombre de nuits par tranche de durée', unit: 'nuits', axis: 'band',
      read: model => (model.sleep.distribution || []).map(r => ({
        x: String(r.label), y: r.count !== null && r.count !== undefined ? r.count : r.value,
      })).filter(p => p.y !== null && p.y !== undefined),
    },

    // ---------------------------------------------------------------- coeur
    'heart.restingDaily': {
      label: 'Fréquence cardiaque de repos par jour', unit: 'bpm', axis: 'time',
      read: fromDayValues('heart.restingDaily'),
    },
    'heart.hrvDaily': {
      label: 'Variabilité cardiaque par jour', unit: 'ms', axis: 'time',
      read: fromDayValues('heart.hrvDaily'),
    },
    'heart.hourly': {
      label: 'Fréquence cardiaque par heure', unit: 'bpm', axis: 'hour',
      read: fromLabelValues('heart.hourly'),
    },
    'heart.monthly.resting': {
      label: 'FC de repos par mois', unit: 'bpm', axis: 'month',
      read: fromMonthValues('heart.monthly', 'resting'),
    },
    'heart.monthly.average': {
      label: 'FC moyenne par mois', unit: 'bpm', axis: 'month',
      read: fromMonthValues('heart.monthly', 'average'),
    },
    'heart.hrvMonthly': {
      label: 'Variabilité cardiaque par mois', unit: 'ms', axis: 'month',
      read: fromMonthValues('heart.hrvMonthly'),
    },

    // ---------------------------------------------------------------- activite
    'activity.stepsDaily': {
      label: 'Pas par jour', unit: 'int', axis: 'time',
      read: fromDayValues('activity.stepsDaily'),
    },
    'activity.stepsRolling7': {
      label: 'Pas, moyenne glissante sur 7 jours', unit: 'int', axis: 'time',
      read: fromDayValues('activity.stepsRolling7'),
    },
    'activity.stepsMonthly': {
      label: 'Pas par jour, moyenne mensuelle', unit: 'int', axis: 'month',
      read: fromMonthValues('activity.stepsMonthly'),
    },
    'activity.stepsDayOfWeek': {
      label: 'Pas par jour de semaine', unit: 'int', axis: 'band',
      read: fromLabelValues('activity.stepsDayOfWeek'),
    },
    'activity.floorsMonthly': {
      label: 'Étages par jour, moyenne mensuelle', unit: 'int', axis: 'month',
      read: fromMonthValues('activity.floorsMonthly'),
    },
    'activity.exerciseMonthly.minutes': {
      label: "Minutes d'exercice par mois", unit: 'min', axis: 'month',
      read: fromMonthValues('activity.exerciseMonthly', 'minutes'),
    },

    // ---------------------------------------------------------------- corps
    'body.weightKg': {
      label: 'Poids', unit: 'kg', axis: 'time',
      read: fromDayValues('body.daily', 'weightKg'),
    },
    'body.bodyFatPercent': {
      label: 'Masse grasse', unit: '%', axis: 'time',
      read: fromDayValues('body.daily', 'bodyFatPercent'),
    },
    'body.skeletalMuscleKg': {
      label: 'Masse musculaire squelettique', unit: 'kg', axis: 'time',
      read: fromDayValues('body.daily', 'skeletalMuscleKg'),
    },
    'body.bodyMassIndex': {
      label: 'Indice de masse corporelle', unit: 'num', axis: 'time',
      read: fromDayValues('body.daily', 'bodyMassIndex'),
    },

    // ---------------------------------------------------------------- stress
    'stress.daily': {
      label: 'Stress par jour', unit: '/100', axis: 'time',
      read: fromDayValues('stress.daily'),
    },
    'stress.monthly.mean': {
      label: 'Stress moyen par mois', unit: '/100', axis: 'month',
      read: fromMonthValues('stress.monthly', 'mean'),
    },
    'stress.monthly.percentAbove60': {
      label: 'Part du temps au-dessus de 60, par mois', unit: '%', axis: 'month',
      read: fromMonthValues('stress.monthly', 'percentAbove60'),
    },
    'stress.hourly': {
      label: 'Stress par heure', unit: '/100', axis: 'hour',
      read: fromLabelValues('stress.hourly'),
    },
    'stress.dayOfWeek': {
      label: 'Stress par jour de semaine', unit: '/100', axis: 'band',
      read: fromLabelValues('stress.dayOfWeek'),
    },
    'stress.vitalityDaily': {
      label: 'Score de vitalité par jour', unit: '/100', axis: 'time',
      read: fromDayValues('stress.vitalityDaily'),
    },

    // ---------------------------------------------------------------- respiration
    'breathing.spo2Daily': {
      label: 'Oxygénation par jour', unit: '%', axis: 'time',
      read: fromDayValues('breathing.spo2Daily'),
    },
    'breathing.spo2Monthly.mean': {
      label: 'Oxygénation moyenne par mois', unit: '%', axis: 'month',
      read: fromMonthValues('breathing.spo2Monthly', 'mean'),
    },
    'breathing.respiratoryDaily': {
      label: 'Fréquence respiratoire par nuit', unit: '/min', axis: 'time',
      read: fromDayValues('breathing.respiratoryDaily'),
    },
    'breathing.skinTempDaily': {
      label: 'Température cutanée par nuit', unit: '°C', axis: 'time',
      read: fromDayValues('breathing.skinTempDaily'),
    },
  };

  /**
   * Les séries réellement disponibles dans un rapport donné.
   *
   * Le prompt système n'annonce au modèle que celles-ci : proposer une série vide
   * l'inviterait à demander un graphique que l'application ne pourrait pas dessiner.
   *
   * @param {object} model un modèle de rapport
   * @returns {{ref: string, label: string, unit: string, axis: string, points: number}[]}
   */
  function availableSeries(model) {
    if (!model) return [];
    const out = [];
    for (const ref of Object.keys(CATALOG)) {
      const entry = CATALOG[ref];
      let points = [];
      try {
        points = entry.read(model) || [];
      } catch (error) {
        points = [];
      }
      if (points.length >= 2) {
        out.push({ ref, label: entry.label, unit: entry.unit, axis: entry.axis, points: points.length });
      }
    }
    return out;
  }

  /**
   * Ne garde que les points de [axis] `time` ou `month` compris dans `[range.from, range.to]`
   * (bornes ISO `AAAA-MM-JJ` incluses). Un axe `band`/`hour` n'a pas de date par point — le
   * profil reste celui de tout l'historique, une fenêtre ne changerait rien à ce qu'il
   * montre.
   *
   * @param {{x: string, y: number}[]} points
   * @param {string} axis
   * @param {{from: string, to: string}} range
   */
  function filterByRange(points, axis, range) {
    if (axis === 'time') {
      return points.filter((p) => p.x >= range.from && p.x <= range.to);
    }
    if (axis === 'month') {
      const fromMonth = range.from.slice(0, 7);
      const toMonth = range.to.slice(0, 7);
      return points.filter((p) => p.x >= fromMonth && p.x <= toMonth);
    }
    return points;
  }

  /**
   * Résout une référence en points, ou rend `null` si la référence est inconnue.
   *
   * @param {string} ref la clé du catalogue
   * @param {object} model le modèle de rapport — toujours celui de tout l'historique, voir
   *   `range` ci-dessous
   * @param {{from: string, to: string}} [range] la fenêtre du **message** qui porte ce
   *   graphique (voir `chat-view.js`), pas la fenêtre active de la conversation : un
   *   graphique déjà affiché doit garder ses données d'origine même si le modèle choisit
   *   une autre période ensuite. Facultatif — sans lui, `points` porte tout l'historique.
   * @returns {{points: Array, unit: string, axis: string, label: string}|null}
   */
  function resolve(ref, model, range) {
    const entry = CATALOG[ref];
    if (!entry || !model) return null;
    let points;
    try {
      points = entry.read(model) || [];
    } catch (error) {
      return null;
    }
    if (range) points = filterByRange(points, entry.axis, range);
    return { points, unit: entry.unit, axis: entry.axis, label: entry.label };
  }

  global.HA = global.HA || {};
  global.HA.chartCatalog = { CATALOG, availableSeries, resolve };
}(typeof self !== 'undefined' ? self : this));
