package com.kmt.healthanalyzer.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Module Hilt fournissant le [HealthConnectClient].
 *
 * `HealthConnectClient.getOrCreate(context)` lève une `IllegalStateException` si le SDK
 * Health Connect n'est pas disponible sur l'appareil (non installé, à mettre à jour...).
 * Hilt construit ses singletons au premier point d'injection, potentiellement avant que
 * l'app ait eu l'occasion de vérifier la disponibilité via [HealthConnectAvailabilityChecker].
 * Fournir le client directement en `@Provides @Singleton` ferait donc planter l'app au
 * démarrage sur tout appareil sans Health Connect installé.
 *
 * Solution retenue : cette méthode `@Provides` n'est PAS annotée `@Singleton`. Dagger/Hilt
 * génère alors automatiquement, pour tout binding, la possibilité de l'injecter sous forme
 * de `Provider<HealthConnectClient>` (voir [HealthConnectReader]). L'appelant ne résout ce
 * provider — donc n'exécute `getOrCreate` — qu'au moment réel de la lecture, après avoir
 * vérifié la disponibilité, et peut attraper l'exception le cas échéant.
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {

    @Provides
    fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)
}
