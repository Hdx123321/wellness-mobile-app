package com.alpinefitness.app.ui.advisor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.mikepenz.markdown.m3.Markdown
import com.alpinefitness.app.ui.AiAdvisorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAdvisorScreen(viewModel: AiAdvisorViewModel) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.streamingContent.length) {
        if (state.messages.isNotEmpty() || state.streamingContent.isNotEmpty())
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorBlink",
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(onClick = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("+ New chat") }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.sessions, key = { it.id }) { session ->
                        NavigationDrawerItem(
                            label = {
                                Text(session.title, maxLines = 2,
                                    fontWeight = if (session.id == state.selectedSessionId) FontWeight.Bold else FontWeight.Normal)
                            },
                            selected = session.id == state.selectedSessionId,
                            onClick = {
                                viewModel.selectSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                Row {
                                    TextButton(onClick = {
                                        renameId = session.id; renameDraft = session.title
                                    }) { Text("✏", style = MaterialTheme.typography.bodySmall) }
                                    IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                        Icon(painterResource(com.alpinefitness.app.R.drawable.ic_delete), "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI wellness advisor", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(painterResource(com.alpinefitness.app.R.drawable.ic_more), "Chats", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Uses your profile and recent tracker data. Guidance is informational, not diagnosis or emergency care.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            state.error?.let {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                }
            }
            if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp), state = listState,
            ) {
                if (state.messages.isEmpty() && !state.loading && state.selectedSessionId != null) {
                    item { Text("Ask about habits, exercise consistency, sleep, hydration, or nutrition records.") }
                }
                items(state.messages, key = { it.id }) { message ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.role == "USER") Arrangement.End else Arrangement.Start) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.86f),
                            colors = if (message.role == "USER") CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) else CardDefaults.cardColors(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(if (message.role == "USER") "You" else "AI advisor", style = MaterialTheme.typography.labelMedium)
                                if (message.role == "USER") Text(message.content)
                                else Markdown(content = message.content, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                if (state.streamingContent.isNotEmpty()) {
                    item(key = "streaming") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("AI advisor", style = MaterialTheme.typography.labelMedium)
                                    Text((state.streamingContent) + " ▎")
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    label = { Text("Ask your advisor") }, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.send(draft) { draft = "" } },
                    enabled = draft.isNotBlank() && !state.sending,
                    modifier = Modifier.padding(start = 8.dp)) { Text(if (state.sending) "..." else "Send") }
            }
        }
    }

    // Rename dialog
    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(value = renameDraft, onValueChange = { if (it.length <= 100) renameDraft = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (renameDraft.isNotBlank()) viewModel.renameSession(id, renameDraft); renameId = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } },
        )
    }
}
