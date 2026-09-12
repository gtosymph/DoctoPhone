# Refonte de l'expérience — specs

Document d'arbitrage. Il fixe les décisions de conception avant tout code, pour les deux
versions : Android (Kotlin, Compose) et web (JavaScript vanille).

Statut : **à arbitrer**. Rien n'est implémenté.
Date : 11 septembre 2026.

---

## 0. Les décisions déjà prises

Ces douze points sont arbitrés. Le reste du document les développe.

| # | Décision |
|---|---|
| 1 | Une seule palette pour les deux versions : la palette éditoriale de `report.css`. |
| 2 | La coquille Compose abandonne Material You et la couleur dynamique. |
| 3 | Direction esthétique : éditorial imprimé. |
| 4 | Une serif pour les titres, une sans pour le texte et les chiffres. |
| 5 | Android passe de quatre onglets à trois : Cette semaine, Rapport, Analyse. |
| 6 | L'import descend dans les Réglages. Les Réglages passent sous une icône d'en-tête. |
| 7 | Un écran d'accueil natif « Cette semaine », en Compose, pas en WebView. |
| 8 | Le rapport garde ses 19 graphiques, mais les hiérarchise : l'essentiel visible, le détail replié. |
| 9 | Chaque graphique gagne une phrase de lecture **calculée**, jamais écrite par le LLM. |
| 10 | Un nouvel export « synthèse médecin » de deux à trois pages A4, à côté du rapport complet. |
| 11 | Le moteur de graphiques SVG est repris en profondeur, sans aucune bibliothèque ajoutée. |
| 12 | Livraison en cinq phases vérifiables une par une. |

---

## 1. Le constat

Huit défauts, tous vérifiés dans le code.

### 1.1 Deux systèmes de design cohabitent dans le même écran

Le rapport partagé (`web/report/report.css`) suit une direction « éditorial coloré » :
papier `#f9f9f7`, encre `#0b0b0b`, accent violet `#4a3aa7`, une couleur par domaine.

La coquille Android (`ui/theme/Theme.kt`) suit Material 3 avec `dynamicLightColorScheme`,
et un repli vert santé `#1B6B4A`. L'utilisateur passe donc d'une barre verte — ou de la
couleur de son fond d'écran — à un rapport violet sur papier, dans le même écran.

Le travail est à moitié fait sans qu'on l'ait remarqué : `ui/theme/DomainColors.kt`
recopie déjà **à l'identique** les cinq couleurs de domaine du CSS, avec leurs trois
variantes et leurs valeurs sombres. Ce qui diverge, c'est uniquement le `ColorScheme`
Material : fond, surface, encre, accent.

### 1.2 Aucun écran ne répond à « comment je vais »

`HealthAnalyzerNavHost.kt` fait partir l'app sur la route `HOME`, et cette route ouvre
`ReportScreen` : une WebView qui charge 19 graphiques et 36 indicateurs. L'app ouvre donc
sur un document, jamais sur une réponse.

Le moteur de dérives existe pourtant déjà, complet et testé (`domain/drift/`). Il ne se
montre aujourd'hui que dans une section du rapport.

### 1.3 L'import occupe un quart de la barre de navigation, à vie

Un utilisateur importe son export Samsung Health une fois, peut-être deux. Health Connect
se synchronise ensuite tout seul, par tâche de fond quotidienne. L'onglet reste malgré
tout, aussi visible que le rapport.

### 1.4 Tout a le même poids visuel

`report-sections.js` déclare six sections. Chacune porte exactement six indicateurs et
entre un et cinq graphiques, tous dessinés dans la même carte, avec le même cadre, le même
rayon d'angle et le même titre. Soit 36 nombres et 19 graphiques, sans qu'aucune
hiérarchie ne dise où regarder d'abord.

### 1.5 Aucune identité typographique

`ui/theme/Type.kt` déclare `FontFamily.Default` sur les huit styles. `web/styles.css`
déclare `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`. Le dossier
`app/src/main/res/font` n'existe pas. L'app porte donc la typographie du système, c'est-à-dire
celle de toutes les autres applications de l'appareil.

### 1.6 Les temps d'attente ne montrent presque rien

Le rapport montre « Calcul du rapport… ». L'analyse montre un état en ligne. L'import
montre une barre. Aucun de ces états ne dit combien de temps il reste, ni ce qui se passe.

### 1.7 Trois couleurs de texte sous le seuil de lisibilité

Mesuré, pas supposé. `--warning`, `--serious` et `--critical` servent de **couleur de
texte** (`.ha-pill`, `.report-inline-status`) et n'étaient redéfinis dans aucun des deux
blocs sombres : ils héritaient donc de valeurs calculées pour du papier.

| Jeton | Thème | Contraste mesuré | Seuil |
|---|---|---|---|
| `--warning` | clair | **1,74:1** | 4,5:1 |
| `--serious` | clair | **2,50:1** | 4,5:1 |
| `--critical` | sombre | **3,62:1** | 4,5:1 |
| `--muted` | clair | **3,41:1** | 4,5:1 |

### 1.8 Deux incohérences mineures, à corriger au passage

- `ui/home/TimeRange.kt` propose 7, 30, 90 et 365 jours. La version web propose en plus
  « Tout l'historique ». Les deux doivent proposer la même chose.
- `web/index.html` numérote ses vues `01 Import`, `02 Rapport`, `03 Analyse`,
  `04 Réglages`. Une numérotation annonce une séquence. Les Réglages n'en font pas partie.

---

## 2. La direction

**Éditorial imprimé.** Cinq principes, qui tranchent les arbitrages à venir.

