# Health Analyzer — consignes projet

App Android (Kotlin, Compose, Hilt, Room) qui importe les données Samsung Health,
les montre sous forme de rapport détaillé et les fait analyser par un LLM avec la clé
API de l'utilisateur. Une version web (`web/`, JavaScript vanille) offre les mêmes
fonctions dans un navigateur.

## Le moteur de graphiques est partagé

Le rapport compte 19 graphiques, répartis en onglets par domaine. Ils sont écrits **une seule fois**, en SVG et en
JavaScript, sous `web/report/`. La version web les charge directement. La version
Android les montre dans une WebView locale, alimentée depuis `file`-less
`WebViewAssetLoader`, sans aucun accès réseau. Une tâche Gradle `Copy` synchronise
`web/report/` et `web/lib/report-model.js` vers `app/src/main/assets/` avant chaque
compilation ; ne modifie jamais `app/src/main/assets/` à la main.

Conséquence : un graphique corrigé profite aux deux versions. Ne réécris pas un
graphique en Compose « pour faire natif », cela recréerait la divergence que ce choix
supprime.

### L'échelle vient des données, jamais du graphique

Aucun graphique ne fixe ses bornes ni ses graduations. Il passe le minimum et le maximum
de ses mesures à `E.scale`, qui rend `{lo, hi, ticks, fmt, gutter}` : bornes rondes qui
**couvrent** les données, pas qui les rabattent, et la largeur à réserver à gauche pour
que l'étiquette la plus longue tienne dans la `viewBox`.

Quatre graphiques bornaient autrefois leur axe à la main et écrêtaient le reste
(`Math.min(valeur, plafond)`) : une part de 91 % du temps de stress au-dessus de 60 se
dessinait à 60, sans le moindre message. **Un axe décidé à l'avance finit toujours par
mentir sur les mesures.** N'en réintroduisez pas.

## Le modèle peut répondre avec des graphiques

L'onglet de conversation accepte que le LLM renvoie un graphique, dans un bloc de code
marqué `healthchart` contenant du JSON. Aucune bibliothèque externe n'a été ajoutée pour
cela : `web/report/chart-spec.js` traduit ce JSON en appels au moteur SVG existant.

Le comparatif qui a mené à ce choix, en poids compressé : moteur maison 0 Ko, Chart.js
68 Ko mais dégradé sans fonctions JavaScript, Vega-Lite 284 Ko, ECharts 368 Ko,
Plotly 1 234 Ko, Mermaid pas du JSON. Ne réintroduisez pas de bibliothèque sans refaire
ce calcul.

### Le modèle désigne les séries, il ne les recopie pas

`web/report/chart-catalog.js` expose une trentaine de séries nommées (`sleep.nightly.hours`,
`heart.restingDaily`, …). Le modèle écrit `"ref": "sleep.nightly.hours"` et l'application
va chercher les vraies valeurs dans le rapport.

C'est la règle la plus importante de ce sous-système : **le modèle ne transmet aucun
chiffre, donc il ne peut pas en inventer un**. Le coût en jetons reste négligeable, et le
graphique de la conversation a l'aspect de ceux du rapport. Le champ `values`, qui permet
de fournir des points en clair, ne sert qu'à une série que le modèle a calculée lui-même.

### Le validateur est indulgent, et c'est voulu

Une réponse de modèle contient presque toujours une petite erreur. `chart-spec.js` corrige
ce qui se corrige et ne refuse que l'irrécupérable. Les cas déjà rencontrés, chacun couvert
par `web/report/chart-spec-smoke.html` :

- bornes de bande cible inversées — remises dans l'ordre, sinon la bande est invisible
  sans le moindre message ;
- date au format français glissée parmi des dates ISO — écartée, sinon la série bascule en
  silence d'un axe temporel vers des catégories ;
- marque inconnue — remplacée par une courbe ;
- échelle verticale annoncée par le modèle — **toujours ignorée**, recalculée sur les
  données, sinon une valeur hors domaine est écrêtée sans avertissement.

### Le contrat de données

