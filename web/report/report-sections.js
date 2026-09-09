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
          kpi(clock(k.bedMedian), 'coucher médian'),
          kpi(clock(k.wakeMedian), 'réveil médian'),
          kpi(k.bedSpreadHours === null ? '—' : '±' + hours(k.bedSpreadHours), 'irrégularité du coucher'),
          kpi(pct(k.pctUnder6h), 'de nuits sous 6h'),
          kpi(k.debtHours === null ? '—' : int(k.debtHours) + ' h', 'dette cumulée'),
        ];
      },
      cards: [
        {
          chart: 'sleep-nightly', span: true, group: 'daily',
          title: 'Durée de sommeil, nuit par nuit',
          note: 'Chaque point est une nuit. La ligne est la moyenne glissante sur 14 nuits. '
            + 'La bande marque la zone cible de 7 à 9 heures.',
        },
        {
          chart: 'sleep-bedtime', span: true, group: 'daily',
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
          kpi(k.restingP10 === null ? '—' : int(k.restingP10) + '–' + int(k.restingP90), 'p10 à p90'),
          kpi(int(k.averageMean) + ' bpm', 'FC moyenne'),
          kpi(int(k.hrvMedian) + ' ms', 'RMSSD médian'),
          kpi(int(k.maxObserved) + ' bpm', 'FC maximale observée'),
          kpi(int(k.measuredDays), 'jours mesurés'),
        ];
      },
      cards: [
        {
          chart: 'heart-monthly', group: 'monthly',
          title: 'FC de repos et FC moyenne, par mois',
          note: 'La FC de repos est le 5e centile des mesures du jour, sur au moins 20 mesures.',
        },
        {
          chart: 'heart-hrv', group: 'monthly',
          title: 'Variabilité cardiaque (RMSSD), par mois',
          note: 'Médiane mensuelle des fenêtres de cinq minutes.',
        },
        {
          chart: 'heart-hourly', span: true, group: 'profile',
          title: 'Fréquence cardiaque au fil de la journée',
          note: 'Moyenne par heure locale, sur toute la période.',
        },
        { render: renderBloodPressure, title: 'Tension artérielle', group: 'measures' },
        { render: renderEcg, title: 'Électrocardiogrammes', group: 'measures' },
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
          kpi(int(k.meanSteps), 'pas par jour, moyenne'),
          kpi(int(k.meanSteps30), 'sur les 30 derniers jours'),
          kpi(int(k.meanSteps90), 'sur les 90 derniers jours'),
          kpi(int(k.bestSteps), 'meilleure journée'),
          kpi(pct(k.pctDaysOver8000), 'de jours au-dessus de 8 000'),
          kpi(int(k.exerciseSessions), 'séances enregistrées'),
        ];
      },
      cards: [
        {
          chart: 'activity-steps', span: true, group: 'daily',
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
      cards: [
        {
          chart: 'body-weight', span: true, group: 'daily',
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
          kpi(pct(k.percentAbove60), 'du temps au-dessus de 60'),
          kpi(int(k.alerts), 'alertes de stress'),
          kpi(k.peakHour === null ? '—' : k.peakHour + 'h', 'heure la plus tendue'),
          kpi(int(k.vitalityMean) + '/100', 'vitalité moyenne'),
          kpi(int(k.measuredDays), 'jours mesurés'),
        ];
      },
      cards: [
        {
          chart: 'stress-vitality', span: true, group: 'daily',
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
          kpi(int(k.spo2Measures), 'mesures d\'oxygénation'),
          kpi(int(k.spo2Under90), 'mesures sous 90 %'),
          kpi(num(k.respiratoryMean) + '/min', 'fréquence respiratoire'),
          kpi(num(k.skinTempMean) + ' °C', 'température cutanée'),
          kpi(k.skinTempStdDev === null ? '—' : '±' + num(k.skinTempStdDev) + ' °C', 'écart-type'),
        ];
      },
      cards: [
        {
          chart: 'breathing-skintemp', span: true, group: 'daily',
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