**Le papier d'abord.** Le fond est un papier cassé, pas un blanc d'écran. Les séparations
se font au filet fin et au blanc tournant, pas à la carte ombrée. Une carte encadrée
signifie « objet détachable » : elle se mérite.

**La couleur est un index, pas une décoration.** Les cinq couleurs de domaine servent à
retrouver un domaine d'un coup d'œil. Elles ne se mélangent jamais aux couleurs
sémantiques (bon, attention, critique), et elles ne portent jamais seules une information —
c'est déjà la règle de `report.css`, elle s'étend à toute l'app.

**Le chiffre est un objet typographique.** Une mesure de santé se lit, se compare et se
recopie chez le médecin. Chiffres tabulaires partout, virgule décimale française, espace
fine pour les milliers, unité toujours présente et toujours plus petite que le nombre.

**La hiérarchie vient du corps et du blanc.** Pas d'un cadre de plus. Sur un écran qui
porte six mesures, deux dominent et quatre suivent.

**L'app se tait quand elle ne sait pas.** Le moteur de dérives applique déjà cette règle :
sous 21 jours de référence, il ne dit rien plutôt que d'inventer. L'interface doit en faire
autant, et surtout **dire pourquoi elle se tait**.

---

## 3. Le système visuel

### 3.1 Les couleurs

`web/report/report.css` reste la **source unique**. Aucune valeur nouvelle n'est créée.

`ui/theme/Color.kt` est réécrit pour porter les mêmes valeurs, et `Theme.kt` supprime
`dynamicLightColorScheme` / `dynamicDarkColorScheme`. Correspondance à respecter :

| Rôle Material | Jeton CSS | Clair | Sombre |
|---|---|---|---|
| `background`, `surface` | `--page` | `#f9f9f7` | `#0d0d0d` |
| `surfaceContainer` | `--surface` | `#fcfcfb` | `#1a1a19` |
| `onBackground`, `onSurface` | `--ink` | `#0b0b0b` | `#ffffff` |
| `onSurfaceVariant` | `--ink-2` | `#52514e` | `#c3c2b7` |
| `outlineVariant` | `--grid` | `#e1e0d9` | `#2c2c2a` |
| `outline` | `--axis` | `#c3c2b7` | `#383835` |
| `primary` | `--accent` | `#4a3aa7` | `#9085e9` |
| `onPrimary` | `--accent-contrast` | `#ffffff` | `#14130f` |
| `error` | `--critical` | `#d03b3b` | `#e66767` |

`--accent-contrast` mérite son existence : en thème sombre l'accent devient un lavande
clair, sur lequel du blanc tombe à 3,13:1, sous le seuil de 4,5:1. L'encre `#14130f` y
remonte à 5,95:1. Compose doit lire la même règle que le CSS.

`DomainColors.kt` ne change pas. Son commentaire d'en-tête décrit déjà le doublon et sa
règle de synchronisation ; il devient la référence de la méthode.

**Un test de parité doit être ajouté** : il lit `report.css`, en extrait les jetons, et
vérifie qu'ils correspondent aux valeurs de `Color.kt`. Le doublon est assumé ; sa dérive
silencieuse ne l'est pas.

### 3.2 La typographie

| Rôle | Police | Graisses | Usage |
|---|---|---|---|
| Titres | **Source Serif 4** | 400, 600 | Titres d'écran, titres de section, gros chiffres de tête |
| Texte et chiffres | **Public Sans** | 400, 600 | Corps, libellés, indicateurs, axes de graphiques |

Pourquoi ces deux-là. Source Serif 4 est dessinée par Adobe pour la lecture longue à
l'écran comme sur papier ; elle donne le grain « document » sans le maniérisme d'une serif
de magazine. Public Sans descend de Libre Franklin et sert les documents publics
américains : elle porte de vrais chiffres tabulaires, ce dont cette app a besoin à chaque
écran. Les deux sont sous licence SIL OFL, donc distribuables dans l'APK. Aucune des deux
n'est la police par défaut vers laquelle tout le monde glisse.

**Le coût est plus élevé ici que sur un site ordinaire, pour deux raisons propres à ce
projet.** La WebView Android n'a aucun accès réseau, et le rapport exporté doit s'ouvrir
sans réseau ni fichier joint. Une police distante est donc impossible dans les deux cas.

Conséquences, à tenir :

1. **Web et WebView** : les fichiers vivent dans `web/report/fonts/`, appelés par
   `@font-face` en chemin relatif. Aucun changement de build n'est nécessaire : la tâche
   Gradle `Sync` de `app/build.gradle.kts` copie déjà `web/report/` en entier vers
   `app/src/main/assets/`. **74 Ko** pour les quatre fontes en woff2.
2. **Android natif** : les mêmes familles en `.ttf` dans `app/src/main/res/font`.
   **194 Ko** au total dans l'APK.
3. **Rapport exporté** : les polices doivent entrer en `data:` base64 dans le fichier, sinon
   il retombe sur la police système. **Seule la serif en 600 est embarquée**, soit
   **31 Ko** ; le texte courant utilise une pile système. Le titre porte l'identité, le
   corps n'en a pas besoin sur papier.

**Des instances statiques, pas les fontes variables.** Mesuré : deux graisses de chaque
famille pèsent 74 Ko en woff2, contre 162 Ko pour les deux fontes variables. L'axe optique
de Source Serif 4 est figé à 20, ce qui couvre la plage de corps réellement utilisée.
Corollaire : chaque famille n'existe qu'en 400 et 600, et demander une graisse
intermédiaire ferait synthétiser des contours épaissis, visiblement plus sales. Un test le
vérifie sur la feuille de style.

