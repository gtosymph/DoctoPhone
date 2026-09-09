# Health Analyzer

Application Android qui importe vos données Samsung Health, les montre sous forme de
tendances, et les fait analyser par un modèle de langage avec votre propre clé API.

Toutes les données restent sur l'appareil. Seuls des agrégats hebdomadaires partent vers
le fournisseur de LLM que vous choisissez, et seulement quand vous lancez une analyse.

## Ce que l'app sait lire

### 1. L'export Samsung Health

Dans Samsung Health : **Paramètres → Télécharger les données personnelles**. L'app écrit
un **dossier** `samsunghealth_<compte>_<horodatage>` dans le stockage du téléphone.

L'app importe ce dossier directement. Elle accepte aussi une archive ZIP de ce dossier,
utile quand l'export a transité par un ordinateur.

C'est la source la plus complète. Elle porte les scores propriétaires Samsung que personne
d'autre n'expose : score de sommeil, score de stress, score d'énergie, composition
corporelle détaillée.

Types importés :

| Type Samsung | Contenu |
|---|---|
| `com.samsung.shealth.sleep` | Nuits, score, efficacité, latence, récupération |
| `com.samsung.health.sleep_stage` | Stades (éveil, léger, profond, REM) |
| `com.samsung.shealth.tracker.heart_rate` | Fréquence cardiaque |
| `com.samsung.shealth.stress` | Score de stress horaire |
| `com.samsung.health.hrv` | Variabilité cardiaque (SDNN, RMSSD) |
| `com.samsung.shealth.tracker.oxygen_saturation` | SpO2 |
| `com.samsung.health.weight` | Poids et composition corporelle |
| `com.samsung.shealth.exercise` | Séances d'exercice |
| `com.samsung.shealth.step_daily_trend` | Pas par jour |
| `com.samsung.shealth.activity.day_summary` | Temps actif, calories, étages |
| `com.samsung.shealth.vitality_score` | Score d'énergie et ses composantes |

### 2. Health Connect

Pour la mise à jour automatique après l'import initial. Samsung Health y écrit les pas,
la fréquence cardiaque, le sommeil, les séances, la SpO2 et le poids. Health Connect
n'expose pas les scores propriétaires Samsung : il complète l'archive, il ne la remplace pas.

L'app est en **lecture seule** sur Health Connect. Elle n'y écrit jamais rien.

## Le format d'export Samsung, en pratique

Ce n'est pas un CSV standard. Les pièges, tous traités dans `SamsungCsvReader` :

- La ligne 1 décrit le type de donnée (`com.samsung.shealth.sleep,7006011,11`), pas les colonnes.
- La ligne 2 porte les noms de colonnes, précédée d'un BOM UTF-8.
- Chaque ligne se termine par une virgule, qui crée une colonne vide parasite.
- Les champs de texte libre suivent la RFC 4180 (guillemets, virgules internes).
- Les horodatages sont en heure locale, avec le décalage dans une colonne `time_offset` séparée.
- Les colonnes lourdes pointent vers un fichier JSON dans `jsons/<type>/<1re lettre>/<uuid>.binning_data.json`.
- Quand l'export arrive compressé, l'archive peut contenir des entrées non compressées à
  descripteur de fin, selon l'outil qui l'a créée. `ZipInputStream` les refuse
  (`only DEFLATED entries can have EXT descriptor`). L'app lit donc avec `ZipFile`.

Règles métier non évidentes :

- `step_daily_trend` écrit une ligne par source et par jour. Seul `source_type = -2` porte
  l'agrégat. Garder les autres compterait les pas deux fois.
- Une ligne de sommeil sans `sleep_duration` décrit un objectif de coucher, pas une nuit mesurée.
- Les stades de sommeil : `40001` éveil, `40002` léger, `40003` profond, `40004` REM.
- Une nuit est rattachée au **jour du réveil**, pas au jour du coucher.
- La fréquence cardiaque au repos est estimée par le 5e centile des mesures du jour :
  la montre ne marque pas les mesures « au repos ».
