package com.kmt.healthanalyzer.data.update

import kotlinx.serialization.Serializable

/**
 * Contrat `release.json`, joint à chaque release par l'action GitHub qui publie l'APK.
 *
 * Ces trois noms de champs sont figés par le contrat entre l'action GitHub (le producteur)
 * et ce lecteur : ne pas les renommer sans mettre à jour l'action en parallèle.
 */
@Serializable
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
)