Une mesure a confirmé le choix d'embarquer la seule graisse 600 dans l'export : sur le
rapport rendu, la serif 400 reste à l'état `unloaded`, aucun élément ne la demande.

Échelle typographique, commune aux deux versions :

| Rôle | Corps | Interligne | Police |
|---|---|---|---|
| Titre d'écran | 30 px / 30 sp | 1,15 | Serif 600 |
| Titre de section | 22 px / 22 sp | 1,2 | Serif 600 |
| Chiffre de tête | 40 px / 40 sp | 1,0 | Sans 600, tabulaire |
| Chiffre secondaire | 20 px / 20 sp | 1,1 | Sans 600, tabulaire |
| Corps | 16 px / 16 sp | 1,55 | Sans 400 |
| Note, légende | 13 px / 13 sp | 1,45 | Sans 400 |
| Étiquette capitale | 11 px / 11 sp | 1,3 | Sans 600, interlettrage +0,08 em |

### 3.3 La mise en page

- Une colonne sous 720 px, deux au-dessus. Règle déjà en place dans `report.css`, conservée.
- Largeur maximale du texte courant : 65 caractères.
- Rythme vertical sur une base de 8 px. Les écarts entre blocs frères viennent d'un `gap`
  de conteneur, jamais d'une marge posée sur chaque enfant.
- Tout contenu large — tableau, graphique, bloc de code — défile dans son propre conteneur.
  Le corps de page ne défile jamais à l'horizontale.

---

## 4. L'écran « Cette semaine »

Nouveau, en Compose natif. Il devient l'écran de départ. Il répond en trois secondes ;
le rapport répond en trois minutes.

### 4.1 Pourquoi hebdomadaire et pas quotidien

L'usage déclaré est hebdomadaire, plus une préparation de rendez-vous médical. Un écran
« Aujourd'hui » montrant la nuit passée ne servirait donc presque jamais. La fenêtre de
sept jours est en outre exactement celle du moteur de dérives
(`DriftDetector.RECENT_WINDOW_DAYS = 7`) : l'écran réutilise un calcul existant au lieu
d'en créer un.

### 4.2 Contenu, de haut en bas

**En-tête.** « Cette semaine » en serif, et dessous la période réelle en toutes lettres :
« du 4 au 10 septembre ». Une icône de réglages à droite.

**Bloc 1 — Ce qui a changé.** La liste des `MetricDrift` détectés, phrasés par
`DriftNarrator`, chacun avec sa pastille de domaine. C'est le bloc le plus haut parce que
c'est la seule information que l'utilisateur ne peut pas obtenir en regardant sa montre.

Trois états, et il faut les distinguer :

| Situation | Ce que l'écran dit |
|---|---|
| Des dérives | La liste, une phrase par dérive. |
| Aucune dérive, données suffisantes | « Rien n'a bougé cette semaine par rapport à vos huit dernières semaines. » |
| Données insuffisantes | « Il faut 21 jours de référence pour comparer. Vous en avez 12. » |

Le troisième cas est le plus important, et c'est celui qui manque aujourd'hui. Un écran
vide sans explication se lit comme une panne.

**Bloc 2 — Les six mesures.** Une par ligne, pas en grille de cartes. Pour chacune :

- le libellé (`DriftMetric.label`, déjà écrit) ;
- la valeur de la semaine, en chiffre de tête, formatée par `DriftMetric.format` ;
- la référence des huit semaines précédentes, en petit, juste dessous ;
- l'écart, signé, en couleur sémantique — jamais en couleur de domaine ;
- une courbe des sept derniers jours, fine, sans axe ni grille, avec le dernier point marqué.

Les six mesures sont déjà définies : durée de sommeil, régularité du coucher, fréquence
cardiaque de repos, variabilité cardiaque, pas quotidiens, poids.

Une mesure sans donnée reste affichée, avec un tiret cadratin et la raison en petit
(« aucune pesée depuis 34 jours »). L'enlever donnerait l'impression qu'elle n'existe pas.

**Bloc 3 — Préparer un rendez-vous.** Un bloc encadré, le seul de l'écran. Deux boutons :
« Créer la synthèse » et « Rapport complet ». Il est encadré parce que c'est le seul
endroit de l'écran qui produit un fichier.

**Bloc 4 — État des données.** En bas, en petit : date de la dernière synchronisation,
nombre de jours couverts, et les avertissements de permission le cas échéant. Un bouton
« Synchroniser » discret.

**Bandeau de premier usage.** Tant qu'aucune donnée n'existe, tout ce qui précède
disparaît au profit d'un bloc unique qui mène à l'import. C'est le seul chemin vers
l'import depuis l'accueil, et c'est suffisant.

### 4.3 D'où viennent les chiffres

Aucun calcul nouveau n'est nécessaire. Tout existe :

- `DetectHealthDriftsUseCase` rend déjà, pour les six mesures, la référence et la semaine
  récente séparées. C'est le chemin que suivent aussi `DriftViewModel` et
  `WeeklyDriftCheckWorker` : un seul calcul, trois consommateurs.
- `HealthRepository.buildReport(range, zone)` rend les séries quotidiennes à filtrer sur
  sept jours : `sleep.nightly`, `heart.restingDaily`, `heart.hrvDaily`,
  `activity.stepsDaily`, `body.daily`.
- `activity.stepsRolling7` porte déjà une moyenne glissante sur sept jours, prête pour la
  courbe miniature.

Le poids mérite une note : `HealthDriftAnalyzer` ne le lit pas via `HealthAggregator`,
qui reporte la dernière pesée sur les jours sans mesure et fausserait la moyenne. Seuls
les jours de pesée réels comptent. L'écran doit suivre la même règle.

