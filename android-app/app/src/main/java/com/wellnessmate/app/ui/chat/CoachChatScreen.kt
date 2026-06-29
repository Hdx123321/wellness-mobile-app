package com.wellnessmate.app.ui.chat

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.data.SessionUser
import com.wellnessmate.app.ui.CoachChatViewModel

@Composable
fun CoachChatScreen(user: SessionUser, viewModel: CoachChatViewModel) {
    val state by viewModel.state.collectAsState()
    val selected = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    var draft by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Coach chat", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (user.role == "COACH") "Your clients" else selected?.let { "Your coach: ${it.coachName}" } ?: "No coach assigned yet",
            modifier = Modifier.padding(bottom = 8.dp),
        )
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
            if (user.role == "COACH" && state.conversations.size > 1) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    state.conversations.forEach { conversation ->
                        TextButton(onClick = { viewModel.selectConversation(conversation.id) }) {
                            Text(conversation.clientName)
                        }
                    }
                }
            }
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
