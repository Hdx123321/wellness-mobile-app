package com.wellnessmate.app.ui.user

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wellnessmate.app.data.SessionUser
import com.wellnessmate.app.reminder.ReminderScheduler
import com.wellnessmate.app.reminder.ReminderSettings
import kotlinx.coroutines.launch

@Composable
fun UserManagementScreen(
    user: SessionUser,
    onProfile: () -> Unit,
    onReminder: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Header("Account", onBack)
        Text(user.displayName ?: user.username, style = MaterialTheme.typography.headlineSmall)
        Text("@${user.username}", modifier = Modifier.padding(bottom = 16.dp))
        MenuCard("Profile management", "Health information and goals", onProfile)
        MenuCard("Daily reminder", "Set a local notification alarm", onReminder)
        MenuCard("Log out", "Clear this device session", onLogout)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ReminderScheduler.settings(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var showEditor by remember { mutableStateOf(false) }

    // ── Save on toggle ──
    fun saveToggle(on: Boolean) {
        enabled = on
        val s = ReminderScheduler.settings(context)
        ReminderScheduler.save(context, s.copy(enabled = on))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Header("Daily reminder", onBack)

        // ── Summary card ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(settings.title, style = MaterialTheme.typography.titleMedium)
                    Text("%02d:%02d".format(settings.hour, settings.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showEditor = true }) { Icon(painterResource(com.wellnessmate.app.R.drawable.ic_settings), "Edit", tint = MaterialTheme.colorScheme.primary) }
                Switch(checked = enabled, onCheckedChange = { saveToggle(it) })
            }
        }
    }

    // ── Edit bottom sheet ──
    if (showEditor) {
        val current = ReminderScheduler.settings(context)
        var editHour by remember { mutableStateOf(current.hour.toString()) }
        var editMinute by remember { mutableStateOf(current.minute.toString().padStart(2, '0')) }
        var editTitle by remember { mutableStateOf(current.title) }
        var editContent by remember { mutableStateOf(current.content) }
        var editMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val h = editHour.toIntOrNull() ?: 20
                val m = editMinute.toIntOrNull() ?: 0
                ReminderScheduler.save(context, ReminderSettings(enabled, h, m,
                    editTitle.ifBlank { "WellnessMate daily check-in" },
                    editContent.ifBlank { "Review today's trackers and record anything missing." }))
                editMessage = "Saved for %02d:%02d.".format(h, m)
            } else {
                editMessage = "Notifications are disabled."
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Edit reminder", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showEditor = false }) { Text("Close") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editHour,
                        onValueChange = { editHour = it.take(2) },
                        label = { Text("Hour (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editMinute,
                        onValueChange = { editMinute = it.take(2) },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it.take(100) },
                    label = { Text("Notification title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it.take(200) },
                    label = { Text("Notification body") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                editMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

                Button(
                    onClick = {
                        val h = editHour.toIntOrNull()
                        val m = editMinute.toIntOrNull()
                        if (h !in 0..23 || m !in 0..59) {
                            editMessage = "Enter a valid time."
                        } else if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReminderScheduler.save(context, ReminderSettings(enabled, h!!, m!!,
                                editTitle.ifBlank { "WellnessMate daily check-in" },
                                editContent.ifBlank { "Review today's trackers and record anything missing." }))
                            editMessage = "Saved."
                            scope.launch {
                                sheetState.hide()
                                showEditor = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Save reminder") }

                // Extra space at bottom so content isn't clipped
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(painterResource(com.wellnessmate.app.R.drawable.ic_back), "Back", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
