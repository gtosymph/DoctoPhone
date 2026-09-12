/**
 * La synthèse remise au médecin : deux à trois pages, pas vingt.
 *
 * Elle vit à côté du rapport complet et ne le remplace pas. Un médecin accorde quelques
 * minutes ; le rapport complet en demande beaucoup plus. L'utilisateur choisit au moment
 * d'exporter.
 *
 * ## Ce qui n'y entre jamais
 *
 * **Aucun texte écrit par un modèle de langage.** Le bilan rédigé reste dans le rapport
 * complet, où l'utilisateur sait d'où il vient et peut le relire. Un document remis à un
 * médecin ne porte que des chiffres mesurés et des phrases calculées sur l'appareil.
 *
 * ## Le bloc hebdomadaire vient de Kotlin
 *
 * Les dérives sont calculées côté application (`domain/drift`), et ce fichier n'en refait
 * aucun calcul : il reçoit des chaînes déjà formatées (voir `SummaryReview.kt`) et se
 * contente de les poser. Deux implémentations d'une même logique de santé finiraient par
 * diverger, et c'est le document médical qui en paierait le prix.
 *
 * Conséquence : la version web, qui n'a pas de moteur de dérives, passe `null` et la
 * synthèse s'ouvre directement sur la période. Le reste du document est identique.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;
  const S = global.HA.reportSections;
  const el = S.el;

  /** Les deux graphiques de la première page : le sommeil et l'activité, jour par jour. */
  const PAGE_ONE_CHARTS = [
    { chart: 'sleep-nightly', title: 'Durée de sommeil, nuit par nuit' },
    { chart: 'activity-steps', title: 'Pas quotidiens, moyenne glissante sur 7 jours' },
  ];

  /** Les graphiques de la seconde page, dans l'ordre où un médecin les parcourt. */
  const PAGE_TWO_CHARTS = [
    { chart: 'heart-monthly', title: 'Fréquence cardiaque de repos et moyenne, par mois' },
    { chart: 'body-weight', title: 'Poids et masse musculaire' },
  ];

  function drawChart(host, key, title) {
    const card = el('div', 'ha-sum-card');
    card.appendChild(el('h3', null, title));
    const holder = el('div', 'ha-chart');
    card.appendChild(holder);
    const draw = global.HA.reportCharts[key];
    if (!draw) {
      E.drawEmpty(holder, 'Graphique indisponible.');
      return card;
    }
    try {
      draw(holder, host.__model);
    } catch (error) {
      E.drawEmpty(holder, 'Ce graphique n\'a pas pu être tracé.');
      if (global.console) global.console.error('[synthèse] ' + key, error);
    }
    return card;
  }

  /** Un tableau simple : en-têtes, puis des lignes de cellules déjà mises en forme. */
  function table(headers, rows, className) {
    const wrap = el('div', 'ha-table-wrap');
    const t = el('table', className || 'ha-mini');
    const head = document.createElement('tr');
    for (const h of headers) head.appendChild(el('th', null, h));
    t.appendChild(head);
    for (const row of rows) {
      const tr = document.createElement('tr');
      for (const cell of row) {
        if (cell && cell.node) tr.appendChild(cell.node);
        else tr.appendChild(el('td', null, cell));
      }
      t.appendChild(tr);
    }
    wrap.appendChild(t);
    return wrap;
  }

  /** L'en-tête du document : ce qu'il couvre, d'où viennent les chiffres, quand il a été édité. */
  function renderHeader(host, model) {
    const head = el('header', 'ha-sum-head');
    head.appendChild(el('p', 'ha-eyebrow', 'Synthèse de santé · données de montre connectée'));
    head.appendChild(el('h1', null, 'Synthèse de santé'));

    const meta = el('div', 'ha-sum-meta');
    const m = model.meta || {};
    if (m.periodLabel) meta.appendChild(el('span', null, 'Période : ' + m.periodLabel));
    if (m.days) meta.appendChild(el('span', null, m.days + ' jours couverts'));
    if (m.nights) meta.appendChild(el('span', null, m.nights + ' nuits mesurées'));
    if (m.timeZone) meta.appendChild(el('span', null, 'Fuseau : ' + m.timeZone));
    meta.appendChild(el('span', null, 'Édité le ' + formatStamp(m.generatedAt)));
    head.appendChild(meta);
    head.appendChild(el('p', 'ha-sum-warning',
      'Mesures issues d\'une montre connectée. Les mesures au poignet restent indicatives.'));

    host.appendChild(head);
  }

  function formatStamp(iso) {
    const date = iso ? new Date(iso) : new Date();
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  /**
   * La semaine écoulée : les six mesures contre la référence, puis ce qui a bougé.
   *
   * Omis quand l'appelant ne fournit rien — la version web n'a pas de moteur de dérives.
   * Un bloc absent vaut mieux qu'un bloc vide dont le médecin se demanderait ce qu'il
   * signifie.
   */
  function renderWeek(host, review) {
    if (!review) return;

    const section = el('section', 'ha-sum-section');
    section.appendChild(el('h2', null, 'La semaine écoulée'));
    section.appendChild(el('p', 'ha-sum-lead', review.periodLabel));

    const rows = (review.metrics || []).map(metric => {
      const first = el('td', null, metric.label);
      if (metric.note) first.appendChild(el('small', 'ha-sum-note', metric.note));
      return [{ node: first }, metric.recent, metric.baseline, metric.delta || '—'];
    });
    section.appendChild(table(['Mesure', 'Semaine', 'Référence', 'Écart'], rows, 'ha-mini ha-sum-table'));

    const drifts = review.drifts || [];
    if (drifts.length) {
      section.appendChild(el('h3', null, 'Ce qui a changé'));
      const list = el('ul', 'ha-sum-list');
      for (const phrase of drifts) list.appendChild(el('li', null, phrase));
      section.appendChild(list);
    } else {
      section.appendChild(el('p', 'ha-sum-quiet', readinessSentence(review)));
    }

    host.appendChild(section);
  }

  /**
   * Pourquoi le bloc « ce qui a changé » est vide.
   *
   * « Aucune dérive » et « pas assez de données » se ressemblent sur le papier et ne
   * veulent pas du tout dire la même chose. Un médecin qui lit « rien n'a bougé » alors
   * que l'app n'a rien pu comparer serait trompé par le document.
   */
  function readinessSentence(review) {
    if (review.readinessKind === 'baseline') {
      return 'Comparaison impossible : ' + review.readinessRequiredDays
        + ' jours de référence sont nécessaires, ' + review.readinessMeasuredDays + ' sont mesurés.';
    }
    if (review.readinessKind === 'recent') {
      return 'Comparaison impossible : ' + review.readinessRequiredDays
        + ' jours mesurés sont nécessaires sur la semaine, ' + review.readinessMeasuredDays + ' le sont.';
    }
    return 'Aucun écart durable par rapport aux huit semaines précédentes.';
  }

  /**
   * Les constats de la période : deux chiffres par domaine, et la phrase de lecture.
   *
   * Les mêmes phrases que le rapport complet, calculées depuis `ReportModel`. Elles sont
   * ici la seule prose du document, et c'est voulu : elles décrivent, elles n'interprètent
   * pas.
   */
  function renderDomains(host, model, keys, title) {
    const sections = S.SECTIONS.filter(s => keys.indexOf(s.key) >= 0 && s.available(model));
    if (!sections.length) return;

    const block = el('section', 'ha-sum-section');
    block.appendChild(el('h2', null, title));

    for (const section of sections) {
      const row = el('div', 'ha-sum-domain');
      if (section.domain) row.dataset.domain = section.domain;
      row.appendChild(el('h3', null, section.title));

      const kpis = (section.kpis ? section.kpis(model) : []).filter(k => k.value !== '—').slice(0, 4);
      if (kpis.length) {
        const line = el('span', 'ha-sum-figures');
        line.textContent = kpis.map(k => k.value + ' ' + k.label).join(' · ');
        row.appendChild(line);
      }

      const insight = section.insight ? section.insight(model) : null;
      if (insight) row.appendChild(el('p', 'ha-sum-insight', insight));

      block.appendChild(row);
    }

    host.appendChild(block);
  }

  /** Le tableau de tension, seulement s'il porte des lignes. */
  function renderBloodPressure(host, model) {
    const rows = model.heart.bloodPressure || [];
    if (!rows.length) return;
    const section = el('section', 'ha-sum-section');
    section.appendChild(el('h2', null, 'Tension artérielle'));
    section.appendChild(table(
      ['Date', 'Systolique', 'Diastolique', 'Pouls'],
      rows.map(r => [r.date, r.systolic, r.diastolic, r.pulse === null ? '—' : r.pulse]),
    ));
    host.appendChild(section);
  }

  /** Les électrocardiogrammes, seulement s'il y en a. */
  function renderEcg(host, model) {
    const rows = model.heart.ecg || [];
    if (!rows.length) return;
    const section = el('section', 'ha-sum-section');
    section.appendChild(el('h2', null, 'Électrocardiogrammes'));
    section.appendChild(table(
      ['Date', 'FC moyenne', 'Résultat'],
      rows.map(r => [r.date, r.meanHeartRate === null ? '—' : r.meanHeartRate + ' bpm', r.classificationLabel || '—']),
    ));
    host.appendChild(section);
  }

  /**
   * Le pied de page, une fois, à la fin.
   *
   * L'avertissement sur la nature des données est remonté dans l'en-tête : c'est là qu'on
   * le lit, et un document imprimé se lit de haut en bas. Le répéter sur chaque page
   * demanderait un `position: fixed` en impression, dont le rendu varie d'un navigateur à
   * l'autre et grignote la hauteur utile de toutes les pages pour un gain douteux.
   */
  function renderFooter(host) {
    const footer = el('footer', 'ha-sum-foot');
    footer.appendChild(el('p', null, 'Cette synthèse ne remplace pas un avis médical.'));
    host.appendChild(footer);
  }

  /**
   * Dessine la synthèse entière dans `root`.
   *
   * @param {HTMLElement} root
   * @param {object} model un `ReportModel` conforme à `report-model.js`
   * @param {object|null} review le bloc hebdomadaire déjà formaté, ou `null`
   */
  function renderSummary(root, model, review) {
    if (!root) return;
    const problems = global.HA.reportModel.validateReportModel(model);
    if (problems.length) {
      root.textContent = '';
      root.appendChild(el('p', 'ha-empty', 'La synthèse est incomplète : ' + problems.join(', ') + '.'));
      if (global.console) global.console.error('[synthèse] modèle invalide', problems);
      return;
    }

    root.textContent = '';
    root.classList.add('ha-report', 'ha-summary');
    root.__model = model;

    /*
     * Le document coule, il ne force aucune coupure de page.
     *
     * La première version imposait exactement deux pages. Mesuré : une synthèse qui porte
     * des prises de tension et des ECG déborde, et la coupure forcée produisait alors
     * quatre pages au lieu de deux — une demi-page vide suivie d'un reste. Laisser le
     * contenu couler, en protégeant chaque bloc d'une coupure interne, donne deux pages
     * quand il n'y a rien de ponctuel à montrer et trois quand il y en a. C'est la vérité
     * du document plutôt qu'une promesse que son contenu ne peut pas tenir.
     */
    renderHeader(root, model);
    renderWeek(root, review);
    for (const spec of PAGE_ONE_CHARTS) root.appendChild(drawChart(root, spec.chart, spec.title));
    renderDomains(root, model, ['sleep', 'heart', 'activity'], 'Sur la période');
    for (const spec of PAGE_TWO_CHARTS) root.appendChild(drawChart(root, spec.chart, spec.title));
    renderDomains(root, model, ['body', 'stress', 'breathing'], 'Corps et vitalité');
    renderBloodPressure(root, model);
    renderEcg(root, model);
    renderFooter(root);
  }

  global.HA = global.HA || {};
  global.HA.reportSummary = { renderSummary };
}(typeof self !== 'undefined' ? self : this));