### 4.4 Ce que cet écran ne fait pas

Il ne porte aucun graphique du rapport, aucun onglet, aucun sélecteur de période. Sa
fenêtre est fixe : sept jours contre huit semaines. Un sélecteur de période sur cet écran
le transformerait en deuxième rapport.

---

## 5. Le rapport

### 5.1 L'inventaire réel

**19 graphiques SVG et 2 tableaux**, en sept onglets. Le chiffre du `CLAUDE.md` est juste.

| Onglet | Contenu |
|---|---|
| Synthèse | Tuiles d'état, verdict rédigé par le LLM, et `heart-correlations` |
| Sommeil | `sleep-nightly`, `sleep-bedtime`, `sleep-stages`, `sleep-distribution`, `sleep-dow` |
| Cœur | `heart-monthly`, `heart-hrv`, `heart-hourly`, + tableaux tension et ECG |
| Activité | `activity-steps`, `activity-exercise`, `activity-floors`, `activity-steps-dow` |
| Corps | `body-weight` |
| Vitalité | `stress-vitality`, `stress-monthly`, `stress-hourly`, `breathing-skintemp`, `breathing-spo2` |
| Tout | Répète tout le reste ; construit à la première activation, caché à l'impression |

Le dix-neuvième graphique est `heart-correlations`, « Les liens mesurés sur vos propres
données ». Il se déclare à part, dans `CORRELATIONS_CARD`, hors du tableau `SECTIONS`, et
il s'affiche dans l'onglet Synthèse. Un inventaire qui ne lit que `SECTIONS` le manque.

**L'onglet Synthèse est la page de garde du rapport**, et il n'est pas traité dans ce
document au même niveau que les domaines. Il porte les tuiles d'état, le verdict rédigé,
la carte de corrélations, et le plan d'action en bas de page. Il est aussi le seul endroit
où le texte du LLM arrive en premier. Sa reprise appartient à la phase 3.

### 5.2 La hiérarchie

Chaque onglet de domaine ouvre sur son **essentiel**. Le reste se déplie sous un bouton
« Voir le détail ». Rien ne disparaît.

| Onglet | Essentiel, visible | Détail, replié |
|---|---|---|
| Sommeil | `sleep-nightly`, `sleep-bedtime` | `sleep-stages`, `sleep-distribution`, `sleep-dow` |
| Cœur | `heart-monthly`, `heart-hrv` | `heart-hourly`, tension, ECG |
| Activité | `activity-steps` | `activity-exercise`, `activity-floors`, `activity-steps-dow` |
| Corps | `body-weight` | — |
| Vitalité | `stress-vitality`, `breathing-skintemp` | `stress-monthly`, `stress-hourly`, `breathing-spo2` |
| Synthèse | tuiles, verdict, `heart-correlations` | — |

Le critère de tri est explicite : **un graphique est essentiel s'il montre une série
quotidienne sur toute la période.** Les profils (par heure, par jour de semaine) et les
agrégats mensuels répondent à une question qu'on se pose ensuite, pas d'abord.

Les tableaux de tension et d'ECG partent au détail parce qu'ils sont vides chez la plupart
des utilisateurs. Ils remontent automatiquement à l'essentiel dès qu'ils portent une ligne.

Les six indicateurs de chaque section se réorganisent aussi : **deux chiffres de tête en
grand, quatre en ligne secondaire.** Les deux de tête, par section :

| Section | Chiffres de tête |
|---|---|
| Sommeil | durée médiane, irrégularité du coucher |
| Cœur | FC de repos moyenne, RMSSD médian |
| Activité | pas par jour sur 30 jours, part des jours au-dessus de 8 000 |
| Corps | dernière pesée, variation sur la période |
| Stress | score moyen, vitalité moyenne |
| Respiration | SpO2 moyenne, température cutanée |

### 5.3 La phrase de lecture calculée

Chaque graphique porte aujourd'hui une `note` : un texte fixe qui explique la méthode
(« La FC de repos est le 5e centile des mesures du jour »). Ces notes restent, elles sont
justes et utiles.

Il leur manque une seconde phrase, qui dit ce que **vos** données montrent :

- Sommeil : « Sur 294 nuits, 41 passent sous 6 h. Le coucher varie de ±1,4 h d'une nuit à l'autre. »
- Cœur : « La FC de repos tient entre 52 et 61 bpm neuf jours sur dix. »
- Activité : « 8 300 pas par jour sur 90 jours, contre 7 100 sur les 30 derniers. »
- Corps : « −2,4 kg sur la période, dont −0,3 kg de muscle. »
- Vitalité : « Le stress dépasse 60 pendant 18 % du temps, avec un pic vers 15 h. »

Deux fautes relevées en les écrivant, et qu'un texte rédigé à la main laisse passer sans
bruit : l'intervalle p10–p90 couvre **huit** jours sur dix, pas neuf ; et la dispersion du
coucher s'écrivait « ±1,8 h » juste sous un indicateur affichant « ±1h46 » — même grandeur,
deux formats sur le même écran.

**Ces phrases sont calculées dans `report-sections.js` à partir de `ReportModel`. Le LLM
ne les écrit pas et ne les voit pas.** C'est la même règle que le catalogue de séries : le
modèle désigne, il ne recopie pas de chiffre, donc il ne peut pas en inventer un. Une
phrase fausse sur une donnée de santé coûte plus cher qu'une phrase absente.

Chaque phrase doit rendre `null` quand la donnée manque, et la carte n'affiche alors que
sa note de méthode.

### 5.4 Le moteur de graphiques

