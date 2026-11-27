package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.email.EmailAccount
import com.keepsakeai.app.email.MailProvider
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.launch

/** Connected mailboxes: trove reads receipts, orders, tickets, bills and renewals from them. */
@Composable
fun EmailCard(viewModel: MindSpaceViewModel) {
    val c = LocalFlow.current
    val scope = rememberCoroutineScope()
    val accounts by viewModel.emailAccounts.collectAsState()
    val syncing by viewModel.emailSyncing.collectAsState()
    var connecting by remember { mutableStateOf(false) }
    var confirmRemoveAll by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    GlassCard(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = Gap.l, vertical = Gap.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Flow.Sky.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mail, null, tint = Flow.Sky, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(Gap.m))
            Text("Email", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Text("receipts · orders · tickets · bills", fontSize = Ts.meta, color = c.muted)
        }
        AnimatedVisibility(syncing) {
            Column(Modifier.padding(top = Gap.m)) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Flow.Sky, trackColor = c.glass)
                Spacer(Modifier.height(Gap.xs))
                Text("reading your inbox…", fontSize = Ts.meta, color = c.muted)
            }
        }
        if (accounts.isEmpty()) {
            Spacer(Modifier.height(Gap.s))
            Text(
                "Connect Gmail or Yahoo with an app password. trove picks up card and autopay charges for the subscription radar, " +
                    "turns tickets into reminders and bills into to-dos.",
                fontSize = Ts.meta, color = c.muted
            )
        }
        accounts.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = c.divider)
            AccountRow(a, syncing, onSync = { viewModel.syncEmailNow() }, onRemove = { viewModel.disconnectEmail(a.id) })
        }
        Spacer(Modifier.height(Gap.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            GradientButton("connect email", icon = Icons.Rounded.AlternateEmail, onClick = { connecting = true }, modifier = Modifier.weight(1f))
            if (accounts.isNotEmpty()) GlassButton("sync now", icon = Icons.Rounded.Sync, enabled = !syncing, onClick = { viewModel.syncEmailNow() }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Gap.s))
        Text(
            "Only emails whose subject looks like a receipt, order, ticket, bill or renewal are opened, read-only. Their subject and " +
                "trimmed text (no footers) go to Gemini, logged in AI activity. Promos are skipped. Your app password is encrypted on this phone.",
            fontSize = Ts.meta, color = c.muted
        )
        if (accounts.isNotEmpty()) {
            Text(
                "remove everything imported from email",
                fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Rose,
                modifier = Modifier.padding(top = Gap.s).clickable { confirmRemoveAll = true }
            )
        }
        message?.let {
            Spacer(Modifier.height(Gap.s))
            Text(it, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
        }
    }

    if (connecting) {
        ConnectEmailDialog(
            onConnect = { address, password, provider, host, port -> viewModel.connectEmail(address, password, provider, host, port) },
            onDone = { connected ->
                connecting = false
                if (connected) message = "Connected. Reading the last 30 days now; it can take a few minutes."
            }
        )
    }
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Flow.Rose) },
            title = { Text("remove email imports?") },
            text = { Text("Deletes every memory and payment trove made from your emails. Your mailboxes stay connected and those emails won't be read again.", fontSize = Ts.body) },
            confirmButton = {
                GradientButton("remove", onClick = {
                    confirmRemoveAll = false
                    scope.launch { val n = viewModel.removeEmailImports(); message = "Removed $n email memories and their payments." }
                })
            },
            dismissButton = { TextButton(onClick = { confirmRemoveAll = false }) { Text("cancel") } }
        )
    }
}

