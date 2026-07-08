package com.alpinefitness.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.alpinefitness.app.data.SessionUser
import com.alpinefitness.app.ui.CoachChatViewModel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch

@Composable
fun CoachChatScreen(user: SessionUser, viewModel: CoachChatViewModel) {
    val state by viewModel.state.collectAsState()
    val selected = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    var draft by rememberSaveable { mutableStateOf("") }
    var showNewChatDialog by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        viewModel.setChatVisible(true)
        onDispose { viewModel.setChatVisible(false) }
    }

    // New chat dialog for coaches
    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("New conversation") },
            text = {
                if (state.availableClients.isEmpty()) {
                    Column {
                        Text("No clients available.")
                        TextButton(onClick = { viewModel.refreshClients() }) { Text("Refresh") }
                    }
                } else {
                    LazyColumn {
                        items(state.availableClients, key = { it.id }) { client ->
                            TextButton(onClick = {
                                viewModel.createConversation(client.id, null)
                                showNewChatDialog = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(client.displayName ?: client.username)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNewChatDialog = false }) { Text("Close") } },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = user.role == "COACH",
        drawerContent = {
            ModalDrawerSheet {
                Text("Conversations", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                if (user.role == "COACH") {
                    Button(
                        onClick = {
                            viewModel.refreshClients()
                            showNewChatDialog = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("+ New conversation") }
                }
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        NavigationDrawerItem(
                            label = { Text(conversation.subject ?: conversation.clientName) },
                            badge = { if (conversation.unreadCount > 0) UnreadDot() },
                            selected = conversation.id == state.selectedConversationId,
                            onClick = {
                                viewModel.selectConversation(conversation.id)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                }
            }
        },
    ) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Coach chat", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (user.role == "COACH") selected?.let { "Chat with ${it.clientName}" } ?: "Your clients"
                    else selected?.let { "Your coach: ${it.coachName}" } ?: "No coach assigned yet",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (user.role == "COACH") {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        painterResource(com.alpinefitness.app.R.drawable.ic_more_two),
                        "Choose conversation",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        state.error?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
        }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.conversations.isEmpty()) {
            Text(
                if (user.role == "COACH") "No clients are assigned to you."
                else "A coach account must be provisioned before chat can begin.",
                modifier = Modifier.padding(vertical = 24.dp),
            )
            Button(onClick = viewModel::refreshConversations) { Text("Check again") }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val mine = message.senderId == user.userId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        Card(modifier = Modifier.fillMaxWidth(0.82f)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(message.senderName, style = MaterialTheme.typography.labelMedium)
                                Text(message.content)
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    label = { Text("Message") },
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
    }
}

@Composable
private fun UnreadDot() {
    Box(Modifier.size(9.dp).background(Color(0xFFD32F2F), CircleShape))
}
