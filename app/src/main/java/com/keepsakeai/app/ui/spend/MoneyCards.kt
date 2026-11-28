package com.keepsakeai.app.ui.spend

import com.keepsakeai.app.ui.components.Pill
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
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
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = Gap.l, top = Gap.m, bottom = Gap.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Indigo, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Gap.s))
            Text("ask about your money", style = MaterialTheme.typography.titleSmall, color = c.text)
        }
        Spacer(Modifier.height(Gap.s))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s), contentPadding = PaddingValues(end = Gap.l)) {
            items(prompts) { p ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f)).clickable { onAsk(p) }
                        .padding(horizontal = Gap.m, vertical = Gap.s)
                ) { Text(p, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo) }
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
                if (statuses.isEmpty()) "set up" else "edit", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onEdit).padding(horizontal = Gap.s, vertical = Gap.xs)
            )
        }
        if (statuses.isEmpty()) {
            Spacer(Modifier.height(Gap.s))
            Text("Set a monthly limit overall or per category. trove shows a daily pace and warns you at 80%.", fontSize = Ts.meta, color = c.muted)
            return@GlassCard
        }
        Spacer(Modifier.height(Gap.s))
        statuses.forEach { b ->
            val state = b.state(day, dim)
            val color = when (state) {
                BudgetState.OK -> Flow.Emerald
                BudgetState.WATCH -> Flow.Amber
                BudgetState.OVER -> Flow.Rose
            }
            Column(Modifier.padding(vertical = Gap.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name.lowercase(), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text("${rupees(b.spent)} / ${rupees(b.limit)}", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = c.text)
                }
                Spacer(Modifier.height(Gap.xs))
                val fill by androidx.compose.animation.core.animateFloatAsState(
                    b.fraction.toFloat().coerceIn(0f, 1f), androidx.compose.animation.core.tween(800), label = "budget"
                )
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(c.divider)) {
                    Box(
                        Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
                    )
                    // Where you "should" be today if spending evenly
                    Box(Modifier.fillMaxWidth(day.toFloat() / dim).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.text.copy(alpha = 0.35f)))
                    }
                }
                Spacer(Modifier.height(Gap.xs))
                Text(
                    when (state) {
                        BudgetState.OVER -> "over by ${rupees(-b.remaining)}"
                        else -> "${rupees(b.remaining)} left · ${rupees(b.perDayLeft)}/day for ${b.daysLeft} days"
                    },
                    fontSize = Ts.meta, color = if (state == BudgetState.OK) c.muted else color
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
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                Text("Leave blank for no limit.", fontSize = Ts.meta, color = c.muted)
                (listOf(OVERALL) + categories).forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.lowercase(), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.width(110.dp))
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
                    fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Purple
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
        Spacer(Modifier.height(Gap.s))
        subs.forEach { s ->
            val d = s.daysUntilDue(now)
            val due = when {
                d < 0 -> "was due ${-d}d ago"
                d == 0 -> "due today"
                d == 1 -> "due tomorrow"
                else -> "in $d days"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTap(s) }.padding(horizontal = Gap.xs, vertical = Gap.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Flow.Purple.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Text(s.name.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = Flow.Purple) }
                Spacer(Modifier.width(Gap.m))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.cadenceLabel} · ${s.count} payments", fontSize = Ts.meta, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(s.amount), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(due, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = if (d <= 1) Flow.Amber else c.muted)
                }
            }
        }
    }
}

/** Everything you will have to pay soon, soonest first: bills, renewals and money to-dos. */
@Composable
fun ComingUpCard(items: List<DueItem>, onOpen: (DueItem) -> Unit, onDone: (DueItem) -> Unit) {
    val c = LocalFlow.current
    val now = System.currentTimeMillis()
    GlassCard(Modifier.fillMaxWidth()) {
        Text("coming up", style = MaterialTheme.typography.titleMedium, color = c.text)
        val known = items.mapNotNull { x -> x.amount }.sum()
        Text(
            "${items.size} to pay" + if (known > 0) " · about ${rupees(known)}" else "",
            fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Amber
        )
        Spacer(Modifier.height(Gap.s))
        items.take(8).forEach { item ->
            val d = item.daysUntil(now)
            val due = when {
                d < 0 -> "${-d}d overdue"
                d == 0 -> "due today"
                d == 1 -> "due tomorrow"
                else -> "in $d days"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(item) }.padding(horizontal = Gap.xs, vertical = Gap.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.kind.label + (item.amount?.let { a -> " · ${rupees(a)}" } ?: ""), fontSize = Ts.meta, color = c.muted)
                }
                Spacer(Modifier.width(Gap.s))
                Column(horizontalAlignment = Alignment.End) {
                    Text(due, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = if (d <= 1) Flow.Amber else c.muted)
                    if (item.kind != DueKind.RENEWAL) {
                        Text(
                            "paid", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Emerald,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onDone(item) }.padding(horizontal = Gap.s, vertical = Gap.xs)
                        )
                    }
                }
            }
        }
        if (items.size > 8) Text("and ${items.size - 8} more", fontSize = Ts.meta, color = c.muted)
    }
}

/** Long-press menu for a payment. */
@Composable
fun PaymentActionsDialog(
    title: String,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onTransfer: (() -> Unit)? = null,
    onInvestment: (() -> Unit)? = null,
    category: String? = null,
    onCategory: (() -> Unit)? = null,
) {
    val c = LocalFlow.current
    @Composable
    fun option(label: String, sub: String?, color: Color, onClick: () -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
                .padding(horizontal = Gap.s, vertical = Gap.m)
        ) {
            Text(label, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = color)
            if (sub != null) Text(sub, fontSize = Ts.meta, color = c.muted)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (onCategory != null) option("change category", category?.let { "now: ${it.lowercase()}" }, c.text, onCategory)
                option("split bill", "share it with friends over UPI", c.text, onSplit)
                if (onInvestment != null) option("it's an investment", "keep it out of spending", c.text, onInvestment)
                if (onTransfer != null) option("it's a transfer", "between your own accounts", c.text, onTransfer)
                option("delete", null, Flow.Rose, onDelete)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("close") } }
    )
}

/** Pick a new category for a payment, optionally for every payment from the same merchant. */
@Composable
fun CategoryDialog(
    merchant: String,
    current: String,
    categories: List<String>,
    onPick: (category: String, allFromMerchant: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalFlow.current
    var picked by remember { mutableStateOf(current) }
    var all by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Gap.s),
                    verticalArrangement = Arrangement.spacedBy(Gap.s)
                ) {
                    categories.forEach { cat -> Pill(cat.lowercase(), picked == cat, { picked = cat }) }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { all = !all }.padding(vertical = Gap.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(checked = all, onCheckedChange = { all = it })
                    Text("also for all $merchant payments, and from now on", fontSize = Ts.body, color = c.text)
                }
            }
        },
        confirmButton = { GradientButton("save", onClick = { onPick(picked, all) }, brush = Flow.spendGradient) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel") } }
    )
}
