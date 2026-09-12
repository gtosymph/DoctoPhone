/**
 * Assemblage du rapport de santé.
 *
 * `renderReport(host, model)` construit toute la page à partir du modèle et de la
 * description des sections. C'est le seul point d'entrée : la version web et la WebView
 * Android appellent la même fonction avec le même modèle, donc elles montrent la même
 * chose.
 *
 * Le rapport se redessine tout seul au changement de thème et au passage d'une
 * disposition large à une disposition étroite, parce que les graphiques choisissent
 * leurs graduations selon la place disponible.
 *
 * Depuis l'ajout des onglets, la page porte deux niveaux de lecture. Une barre
 * d'onglets (motif ARIA `tablist`/`tab`/`tabpanel`) répartit les dix-neuf graphiques par
 * domaine — Synthèse, Sommeil, Cœur, Activité, Corps, Vitalité — pour qu'une visite ne
 * charge pas tout d'un coup. Le dernier onglet, « Tout », remet bout à bout le contenu
 * de tous les autres : c'est la page unique d'avant les onglets, gardée intacte pour qui
 * préfère tout lire d'affilée. Il double donc le travail de dessin des six autres — sur
 * une WebView Android, ce doublement se chiffre en dizaines de millisecondes à chaque
 * redessin (changement de thème, de période) — et personne ne l'ouvre la plupart du
 * temps : il est construit à la demande, à la première activation, jamais avant (voir
 * `renderTabs`). Les six autres panneaux, eux, sont construits d'emblée : l'impression
 * en a besoin, et elle n'ouvre aucun onglet au clic. Basculer d'un panneau à l'autre
 * reste du pur CSS (`hidden`), sans requête réseau — l'export autonome
 * (`export-template.html`) doit fonctionner sans serveur.
 */

