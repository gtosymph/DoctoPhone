package com.kmt.healthanalyzer.data.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Nom du paquet du fournisseur officiel Health Connect (application Google). */
internal const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

/** URL de la fiche Play Store permettant d'installer ou de mettre à jour Health Connect. */
const val HEALTH_CONNECT_PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE"

/** État de disponibilité de Health Connect sur l'appareil courant. */
enum class HealthConnectStatus {
    /** Health Connect est installé, à jour, et prêt à recevoir une demande de permissions. */
    AVAILABLE,

    /** Health Connect est présent mais sa version est trop ancienne : une mise à jour est requise. */
    UPDATE_REQUIRED,

    /** Health Connect n'est pas installé sur l'appareil. */
    NOT_INSTALLED,

    /** L'appareil ne peut pas exécuter Health Connect (version Android trop ancienne). */
    NOT_SUPPORTED,
}

/**
 * Détermine l'état de disponibilité de Health Connect sur l'appareil.
 *
 * L'app est en LECTURE SEULE vis-à-vis de Health Connect : ce vérificateur ne fait jamais
 * d'écriture. Il sert uniquement à décider si l'app peut lancer une demande de permissions
 * ou doit d'abord rediriger l'utilisateur vers le Play Store (installation ou mise à jour).
 */
@Singleton
class HealthConnectAvailabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Rend l'état de disponibilité actuel de Health Connect. */
    fun status(): HealthConnectStatus {
        // Health Connect exige au minimum Android 9 (API 28) pour être installable.
        // Le minSdk de l'app est 29, donc cette branche est normalement inatteignable ;
        // elle est conservée pour rester correcte si le minSdk venait à être abaissé.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return HealthConnectStatus.NOT_SUPPORTED
        }
        return when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectStatus.UPDATE_REQUIRED
            // SDK_UNAVAILABLE : le système d'exploitation est compatible (vérifié ci-dessus)
            // mais Health Connect n'est pas installé. La seule action possible est le Play Store.
            else -> HealthConnectStatus.NOT_INSTALLED
        }
    }

    /** URL du Play Store à ouvrir pour installer ou mettre à jour Health Connect. */
    fun playStoreUrl(): String = HEALTH_CONNECT_PLAY_STORE_URL
}