- La régularité du coucher passe par une moyenne circulaire : 23 h et 1 h sont distants de
  2 heures, pas de 22 heures.

## Architecture

```
domain/
  model/         modèles canoniques, indépendants de la source
  analysis/      agrégation, tendances, construction du prompt LLM
  usecase/       cas d'usage d'analyse
  repository/    contrat d'écriture (HealthDataSink)
data/
  samsung/       lecteur CSV, mappeurs, importateur (dossier ou archive)
  healthconnect/ lecture Health Connect
  db/            Room (entités, DAO, mappeurs)
  llm/           clients Anthropic / OpenAI / Gemini
  settings/      clés API chiffrées
  preferences/   réglages
  repository/    dépôt unique, sink Room
ui/              Compose (accueil, import, analyse, réglages)
```

## Construire

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # APK de test
./gradlew testDebugUnitTest      # tests unitaires
```

Pour vérifier le parseur sur une archive réelle :

```bash
SAMSUNG_EXPORT_ZIP=/chemin/vers/export.zip ./gradlew testDebugUnitTest --tests "*RealArchiveImportTest*"
```

Le test s'ignore de lui-même quand la variable n'est pas définie. Aucune donnée personnelle
n'entre dans le dépôt.

## Publication

Chaque poussée sur `main` déclenche l'action `.github/workflows/release.yml`. Elle lance
d'abord les ~392 tests unitaires (`./gradlew testDebugUnitTest`) : un test qui échoue
arrête la publication, aucune release ne sort d'un état rouge. Elle construit ensuite
l'APK de publication, la nomme `DoctoPhone-<versionName>.apk`, et la publie comme release
GitHub sous l'étiquette `v<versionName>`, avec un second fichier `release.json` qui donne
le numéro de version exact à l'app pour sa vérification de mise à jour.

Le numéro de version vient de `github.run_number`, qui ne redescend jamais, même après un
`git revert` : c'est ce qui permet à Android d'accepter chaque APK publié comme une mise
à jour du précédent. La base majeure/mineure (`0.2` par exemple) vit dans
`gradle.properties` (`appVersionBase`).

L'action attend quatre secrets du dépôt :

| Secret | Contenu |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Le keystore de signature, encodé en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Le mot de passe du magasin |
| `ANDROID_KEY_ALIAS` | L'alias de la clé à l'intérieur du magasin |
| `ANDROID_KEY_PASSWORD` | Le mot de passe de la clé |

Les APK sont signés avec le keystore de débogage Android
(`~/.android/debug.keystore`), choix assumé : l'app déjà installée sur l'appareil de
l'utilisateur porte cette signature, et Android refuse une mise à jour dont la signature
diffère. Une construction locale sans ces secrets retombe automatiquement sur la
signature debug de la machine (voir le commentaire dans `app/build.gradle.kts`), donc
`./gradlew assembleRelease` reste utilisable sans rien configurer.

**Si le keystore de débogage est perdu**, aucune publication future ne pourra plus
s'installer par-dessus l'app existante : Android compare les signatures et refuse la mise
à jour. La seule sortie est de désinstaller l'app sur chaque appareil concerné, puis de la
réinstaller avec un APK signé par un nouveau keystore.

Pour publier à la main sans attendre un push, lancez le workflow depuis l'onglet Actions
de GitHub (`workflow_dispatch`), ou avec la CLI : `gh workflow run release.yml`.

Le numéro de version vient de `github.run_number`, qui ne change pas si vous relancez le
même run depuis l'interface GitHub. **Si une publication échoue après la création de la
release** (donc après `gh release create`), une relance de ce run bute sur une étiquette
déjà prise. Dans ce cas, supprimez la release et son étiquette avant de relancer, ou
poussez simplement un nouveau commit sur `main` pour obtenir un run_number neuf.

## Avertissement

Cette app n'est pas un dispositif médical. Elle ne pose aucun diagnostic. L'analyse produite
par le modèle de langage ne remplace pas un avis médical.
