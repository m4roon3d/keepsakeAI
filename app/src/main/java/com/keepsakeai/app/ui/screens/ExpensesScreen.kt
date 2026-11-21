package com.keepsakeai.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.NumberFormat
import java.util.Locale

private val categories = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Other")

private fun categoryIcon(cat: String): Pair<ImageVector, Color> = when (cat) {
    "Food" -> Icons.Rounded.Restaurant to Flow.Amber
    "Transport" -> Icons.Rounded.DirectionsCar to Flow.Sky
    "Shopping" -> Icons.Rounded.ShoppingBag to Flow.Pink
    "Bills" -> Icons.Rounded.Receipt to Flow.Indigo
    "Entertainment" -> Icons.Rounded.Theaters to Flow.Purple
    else -> Icons.Rounded.Receipt to Flow.Teal
}

private fun hasSmsPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

private fun currencySymbol(code: String) = when (code.uppercase()) {
    "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "$code "
}

private fun money(code: String, v: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0; maximumFractionDigits = 2
    }
    return currencySymbol(code) + nf.format(v)
}

@Composable
fun ExpensesScreen(
    viewModel: MindSpaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val expenses by viewModel.expenses.collectAsState()
    val pendingUpi by viewModel.pendingUpi.collectAsState()
    val currency = (context.applicationContext as MindSpaceApp).apiKeyStore.defaultCurrency.ifBlank { "INR" }

    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var smsDays by remember { mutableStateOf(30) }
    var smsBlocked by remember { mutableStateOf(false) }
    val scanningSms by viewModel.isScanningSms.collectAsState()
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsBlocked = !granted
        if (granted) viewModel.scanSmsInboxForUpi(smsDays)
    }

    val total = expenses.sumOf { it.amount }
    val byCategory = expenses.groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlowHeader(eyebrow = "Receipts & UPI", title = "Spend") {
                GlassButton(
                    "UPI Listener",
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                    icon = Icons.Rounded.NotificationsActive,
                    tint = Flow.Emerald
                )
            }
        }

        // Replay the SMS inbox into the approval queue
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Flow.Emerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sms, null, tint = Flow.Emerald, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Import from Messages", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text("Pull past UPI & bank debit SMS into your review queue", fontSize = 11.sp, color = c.muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, label) ->
                        Pill(label, smsDays == d, { smsDays = d }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    if (scanningSms) "Reading messages…" else "Scan messages",
                    enabled = !scanningSms,
                    brush = Flow.spendGradient,
                    icon = Icons.Rounded.Sms,
                    onClick = {
                        if (hasSmsPermission(context)) viewModel.scanSmsInboxForUpi(smsDays)
                        else smsPermission.launch(Manifest.permission.READ_SMS)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (smsBlocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "SMS access is blocked. Open App info → ⋮ → Allow restricted settings, then Permissions → SMS → Allow.",
                        fontSize = 11.sp, color = Flow.Rose,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Read on your phone only. Nothing is uploaded; you approve each payment.", fontSize = 11.sp, color = c.muted)
            }
        }

        if (pendingUpi.isNotEmpty()) {
            item {
                val shape = RoundedCornerShape(26.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (c.dark) Flow.Amber.copy(alpha = 0.10f) else Color(0xCCFFFBEB))
                        .border(1.dp, Flow.Amber.copy(alpha = 0.45f), shape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Inbox, null, tint = Flow.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Waiting for approval (${pendingUpi.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (c.dark) Color(0xFFFCD34D) else Color(0xFF78350F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Approve all", color = Flow.Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.approveAllPendingUpi() }.padding(6.dp)
                        )
                    }
                    pendingUpi.forEach { item ->
                        var selectedCat by remember(item.id) { mutableStateOf(item.suggestedCategory) }
                        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                            Row {
                                Text(item.merchant, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(money(item.currency, item.amount), style = MaterialTheme.typography.titleSmall, color = Flow.Indigo)
                            }
                            Text("${item.bankOrApp}${item.utrRef?.let { " · UTR $it" } ?: ""}", fontSize = 11.sp, color = c.muted)
                            Spacer(Modifier.height(4.dp))
                            Text("“${item.rawMessage}”", fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { cat ->
                                    Pill(cat, selectedCat == cat, { selectedCat = cat })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Dismiss", color = c.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.dismissPendingUpi(item) }.padding(10.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                GradientButton("Approve", onClick = { viewModel.approvePendingUpi(item, selectedCat) }, brush = Flow.spendGradient)
                            }
                        }
                    }
                }
            }
        }

        item {
            GradientCard(Flow.spendGradient, Modifier.fillMaxWidth(), glow = Flow.Emerald) {
                Text("Approved spend", color = Color(0xFFD1FAE5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(money(currency, total), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                if (byCategory.isNotEmpty()) Spacer(Modifier.height(12.dp))
                byCategory.forEach { (cat, sum) ->
                    val pct = if (total > 0) (sum / total).toFloat() else 0f
                    Row(Modifier.fillMaxWidth()) {
                        Text(cat, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${money(currency, sum)} · ${(pct * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(CircleShape).background(Color.White))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (byCategory.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Approved UPI payments and logged expenses show up here.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Quick log", style = MaterialTheme.typography.titleSmall, color = c.text)
                Spacer(Modifier.height(10.dp))
                GlassField(merchant, { merchant = it }, "Merchant (e.g. Swiggy, Metro)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GlassField(
                    amountText, { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount",
                    Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat -> Pill(cat, category == cat, { category = cat }) }
                }
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    "Add transaction",
                    brush = Flow.spendGradient,
                    enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.addManualExpense(merchant, amt, category)
                            merchant = ""
                            amountText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(expenses, key = { it.id }) { exp ->
            val (icon, tint) = categoryIcon(exp.category)
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exp.merchant, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${exp.category} · ${exp.sourceMethod}", fontSize = 11.sp, color = c.muted, maxLines = 1)
                    }
                    Text(money(exp.currency, exp.amount), style = MaterialTheme.typography.titleSmall, color = Flow.Emerald)
                }
            }
        }
    }
}