@Composable
private fun AccountRow(a: EmailAccount, syncing: Boolean, onSync: () -> Unit, onRemove: () -> Unit) {
    val c = LocalFlow.current
    var askRemove by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = Gap.m), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background((if (a.provider == MailProvider.YAHOO) Flow.Purple else Flow.Sky).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(a.address.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = if (a.provider == MailProvider.YAHOO) Flow.Purple else Flow.Sky)
        }
        Spacer(Modifier.width(Gap.m))
        Column(Modifier.weight(1f)) {
            Text(a.address, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                a.lastError != null -> a.lastError
                a.lastSyncAt == 0L -> if (syncing) "first read in progress" else "waiting for first read"
                else -> "${a.provider.label} · read ${ago(a.lastSyncAt)} · ${a.kept} kept"
            }
            Text(status, fontSize = Ts.meta, color = if (a.lastError != null) Flow.Rose else c.muted, maxLines = 2)
        }
        if (a.lastError != null) {
            Icon(Icons.Rounded.Sync, "retry", tint = Flow.Sky, modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onSync).padding(Gap.s))
        }
        Icon(Icons.Rounded.DeleteOutline, "disconnect", tint = c.muted, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { askRemove = true }.padding(Gap.s))
    }
    if (askRemove) {
        AlertDialog(
            onDismissRequest = { askRemove = false },
            title = { Text("disconnect ${a.address}?") },
            text = { Text("trove stops reading it and forgets the app password. What it already kept stays. You can also delete the app password in your account.", fontSize = Ts.body) },
            confirmButton = { GradientButton("disconnect", onClick = { askRemove = false; onRemove() }) },
            dismissButton = { TextButton(onClick = { askRemove = false }) { Text("cancel") } }
        )
    }
}

@Composable
private fun ConnectEmailDialog(
    onConnect: suspend (String, String, MailProvider, String, Int) -> String?,
    onDone: (Boolean) -> Unit,
) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(MailProvider.GMAIL) }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Typing a Gmail or Yahoo address picks the provider for you.
    LaunchedEffect(address) { MailProvider.forAddress(address)?.let { provider = it } }
    val microsoft = MailProvider.isMicrosoft(address)

    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        icon = { Icon(Icons.Rounded.Mail, null, tint = Flow.Sky) },
        title = { Text("connect email") },
        text = {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                    MailProvider.entries.forEach { p -> Pill(p.label, provider == p, { provider = p; error = null }, Modifier.weight(1f)) }
                }
                Text(provider.steps, fontSize = Ts.meta, color = c.muted)
                provider.appPasswordUrl?.let { url ->
                    Row(
                        Modifier.clip(CircleShape).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }.padding(vertical = Gap.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, tint = Flow.Sky, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(Gap.xs))
                        Text("open ${provider.label} app passwords", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Sky)
                    }
                }
                GlassField(
                    address, { address = it; error = null }, "you@${if (provider == MailProvider.YAHOO) "yahoo.com" else "gmail.com"}",
                    Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.AlternateEmail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                GlassField(
                    password, { password = it; error = null }, "App password", Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Rounded.Key, visualTransformation = PasswordVisualTransformation()
                )
                if (provider == MailProvider.OTHER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        GlassField(host, { host = it }, "imap.example.com", Modifier.weight(1f))
                        GlassField(port, { port = it.filter(Char::isDigit).take(5) }, "993", Modifier.width(84.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                if (microsoft) {
                    Text("Outlook and Hotmail no longer allow app passwords for other apps, so they can't connect this way.", fontSize = Ts.meta, color = Flow.Amber)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Flow.Sky, trackColor = c.glass)
                error?.let { Text(it, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Rose) }
            }
        },
        confirmButton = {
            GradientButton(
                if (busy) "checking…" else "connect",
                enabled = !busy && !microsoft && address.contains('@') && password.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val h = if (provider == MailProvider.OTHER) host else provider.host
                        val p = if (provider == MailProvider.OTHER) port.toIntOrNull() ?: 993 else provider.port
                        val err = onConnect(address, password, provider, h, p)
                        busy = false
                        if (err == null) onDone(true) else error = err
                    }
                }
            )
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("cancel") } }
    )
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}
