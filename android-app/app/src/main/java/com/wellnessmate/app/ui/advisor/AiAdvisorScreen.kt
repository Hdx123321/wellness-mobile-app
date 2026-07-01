package com.wellnessmate.app.ui.advisor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.ui.AiAdvisorViewModel

@Composable
fun AiAdvisorScreen(viewModel: AiAdvisorViewModel) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages or tokens arrive
    LaunchedEffect(state.messages.size, state.streamingContent.length) {
        if (state.messages.isNotEmpty() || state.streamingContent.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

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
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty() && !state.loading && !state.sending) {
                item { Text("Ask about habits, exercise consistency, sleep, hydration, or nutrition records.") }
            }
            items(state.messages, key = { it.id }) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "USER") Arrangement.End else Arrangement.Start,
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (message.role == "USER") "You" else "AI advisor",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(message.content)
                        }
                    }
                }
            }
            // Streaming typewriter card
            item {
                AnimatedVisibility(
                    visible = state.sending || state.streamingContent.isNotEmpty(),
                    enter = fadeIn(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "AI advisor",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (state.streamingContent.isEmpty()) {
                                    // Waiting for first token
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(4.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            "Thinking...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    // Typewriter text with blinking cursor
                                    val annotated = buildAnnotatedString {
                                        append(state.streamingContent)
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                            append("|") // blinking cursor
                                        }
                                    }
                                    Text(annotated)
                                }
                            }
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
                enabled = !state.sending,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.send(draft) { draft = "" } },
                enabled = draft.isNotBlank() && !state.sending,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                if (state.sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}
