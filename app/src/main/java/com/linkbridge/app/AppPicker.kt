package com.linkbridge.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Fenêtre de choix des applications autorisées à utiliser la connexion reçue. */
@Composable
internal fun AppPickerDialog(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
        title = { Text("Applications autorisées") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rechercher") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (apps.isEmpty()) "Chargement des applications…" else "${selected.size} sélectionnée(s)",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.packageName) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { onToggle(app.packageName) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    )
}