(function (global) {
  'use strict';

  const E = global.HA.engine;
  const S = global.HA.reportSections;
  const el = S.el;

  /** Délai de calme avant de redessiner après un redimensionnement. */
  const RESIZE_SETTLE_MS = 180;

  /** Les six sections dessinées dans l'onglet « Tout », dans l'ordre de lecture. */
  const ALL_SECTION_KEYS = ['sleep', 'heart', 'activity', 'body', 'stress', 'breathing'];

  let current = null;

  /** L'onglet actif survit à un redessin (thème, période) : mémorisé en dehors de `current`. */
  let activeTabId = 'synthese';

  /** Le seul `ResizeObserver` de la piste d'onglets, réutilisé d'un redessin à l'autre
   * pour ne pas en accumuler un par appel de `renderTabs` (voir plus bas). */
  let tabFadeObserver = null;

  function sectionIndex(i) {
    return String(i + 1).padStart(2, '0');
  }

  /** L'en-tête : titre, volumétrie, période et avertissement. Commun à tous les onglets. */
  function renderMasthead(host, model) {
    const meta = model.meta;
    const narrative = model.narrative;
    const head = el('header', 'ha-masthead');

    head.appendChild(el('p', 'ha-eyebrow', 'Bilan de santé personnel · données Samsung Health'));
    head.appendChild(el('h1', null, narrative && narrative.headline
      ? narrative.headline
      : 'Votre bilan de santé'));

    const counts = [];
    if (meta.nights) counts.push(meta.nights + ' nuits');
    if (meta.heartRateSamples) counts.push(E.fmtInt(meta.heartRateSamples) + ' mesures cardiaques');
    if (meta.hrvWindows) counts.push(E.fmtInt(meta.hrvWindows) + ' fenêtres de variabilité');
    if (meta.activeDays) counts.push(meta.activeDays + ' journées d\'activité');
    if (counts.length) {
      head.appendChild(el('p', 'ha-sub', 'Analyse de ' + counts.join(', ') + '.'));
    }

    // `periodLabel` et le couple `from`/`to` décrivent la même période : n'en montrer
    // qu'un seul, sinon la date s'écrit deux fois de suite dans l'en-tête.
    const row = el('div', 'ha-meta-row');
    if (meta.periodLabel) {
      row.appendChild(el('span', null, 'Période : ' + meta.periodLabel));
    } else if (meta.from && meta.to) {
      row.appendChild(el('span', null, 'Période : ' + E.fmtDate(meta.from) + ' → ' + E.fmtDate(meta.to)));
    }
    if (meta.profile && meta.profile.heightCm) {
      const parts = [E.fmtInt(meta.profile.heightCm) + ' cm'];
      if (meta.profile.weightKg) parts.push(E.fmtNum(meta.profile.weightKg) + ' kg');
      row.appendChild(el('span', null, parts.join(' · ')));
    }
    row.appendChild(el('span', null, 'Fuseau : ' + meta.timeZone));
    head.appendChild(row);

    head.appendChild(el('p', 'ha-disclaimer',
      'Ce rapport résume des données de montre connectée. Il ne constitue pas un avis médical. '
      + 'Les mesures au poignet restent indicatives. Discutez de tout point signalé avec un médecin.'));

    host.appendChild(head);
  }

  /** Les tuiles de synthèse. */
  function renderTiles(host, model) {
    if (!model.tiles.length) return;
    const wrap = el('div', 'ha-tiles');
    for (const tile of model.tiles) {
      const node = el('div', 'ha-tile');
      node.appendChild(el('span', 'ha-k', tile.label));
      const value = el('span', 'ha-v', tile.value);
      if (tile.unit) {
        const unit = el('small', null, ' ' + tile.unit);
        value.appendChild(unit);
      }
      node.appendChild(value);
      node.appendChild(el('span', 'ha-s', tile.sub));
      const status = global.HA.reportModel.TILE_STATUS.includes(tile.status)
        ? tile.status : 'neutral';
      node.appendChild(el('span', 'ha-pill ' + status, statusLabel(status)));
      wrap.appendChild(node);
    }
    host.appendChild(wrap);
  }

  function statusLabel(status) {
    switch (status) {
      case 'good': return 'Bon';
      case 'warn': return 'À surveiller';
      case 'serious': return 'Insuffisant';
      case 'critical': return 'Préoccupant';
      default: return 'Sans donnée';
    }
  }

  /** Le récit du modèle : le verdict global écrit par le LLM. */
  function renderVerdict(host, model) {
    const narrative = model.narrative;
    if (!narrative || !narrative.verdict) return;
    const verdict = el('div', 'ha-verdict');
    verdict.appendChild(el('p', null, narrative.verdict));
    host.appendChild(verdict);
  }

  /**
   * Le contenu de l'onglet Synthèse : tuiles, récit du modèle, corrélations croisées.
   *
   * Réutilisé tel quel par l'onglet « Tout », qui l'ouvre avant d'enchaîner les six
   * sections de domaine — c'est la même vue qu'aujourd'hui, en une seule page.
   */
  function renderSyntheseContent(host, model) {
    renderTiles(host, model);
    renderVerdict(host, model);
    const grid = el('div', 'ha-grid2');
    grid.appendChild(renderCard(S.CORRELATIONS_CARD, model));
    host.appendChild(grid);
  }



  /*
   * Les blocs de détail à ouvrir avant impression.
   *
   * Un écouteur par bloc poserait une fuite : `renderReport` vide et reconstruit le
   * document à chaque changement de période, de thème ou de disposition, mais un
   * `addEventListener` posé sur `window` survit à la destruction du nœud qu'il capture.
   * Quatre changements de période, et la page traîne quatre générations de blocs détachés.
   *
   * Un seul écouteur, posé une fois, parcourt une liste que chaque rendu réinitialise.
   */
  let printHandlers = [];

  function runPrintHandlers() {
    for (const handler of printHandlers) {
      try {
        handler();
      } catch (error) {
        if (global.console) global.console.error('[rapport] préparation de l\'impression', error);
      }
    }
  }

  if (global.addEventListener) global.addEventListener('beforeprint', runPrintHandlers);
  if (global.matchMedia) {
    const printMedia = global.matchMedia('print');
    if (printMedia.addEventListener) {
      printMedia.addEventListener('change', function (event) { if (event.matches) runPrintHandlers(); });
    }
  }

  /** Combien d'indicateurs passent en gros, en tête de section. */
  /** Durée du dépliage du détail. Doit suivre `.ha-detail-body` dans `report.css`. */
  const DETAIL_ANIMATION_MS = 200;

  const LEAD_KPI_COUNT = 2;

  /** Une rangée d'indicateurs : la valeur en gros, le libellé en dessous. */
  function renderKpiRow(items, className) {
    const wrap = el('div', className);
    for (const k of items) {
      const item = el('div', 'ha-kpi');
      item.appendChild(el('span', 'ha-n', k.value));
      item.appendChild(el('span', 'ha-l', k.label));
      wrap.appendChild(item);
    }
    return wrap;
  }

  /** Une carte : titre, note, puis le graphique ou le contenu propre à la carte. */
  function renderCard(card, model) {
    const node = el('div', 'ha-card' + (card.span ? ' ha-span2' : ''));
    node.appendChild(el('h3', null, card.title));
    if (card.note) node.appendChild(el('p', 'ha-note', card.note));

    if (card.render) {
      card.render(node, model);
      return node;
    }

    const holder = el('div', 'ha-chart');
    holder.dataset.chart = card.chart;
    node.appendChild(holder);
    const draw = global.HA.reportCharts[card.chart];
    if (!draw) {
      E.drawEmpty(holder, 'Graphique inconnu : ' + card.chart);
      return node;
    }
    try {
      draw(holder, model);
    } catch (error) {
      // Un graphique qui échoue ne doit pas emporter le rapport entier.
      E.drawEmpty(holder, 'Ce graphique n\'a pas pu être tracé.');
      if (global.console) global.console.error('[rapport] ' + card.chart, error);
    }
    return node;
  }

  /**
   * Les cartes d'une section, regroupées par cadence (jour par jour, mois par mois,
   * profils, mesures ponctuelles). Un intertitre court précède chaque groupe — mais
   * seulement quand le bloc en compte plus d'un, sinon il ne ferait que répéter le
   * titre de la section qui le précède déjà.
   */
  function renderCardGroups(host, cards, model) {
    const present = S.GROUP_ORDER.filter(g => cards.some(c => (c.group || 'daily') === g));
    const showTitles = present.length > 1;
    for (const group of present) {
      if (showTitles) {
        host.appendChild(el('p', 'ha-group-title', S.GROUP_LABELS[group]));
      }
      const grid = el('div', 'ha-grid2');
      for (const card of cards) {
        if ((card.group || 'daily') === group) grid.appendChild(renderCard(card, model));
      }
      host.appendChild(grid);
    }
  }

  /**
   * Une carte vide-t-elle sa section ? Les tableaux de tension et d'ECG sont vides chez la
   * plupart des gens ; ils partent au détail tant qu'ils ne portent aucune ligne, et
   * remontent tout seuls dès qu'ils en portent une.
   */
  function cardHasContent(card, model) {
    if (!card.hasContent) return true;
    try {
      return card.hasContent(model);
    } catch (error) {
      if (global.console) global.console.error('[rapport] hasContent ' + card.title, error);
      return true;
    }
  }

  /**
   * Les cartes d'une section : l'essentiel visible, le reste replié.
   *
   * Le critère de tri est explicite et tient en une phrase : **une carte est essentielle
   * quand elle montre une série quotidienne sur toute la période**. Les profils — par heure,
   * par jour de semaine — et les agrégats mensuels répondent à une question qu'on se pose
   * ensuite, pas d'abord. Rien ne disparaît : le dépliage rend la section entière.
   *
   * Le bloc replié n'est pas un `<details>`. Le contenu d'un `<details>` fermé ne s'imprime
   * pas, et le rapport imprimé doit être complet — c'est même son usage principal, chez le
   * médecin. Une classe et un bouton permettent de le forcer ouvert à l'impression, ce
   * qu'aucune règle CSS ne sait faire sur un `<details>`.
   */
  function renderCards(host, cards, model) {
    const usable = cards.filter(c => cardHasContent(c, model));
    const essential = usable.filter(c => c.essential);
    const detail = usable.filter(c => !c.essential);

    // Sans carte marquée essentielle, tout reste visible : mieux vaut une section trop
    // longue qu'une section entièrement cachée derrière un bouton.
    if (!essential.length) {
      renderCardGroups(host, usable, model);
      return;
    }

    renderCardGroups(host, essential, model);
    if (!detail.length) return;

    const wrap = el('div', 'ha-detail');
    const body = el('div', 'ha-detail-body');
    body.hidden = true;

    /*
     * Les cartes du détail sont dessinées à la PREMIÈRE ouverture, jamais avant.
     *
     * Un SVG construit dans un conteneur masqué n'a aucune largeur à mesurer : il sortait
     * à zéro pixel, et le bloc s'ouvrait sur trois graphiques invisibles. Appeler `redraw()`
     * après coup ne réglait rien et ajoutait un défaut : `redraw()` reconstruit le rapport
     * entier, donc le bloc se refermait dans la foulée.
     *
     * Le dessin différé règle les deux d'un coup, et allège le chargement : sur un rapport
     * de six sections, une bonne moitié des graphiques n'est jamais tracée si personne ne
     * déplie.
     */
    let drawn = false;
    let isOpen = false;
    const label = 'Voir le détail (' + detail.length + ')';
    const button = el('button', 'ha-detail-toggle', label);
    button.type = 'button';
    button.setAttribute('aria-expanded', 'false');

    /**
     * Anime la hauteur du bloc, puis la relâche.
     *
     * `height: auto` ne se transitionne pas : la hauteur d'arrivée est donc mesurée, posée
     * en pixels, puis rendue à `auto` une fois le mouvement fini. La garder figée
     * déborderait dès qu'un graphique se redessine plus large — au changement d'orientation,
     * par exemple.
     *
     * La lecture de `offsetHeight` force le navigateur à recalculer la mise en page entre
     * les deux valeurs ; sans elle, il ne verrait qu'un seul changement et n'animerait rien.
     */
    let settleTimer = null;

    /**
     * Ouvre ou referme le bloc.
     *
     * **Seul le dépliage s'anime**, comme le demandent les specs. Le repli est immédiat,
     * et c'est un choix : animer la fermeture rendrait l'état asynchrone, donc `hidden`
     * resterait faux pendant deux dixièmes de seconde après un clic sur « Masquer ». Un
     * lecteur d'écran continuerait d'annoncer un contenu que la personne vient de
     * refermer, et le second clic ne serait plus vérifiable d'un coup.
     */
    function setOpen(open) {
      isOpen = open;

      if (!open) {
        if (settleTimer !== null) {
          global.clearTimeout(settleTimer);
          settleTimer = null;
        }
        body.style.height = '';
        body.hidden = true;
        return;
      }

      body.hidden = false;
      // La hauteur d'arrivée se mesure à hauteur libre. La mesurer après avoir posé
      // `0px` rendait zéro — un élément bridé à zéro n'a plus de contenu à mesurer — et
      // le bloc s'ouvrait sur rien du tout.
      body.style.height = '';
      const target = body.scrollHeight;
      body.style.height = '0px';

      /*
       * Le retour de `display: none` et le changement de hauteur doivent tomber dans deux
       * images différentes.
       *
       * Un élément qui vient d'apparaître n'a pas d'état antérieur : le navigateur ne voit
       * qu'un seul changement, de « absent » à « haut de 1452 pixels », et n'anime rien.
       * Une lecture de `offsetHeight` n'y suffit pas — elle force le calcul de la mise en
       * page, pas le passage à l'image suivante.
       *
       * Ce report ne concerne que la hauteur : `hidden` et `aria-expanded` ont déjà changé
       * au-dessus, donc l'état lu par un lecteur d'écran est juste dès le clic.
       */
      global.setTimeout(function () {
        if (!isOpen) return;
        body.style.height = target + 'px';
        armSettle();
      }, 0);
    }

    /**
     * Rend sa hauteur libre au bloc, et le cache s'il est replié.
     *
     * Une hauteur laissée en place fige le bloc : un graphique qui se redessine plus
     * haut, au changement d'orientation par exemple, déborderait sous un couvercle.
     */
    function settle() {
      if (settleTimer !== null) {
        global.clearTimeout(settleTimer);
        settleTimer = null;
      }
      body.style.height = '';
      body.hidden = !isOpen;
    }

    /**
     * Arme la fin du mouvement, avec un filet.
     *
     * `transitionend` ne se déclenche pas dans tous les cas : durée nulle sous
     * `prefers-reduced-motion`, bloc hors du document, transition interrompue. Sans ce
     * filet, la hauteur restait figée et le bloc replié gardait `hidden` à faux —
     * invisible à l'œil, mais toujours présent pour un lecteur d'écran.
     */
    function armSettle() {
      if (settleTimer !== null) global.clearTimeout(settleTimer);
      settleTimer = global.setTimeout(settle, DETAIL_ANIMATION_MS + 60);
    }

    body.addEventListener('transitionend', function (event) {
      if (event.propertyName && event.propertyName !== 'height') return;
      settle();
    });

    button.addEventListener('click', function () {
      const open = !isOpen;
      if (open && !drawn) {
        // Le dessin précède l'ouverture : la hauteur d'arrivée se mesure sur le contenu
        // réel, pas sur un bloc encore vide.
        body.hidden = false;
        renderCardGroups(body, detail, model);
        drawn = true;
      }
      setOpen(open);
      button.setAttribute('aria-expanded', String(open));
      button.textContent = open ? 'Masquer le détail' : label;
    });

    /*
     * L'impression est le seul cas où le bloc doit sortir sans qu'on ait cliqué. La règle
     * CSS le rend visible ; encore faut-il qu'il ait un contenu à rendre. Le bloc s'inscrit
     * dans `printHandlers`, dont un unique écouteur de page se charge — voir son
     * commentaire pour la raison.
     */
    printHandlers.push(function () {
      if (drawn) return;
      renderCardGroups(body, detail, model);
      drawn = true;
    });

    wrap.appendChild(button);
    wrap.appendChild(body);
    host.appendChild(wrap);
  }

  /** Une section : en-tête coloré par domaine, verdict, indicateurs, cartes groupées, points du LLM. */
  function renderSection(host, section, index, model, idPrefix) {
    const node = el('section', 'ha-section');
    node.id = idPrefix + section.key;
    if (section.domain) node.dataset.domain = section.domain;

    const head = el('div', 'ha-sec-head');
    head.appendChild(el('span', 'ha-idx', sectionIndex(index)));
    head.appendChild(el('h2', null, section.title));
    node.appendChild(head);

    const narrative = model.narrative && model.narrative.sections
      ? model.narrative.sections[section.key] : null;
    if (narrative && narrative.verdict) {
      node.appendChild(el('p', 'ha-sec-verdict', narrative.verdict));
    }

    // Deux chiffres de tête, puis les autres en second rang. Les indicateurs sans valeur
    // sont écartés d'abord, donc un chiffre de tête absent laisse le suivant prendre sa
    // place plutôt que de laisser un trou en haut de la section.
    const kpis = (section.kpis ? section.kpis(model) : []).filter(k => k.value !== '—');
    if (kpis.length) {
      const lead = kpis.slice(0, LEAD_KPI_COUNT);
      const rest = kpis.slice(LEAD_KPI_COUNT);
      if (lead.length) node.appendChild(renderKpiRow(lead, 'ha-kpis ha-kpis-lead'));
      if (rest.length) node.appendChild(renderKpiRow(rest, 'ha-kpis'));
    }

    // La phrase de lecture : ce que VOS données montrent, calculé ici, jamais écrit par le
    // LLM et jamais montré au LLM. Voir `report-sections.js`.
    const insight = section.insight ? section.insight(model) : null;
    if (insight) node.appendChild(el('p', 'ha-insight', insight));

    renderCards(node, section.cards, model);

    if (narrative && narrative.points && narrative.points.length) {
      const list = el('ul', 'ha-points');
      for (const point of narrative.points) list.appendChild(el('li', null, point));
      node.appendChild(list);
    }

    host.appendChild(node);
  }

  /**
   * Dessine, dans l'ordre, les sections de domaine disponibles parmi `keys`.
   *
   * `idPrefix` distingue les identifiants DOM d'un onglet à l'autre : la section
   * Sommeil est dessinée à la fois dans l'onglet Sommeil et dans l'onglet Tout, et deux
   * éléments ne peuvent pas partager le même `id`.
   */
  function renderSectionsInto(host, keys, model, idPrefix) {
    let index = 0;
    for (const key of keys) {
      const section = S.SECTIONS.find(s => s.key === key);
      if (!section || !section.available(model)) continue;
      renderSection(host, section, index, model, idPrefix);
      index += 1;
    }
  }

  /** Le plan d'action, écrit par le LLM. Page-level : ni un domaine, ni propre à un onglet. */
  function renderPlan(host, model, index) {
    const plan = model.narrative && model.narrative.plan;
    if (!plan || !plan.length) return;

    const node = el('section', 'ha-section');
    node.id = 'ha-plan';
    const head = el('div', 'ha-sec-head');
    head.appendChild(el('span', 'ha-idx', sectionIndex(index)));
    head.appendChild(el('h2', null, 'Plan d\'action priorisé'));
    node.appendChild(head);

    const wrap = el('div', 'ha-plan');
    plan.forEach((step, i) => {
      const item = el('div', 'ha-step');
      item.appendChild(el('span', 'ha-n', String(i + 1)));
      const body = el('div');
      body.appendChild(el('h4', null, step.title));
      body.appendChild(el('p', null, step.body));
      item.appendChild(body);
      wrap.appendChild(item);
    });
    node.appendChild(wrap);
    host.appendChild(node);
  }

  function renderFooter(host, model) {
    const footer = el('footer', 'ha-footer');
    footer.appendChild(el('p', null,
      'Méthode. Une nuit rassemble toutes les sessions de sommeil rattachées au jour du réveil. '
      + 'La FC de repos est le 5e centile des mesures du jour, sur au moins 20 mesures. '
      + 'La variabilité est la médiane des fenêtres RMSSD de cinq minutes. '
      + 'Les heures locales viennent du décalage enregistré avec chaque mesure. '
      + 'Les corrélations sont des coefficients de Pearson sur les jours appariés. '
      + 'Ce document n\'est pas un avis médical.'));
    footer.appendChild(el('p', null, 'Rapport créé le ' + formatStamp(model.meta.generatedAt) + '.'));
    host.appendChild(footer);
  }

  function formatStamp(iso) {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.getDate() + ' ' + E.MONTHS_FR[d.getMonth()] + ' ' + d.getFullYear()
      + ' à ' + String(d.getHours()).padStart(2, '0') + 'h' + String(d.getMinutes()).padStart(2, '0');
  }

  /** Un onglet porte-t-il assez de données pour mériter d'être montré ? */
  function tabAvailable(tab, model) {
    if (!tab.sections) return true; // Synthèse et Tout sont toujours montrés.
    return tab.sections.some((key) => {
      const section = S.SECTIONS.find(s => s.key === key);
      return section && section.available(model);
    });
  }

  /** Remplit le panneau d'un onglet avec son contenu. */
  function fillTabPanel(panel, tab, model) {
    if (tab.id === 'synthese') {
      renderSyntheseContent(panel, model);
    } else if (tab.id === 'tout') {
      renderSyntheseContent(panel, model);
      renderSectionsInto(panel, ALL_SECTION_KEYS, model, 'ha-tout-');
    } else {
      renderSectionsInto(panel, tab.sections, model, 'ha-' + tab.id + '-');
    }
  }

  /**
   * Construit la barre d'onglets et ses panneaux, motif ARIA `tablist`/`tab`/`tabpanel`.
   *
   * Six des sept panneaux sont dessinés d'un coup ; seule leur visibilité bascule au
   * clic ou à la flèche, via l'attribut `hidden`. Le septième, « Tout », double le
   * contenu des six autres (voir le commentaire d'en-tête) : il ne se construit qu'à sa
   * première activation, via `ensureToutBuilt`. Comme `renderReport` vide et reconstruit
   * toute la page à chaque redessin, rien n'a besoin d'être invalidé à la main — un
   * onglet « Tout » resté ouvert d'un redessin à l'autre se reconstruit tout de suite,
   * pas au prochain clic, parce que `activate()` appelle `ensureToutBuilt` y compris
   * pour restaurer l'onglet mémorisé, plus bas. `@media print` ignore `hidden` : sur les
   * six panneaux toujours construits, l'impression doit tout montrer, pas seulement
   * l'onglet ouvert (voir report.css — « Tout » lui-même en est exclu, doublon inutile).
   */
  function renderTabs(host, model) {
    const tabs = S.TABS.filter(t => tabAvailable(t, model));

    // La piste défile à l'horizontale sur un téléphone (sept onglets n'y tiennent pas
    // sur une ligne) ; le dégradé de bord vit sur l'enveloppe, pas sur la piste elle-même,
    // pour ne jamais rogner l'anneau de focus d'un onglet — voir report.css.
    const tabbarWrap = el('div', 'ha-tabbar-wrap');
    host.appendChild(tabbarWrap);

    const bar = el('div', 'ha-tabbar');
    bar.setAttribute('role', 'tablist');
    bar.setAttribute('aria-label', 'Sections du rapport');
    tabbarWrap.appendChild(bar);

    const panelsWrap = el('div', 'ha-tabpanels');
    host.appendChild(panelsWrap);

    const buttons = [];
    const panels = [];
    let toutBuilt = false;

    for (const tab of tabs) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'ha-tab';
      btn.id = 'ha-tab-' + tab.id;
      btn.setAttribute('role', 'tab');
      btn.setAttribute('aria-controls', 'ha-panel-' + tab.id);
      if (tab.domain) btn.dataset.domain = tab.domain;
      btn.textContent = tab.label;
      bar.appendChild(btn);
      buttons.push(btn);

      const panel = el('section', 'ha-tabpanel');
      panel.id = 'ha-panel-' + tab.id;
      panel.dataset.tab = tab.id;
      panel.setAttribute('role', 'tabpanel');
      panel.setAttribute('aria-labelledby', btn.id);
      panel.tabIndex = 0;
      if (tab.domain) panel.dataset.domain = tab.domain;
      panelsWrap.appendChild(panel);
      panels.push(panel);

      // « Tout » attend sa première activation (voir le commentaire de fonction) ;
      // les six autres sont construits ici, tout de suite.
      if (tab.id !== 'tout') fillTabPanel(panel, tab, model);
    }

    function ensureToutBuilt() {
      if (toutBuilt) return;
      const i = tabs.findIndex(t => t.id === 'tout');
      if (i === -1) return;
      fillTabPanel(panels[i], tabs[i], model);
      toutBuilt = true;
    }

    // Dégradé de bord : visible du côté où il reste quelque chose à faire défiler,
    // jamais du côté où la piste est déjà en butée. `+/- 1` absorbe l'arrondi flottant
    // que les navigateurs introduisent sur `scrollLeft` et `scrollWidth`.
    function updateTabFade() {
      const scrollable = bar.scrollWidth > bar.clientWidth + 1;
      const atStart = bar.scrollLeft <= 1;
      const atEnd = bar.scrollLeft >= bar.scrollWidth - bar.clientWidth - 1;
      tabbarWrap.classList.toggle('ha-tab-fade-l', scrollable && !atStart);
      tabbarWrap.classList.toggle('ha-tab-fade-r', scrollable && !atEnd);
    }

    let fadeRaf = null;
    bar.addEventListener('scroll', () => {
      if (fadeRaf) return;
      fadeRaf = requestAnimationFrame(() => { fadeRaf = null; updateTabFade(); });
    }, { passive: true });

    // Une seule instance vivante : sinon chaque redessin (thème, période) en empilerait
    // une nouvelle sans jamais la détacher.
    if (tabFadeObserver) tabFadeObserver.disconnect();
    if (global.ResizeObserver) {
      tabFadeObserver = new ResizeObserver(updateTabFade);
      tabFadeObserver.observe(bar);
    }

    function activate(id, focusButton) {
      if (id === 'tout') ensureToutBuilt();
      tabs.forEach((tab, i) => {
        const isActive = tab.id === id;
        buttons[i].setAttribute('aria-selected', String(isActive));
        buttons[i].tabIndex = isActive ? 0 : -1;
        panels[i].hidden = !isActive;
        if (isActive) {
          // L'onglet actif reste dans le champ de vision de la piste, même hors écran
          // (téléphone étroit, onglet restauré après un redessin, flèche/Fin qui saute
          // loin) : sans cela, on peut se retrouver sur « Tout » sans le voir.
          // `block: 'nearest'` ne bouge jamais la page verticalement pour si peu.
          buttons[i].scrollIntoView({ block: 'nearest', inline: 'nearest' });
          if (focusButton) buttons[i].focus();
        }
      });
      activeTabId = id;
      updateTabFade();
    }

    // Flèches, Début et Fin : le focus se déplace ET active l'onglet visé
    // (« activation automatique », le motif ARIA le plus courant pour une tablist).
    bar.addEventListener('keydown', (ev) => {
      const from = tabs.findIndex(t => t.id === activeTabId);
      let to = null;
      if (ev.key === 'ArrowRight') to = (from + 1) % tabs.length;
      else if (ev.key === 'ArrowLeft') to = (from - 1 + tabs.length) % tabs.length;
      else if (ev.key === 'Home') to = 0;
      else if (ev.key === 'End') to = tabs.length - 1;
      if (to === null) return;
      ev.preventDefault();
      activate(tabs[to].id, true);
    });

    buttons.forEach((btn, i) => {
      btn.addEventListener('click', () => activate(tabs[i].id, false));
    });

    // L'onglet mémorisé survit à un redessin. S'il n'existe plus dans ce modèle (un
    // domaine sans donnée sur la nouvelle période, par exemple), on retombe sur Synthèse.
    const restored = tabs.some(t => t.id === activeTabId) ? activeTabId : 'synthese';
    activate(restored, false);
  }

  /**
   * Construit le rapport complet dans le conteneur donné.
   *
   * @param {Element} host le conteneur, vidé au passage
   * @param {object} model un modèle conforme à `report-model.js`
   */
  function renderReport(host, model) {
    if (!host) return;
    const problems = global.HA.reportModel.validateReportModel(model);
    if (problems.length) {
      host.textContent = '';
      host.appendChild(el('p', 'ha-empty', 'Le rapport est incomplet : ' + problems.join(', ') + '.'));
      if (global.console) global.console.error('[rapport] modèle invalide', problems);
      return;
    }

    current = { host, model };
    // Les blocs de détail du rendu précédent partent avec le document qu'on efface.
    printHandlers = [];
    host.textContent = '';
    host.classList.add('ha-report');
    E.hideTooltip();

    renderMasthead(host, model);
    renderTabs(host, model);

    const availableCount = S.SECTIONS.filter(s => s.available(model)).length;
    renderPlan(host, model, availableCount);
    renderFooter(host, model);
  }

  /** Redessine le rapport courant, sans recalculer le modèle. */
  function redraw() {
    if (current) renderReport(current.host, current.model);
  }

  let resizeTimer = null;
  let lastCompact = E.isCompact();

  global.addEventListener('resize', () => {
    // Seul le passage d'une disposition à l'autre justifie un redessin complet.
    if (E.isCompact() === lastCompact) return;
    lastCompact = E.isCompact();
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(redraw, RESIZE_SETTLE_MS);
  });

  if (global.matchMedia) {
    global.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', redraw);
  }
  new MutationObserver(redraw).observe(document.documentElement, {
    attributes: true, attributeFilter: ['data-theme'],
  });

  global.HA = global.HA || {};
  global.HA.report = { renderReport, redraw };
}(typeof self !== 'undefined' ? self : this));
