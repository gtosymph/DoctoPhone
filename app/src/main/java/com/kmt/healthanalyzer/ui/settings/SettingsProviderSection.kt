package com.kmt.healthanalyzer.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kmt.healthanalyzer.data.llm.LlmProvider

/** Une carte sélectionnable par fournisseur LLM disponible. */
@Composable
fun ProviderSelector(selected: LlmProvider, onSelect: (LlmProvider) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LlmProvider.entries.forEach { provider ->
            val isSelected = provider == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Rôle radio et cible de sélection uniques : le RadioButton ci-dessous ne
                    // porte plus son propre `onClick`, sinon la carte offre deux zones de
                    // tap qui se chevauchent, ce que le lecteur d'écran annonce deux fois.
                    .selectable(selected = isSelected, onClick = { onSelect(provider) }, role = Role.RadioButton),
                colors = if (isSelected) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Saisie de la clé API du fournisseur choisi, avec bascule de visibilité. */
@Composable
fun ApiKeyCard(
    provider: LlmProvider,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var apiKeyText by rememberSaveable(provider) { mutableStateOf("") }
    var visible by rememberSaveable(provider) { mutableStateOf(false) }

    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clé API — ${provider.displayName}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Clé API") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Masquer la clé" else "Montrer la clé",
                        )
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(apiKeyText); apiKeyText = "" }, enabled = apiKeyText.isNotBlank()) {
                    Text("Enregistrer")
                }
                if (hasKey) {
                    OutlinedButton(onClick = onClear) { Text("Effacer") }
                }
            }
            TextButton(onClick = { context.openConsole(provider.consoleUrl) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Obtenir une clé ${provider.displayName}")
            }
        }
    }
}

private fun android.content.Context.openConsole(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
