package com.alpinefitness.app.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alpinefitness.app.ui.AiAdvisorViewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAdvisorScreen(
    viewModel: AiAdvisorViewModel,
    onDataChanged: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.streamingContent.length) {
        if (state.messages.isNotEmpty() || state.streamingContent.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = {
                            viewModel.createNewSession()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("+ New chat")
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.sessions, key = { it.id }) { session ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    session.title,
                                    maxLines = 2,
                                    fontWeight = if (session.id == state.selectedSessionId) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            },
                            selected = session.id == state.selectedSessionId,
                            onClick = {
                                viewModel.selectSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            renameId = session.id
                                            renameDraft = session.title
                                        },
                                    ) {
                                        Text("Edit", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                        Icon(
                                            painterResource(com.alpinefitness.app.R.drawable.ic_delete),
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI wellness advisor", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        painterResource(com.alpinefitness.app.R.drawable.ic_more),
                        contentDescription = "Chats",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
            if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState,
            ) {
                if (state.messages.isEmpty() && !state.loading && state.selectedSessionId != null) {
                    item { Text("Ask about habits, exercise consistency, sleep, hydration, or nutrition records.") }
                }
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        role = message.role,
                        content = message.content,
                    )
                }
                if (state.streamingContent.isNotEmpty()) {
                    item(key = "streaming") {
                        ChatBubble(
                            role = "ASSISTANT",
                            content = state.streamingContent + " |",
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    label = { Text("Ask your advisor") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.send(draft, onSent = { draft = "" }, onCompleted = onDataChanged) },
                    enabled = draft.isNotBlank() && !state.sending,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (state.sending) "..." else "Send")
                }
            }
        }
    }

    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { if (it.length <= 100) renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameDraft.isNotBlank()) viewModel.renameSession(id, renameDraft)
                        renameId = null
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatBubble(role: String, content: String) {
    val isUser = role == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            colors = if (isUser) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                CardDefaults.cardColors()
            },
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if (isUser) "You" else "AI advisor", style = MaterialTheme.typography.labelMedium)
                if (isUser) {
                    Text(content)
                } else {
                    AdvisorMarkdown(content = content, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AdvisorMarkdown(content: String, modifier: Modifier = Modifier) {
    val normalizedContent = content.replace("\\n", "\n")
    val segments = parseMarkdownSegments(normalizedContent)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.TextBlock -> {
                    if (segment.text.isNotBlank()) {
                        Markdown(content = segment.text.trim(), modifier = Modifier.fillMaxWidth())
                    }
                }
                is MarkdownSegment.TableBlock -> AdvisorTable(segment)
            }
        }
    }
}

@Composable
private fun AdvisorTable(table: MarkdownSegment.TableBlock) {
    val cellWidth = 116.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
    ) {
        Row(modifier = Modifier.background(headerColor)) {
            table.headers.forEach { header ->
                Text(
                    header,
                    modifier = Modifier.width(cellWidth).padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        table.rows.forEach { row ->
            Row {
                table.headers.indices.forEach { index ->
                    Text(
                        row.getOrNull(index).orEmpty(),
                        modifier = Modifier
                            .width(cellWidth)
                            .border(0.5.dp, borderColor)
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MarkdownSegment
}

private fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val lines = content.lines()
    val segments = mutableListOf<MarkdownSegment>()
    val text = StringBuilder()
    var index = 0

    fun flushText() {
        if (text.isNotBlank()) {
            segments += MarkdownSegment.TextBlock(text.toString())
            text.clear()
        }
    }

    while (index < lines.size) {
        if (index + 1 < lines.size && isTableRow(lines[index]) && isSeparatorRow(lines[index + 1])) {
            val headers = splitTableRow(lines[index])
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && isTableRow(lines[index])) {
                rows += splitTableRow(lines[index])
                index++
            }
            if (headers.isNotEmpty() && rows.isNotEmpty()) {
                flushText()
                segments += MarkdownSegment.TableBlock(headers, rows)
            }
        } else {
            text.appendLine(lines[index])
            index++
        }
    }
    flushText()
    return segments.ifEmpty { listOf(MarkdownSegment.TextBlock(content)) }
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains("|") && trimmed.count { it == '|' } >= 2
}

private fun isSeparatorRow(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { cell ->
        val normalized = cell.replace(":", "").trim()
        normalized.length >= 3 && normalized.all { it == '-' }
    }
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }
