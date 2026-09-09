package com.kmt.healthanalyzer.data.llm

/**
 * Lance [block] et vérifie qu'il lève une exception du type [T].
 *
 * Le module de test n'a pas la dépendance `kotlin-test` : ce petit utilitaire évite de
 * la déclarer uniquement pour `assertFailsWith`.
 */
internal suspend inline fun <reified T : Throwable> assertFailsWithType(
    crossinline block: suspend () -> Unit,
): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Type d'exception inattendu : ${e::class.simpleName}", e)
    }
    throw AssertionError("Aucune exception levée, ${T::class.simpleName} attendue.")
}
