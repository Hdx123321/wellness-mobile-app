package com.wellnessmate.app.ui.user

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val original = remember { ReminderScheduler.settings(context) }
    var enabled by remember { mutableStateOf(original.enabled) }
    var hour by remember { mutableStateOf(original.hour.toString()) }
    var minute by remember { mutableStateOf(original.minute.toString().padStart(2, '0')) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            ReminderScheduler.save(context, ReminderSettings(enabled, hour.toInt(), minute.toInt()))
            message = "Reminder saved for %02d:%02d.".format(hour.toInt(), minute.toInt())
        } else {
            message = "Notifications are disabled."
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Header("Daily reminder", onBack)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enable reminder", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.take(2) },
                        label = { Text("Hour (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.take(2) },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                Button(
                    onClick = {
                        val h = hour.toIntOrNull()
                        val m = minute.toIntOrNull()
                        if (h !in 0..23 || m !in 0..59) {
                            message = "Enter a valid time."
                        } else if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReminderScheduler.save(context, ReminderSettings(enabled, h!!, m!!))
                            message = if (enabled) "Reminder saved for %02d:%02d.".format(h, m) else "Reminder disabled."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Save reminder") }
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
        Text(title, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onBack) { Text("Back") }
    }
}
