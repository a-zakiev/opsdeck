package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPanel(
    serverUrl: String,
    authToken: String,
    serverError: String?,
    onApply: (url: String, token: String) -> Unit,
    onClose: () -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlField by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    var tokenField by rememberSaveable(authToken) { mutableStateOf(authToken) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text("Close") }
            }

            Text("Prometheus server", style = MaterialTheme.typography.titleMedium)
            Text(
                "Standard Prometheus HTTP API with node_exporter metrics, https only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                label = { Text("Server URL") },
                singleLine = true,
                isError = serverError != null,
                supportingText = serverError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tokenField,
                onValueChange = { tokenField = it },
                label = { Text("Bearer token (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onApply(urlField, tokenField) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }

            Text("Layout", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onResetLayout, modifier = Modifier.fillMaxWidth()) {
                Text("Reset panel layout")
            }
        }
    }
}
