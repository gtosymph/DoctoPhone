package com.kmt.healthanalyzer.ui.state

import com.kmt.healthanalyzer.data.llm.LlmError

/**
 * Le geste qu'un état propose, quand il en propose un.
 *
 * Le libellé vit ici et non dans chaque écran : « Réessayer » écrit trois fois finit par
 * s'écrire « Recommencer » la quatrième.
 */
enum class StateAction(val label: String) {
    IMPORT("Importer mes données"),
    GRANT_HISTORY("Autoriser l'accès"),
    OPEN_SETTINGS("Ouvrir les réglages"),
    RETRY("Réessayer"),
    CANCEL("Annuler"),

    /** Aucun geste : une attente, ou un constat dont rien ne dépend. */
    NONE(""),
}

/** Ce qu'un écran montre quand quelque chose a échoué : la phrase, et le geste qui suit. */
data class FailureCopy(val message: String, val action: StateAction)

/**
 * Les textes des états de l'app, en un seul endroit.
 *
 * Ce sont les seules phrases que l'utilisateur lit quand rien ne va, et ce sont celles
 * qu'on écrit le plus vite, au fil des écrans. Le résultat habituel est un ton par écran
 * et, plus grave, des messages qui décrivent la panne sans dire comment en sortir :
 * « La requête réseau a échoué » laisse son lecteur devant un mur.
 *
 * Trois règles les tiennent, et [StateCopyTest] les vérifie une par une :
 *
 * 1. **Un état vide dit toujours pourquoi.** « Aucune dérive » et « pas assez de données »
 *    se ressemblent à l'écran et ne veulent pas dire la même chose.
 * 2. **Une attente dit toujours sur quoi elle porte** : un mois, un nombre de jours, une
 *    étape. Sans cela, elle ne se distingue pas d'un blocage.
 * 3. **Un échec dit toujours quoi faire ensuite.** Aucune excuse, aucune formule vague,
 *    et jamais la cause technique brute — une trace d'exception n'aide personne.
 *
 * Les nombres s'écrivent en chiffres, partout. Le tableau des specs mêle « 21 jours » et
 * « Cinq jours » ; un écran qui fait les deux se lit comme écrit à deux mains, et la
 * typographie du projet aligne les chiffres en colonne précisément pour qu'on les
 * compare d'un coup d'œil.
 */
object StateCopy {

    // ------------------------------------------------------------------ vides

    const val NO_DATA =
        "Aucune donnée pour l'instant. Importez votre export Samsung Health, " +
            "ou connectez Health Connect."

    /**
     * Health Connect ne rend que les trente derniers jours tant que la permission
     * d'historique manque. Le dire au moment où l'import s'arrête court, plutôt que de
     * laisser croire à un export incomplet.
     */
    const val HISTORY_PERMISSION_MISSING =
        "Health Connect ne donne accès qu'aux 30 derniers jours. Autorisez l'accès aux " +
            "données antérieures pour remonter plus loin."

    /** « Il faut 21 jours de référence pour comparer. Vous en avez 12. » */
    fun shortBaseline(required: Int, measured: Int): String =
        "Il faut $required jours de référence pour comparer. Vous en avez $measured."

    /** « 5 jours mesurés sont nécessaires cette semaine. Vous en avez 3. » */
    fun sparseWeek(required: Int, measured: Int): String =
        "$required jours mesurés sont nécessaires cette semaine. Vous en avez $measured."

    // ------------------------------------------------------------------ attentes

    /** « Récupération de l'historique… septembre 2026 » */
    fun syncing(step: String): String = "Récupération de l'historique… $step"

    /** « Calcul du rapport sur 294 jours… » */
    fun buildingReport(days: Int): String = "Calcul du rapport sur $days jours…"

    const val ANALYZING = "Analyse en cours…"

    /**
     * L'attente du bloc de dérives.
     *
     * Elle nomme la fenêtre comparée — sept jours contre les huit semaines précédentes —
     * parce que c'est la règle la moins évidente du bloc, et celle qui explique pourquoi
     * une dérive apparaît ou n'apparaît pas.
     */
    const val COMPARING_WEEK =
        "Comparaison de la semaine écoulée à vos huit dernières semaines…"

    // ------------------------------------------------------------------ échecs

    const val MISSING_API_KEY =
        "L'analyse a besoin d'une clé API. Elle reste sur votre appareil."

    const val NETWORK_FAILED =
        "La requête n'a pas abouti. Vérifiez votre connexion, puis réessayez."

    /**
     * Traduit une cause d'échec en une phrase et un geste.
     *
     * La cause technique ne traverse jamais : ni message d'exception, ni nom de classe,
     * ni corps de réponse. Le journal la garde pour qui débogue ; l'écran, lui, n'a que
     * deux choses à dire — ce qui s'est passé en une phrase, et quoi faire ensuite.
     */
    fun forFailure(cause: Throwable?): FailureCopy = when (cause) {
        is LlmError.MissingApiKey -> FailureCopy(MISSING_API_KEY, StateAction.OPEN_SETTINGS)

        is LlmError.InvalidApiKey -> FailureCopy(
            "Le fournisseur refuse cette clé API. Vérifiez-la dans les réglages, " +
                "ou enregistrez-en une nouvelle.",
            StateAction.OPEN_SETTINGS,
        )

        is LlmError.RateLimited -> FailureCopy(
            cause.retryAfterSeconds
                ?.let { "Le fournisseur limite le débit. Attendez $it secondes, puis réessayez." }
                ?: "Le fournisseur limite le débit des requêtes. Attendez un instant, puis réessayez.",
            StateAction.RETRY,
        )

        is LlmError.Network -> FailureCopy(NETWORK_FAILED, StateAction.RETRY)

        // Le code HTTP reste : il ne coûte rien à lire, il distingue une panne du
        // fournisseur d'une erreur de la requête, et il sert à qui rapporte le problème.
        is LlmError.Server -> FailureCopy(
            "Le fournisseur ne répond pas correctement (erreur ${cause.statusCode}). " +
                "Réessayez dans quelques minutes.",
            StateAction.RETRY,
        )

        is LlmError.Malformed -> FailureCopy(
            "La réponse du fournisseur est illisible. Réessayez, ou changez de modèle " +
                "dans les réglages.",
            StateAction.RETRY,
        )

        else -> FailureCopy(
            "L'opération n'a pas abouti. Réessayez ; si cela persiste, vérifiez vos " +
                "réglages.",
            StateAction.RETRY,
        )
    }
}
