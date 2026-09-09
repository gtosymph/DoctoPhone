package com.kmt.healthanalyzer.ui.importer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kmt.healthanalyzer.data.healthconnect.HEALTH_CONNECT_PLAY_STORE_URL
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectStatus
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import com.kmt.healthanalyzer.ui.theme.domainColors

/**
 * Section Health Connect de l'écran d'import.
 *
 * L'app est en lecture seule vis-à-vis de Health Connect : cette section demande l'accès
 * puis déclenche une synchronisation, elle n'écrit jamais.
 */
@Composable
fun HealthConnectSection(
    status: HealthConnectStatus,
    isSyncing: Boolean,
    syncMessage: String?,
    syncHasIssue: Boolean = false,
    onRequestPermissions: () -> Unit,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    val vitality = HealthAnalyzerTheme.domainColors.vitality

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = vitality.base,
                )
                Text(
                    "Health Connect",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (status) {
                HealthConnectStatus.AVAILABLE -> {
                    Text(
                        "Autorisez la lecture pour compléter vos données avec Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRequestPermissions) { Text("Autoriser l'accès") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onSync, enabled = !isSyncing) { Text("Synchroniser") }
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                HealthConnectStatus.NOT_INSTALLED -> {
                    Text(
                        "Health Connect n'est pas installé sur cet appareil.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { context.openPlayStore() }) { Text("Installer Health Connect") }
                }
                HealthConnectStatus.UPDATE_REQUIRED -> {
                    Text(
                        "Health Connect doit être mis à jour avant de continuer.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { context.openPlayStore() }) { Text("Mettre à jour Health Connect") }
                }
                HealthConnectStatus.NOT_SUPPORTED -> {
                    Text(
                        "Cet appareil ne gère pas Health Connect. L'import de l'archive Samsung Health reste possible.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            syncMessage?.let { message ->
                val color = if (syncHasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}

private fun android.content.Context.openPlayStore() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HEALTH_CONNECT_PLAY_STORE_URL)))
}