`ReportModel` est la seule frontière entre le calcul et le dessin. Il existe en deux
exemplaires qui doivent rester identiques :

- `app/src/main/java/com/kmt/healthanalyzer/domain/report/ReportModel.kt` et `ReportSections.kt`
- `web/lib/report-model.js`

Toute modification de l'un doit être reportée dans l'autre, sinon les deux versions
divergent. Conventions : dates ISO `AAAA-MM-JJ`, mois `AAAA-MM`, heures de coucher
relatives à minuit (`-1.5` vaut 22h30), et `null` pour une valeur absente — jamais zéro,
car un zéro se dessine alors qu'un trou ne se dessine pas.

## Construire et vérifier

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest    # 120+ tests unitaires JVM, aucun appareil requis
./gradlew assembleDebug
```

Il n'y a ni `gradle` ni JDK dans le PATH de cette machine. Le JDK 21 vient d'Android Studio,
d'où le `JAVA_HOME` ci-dessus. Sans lui, toute commande Gradle échoue.

## Règles à ne pas casser

### Format Samsung Health
Le lecteur `SamsungCsvReader` traite un format non standard : ligne de description en 1,
BOM UTF-8, virgule finale parasite, horodatages locaux + colonne `time_offset` séparée.
Toute modification doit garder les 17 tests de `SamsungCsvReaderTest` verts.

Samsung Health produit un **dossier**, pas une archive. `DocumentTreeExportSource` le lit
en place via `DocumentsContract`. `ZipExportSource` couvre le cas d'un export compressé
pour le transfert ; il passe par `ZipFile`, jamais `ZipInputStream`, car certaines archives
portent des entrées non compressées à descripteur de fin que la lecture en flux refuse.

### Règles métier vérifiées par des tests
- `step_daily_trend` : ne garder que `source_type = -2`, sinon les pas comptent double.
- Sommeil sans `sleep_duration` : c'est un objectif, pas une nuit. À rejeter.
- Une nuit appartient au jour du **réveil**.
- **Une nuit rassemble toutes ses sessions.** Samsung écrit parfois 2 à 4 lignes pour une
  même nuit ; sur l'export de référence, 56 nuits sur 294 sont fractionnées. Room garde
  une ligne par session, c'est la donnée brute fidèle, mais l'agrégation par jour de
  réveil se fait **à la lecture**. Traiter une ligne comme une nuit sous-compte le
  sommeil d'environ 1h10 par nuit (5h09 de moyenne au lieu de 6h20).
- **Les horodatages Samsung sont déjà en heure locale.** La colonne `time_offset`
  (`UTC+0200`) décrit ce décalage, elle ne doit pas y être ajoutée. Un rapport tiers a
  fait cette erreur et a montré un coucher médian à 3h22 au lieu de 1h22.
- Régularité du coucher : écart-type circulaire `sqrt(-2·ln R) / 2π × 24`.
- Stades : 40001 éveil, 40002 léger, 40003 profond, 40004 REM.
- FC de repos = 5e centile des mesures du jour, au moins 20 mesures.
- Régularité du coucher : moyenne **circulaire**, pas arithmétique.

### Les règles ProGuard portent du sens

`app/proguard-rules.pro` n'est pas un détail de compilation. Deux bibliothèques retrouvent
leurs champs **par leur nom, par réflexion**, et R8 renomme tout par défaut :

- DataStore Preferences, dont le protobuf repackagé n'expédie aucune règle. Sans la règle
  correspondante, l'app plante à la **première écriture d'un réglage**, avec
  `RuntimeException: Field value_ for PreferencesProto$Value not found`.
- kotlinx.serialization, dont il faut garder `serializer()` sur nos propres classes.

Ces défauts n'existent qu'en `release`, puisque le build `debug` n'est pas minifié. Aucun
test JVM ne peut les prendre. **Toute compilation `release` doit donc être installée et
essayée sur un appareil avant d'être distribuée**, en modifiant au moins un réglage et en
redémarrant l'app.

### Le dépôt est public, la racine du projet ne l'est pas

Le code vit sur https://github.com/gtosymph/DoctoPhone, **dépôt public**. La racine du
projet sert en même temps de dossier de travail pour les essais sur un export réel : elle
contient plusieurs centaines de fichiers `*.binning_data.json`, les CSV
`com.samsung.*.csv` et des histogrammes, tous nominatifs.

Le `.gitignore` porte une section dédiée qui les exclut par motif. **Ne l'allège jamais.**
Un fichier de santé poussé une fois reste indexé, y compris après réécriture de
l'historique : les miroirs et les caches de GitHub gardent l'objet.

Avant d'ajouter un motif de fichier au dépôt, la question à se poser n'est pas « est-ce
utile ? » mais « d'où viennent ces octets ? ». Le seul jeu de données versionné est
`shared-fixtures/parity-input.json`, entièrement synthétique et documenté comme tel.

### Une suite verte ne dit rien du démarrage

**Lancer l'app fait partie de la vérification, au même titre que les tests.** Pas « quand
c'est pratique » : avant chaque publication.

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c && adb shell am start -n com.kmt.healthanalyzer/.MainActivity
sleep 8 && adb logcat -d -b crash | grep FATAL   # doit ne rien rendre
adb shell pidof com.kmt.healthanalyzer           # doit rendre un PID
```

