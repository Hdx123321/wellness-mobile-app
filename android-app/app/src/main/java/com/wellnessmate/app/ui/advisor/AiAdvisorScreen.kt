package com.wellnessmate.app.ui.advisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.ui.AiAdvisorViewModel

@Composable
fun AiAdvisorScreen(viewModel: AiAdvisorViewModel) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI wellness advisor", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Uses your profile and recent tracker data. Guidance is informational, not diagnosis or emergency care.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        state.error?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
        }
        if (state.loading) CircularProgressIndicator()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty() && !state.loading) {
                item { Text("Ask about habits, exercise consistency, sleep, hydration, or nutrition records.") }
            }
            items(state.messages, key = { it.id }) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "USER") Arrangement.End else Arrangement.Start,
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if (message.role == "USER") "You" else "AI advisor", style = MaterialTheme.typography.labelMedium)
                            Text(message.content)
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                label = { Text("Ask your advisor") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.send(draft) { draft = "" } },
                enabled = draft.isNotBlank() && !state.sending,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Send") }
        }
    }
}