Repris en profondeur, toujours sans bibliothèque. Le comparatif de poids qui a mené à ce
choix reste valable et figure dans `CLAUDE.md`.

Ce qui change :

- **Les axes.** Graduations moins nombreuses, alignées sur des valeurs rondes, jamais
  coupées par le bord de la `viewBox`. Chaque étiquette nomme une valeur que le graphique
  atteint réellement.
- **La grille.** Horizontale seulement, en `--grid`, derrière les marques.
- **Le dernier point.** Marqué sur toute série quotidienne : c'est l'état actuel, et c'est
  ce que l'œil cherche.
- **Les bandes cibles.** Étiquetées dans la bande elle-même, plus en légende séparée.
- **Le survol.** L'infobulle existe (`.ha-tooltip`) ; elle doit aussi répondre au toucher,
  et se placer sans jamais sortir du cadre.
- **Le noir et blanc.** Toute couleur de série est doublée par un style de trait ou un
  libellé. Vérifiable en imprimant en niveaux de gris.
- **Les trous.** Un jour sans mesure reste un trou dans la courbe. Jamais un zéro. Règle
  déjà écrite dans le contrat `ReportModel` ; elle doit se voir.

Chaque modification profite aux deux versions, puisque le moteur est partagé.

---

## 6. L'export « synthèse médecin »

Nouveau gabarit `web/report/export-summary-template.html`, à côté du gabarit complet
existant. Même mécanique de remplissage — trois marqueurs, styles, scripts et modèle — et
même feuille de style.

### 6.1 Pourquoi un second gabarit

Un médecin accorde quelques minutes. Le rapport complet en fait vingt pages. Les deux
existent donc, et l'utilisateur choisit au moment d'exporter.

**Deux à trois pages, pas une à deux.** Mesuré à la largeur utile d'une A4 : la synthèse
occupe deux pages quand il n'y a aucune mesure ponctuelle, trois quand elle porte des
prises de tension ou des ECG. La première version imposait exactement deux pages par une
coupure forcée ; le contenu ne pouvant pas tenir, elle produisait quatre pages — une
demi-page vide suivie d'un reste. Le document coule désormais, chaque bloc protégé d'une
coupure interne, et il annonce le compte qu'il tient vraiment.

### 6.2 Contenu

**Page 1 — ce qui a changé.**
1. En-tête : période couverte, nombre de jours mesurés, source des données, date d'édition.
2. Les six mesures de la semaine contre la référence, en tableau, avec l'écart signé.
3. Les dérives détectées, en phrases.
4. Deux graphiques : `sleep-nightly` et `activity-steps`.

**Page 2 — le fond.**
5. Cœur : `heart-monthly`, et le tableau de tension s'il porte des lignes.
6. Corps : `body-weight`.
7. Vitalité : les indicateurs seuls, sans graphique.
8. Les tableaux d'ECG s'ils portent des lignes.

**L'avertissement remonte dans l'en-tête** — « Mesures issues d'une montre connectée,
les mesures au poignet restent indicatives » — et le pied de page ne porte plus que
« Cette synthèse ne remplace pas un avis médical ». Le répéter sur chaque page demanderait
un `position: fixed` en impression, dont le rendu varie d'un navigateur à l'autre et qui
grignote la hauteur utile de toutes les pages.

### 6.3 Règles d'impression

`report.css` porte déjà un bloc `@media print` bien pensé : la barre d'onglets disparaît,
tous les panneaux sortent, le panneau « Tout » reste caché pour ne pas doubler le document.
Le nouveau gabarit ajoute :

- `@page { size: A4 portrait; margin: 14mm; }`
- `break-inside: avoid` sur chaque bloc, `break-after: page` entre les deux pages ;
- aucune couleur de fond, aucun aplat décoratif : l'encre coûte cher et les imprimantes
  de cabinet sont en noir et blanc ;
- les couleurs de domaine réduites au filet de titre, chaque série doublée d'un libellé ;
- une largeur de graphique fixée en millimètres, pas en pixels.

### 6.4 Ce qui ne doit pas y entrer

Aucun texte produit par le LLM. Le bilan rédigé reste dans le rapport complet, où
l'utilisateur sait d'où il vient. Une synthèse remise à un médecin ne doit porter que des
chiffres mesurés et des phrases calculées.

---

## 7. La navigation

### 7.1 Android

Trois onglets : **Cette semaine**, **Rapport**, **Analyse**.

- L'écran de départ devient `WeekScreen`.
- `ImportScreen` perd son onglet et se rejoint depuis les Réglages, ou depuis le bandeau de
  premier usage de l'accueil.
- `SettingsScreen` perd son onglet et se rejoint par une icône en haut à droite de chaque
  écran.
- Les routes existantes sont conservées ; seule la barre change. Rien ne devient
  inaccessible.

### 7.2 Web

Mêmes trois vues, plus un lien « Réglages » dans l'en-tête. La numérotation `01` à `04`
des `view-eyebrow` disparaît : elle annonçait une séquence qui n'existe plus.

### 7.3 La période

`TimeRange` gagne une valeur `ALL` (« Tout l'historique »), pour rejoindre ce que la
version web propose déjà. Le sélecteur passe à cinq segments, ou à un menu sur écran
étroit.

---

## 8. Les états

Chaque état ci-dessous a un texte exact, à reprendre tel quel.

| Situation | Texte | Action offerte |
|---|---|---|
| Aucune donnée | « Aucune donnée pour l'instant. Importez votre export Samsung Health, ou connectez Health Connect. » | Importer / Connecter |
| Synchronisation | « Récupération de l'historique… septembre 2026 » | — |
| Calcul du rapport | « Calcul du rapport sur 294 jours… » | — |
| Référence trop courte | « Il faut 21 jours de référence pour comparer. Vous en avez 12. » | — |
| Semaine trop trouée | « Cinq jours mesurés sont nécessaires cette semaine. Vous en avez trois. » | — |
| Permission d'historique absente | « Health Connect ne donne accès qu'aux 30 derniers jours. Autorisez l'accès aux données antérieures pour remonter plus loin. » | Autoriser l'accès |
| Aucune clé API | « L'analyse a besoin d'une clé API. Elle reste sur votre appareil. » | Ouvrir les réglages |
| Analyse en cours | « Analyse en cours… » avec un indicateur indéterminé | Annuler |
| Échec réseau | « La requête n'a pas abouti. Vérifiez votre connexion, puis réessayez. » | Réessayer |

**Un défaut existant à corriger dans cette phase.** `DriftSection.kt` commence par
`if (state.isLoading) return` : pendant le calcul, la section entière disparaît de l'écran.
L'utilisateur voit donc un rapport sans bloc de dérives, puis le bloc apparaît sans
prévenir. Un bloc qui s'efface se lit comme un bloc qui n'existe pas.

Trois règles derrière ce tableau.

1. **Un état vide dit toujours pourquoi.** « Aucune dérive » et « pas assez de données »
   se ressemblent à l'écran et ne veulent pas dire la même chose.
2. **Une attente dit toujours sur quoi elle porte.** Un mois, un nombre de jours, une
   étape.
3. **Un échec dit toujours quoi faire ensuite.** Aucune excuse, aucune formule vague.

---

## 9. Le mouvement

Sobre et utile. Le mouvement sert à montrer une relation, jamais à remplir un silence.

- **Entrée d'un graphique** : la courbe se trace en 400 ms, une seule fois, à la première
  apparition. Elle montre le sens de lecture, de gauche à droite.
- **Changement d'onglet** : fondu croisé de 150 ms, sans glissement latéral.
- **Dépliage du détail** : hauteur animée sur 200 ms.
- **Les chiffres ne défilent pas.** Un compteur qui grimpe vers la valeur finale est un
  effet de démonstration ; sur une mesure de santé, il se lit comme une donnée instable.
- `prefers-reduced-motion: reduce` coupe tout, sans exception. Côté Compose, la même
  décision se lit dans les réglages d'animation du système.

---

## 10. L'accessibilité

- Contraste minimal de 4,5:1 sur tout texte, dans les trois états de thème (clair, sombre
  système, sombre forcé). La règle `--accent-contrast` est l'exemple à suivre.
- La couleur ne porte jamais seule une information. Règle déjà écrite dans `report.css`,
  étendue à la coquille.
- Cible tactile de 48 dp minimum.
- Le corps du texte suit le réglage système de taille de police. Aucune hauteur fixe ne
  doit couper un texte agrandi.
- Chaque graphique porte une description textuelle lisible par un lecteur d'écran ; la
  phrase de lecture calculée (§ 5.3) sert exactement à cela.
- Le focus clavier est visible sur la version web.

---

## 11. Ce qui ne bouge pas

Ces garde-fous survivent à la refonte. Ils sont détaillés dans `CLAUDE.md`.

- `HealthPromptBuilder` reste la seule porte de sortie vers un tiers, et il n'envoie que
  des agrégats. `ReportModel` ne franchit jamais cette porte.
- La WebView n'a aucun accès réseau. Les polices embarquées ne changent rien à cela : ce
  sont des assets locaux.
- Le texte venu du LLM se pose en `textContent`, jamais en `innerHTML`.
- `ReportModel` existe en deux exemplaires qui doivent rester identiques. Toute phrase de
  lecture calculée ajoutée d'un côté doit l'être de l'autre, dans le même commit.
- `app/src/main/assets/` est un dossier généré. On n'y touche pas à la main.
- Aucune donnée de santé n'entre dans le dépôt. Le dépôt est public.
- Aucune bibliothèque de graphiques n'est ajoutée.
- Lancer l'app fait partie de la vérification, avant chaque publication.

---

## 12. Les phases de livraison

Cinq phases. Chacune se vérifie seule et peut être publiée seule.

### Phase 1 — Le système visuel — **livrée**

Palette unifiée, typographie embarquée, échelle typographique, contrastes corrigés. Aucun
changement de fonction.

Ce qui a été fait, en plus du prévu : les trois contrastes du § 1.7 ; le remplacement des
polices dans le rapport exporté, qui les appelait par un chemin relatif inexistant dans un
fichier autonome ; et `DriftSection`, qui disparaissait pendant son calcul.

Vérifié : `ThemeTokenParityTest` relit `report.css` et compare jeton par jeton, cassé
exprès des deux façons pour s'assurer qu'il sait échouer ; 464 tests, 0 échec ; APK
installé et lancé sur émulateur, palette relevée au pixel (`#f9f9f7` et `#e1e0d9`
exactement), aucun plantage ; export ouvert depuis un blob — donc sans dossier voisin — et
sa serif s'y charge.

### Phase 2 — La navigation et « Cette semaine » — **livrée**

Trois onglets, Réglages sous icône, import déplacé, nouvel écran d'accueil.

Deux décisions prises en cours de route, et toutes deux nées d'un regard sur l'écran avec
de vraies mesures :

1. **Montrer une valeur et affirmer une dérive ont des seuils distincts.** L'archive
   importée s'arrêtait quelques jours avant la date du jour ; les six lignes affichaient un
   tiret sous une référence bien remplie, et l'écran donnait à lire « aucune donnée » là où
   l'information était « il manque les derniers jours ». Décrire une semaine demande moins
   de preuves qu'affirmer un changement : les seuils d'affichage sont plus bas, et le
   nombre de jours mesurés s'affiche à côté de la valeur.
2. **Le poids est la seule mesure éparse.** On se pèse une ou deux fois par semaine. Lui
   appliquer le seuil des mesures quotidiennes affichait un tiret presque toujours. Une
   seule pesée suffit désormais à dire le poids de la semaine ; les seuils de détection,
   eux, n'ont pas bougé.

Vérifié : 486 tests, 0 échec, dont 20 sur le bilan hebdomadaire et son ViewModel ; APK
installé et lancé, les trois onglets, l'icône de réglages et l'import parcourus sans
plantage ; l'écran relu deux fois sur l'appareil, une fois avec des valeurs d'essai et une
fois avec les vraies mesures — c'est la seconde qui a révélé les deux défauts ci-dessus.

### Phase 3 — La hiérarchie du rapport — **livrée**

Livré : essentiel et détail, deux chiffres de tête, phrases de lecture calculées, et les
tableaux de tension et d'ECG qui remontent d'eux-mêmes dès qu'ils portent une ligne.

La reprise du moteur de graphiques (§ 5.4), d'abord reportée, a été livrée ensuite : voir
plus bas.

Trois décisions prises en cours de route :

1. **Le détail est dessiné à la première ouverture, pas avant.** Un SVG construit dans un
   conteneur masqué sort à zéro pixel de large. La première version appelait `redraw()`
   après coup, ce qui reconstruisait le rapport entier et refermait le bloc dans la foulée.
2. **Le bloc replié n'est pas un `<details>`.** Le contenu d'un `<details>` fermé ne
   s'imprime pas, et le rapport imprimé doit être complet — c'est son usage principal.
   Une classe et un bouton se laissent forcer ouverts par une règle d'impression, ce
   qu'aucune CSS ne sait faire sur un `<details>`.
3. **Un seul écouteur d'impression pour toute la page.** Un écouteur par bloc aurait
   survécu à chaque redessin en retenant des nœuds détachés.

Vérifié : 158 tests JS, 486 tests Kotlin, 0 échec ; les deux garde-fous existants de
l'onglet « Tout » sont passés au rouge en comptant 19 graphiques là où il n'y en a plus
que 9 avant dépliage — recentrés sur le nouveau contrat, et complétés par le test qui
manquait ; la promesse « rien ne disparaît » cassée exprès pour vérifier que le test la
défend ; dépliage essayé au doigt dans la WebView Android ; APK publié installé et lancé.

### Phase 4 — L'export médecin — **livrée**

Gabarit de synthèse, règles d'impression A4.

Vérification : synthèse réelle produite sur l'appareil et ouverte depuis un dossier sans
aucun asset ; deux à trois pages selon ce que la période porte ; aucun texte de LLM ;
palette claire forcée à l'impression, sous la garde d'un test de parité.

### Phase 3 bis — Le moteur de graphiques — **livrée**

Les dix-neuf graphiques ne fixent plus ni leurs bornes ni leurs graduations : ils donnent
l'étendue de leurs données à `E.scale`, qui rend un axe complet. Ce déplacement corrige
d'un coup une famille de défauts que chaque graphique portait pour son compte.

Cinq décisions prises en cours de route :

1. **L'écrêtage disparaît partout.** Quatre graphiques rabattaient les valeurs hautes sur
   leur plafond : le stress à 60, le sommeil à 12 h, la vitalité à 30 et 100,
   l'oxygénation à 88 %. Une part de 91 % du temps au-dessus de 60 se lisait donc 60.
2. **La marge gauche vient de l'étiquette la plus large**, mesurée avant de créer le SVG.
   Elle valait 44 px en dur ; « 105 kg » en demande davantage et perdait son premier
   caractère, coupé par le bord de la `viewBox`.
3. **Cinq intervalles visés, et une grille dense coûte plus cher qu'une grille lâche.**
   Avec quatre, un axe de poids allant de 35 à 90 kg prenait un pas de 20 et s'étendait
   de 20 à 100, ce qui aplatissait les deux courbes au milieu du cadre.
4. **La légende montre le trait de sa série, pas une pastille.** Une pastille imprimée en
   niveaux de gris ne distingue plus rien. Conséquence : l'échantillon est un SVG, et les
   deux garde-fous qui comptaient les graphiques en comptant les balises `svg` sont passés
   au rouge — ils comptent maintenant `svg[role="img"]`.
5. **La grille reste horizontale**, sauf pour les corrélations, dont les barres sont
   horizontales : la grandeur s'y lit en abscisse, et les traits qui la jalonnent sont
   forcément verticaux.

Vérifié : 176 tests JS, 495 tests Kotlin, 0 échec ; dix-sept nouveaux tests, dont celui de
l'écrêtage cassé exprès pour vérifier qu'il sait échouer ; les dix-huit axes relevés un par
un ; lecture en niveaux de gris essayée sur la synthèse ; APK de débogage installé, lancé,
et infobulle sortie au doigt dans la WebView.

### Phase 5 — Les états, le mouvement, l'accessibilité — **livrée**

**Les états.** Les neuf textes du § 8 vivent dans `ui/state/StateCopy.kt`, et non plus au
fil des écrans. Cinq y étaient déjà, sous une forme proche ; quatre manquaient.

Trois décisions prises en cours de route :

1. **Un échec porte sa cause, pas sa phrase.** `ChatResult.Failure` et
   `NarrativeResult.Failure` transportaient un message tout écrit, ce qui laissait la
   cause technique traverser jusqu'à l'écran : « Le chargement du contexte de santé a
   échoué : base illisible ». Ils portent maintenant un `Throwable`, et
   `StateCopy.forFailure` en tire la phrase **et** le geste. Trois tests affirmaient
   l'ancien contrat — que le message de l'exception atteigne l'écran — et ont été
   recentrés.
2. **Le geste vient de l'état, pas d'une tournure de phrase.** L'écran d'analyse décidait
   d'afficher « Ouvrir les réglages » en cherchant « clé API » dans le texte du message.
   Le bouton se serait détaché en silence à la première reformulation.
3. **« Analyse en cours… » s'annule.** Le tableau le demandait ; rien ne le faisait. Un
   appel au fournisseur dure parfois une demi-minute, et la seule sortie était de quitter
   l'écran, ce qui laissait l'appel courir. La question déjà posée reste en base :
   l'effacer donnerait le sentiment que l'app a perdu ce qu'on venait de taper.

Le défaut nommé dans le § 8 — `DriftSection` qui s'effaçait pendant son calcul — avait
déjà été corrigé en phase 2.

**Le mouvement.** Trois gestes, et rien d'autre : l'entrée d'une courbe (400 ms, à la
première apparition seulement), le fondu d'onglet (150 ms, sans glissement latéral), le
dépliage du détail (200 ms).