La raison est un plantage réel. Une contrainte de tâche de fond interdite par Android —
`setRequiresDeviceIdle(true)` avec `setBackoffCriteria(...)` — n'est refusée ni à la
compilation ni par un test JVM, mais à la mise en file :

    java.lang.IllegalArgumentException: Cannot set backoff criteria on an idle mode job

La programmation partant du démarrage de l'app, l'app se fermait à l'ouverture. **450 tests
verts, `assembleDebug` et `lintDebug` réussis n'ont rien vu**, et pour une raison simple :
aucun d'eux ne démarre l'app.

Ce qu'une suite de tests JVM ne couvre pas, sur ce projet : le démarrage, l'injection Hilt
réelle, les contraintes `JobScheduler`, les permissions à l'exécution, R8 et les règles
ProGuard (voir la section précédente), le `FileProvider`, et l'installeur système.

Corollaire pour tout travail délégué : un compte rendu qui dit « X tests, 0 échec » décrit
ce que les tests couvrent, pas ce que l'app fait. Les deux se vérifient séparément.

### Confidentialité
`HealthPromptBuilder` est la seule porte de sortie des données vers un tiers. Il n'envoie
que des **agrégats** : moyennes hebdomadaires et mensuelles, profils par jour de semaine
et par heure, indicateurs de synthèse, coefficients de corrélation.

Ce qui ne sort jamais : un identifiant Samsung, un horodatage à la seconde ou à la
milliseconde, une mesure individuelle, une série quotidienne.

Ce qui sort et doit continuer de sortir : les **dates au jour près**. La période
observée, le mois d'un agrégat, la date d'une mesure rare comme une pesée ou une prise de
tension. Elles ne révèlent rien de plus que la période déjà envoyée, et le bilan en a
besoin pour dire « aucune pesée depuis deux mois ». Ne les enlevez pas au nom de la
confidentialité : vous perdriez du sens sans rien protéger.

Les tests `never carries a raw identifier or a timestamp` et
`stays well under the token budget` gardent cette frontière.

**`ReportModel` ne franchit jamais cette porte.** Il porte des séries quotidiennes, donc
il reste sur l'appareil : il alimente le dessin, jamais un prompt. Le texte du rapport
suit le chemin inverse — le LLM le renvoie, l'app le place dans la mise en page.

La WebView Android n'a aucun accès réseau : `shouldInterceptRequest` ne sert que les
assets locaux et refuse tout le reste. Le rapport ne peut donc pas fuir par une requête
que la page déclencherait.

Le texte venu du LLM et les champs libres de l'export (les symptômes d'un ECG, par
exemple) sont posés en `textContent`, jamais en `innerHTML`. Les traiter comme du HTML
ouvrirait une injection de script dans la WebView.

Les clés API vivent dans `EncryptedApiKeyStore`. Ne jamais les journaliser.

