package com.wellnessmate.app.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wellnessmate.app.R
import com.wellnessmate.app.data.SessionUser
import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.data.TrackerTypeResponse
import com.wellnessmate.app.ui.TrackerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DASHBOARD = "dashboard"
private const val HISTORY = "history"
private const val FORM = "form/{type}/{id}"
private val builtInTypes = listOf("FOOD", "WEIGHT", "WORKOUT", "STEPS", "SLEEP", "WATER")

/** Main post-onboarding navigation for dashboard, history, and shared tracker form. @author TODO(team member) */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainTrackerNav(user: SessionUser, viewModel: TrackerViewModel, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (route == HISTORY) stringResource(R.string.history) else stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) } },
            )
        },
        bottomBar = {
            if (route == DASHBOARD || route == HISTORY) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { navController.navigate(DASHBOARD) { launchSingleTop = true } }) {
                        Text(stringResource(R.string.dashboard))
                    }
                    TextButton(onClick = { navController.navigate(HISTORY) { launchSingleTop = true } }) {
                        Text(stringResource(R.string.history))
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(DASHBOARD) {
                DashboardScreen(user, viewModel) { type -> navController.navigate("form/$type/-1") }
            }
            composable(HISTORY) {
                HistoryScreen(
                    viewModel = viewModel,
                    onEdit = { navController.navigate("form/${it.type}/${it.id}") },
                    onAdd = { navController.navigate("form/$it/-1") },
                )
            }
            composable(
                route = FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { entry ->
                TrackerFormScreen(
                    type = entry.arguments?.getString("type") ?: "WATER",
                    id = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(user: SessionUser, viewModel: TrackerViewModel, onAdd: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    if (state.loading) return LoadingState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                stringResource(R.string.hello_user, user.displayName ?: user.username),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.dashboard_intro), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            ErrorBanner(state.error, viewModel::clearError)
        }
        items(state.types) { type ->
            val latest = state.entries.firstOrNull { it.type == type.type }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onAdd(type.type) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(typeLabel(type.type), style = MaterialTheme.typography.titleMedium)
                        Text(latest?.let { "${formatAmount(it.amount)} ${it.unit}" } ?: stringResource(R.string.no_entries))
                    }
                    Text(stringResource(R.string.add))
                }
            }
        }
        item {
            TextButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    viewModel: TrackerViewModel,
    onEdit: (TrackerEntryResponse) -> Unit,
    onAdd: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = state.entries.filter { filter == null || it.type == filter }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.all)) })
                    builtInTypes.take(3).forEach { type ->
                        FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(typeLabel(type)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    builtInTypes.drop(3).forEach { type ->
                        FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(typeLabel(type)) })
                    }
                }
                ErrorBanner(state.error, viewModel::clearError)
            }
            if (state.loading) item { LoadingState() }
            else if (visible.isEmpty()) item { Text(stringResource(R.string.no_history), modifier = Modifier.padding(24.dp)) }
            else items(visible, key = { it.id }) { item ->
                TrackerHistoryRow(item, onEdit = { onEdit(item) }, onDelete = { deleteId = item.id })
                HorizontalDivider()
            }
        }
        Button(
            onClick = { onAdd(filter ?: "WATER") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.add_entry)) }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_entry)) },
            text = { Text(stringResource(R.string.delete_confirmation)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); deleteId = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrackerHistoryRow(item: TrackerEntryResponse, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel(item.type), style = MaterialTheme.typography.titleMedium)
            Text("${formatAmount(item.amount)} ${item.unit}${item.detail?.let { " • $it" } ?: ""}")
            Text(formatTime(item.recordedAt), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
    }
}

@Composable
private fun TrackerFormScreen(type: String, id: Long?, viewModel: TrackerViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val definition = state.types.firstOrNull { it.type == type }
    val existing = id?.let { target -> state.entries.firstOrNull { it.id == target } }
    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var detail by rememberSaveable(id) { mutableStateOf("") }
    var notes by rememberSaveable(id) { mutableStateOf("") }
    var localError by rememberSaveable(id) { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        if (existing != null && !initialized) {
            amount = formatAmount(existing.amount)
            detail = existing.detail.orEmpty()
            notes = existing.notes.orEmpty()
            initialized = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(
            stringResource(if (id == null) R.string.add_tracker_title else R.string.edit_tracker_title, typeLabel(type)),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("${definition?.amountLabel ?: stringResource(R.string.amount)} (${definition?.unit.orEmpty()})") },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (definition?.integerOnly == true) KeyboardType.Number else KeyboardType.Decimal,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        if (definition?.detailLabel != null) {
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text(definition.detailLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes_optional)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            minLines = 3,
        )
        (localError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = {
                val numeric = amount.toDoubleOrNull()
                if (numeric == null || (definition?.detailRequired == true && detail.isBlank())) {
                    localError = "Enter a valid amount and required detail."
                } else {
                    localError = null
                    viewModel.save(
                        id = id,
                        request = TrackerEntryRequest(
                            type = type,
                            recordedAt = existing?.recordedAt ?: Instant.now().toString(),
                            amount = numeric,
                            detail = detail.ifBlank { null },
                            notes = notes.ifBlank { null },
                        ),
                        onSaved = onDone,
                    )
                }
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (state.saving) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(stringResource(R.string.save))
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ErrorBanner(error: String?, dismiss: () -> Unit) {
    error?.let {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

private fun typeLabel(type: String): String = type.lowercase().replaceFirstChar(Char::uppercase)
private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
private fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)