Deux décisions :

1. **Seul le dépliage s'anime ; le repli est immédiat.** Animer la fermeture rendait
   l'état asynchrone : `hidden` restait faux deux dixièmes de seconde après un clic sur
   « Masquer », donc un lecteur d'écran continuait d'annoncer un contenu refermé. Un
   garde-fou existant l'a attrapé.
2. **Le retour de `display: none` et le changement de hauteur tombent dans deux images
   différentes.** Un élément qui vient d'apparaître n'a pas d'état antérieur : le
   navigateur ne voit qu'un seul changement et n'anime rien. Une lecture d'`offsetHeight`
   n'y suffit pas. Un filet de sécurité relâche la hauteur même quand `transitionend`
   n'arrive jamais — durée nulle, bloc hors du document, transition interrompue.

**L'accessibilité.** Le contraste de la coquille Compose est mesuré par
`ThemeContrastTest`, qui a trouvé deux vrais défauts :

- `--axis` tenait **1,70:1** en clair et 1,65:1 en sombre, sous le seuil de 3:1 des
  éléments non textuels. Ce jeton est à la fois la ligne de base des graphiques et la
  bordure `outline` de Material : un champ de saisie dont on ne voit pas le contour est
  un défaut d'accessibilité, et la ligne de base disparaissait à l'impression. Corrigé à
  `#8e8d82` (3,17:1) et `#64645d` (3,26:1).