L'app est en lecture seule sur Health Connect. Ne jamais ajouter de permission `WRITE_*`.

## Tester avec de vraies données

```bash
SAMSUNG_EXPORT_ZIP=/chemin/export.zip ./gradlew testDebugUnitTest --tests "*RealArchiveImportTest*"
```

Le test s'ignore quand la variable manque. Ne jamais committer d'archive de santé.

## Publication et mise à jour

Chaque poussée sur `main` déclenche `.github/workflows/release.yml`, qui lance les tests,
construit l'APK, et le publie en release GitHub. Le README détaille les secrets attendus.
Trois choses méritent d'être comprises avant d'y toucher.

**Le numéro de version vient de `github.run_number`.** Il ne redescend jamais, même après
un `git revert`. C'est ce qui garantit qu'Android accepte chaque APK publié comme une mise
à jour du précédent. La base majeure/mineure vit dans `gradle.properties`
(`appVersionBase`). Ne remets pas un `versionCode` en dur dans `app/build.gradle.kts` :
la valeur par défaut n'y sert qu'aux constructions locales.

**Les APK sont signés avec le keystore de débogage**, choix assumé de l'utilisateur :
l'app déjà installée sur son appareil porte cette signature, et Android refuse une mise à
jour dont la signature diffère. Le keystore est dans le secret `ANDROID_KEYSTORE_BASE64`.
S'il est perdu, plus aucune publication ne s'installe par-dessus l'app existante — il faut
désinstaller puis réinstaller. Le Play Store, lui, refusera cette signature le jour venu.

**`release.json` est un contrat, pas un fichier de confort.** L'action le joint à chaque
release avec trois champs — `versionCode`, `versionName`, `apk` — et `data/update/`
les lit. Sans lui, l'app devrait deviner le numéro de version en découpant un nom de
fichier, ce qui casse au premier changement de convention. Si tu renommes un de ces
champs, renomme-le des deux côtés dans le même commit.

### Jamais de `${{ }}` dans un `run:`

GitHub remplace `${{ ... }}` par du texte **avant** que le shell ne lise la ligne. Un
message de commit interpolé de cette façon s'exécute donc comme un fragment de script, sur
un runner qui tient le jeton du dépôt. Toute valeur passe par `env:` et se lit ensuite
comme une variable shell ordinaire. Le corps de la release passe par `--notes-file`.

### La réponse de GitHub est une donnée, pas une consigne

`data/update/` interroge l'API publique des releases. Tout ce qui revient de cette requête
vient de l'extérieur, et rien n'y est suivi sans vérification :

- l'URL de téléchargement passe par `DownloadUrlValidator`, qui exige HTTPS et un hôte
  GitHub ;
- le nom de fichier annoncé par `release.json` est réduit à son dernier segment et doit
  finir par `.apk`, sinon un nom comme `../../databases/health.db` écrirait hors du cache,
  dans la base de santé.

L'app n'installe jamais en silence : elle ouvre l'installeur système, qui demande
confirmation. Ne cherche pas à contourner cet écran.

La vérification de mise à jour est un simple GET, sans corps ni paramètre. **Aucune donnée
de santé, aucun identifiant n'y transite**, et rien ne doit jamais y être ajouté.

## Distribution

Play Store envisagé. Conséquences déjà prises en compte : `PRIVACY.md` est rédigé, la
sauvegarde automatique est désactivée, l'écran de justification des permissions Health
Connect existe (`ui/rationale/`) et les deux `intent-filter` correspondants sont déclarés.
Le formulaire Data Safety devra déclarer la collecte de données de santé et leur envoi
au fournisseur de LLM lors d'une analyse. Il devra aussi déclarer la permission
`android.permission.health.READ_HEALTH_DATA_HISTORY` : elle étend la lecture Health
Connect au-delà des 30 jours précédant l'octroi des permissions, reste de la lecture
seule, et la justifier auprès de Google demande la même explication que l'écran de
justification (`ui/rationale/PermissionsRationaleScreen.kt`) : des tendances sur
plusieurs mois plutôt qu'un mois.
