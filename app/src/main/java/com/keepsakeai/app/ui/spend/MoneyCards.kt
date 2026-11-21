package com.keepsakeai.app.ui.spend

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.util.Calendar
import java.util.Locale

private fun rupees(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)

/** Suggested money questions that open Pilot with the question ready. */
@Composable
fun AskMoneyCard(onAsk: (String) -> Unit) {
    val c = LocalFlow.current
    val prompts = listOf(
        "Where did my money go this month?",
        "Am I spending more on food than last month?",
        "Any unusual or one-off big spends?",
        "How much do I spend on subscriptions a year?",
        "Which merchant do I pay most often?",
    )
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Indigo, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("ask about your money", style = MaterialTheme.typography.titleSmall, color = c.text)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 14.dp)) {
            items(prompts) { p ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f)).clickable { onAsk(p) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(p, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo) }
            }
        }
    }
}

/** Monthly budgets with pace: how much you can still spend per day. */
@Composable
fun BudgetsCard(statuses: List<BudgetStatus>, onEdit: () -> Unit) {
    val c = LocalFlow.current
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("budgets", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Text(
                if (statuses.isEmpty()) "set up" else "edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (statuses.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Set a monthly limit overall or per category. keepsake shows a daily pace and warns you at 80%.", fontSize = 12.sp, color = c.muted)
            return@GlassCard
        }
        Spacer(Modifier.height(6.dp))
        statuses.forEach { b ->
            val state = b.state(day, dim)
            val color = when (state) {
                BudgetState.OK -> Flow.Emerald
                BudgetState.WATCH -> Flow.Amber
                BudgetState.OVER -> Flow.Rose
            }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name.lowercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text("${rupees(b.spent)} / ${rupees(b.limit)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(c.divider)) {
                    Box(
                        Modifier.fillMaxWidth(b.fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
                    )
                    // Where you "should" be today if spending evenly
                    Box(Modifier.fillMaxWidth(day.toFloat() / dim).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.text.copy(alpha = 0.35f)))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    when (state) {
                        BudgetState.OVER -> "over by ${rupees(-b.remaining)}"
                        else -> "${rupees(b.remaining)} left · ${rupees(b.perDayLeft)}/day for ${b.daysLeft} days"
                    },
                    fontSize = 11.sp, color = if (state == BudgetState.OK) c.muted else color
                )
            }
        }
    }
}

@Composable
fun BudgetDialog(current: Map<String, Double>, categories: List<String>, onSave: (Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    val c = LocalFlow.current
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            (listOf(OVERALL) + categories).forEach { k -> put(k, current[k]?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("monthly budgets") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave blank for no limit.", fontSize = 12.sp, color = c.muted)
                (listOf(OVERALL) + categories).forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.lowercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.width(110.dp))
                        GlassField(
                            values[k].orEmpty(), { v -> values[k] = v.filter { it.isDigit() } }, "₹ —", Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientButton("save", onClick = {
                onSave(values.mapNotNull { (k, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap())
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel") } }
    )
}

/** Detected subscriptions and repeat payments with next due dates. */
@Composable
fun SubscriptionsCard(subs: List<Recurring>, remindersOn: Boolean, onToggleReminders: () -> Unit, onTap: (Recurring) -> Unit) {
    val c = LocalFlow.current
    val now = System.currentTimeMillis()
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("subscriptions & repeats", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "${rupees(subs.sumOf { it.monthly })}/month · ${rupees(subs.sumOf { it.monthly } * 12)}/year",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Flow.Purple
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (remindersOn) Flow.Purple.copy(alpha = 0.16f) else c.glass)
                    .clickable(onClick = onToggleReminders),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (remindersOn) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                    if (remindersOn) "Renewal reminders on" else "Renewal reminders off",
                    tint = if (remindersOn) Flow.Purple else c.muted, modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        subs.forEach { s ->
            val d = s.daysUntilDue(now)
            val due = when {
                d < 0 -> "was due ${-d}d ago"
                d == 0 -> "due today"
                d == 1 -> "due tomorrow"
                else -> "in $d days"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTap(s) }.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Flow.Purple.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Text(s.name.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = Flow.Purple) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.cadenceLabel} · ${s.count} payments", fontSize = 11.sp, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(s.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text(due, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (d <= 1) Flow.Amber else c.muted)
                }
            }
        }
    }
}

/** Long-press menu for a payment. */
@Composable
fun PaymentActionsDialog(title: String, onSplit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("What would you like to do with this payment?") },
        confirmButton = { GradientButton("split bill", onClick = onSplit, brush = Flow.spendGradient) },
        dismissButton = { TextButton(onClick = onDelete) { Text("delete", color = Flow.Rose, fontWeight = FontWeight.Bold) } }
    )
}
