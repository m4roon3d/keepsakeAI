package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.keepsakeai.app.backup.BackupManager
import com.keepsakeai.app.data.preferences.SecurityPrefs
import com.keepsakeai.app.security.BiometricVaultHelper
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GlassToggle
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App lock plus password-encrypted backup and restore (save it to Google Drive from the picker). */
@Composable
fun PrivacyCard(viewModel: MindSpaceViewModel?) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val sec = remember { SecurityPrefs(context) }
    var lockOn by remember { mutableStateOf(sec.appLock) }
    var lastBackup by remember { mutableStateOf(sec.lastBackupAt) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Backup: ask for a password, then where to save.
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { backupPassword = ""; return@rememberLauncherForActivityResult }
        busy = true
        scope.launch {
            message = runCatching { BackupManager.export(context, uri, backupPassword.toCharArray()) }.fold(
                onSuccess = { s ->
                    sec.lastBackupAt = System.currentTimeMillis(); lastBackup = sec.lastBackupAt
                    "Backed up ${s.memories} memories, ${s.payments} payments and ${s.facts} facts."
                },
                onFailure = { "Backup failed: ${it.localizedMessage}" }
            )
            backupPassword = ""
            busy = false
        }
    }

    // Restore: pick the file, then ask for its password.
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Gap.l, vertical = Gap.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = Flow.Emerald, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Gap.m))
            Text("Privacy & backup", style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        Spacer(Modifier.height(Gap.m))
        GlassToggle(
            "App lock (fingerprint / face / PIN)", lockOn,
            onToggle = {
                if (lockOn) { sec.appLock = false; lockOn = false; return@GlassToggle }
                if (activity == null || !BiometricVaultHelper.canAuthenticate(activity)) {
                    message = "Set up a screen lock or fingerprint on your phone first."
                    return@GlassToggle
                }
                BiometricVaultHelper.authenticate(activity, onSuccess = { sec.appLock = true; lockOn = true; message = "App lock on. trove locks after 3 minutes away." }, onError = { })
            },
            modifier = Modifier.fillMaxWidth(),
            accent = Flow.Emerald
        )
        Spacer(Modifier.height(Gap.m))
        HorizontalDivider(color = c.divider)
        Spacer(Modifier.height(Gap.m))
        Text(
            "Encrypted backup of memories, payments, facts, budgets and reminders (not your API keys or photos). Choose Google Drive in the save screen to keep it in the cloud.",
            fontSize = Ts.meta, color = c.muted
        )
        Spacer(Modifier.height(Gap.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            GradientButton(if (busy) "working…" else "back up", enabled = !busy, icon = Icons.Rounded.Backup, onClick = { askBackupPassword = true }, modifier = Modifier.weight(1f))
            GlassButton("restore", enabled = !busy, icon = Icons.Rounded.Restore, onClick = { openDoc.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Gap.s))
        Text(
            if (lastBackup > 0) "last backup " + SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(lastBackup)).lowercase() else "no backup yet",
            fontSize = Ts.meta, color = c.muted
        )
        message?.let {
            Spacer(Modifier.height(Gap.s))
            Text(it, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
        }
    }

    if (askBackupPassword) {
        PasswordDialog(
            title = "protect your backup",
            body = "You'll need this password to restore. trove can't recover it.",
            confirmLabel = "choose where to save",
            needsConfirm = true,
            onConfirm = { pw ->
                backupPassword = pw
                askBackupPassword = false
                val name = "trove-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".ksai"
                createDoc.launch(name)
            },
            onDismiss = { askBackupPassword = false }
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = "restore backup",
            body = "This replaces what's in trove now with the backup's contents.",
            confirmLabel = "restore",
            needsConfirm = false,
            onConfirm = { pw ->
                restoreUri = null
                busy = true
                scope.launch {
                    message = runCatching { BackupManager.restore(context, uri, pw.toCharArray()) }.fold(
                        onSuccess = { s -> viewModel?.refreshWidget(); "Restored ${s.memories} memories, ${s.payments} payments and ${s.facts} facts." },
                        onFailure = { it.message ?: "Restore failed." }
                    )
                    busy = false
                }
            },
            onDismiss = { restoreUri = null }
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmLabel: String,
    needsConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalFlow.current
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 6 && (!needsConfirm || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null, tint = Flow.Emerald) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                Text(body, fontSize = Ts.meta, color = c.muted)
                GlassField(pw, { pw = it }, "Password (6+ characters)", Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                if (needsConfirm) {
                    GlassField(pw2, { pw2 = it }, "Repeat password", Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                    if (pw2.isNotEmpty() && pw != pw2) Text("Passwords don't match.", fontSize = Ts.meta, color = Flow.Rose)
                }
            }
        },
        confirmButton = { GradientButton(confirmLabel, enabled = ok, onClick = { onConfirm(pw) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel") } }
    )
}
