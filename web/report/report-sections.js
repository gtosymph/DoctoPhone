/**
 * La composition du rapport : quels onglets, quelles sections, quelles cartes, quels
 * indicateurs.
 *
 * Ce fichier ne dessine rien. Il décrit la page. `report-render.js` lit cette
 * description et construit le document. Ajouter un graphique se fait donc ici, en une
 * ligne, sans toucher au moteur.
 *
 * Chaque carte porte soit un `chart` (une clé de `HA.reportCharts`), soit un `render`
 * (une fonction qui construit elle-même le contenu, pour les tableaux). Elle porte
 * aussi un `group` (voir `GROUP_ORDER`), qui dit dans quel bloc de cadence — jour par
 * jour, mois par mois, profil, mesure ponctuelle — elle se range à l'intérieur de sa
 * section.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;

  /** Crée un élément avec sa classe et son texte. Jamais d'`innerHTML` : le texte peut venir du LLM. */
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  /** Un indicateur chiffré : la valeur en gros, le libellé en dessous. */
  function kpi(value, label) {
    return { value, label };
  }

  function hours(v) { return v === null || v === undefined ? '—' : E.fmtHours(v); }
  function clock(v) { return v === null || v === undefined ? '—' : E.fmtClock(v); }
  function pct(v) { return v === null || v === undefined ? '—' : E.fmtNum(v, 0) + ' %'; }
  function num(v, d) { return v === null || v === undefined ? '—' : E.fmtNum(v, d); }
  function int(v) { return v === null || v === undefined ? '—' : E.fmtInt(v); }


  /**
   * Un nombre signé, avec un vrai signe moins typographique (U+2212).
   *
   * Le trait d'union du clavier est plus court et ne s'aligne pas sur la chasse du plus :
   * dans une colonne de variations, la différence se voit.
   */
  function signed(v, d, unit) {
    if (v === null || v === undefined) return null;
    const sign = v > 0 ? '+' : (v < 0 ? '−' : '');
    return sign + E.fmtNum(Math.abs(v), d) + (unit || '');
  }


  /** Accorde un nom au pluriel. Une phrase se lit à voix haute ; « (s) » ne se prononce pas. */
  function plural(count, singular, pluralForm) {
    return count > 1 ? (pluralForm || singular + 's') : singular;
  }

  /** Assemble les morceaux non vides d'une phrase, ou rend `null` s'il n'en reste aucun. */
  function sentence(parts) {
    const kept = parts.filter(p => p !== null && p !== undefined && p !== '');
    return kept.length ? kept.join(' ') : null;
  }

  /** Vrai quand la valeur est un nombre exploitable — ni absent, ni NaN. */
  function has(v) { return v !== null && v !== undefined && Number.isFinite(v); }

  /**
   * Construit un petit tableau à partir d'en-têtes et de lignes de texte.
   *
   * Le tableau part dans un conteneur qui défile horizontalement. Sur un téléphone,
   * quatre colonnes ne tiennent pas dans une carte : sans ce conteneur, la dernière
   * colonne sort de la carte au lieu de rester atteignable.
   */
  function table(headers, rows) {
    const wrap = el('div', 'ha-table-wrap');
    const t = el('table', 'ha-mini');
    const head = document.createElement('tr');
    for (const h of headers) head.appendChild(el('th', null, h));
    t.appendChild(head);
    for (const row of rows) {
      const tr = document.createElement('tr');
      for (const cell of row) tr.appendChild(el('td', null, cell));
      t.appendChild(tr);
    }
    wrap.appendChild(t);
    return wrap;
  }

  /** Le tableau des prises de tension. */
  function renderBloodPressure(host, model) {
    const rows = model.heart.bloodPressure;
    if (!rows.length) {
      host.appendChild(el('p', 'ha-empty', 'Aucune prise de tension dans l\'export.'));
      return;
    }
    host.appendChild(table(
      ['Date', 'Systolique', 'Diastolique', 'Pouls'],
      rows.map(r => [E.fmtDate(r.date), r.systolic, r.diastolic, r.pulse === null ? '—' : r.pulse]),
    ));
    const note = el('p', 'ha-note',
      'Mesures au poignet, indicatives. Une tension se confirme par une automesure au brassard, '
      + 'trois fois le matin et trois fois le soir pendant une semaine.');
    note.style.marginTop = '8px';
    host.appendChild(note);
  }

  /** Le tableau des enregistrements d'ECG. */
  function renderEcg(host, model) {
    const rows = model.heart.ecg;
    if (!rows.length) {
      host.appendChild(el('p', 'ha-empty', 'Aucun enregistrement d\'ECG dans l\'export.'));
      return;
    }
    host.appendChild(table(
      ['Date', 'FC', 'Résultat'],
      rows.map(r => [
        E.fmtDate(r.date),
        r.meanHeartRate === null ? '—' : r.meanHeartRate,
        r.classificationLabel,
      ]),
    ));
    const note = el('p', 'ha-note',
      'L\'app ne traduit pas un résultat d\'ECG en diagnostic. Ouvrez Samsung Health Monitor '
      + 'pour lire le libellé officiel de chaque enregistrement.');
    note.style.marginTop = '8px';
    host.appendChild(note);
  }

  /**
   * La carte des corrélations croisées.
   *
   * Elle ne parle pas que du cœur — `model.correlations` mélange des paires de toutes
   * les catégories (sommeil, pas, poids...). Elle vit donc à part, pour être posée dans
   * l'onglet Synthèse plutôt que dans la section Cœur, où elle n'avait qu'à moitié sa
   * place.
   */
  const CORRELATIONS_CARD = {
    chart: 'heart-correlations', span: true,
    title: 'Les liens mesurés sur vos propres données',
    note: 'Corrélations de Pearson sur les jours où les deux mesures existent. '
      + 'Une corrélation n\'est pas une cause.',
  };

  /**
   * Les groupes de cadence, dans l'ordre où ils se lisent à l'intérieur d'un domaine :
   * d'abord le jour par jour, puis le mois par mois, puis les profils, puis les mesures
   * ponctuelles (tension, ECG) qui ne sont ni l'un ni l'autre.
   *
   * Une carte porte un `group` parmi ces clés. `report-render.js` s'en sert pour poser
   * un intertitre court avant chaque groupe — seulement quand un domaine en compte
   * plusieurs, sinon l'intertitre ne ferait que répéter le titre de la section.
   */
  const GROUP_ORDER = ['daily', 'monthly', 'profile', 'measures'];
  const GROUP_LABELS = {
    daily: 'Vue quotidienne',
    monthly: 'Vue mensuelle',
    profile: 'Profils',
    measures: 'Mesures ponctuelles',
  };

  /**
   * Les six sections du rapport, dans l'ordre de lecture.
   *
   * `available` dit si la section porte assez de données pour mériter d'être montrée.
   * Une section vide est enlevée : mieux vaut un rapport court qu'un rapport creux.
   *
   * `domain` relie la section à sa couleur (voir les jetons `--d-*` de `report.css`) et
   * à son onglet. Stress et respiration partagent le domaine `vitality` : ils gardent
   * chacun leur section, mais s'affichent dans le même onglet et la même couleur.
   */
  const SECTIONS = [
    {
      key: 'sleep',
      title: 'Sommeil',
      domain: 'sleep',
      available: m => m.sleep.nightly.length > 0,
      kpis: m => {
        const k = m.sleep.kpi;
        return [
          kpi(hours(k.medianHours), 'durée médiane'),
          kpi(k.bedSpreadHours === null ? '—' : '±' + hours(k.bedSpreadHours), 'irrégularité du coucher'),
          kpi(clock(k.bedMedian), 'coucher médian'),
          kpi(clock(k.wakeMedian), 'réveil médian'),
          kpi(pct(k.pctUnder6h), 'de nuits sous 6h'),
          kpi(k.debtHours === null ? '—' : int(k.debtHours) + ' h', 'dette cumulée'),
        ];
      },
      insight: m => {
        const k = m.sleep.kpi;
        return sentence([
          has(k.nights) ? int(k.nights) + ' nuits analysées.' : null,
          has(k.pctUnder6h) ? pct(k.pctUnder6h) + ' passent sous 6 h.' : null,
          // `hours()` et non un nombre décimal : l'indicateur « irrégularité du coucher »
          // juste au-dessus affiche ±1h46, et la même grandeur ne peut pas se lire en deux
          // formats sur le même écran. Accessoirement, « 1h46 » ne se coupe pas en fin de
          // ligne, là où « 1,8 h » sépare la valeur de son unité.
          has(k.bedSpreadHours)
            ? 'Le coucher varie de ±' + hours(k.bedSpreadHours) + ' d\'une nuit à l\'autre.'
            : null,
        ]);
      },
      cards: [
        {
          chart: 'sleep-nightly', span: true, group: 'daily', essential: true,
          title: 'Durée de sommeil, nuit par nuit',
          note: 'Chaque point est une nuit. La ligne est la moyenne glissante sur 14 nuits. '
            + 'La bande marque la zone cible de 7 à 9 heures.',
        },
        {
          chart: 'sleep-bedtime', span: true, group: 'daily', essential: true,
          title: 'Heure de coucher, nuit par nuit',
          note: 'Les couchers après 2h du matin sont marqués en rouge. '
            + 'L\'axe ne se coupe pas à minuit.',
        },
        {
          chart: 'sleep-stages', span: true, group: 'monthly',
          title: 'Architecture du sommeil par mois',
          note: 'Répartition des stades. Les repères usuels donnent 13 à 23 % de sommeil '
            + 'profond et 20 à 25 % de sommeil paradoxal.',
        },
        {
          chart: 'sleep-distribution', group: 'profile',
          title: 'Distribution des durées',
          note: 'Nombre de nuits par tranche d\'une heure.',
        },
        {
          chart: 'sleep-dow', group: 'profile',
          title: 'Durée selon le jour de la semaine',
          note: 'Chaque nuit est rattachée au jour du réveil.',
        },
      ],
    },
    {
      key: 'heart',
      title: 'Cœur',
      domain: 'heart',
      available: m => m.heart.monthly.length > 0 || m.heart.bloodPressure.length > 0,
      kpis: m => {
        const k = m.heart.kpi;
        return [
          kpi(int(k.restingMean) + ' bpm', 'FC de repos moyenne'),
          kpi(int(k.hrvMedian) + ' ms', 'RMSSD médian'),
          kpi(k.restingP10 === null ? '—' : int(k.restingP10) + '–' + int(k.restingP90), 'p10 à p90'),
          kpi(int(k.averageMean) + ' bpm', 'FC moyenne'),
          kpi(int(k.maxObserved) + ' bpm', 'FC maximale observée'),
          kpi(int(k.measuredDays), 'jours mesurés'),
        ];
      },
      insight: m => {
        const k = m.heart.kpi;
        return sentence([
          // p10 à p90 couvre 80 % des jours, donc huit sur dix. Écrire « neuf sur dix »
          // serait une surpromesse que rien dans les données ne soutient.
          (has(k.restingP10) && has(k.restingP90))
            ? 'FC de repos entre ' + int(k.restingP10) + ' et ' + int(k.restingP90)
              + ' bpm huit jours sur dix.'
            : null,
          has(k.hrvMedian) ? 'Variabilité médiane à ' + int(k.hrvMedian) + ' ms.' : null,
          has(k.measuredDays) ? 'Sur ' + int(k.measuredDays) + ' jours mesurés.' : null,
        ]);
      },
      cards: [
        {
          chart: 'heart-monthly', group: 'monthly', essential: true,
          title: 'FC de repos et FC moyenne, par mois',
          note: 'La FC de repos est le 5e centile des mesures du jour, sur au moins 20 mesures.',
        },
        {
          chart: 'heart-hrv', group: 'monthly', essential: true,
          title: 'Variabilité cardiaque (RMSSD), par mois',
          note: 'Médiane mensuelle des fenêtres de cinq minutes.',
        },
        {
          chart: 'heart-hourly', span: true, group: 'profile',
          title: 'Fréquence cardiaque au fil de la journée',
          note: 'Moyenne par heure locale, sur toute la période.',
        },
        // Ces deux tableaux sont vides chez la plupart des gens : ils restent au détail
        // tant qu'ils ne portent aucune ligne, et remontent tout seuls dès qu'ils en
        // portent une. Une carte « aucune mesure » répétée à chaque ouverture n'apprend
        // rien ; la même carte qui apparaît le jour d'une prise de tension, si.
        {
          render: renderBloodPressure, title: 'Tension artérielle', group: 'measures',
          hasContent: m => m.heart.bloodPressure.length > 0,
        },
        {
          render: renderEcg, title: 'Électrocardiogrammes', group: 'measures',
          hasContent: m => m.heart.ecg.length > 0,
        },
      ],
    },
    {
      key: 'activity',
      title: 'Activité',
      domain: 'activity',
      available: m => m.activity.stepsDaily.length > 0,
      kpis: m => {
        const k = m.activity.kpi;
        return [
          kpi(int(k.meanSteps30), 'sur les 30 derniers jours'),
          kpi(pct(k.pctDaysOver8000), 'de jours au-dessus de 8 000'),
          kpi(int(k.meanSteps), 'pas par jour, moyenne'),
          kpi(int(k.meanSteps90), 'sur les 90 derniers jours'),
          kpi(int(k.bestSteps), 'meilleure journée'),
          kpi(int(k.exerciseSessions), 'séances enregistrées'),
        ];
      },
      insight: m => {
        const k = m.activity.kpi;
        return sentence([
          has(k.meanSteps30) ? int(k.meanSteps30) + ' pas par jour sur les 30 derniers jours.' : null,
          has(k.meanSteps90) ? int(k.meanSteps90) + ' sur les 90 derniers.' : null,
          has(k.pctDaysOver8000) ? pct(k.pctDaysOver8000) + ' des jours passent 8 000 pas.' : null,
        ]);
      },
      cards: [
        {
          chart: 'activity-steps', span: true, group: 'daily', essential: true,
          title: 'Pas quotidiens — moyenne glissante sur 7 jours',
          note: 'La bande marque l\'objectif de 7 000 à 8 000 pas par jour.',
        },
        {
          chart: 'activity-exercise', group: 'monthly',
          title: 'Minutes d\'exercice par mois',
          note: 'Seules les séances enregistrées comptent, pas la marche passive.',
        },
        {
          chart: 'activity-floors', span: true, group: 'monthly',
          title: 'Étages montés par mois',
          note: 'Moyenne quotidienne d\'étages, mois par mois.',
        },
        {
          chart: 'activity-steps-dow', group: 'profile',
          title: 'Pas selon le jour de la semaine',
          note: 'Moyenne par jour, sur toute la période.',
        },
      ],
    },
    {
      key: 'body',
      title: 'Poids et composition',
      domain: 'body',
      available: m => m.body.daily.length > 0,
      kpis: m => {
        const k = m.body.kpi;
        return [
          kpi(num(k.lastWeightKg) + ' kg', 'dernière pesée'),
          kpi(k.deltaKg === null ? '—' : (k.deltaKg > 0 ? '+' : '') + num(k.deltaKg) + ' kg', 'variation sur la période'),
          kpi(num(k.bodyMassIndex), 'IMC'),
          kpi(pct(k.bodyFatPercent), 'masse grasse'),
          kpi(k.deltaMuscleKg === null ? '—' : (k.deltaMuscleKg > 0 ? '+' : '') + num(k.deltaMuscleKg) + ' kg', 'variation du muscle'),
          kpi(k.daysSinceLastMeasure === null ? '—' : int(k.daysSinceLastMeasure) + ' j', 'depuis la dernière pesée'),
        ];
      },
      insight: m => {
        const k = m.body.kpi;
        return sentence([
          has(k.deltaKg) ? signed(k.deltaKg, 1, ' kg') + ' sur la période.' : null,
          has(k.deltaMuscleKg) ? 'Dont ' + signed(k.deltaMuscleKg, 1, ' kg') + ' de muscle.' : null,
          has(k.daysSinceLastMeasure) && k.daysSinceLastMeasure > 14
            ? 'Aucune pesée depuis ' + int(k.daysSinceLastMeasure) + ' jours.'
            : null,
        ]);
      },
      cards: [
        {
          chart: 'body-weight', span: true, group: 'daily', essential: true,
          title: 'Poids et masse musculaire',
          note: 'Une perte de poids saine préserve le muscle. Suivez les deux courbes ensemble.',
        },
      ],
    },
    {
      key: 'stress',
      title: 'Stress et récupération',
      domain: 'vitality',
      available: m => m.stress.monthly.length > 0 || m.stress.vitalityDaily.length > 0,
      kpis: m => {
        const k = m.stress.kpi;
        return [
          kpi(int(k.mean) + '/100', 'score moyen'),
          kpi(int(k.vitalityMean) + '/100', 'vitalité moyenne'),
          kpi(pct(k.percentAbove60), 'du temps au-dessus de 60'),
          kpi(int(k.alerts), 'alertes de stress'),
          kpi(k.peakHour === null ? '—' : k.peakHour + 'h', 'heure la plus tendue'),
          kpi(int(k.measuredDays), 'jours mesurés'),
        ];
      },
      insight: m => {
        const k = m.stress.kpi;
        return sentence([
          has(k.percentAbove60) ? 'Le stress dépasse 60 pendant ' + pct(k.percentAbove60) + ' du temps.' : null,
          has(k.peakHour) ? 'Le pic tombe vers ' + int(k.peakHour) + ' h.' : null,
          has(k.vitalityMean) ? 'Vitalité moyenne à ' + int(k.vitalityMean) + '/100.' : null,
        ]);
      },
      cards: [
        {
          chart: 'stress-vitality', span: true, group: 'daily', essential: true,
          title: 'Score de vitalité, jour par jour',
          note: 'Samsung calcule ce score à partir du sommeil, de l\'activité et du cœur nocturne.',
        },
        {
          chart: 'stress-monthly', group: 'monthly',
          title: 'Stress moyen par mois',
          note: 'La courbe donne le score moyen, les barres la part du temps en zone élevée.',
        },
        {
          chart: 'stress-hourly', group: 'profile',
          title: 'Profil horaire du stress',
          note: 'Moyenne par heure locale, sur toute la période.',
        },
      ],
    },
    {
      key: 'breathing',
      title: 'Oxygénation, respiration et température',
      domain: 'vitality',
      available: m => m.breathing.spo2Monthly.length > 0 || m.breathing.skinTempDaily.length > 0,
      kpis: m => {
        const k = m.breathing.kpi;
        return [
          kpi(num(k.spo2Mean) + ' %', 'SpO2 moyenne'),
          kpi(num(k.skinTempMean) + ' °C', 'température cutanée'),
          kpi(int(k.spo2Measures), 'mesures d\'oxygénation'),
          kpi(int(k.spo2Under90), 'mesures sous 90 %'),
          kpi(num(k.respiratoryMean) + '/min', 'fréquence respiratoire'),
          kpi(k.skinTempStdDev === null ? '—' : '±' + num(k.skinTempStdDev) + ' °C', 'écart-type'),
        ];
      },
      insight: m => {
        const k = m.breathing.kpi;
        return sentence([
          has(k.spo2Mean) ? 'SpO2 moyenne à ' + num(k.spo2Mean, 1) + ' %.' : null,
          (has(k.spo2Measures) && has(k.spo2Under90))
            ? int(k.spo2Under90) + ' ' + plural(k.spo2Under90, 'mesure')
              + ' sous 90 % sur ' + int(k.spo2Measures) + '.'
            : null,
          has(k.skinTempStdDev)
            ? 'La température cutanée varie de ±' + num(k.skinTempStdDev, 1) + ' °C.'
            : null,
        ]);
      },
      cards: [
        {
          chart: 'breathing-skintemp', span: true, group: 'daily', essential: true,
          title: 'Température cutanée nocturne',
          note: 'Mesurée au poignet. L\'écart à votre référence compte plus que la valeur absolue.',
        },
        {
          chart: 'breathing-spo2', span: true, group: 'monthly',
          title: 'Oxygénation par mois — moyenne et minimum',
          note: 'Une valeur diurne isolée sous 90 % vient presque toujours d\'un mauvais contact du capteur.',
        },
      ],
    },
  ];

  /**
   * La barre d'onglets, dans l'ordre où elle se lit.
   *
   * `sections` relie un onglet aux clés de `SECTIONS` qu'il rassemble — une seule pour
   * la plupart des domaines, deux pour Vitalité (stress et respiration partagent
   * l'onglet mais gardent chacun leur section). `synthese` et `tout` n'en portent pas :
   * `report-render.js` les construit à part, l'un à partir des tuiles et du récit du
   * modèle, l'autre en mettant bout à bout le contenu de tous les autres onglets.
   */
  const TABS = [
    { id: 'synthese', label: 'Synthèse' },
    { id: 'sleep', label: 'Sommeil', domain: 'sleep', sections: ['sleep'] },
    { id: 'heart', label: 'Cœur', domain: 'heart', sections: ['heart'] },
    { id: 'activity', label: 'Activité', domain: 'activity', sections: ['activity'] },
    { id: 'body', label: 'Corps', domain: 'body', sections: ['body'] },
    { id: 'vitality', label: 'Vitalité', domain: 'vitality', sections: ['stress', 'breathing'] },
    { id: 'tout', label: 'Tout' },
  ];

  global.HA = global.HA || {};
  global.HA.reportSections = {
    SECTIONS, TABS, CORRELATIONS_CARD, GROUP_ORDER, GROUP_LABELS, el, table,
  };
}(typeof self !== 'undefined' ? self : this));
