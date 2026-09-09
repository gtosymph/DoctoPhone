# Jeu d'essai de parité

`parity-input.json` contient 180 jours de mesures **synthétiques**. Aucune donnée de
santé réelle n'entre ici, et aucune ne doit y entrer : le dépôt n'en contient jamais.

## À quoi il sert

Le rapport est calculé deux fois : en Kotlin par `ReportBuilder`, en JavaScript par
`HA.reportBuilder`. Les deux doivent produire le **même** modèle, sinon l'app Android et
la version web montrent des chiffres différents pour les mêmes données.

Ce fichier est l'entrée commune du test qui le vérifie :

1. Le test Kotlin `ReportParityTest` lit ce fichier, construit un `ReportModel` et écrit
   le JSON obtenu dans `build/parity/android.json`.
2. Le script `web/test/parity.js` lit le même fichier, appelle le constructeur
   JavaScript et écrit `web/test/out/web.json`.
3. La comparaison des deux fichiers ne doit montrer aucune différence, hormis
   `meta.generatedAt`, qui porte l'instant de création.

## Comment il est structuré

Les clés de premier niveau portent exactement les noms des magasins IndexedDB de la
version web : `sleepNights`, `sleepStages`, `heartRate`, `stress`, `stressAlerts`,
`hrv`, `spo2`, `exercise`, `bodyComposition`, `dailySteps`, `dailyActivity`,
`energyScores`, `bloodPressure`, `ecg`, `snoring`, `respiratory`, `skinTemp`,
`sleepApnea`, `dailyFloors`.

Les noms de champs à l'intérieur suivent les modèles de domaine Kotlin
(`HealthModels.kt` et `ClinicalModels.kt`). Les horodatages sont des entiers en
millisecondes epoch, les dates des chaînes `AAAA-MM-JJ`.

Le fichier porte aussi `zone`, `zoneOffsetMinutes`, `from`, `to`, `sleepTargetHours` et
`profile`, qui servent d'options aux deux constructeurs.

## Ce que le jeu d'essai couvre volontairement

- **36 nuits fractionnées** en deux sessions, sur 166 nuits distinctes. C'est le cas qui
  faisait sous-compter le sommeil d'environ 1h10 par nuit.
- **Des couchers de part et d'autre de minuit**, entre 23h et 2h30, pour vérifier la
  moyenne circulaire et la convention d'heure relative.
- **Des trous** : une nuit sur onze manque, les pesées sont espacées de neuf jours, la
  respiration et la température ne sont mesurées qu'un jour sur sept.
- **Des mesures rares** : deux prises de tension, deux ECG, un test d'apnée. Ces
  sections doivent se montrer sans planter et sans inventer de tendance.
- **Deux origines de données pour l'heure de coucher.** Une nuit sur deux porte
  `localBedTime`, comme les nuits venues de l'export Samsung. L'autre moitié ne l'a pas,
  comme les nuits venues de Health Connect : le constructeur doit alors déduire l'heure
  locale de `bedTime`. Ce trou a réellement vidé le graphique des couchers dans les deux
  versions ; le jeu d'essai est là pour que cela ne se reproduise pas.
- **32 mesures cardiaques par jour**, au-delà des 20 exigées pour calculer une fréquence
  de repos. En dessous, la section cœur reste vide et le défaut passe inaperçu.

## Régénérer le fichier

Le générateur est reproductible : il utilise la graine `20260908`. Ne le modifiez qu'en
sachant que tous les nombres attendus des tests changeront.
