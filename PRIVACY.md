# Politique de confidentialité — Health Analyzer

Dernière mise à jour : 8 septembre 2026.

## Ce que l'app collecte

Health Analyzer lit des données de santé que vous lui fournissez vous-même :

1. l'archive d'export Samsung Health que vous choisissez dans l'app ;
2. les données de santé de Health Connect, si vous accordez les autorisations de lecture.

L'app ne collecte rien d'autre. Elle ne demande ni compte, ni identifiant, ni adresse
de courrier électronique. Elle ne contient ni publicité, ni traceur, ni outil de mesure
d'audience.

## Où vivent vos données

Toutes les données de santé sont enregistrées dans une base locale, sur votre appareil.
Elles ne sont envoyées à aucun serveur de l'éditeur. L'éditeur n'exploite aucun serveur.

La sauvegarde automatique Android est désactivée pour cette app : vos données de santé
ne partent pas vers un stockage en nuage à votre insu.

## Ce qui sort de l'appareil, et quand

Une seule action envoie des données hors de l'appareil : quand **vous** lancez une analyse
sur l'écran « Analyse ».

Dans ce cas, l'app envoie au fournisseur de modèle de langage que vous avez choisi
(Anthropic, OpenAI ou Google) :

- des moyennes par semaine : pas, durée et score de sommeil, fréquence cardiaque au repos,
  variabilité cardiaque, stress, poids, score d'énergie ;
- des tendances calculées sur ces moyennes ;
- un résumé de votre régularité de sommeil ;
- la question que vous avez éventuellement écrite.

L'app **n'envoie pas** : vos mesures individuelles, vos horodatages précis, les identifiants
Samsung de vos enregistrements, votre position, ni aucune information qui vous identifie.

Le traitement chez le fournisseur suit sa propre politique de confidentialité. Consultez-la
avant d'utiliser l'analyse.

## Votre clé API

Votre clé API est enregistrée chiffrée sur l'appareil, avec `EncryptedSharedPreferences` et
une clé maîtresse AES-256-GCM gardée par le magasin de clés Android. Elle n'est envoyée
qu'au fournisseur auquel elle appartient. Elle n'est jamais journalisée.

## Health Connect

L'app demande uniquement des autorisations de **lecture**. Elle n'écrit jamais dans
Health Connect. Vous pouvez retirer ces autorisations à tout moment dans les réglages
Android, sans désinstaller l'app.

Elle demande aussi la permission d'historique Health Connect (`READ_HEALTH_DATA_HISTORY`),
qui étend cette lecture au-delà des 30 jours précédant l'octroi des permissions : sans
elle, Health Connect ne rend que le dernier mois, ce qui ne permet pas de tendance sur
plusieurs mois. Cette permission n'élargit rien côté écriture.

## Effacer vos données

Les réglages contiennent un bouton « Effacer toutes les données locales ». Désinstaller
l'app supprime également l'intégralité de la base et des clés.

## Nature du service

Health Analyzer n'est pas un dispositif médical. Elle ne pose aucun diagnostic et ne
propose aucun traitement. Consultez un professionnel de santé pour toute question médicale.