- `--critical` posé sur son propre aplat d'erreur ne tenait que **4,08:1**, et un message
  d'erreur est le dernier texte qu'on veut rendre difficile à lire. Corrigé à `#be3131`
  (4,81:1 sur ce fond, 5,42:1 sur le papier).

Les dix-neuf graphiques portent maintenant un `<title>` et un `<desc>` reliés par
`aria-labelledby`. La description se remplit au fil du dessin — `grid`, `xTimeAxis`,
`lastPoint` y ajoutent chacun leur phrase — ce qui la donne aux dix-neuf sans les
réécrire un par un, et surtout sans qu'elle se périme quand l'un d'eux change d'axe :
« Échelle verticale de 0h à 10h. Bande cible marquée : cible 7–9 h. Période du 15 juil. 25
au 7 sept. 26, soit 419 jours. Dernière valeur : 6h41. »

Enfin : 44 pixels de hauteur minimale sur les commandes du rapport, un contour de focus
clavier sur chacune, et le filet de titre qui suit désormais la hauteur du texte plutôt
qu'une valeur écrite en dur — à 200 % de taille de police système, il restait un moignon.

Vérifié : 187 tests JS, 513 tests Kotlin, 0 échec ; contrastes mesurés un par un, dont le
calcul lui-même vérifié sur trois valeurs de référence — dont une que j'avais fausse ;
`prefers-reduced-motion` essayé dans les deux sens.

---

## 13. Ce qui reste à arbitrer

1. **La paire de polices.** Source Serif 4 et Public Sans sont un choix sobre. Une serif
   plus marquée (Fraunces) donnerait davantage de caractère, au risque du maniérisme sur un
   document médical.
2. ~~**Le poids du rapport exporté.**~~ Tranchée par la mesure : la serif 600 seule pèse
   31 Ko en base64, pas 80. Le coût est assez faible pour ne plus poser question, et
   l'option est en place.
3. **L'onglet « Tout » du rapport.** Il répète le contenu de tous les autres. Il reste
   utile pour chercher dans la page ; il double la longueur du document.
4. **Les couleurs de domaine sur l'écran d'accueil.** La charte actuelle les réserve aux
   données de santé et interdit qu'un écran entier porte une seule couleur. L'écran
   « Cette semaine » porte six domaines à la fois : la règle tient, mais elle mérite d'être
   confirmée.
5. **La notification hebdomadaire existe déjà et se déclenche.** `WeeklyDriftCheckWorker`
   appelle `DriftNotifier` dès qu'une dérive est détectée, sur le canal « Bilans de
   santé », avec le titre « Bilan de la semaine : n mesures ont changé ». La question
   n'est donc pas de la créer, mais de décider si elle doit mener au nouvel écran
   « Cette semaine » plutôt qu'à `MainActivity` — et si elle doit aussi se déclencher
   sans dérive, pour marquer le rendez-vous hebdomadaire.
