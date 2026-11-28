package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import com.keepsakeai.app.ui.theme.Display
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.Donut
import com.keepsakeai.app.ui.spend.AskMoneyCard
import com.keepsakeai.app.ui.spend.BudgetDialog
import com.keepsakeai.app.ui.spend.BudgetsCard
import com.keepsakeai.app.ui.spend.PaymentActionsDialog
import com.keepsakeai.app.ui.spend.SplitDialog
import com.keepsakeai.app.ui.spend.SubscriptionsCard
import com.keepsakeai.app.ui.spend.budgetStatuses
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.ui.spend.GroupBy
import com.keepsakeai.app.ui.spend.LegendRow
import com.keepsakeai.app.ui.spend.Slice
import com.keepsakeai.app.ui.spend.SpendFilter
import com.keepsakeai.app.ui.spend.SpendGroup
import com.keepsakeai.app.ui.spend.SpendPeriod
import com.keepsakeai.app.ui.spend.StackedBar
import com.keepsakeai.app.ui.spend.TrendBars
import com.keepsakeai.app.ui.spend.accountOf
import com.keepsakeai.app.ui.spend.applyFilter
import com.keepsakeai.app.ui.spend.breakdown
import com.keepsakeai.app.ui.spend.buckets
import com.keepsakeai.app.ui.spend.channelOf
import com.keepsakeai.app.ui.spend.dayLabel
import com.keepsakeai.app.ui.spend.grouped
import com.keepsakeai.app.ui.spend.inRange
import com.keepsakeai.app.ui.spend.percentChange
import com.keepsakeai.app.ui.spend.periodRange
import com.keepsakeai.app.ui.spend.previousRange
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.spend.topMerchants
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val categories = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Cash", "Card bill", "Other")

internal fun categoryIcon(cat: String): Pair<ImageVector, Color> = when (cat) {
    "Food" -> Icons.Rounded.Restaurant to Flow.Amber
    "Transport" -> Icons.Rounded.DirectionsCar to Flow.Sky
    "Shopping" -> Icons.Rounded.ShoppingBag to Flow.Pink
    "Bills" -> Icons.Rounded.Receipt to Flow.Indigo
    "Entertainment" -> Icons.Rounded.Theaters to Flow.Purple
    "Health" -> Icons.Rounded.LocalHospital to Flow.Rose
    "Cash" -> Icons.Rounded.Payments to Flow.Emerald
    "Card bill" -> Icons.Rounded.Payments to Flow.Indigo
    else -> Icons.Rounded.Receipt to Color(0xFF94A3B8)
}

private fun channelColor(ch: String): Color = when (ch) {
    "UPI" -> Flow.Indigo
    "Card" -> Flow.Pink
    "Bank transfer" -> Flow.Sky
    "ATM" -> Flow.Amber
    "Manual" -> Flow.Purple
    "Receipt" -> Flow.Teal
    else -> Flow.Violet
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

/** Short form for chart labels: ₹1.2L, ₹45.3K, ₹860. */
private fun moneyShort(code: String, v: Double): String {
    val s = currencySymbol(code)
    return when {
        v >= 1_00_00_000 -> s + String.format(Locale.US, "%.1fCr", v / 1_00_00_000)
        v >= 1_00_000 -> s + String.format(Locale.US, "%.1fL", v / 1_00_000)
        v >= 10_000 -> s + String.format(Locale.US, "%.1fK", v / 1000)
        else -> money(code, Math.round(v).toDouble())
    }
}

private fun pct(part: Double, whole: Double) = if (whole > 0) ((part / whole) * 100).toInt() else 0

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: MindSpaceViewModel,
    modifier: Modifier = Modifier,
    onAskPilot: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val expenses by viewModel.expenses.collectAsState()
    val pendingUpi by viewModel.pendingUpi.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val investments by viewModel.investments.collectAsState()
    val cleaning by viewModel.isCleaning.collectAsState()
    val cleanupPlan by viewModel.cleanupPlan.collectAsState()
    val currency = (context.applicationContext as MindSpaceApp).apiKeyStore.defaultCurrency.ifBlank { "INR" }

    // Dashboard state
    var period by rememberSaveable { mutableStateOf(SpendPeriod.THIS_MONTH) }
    var filter by remember { mutableStateOf(SpendFilter()) }
    var groupBy by rememberSaveable { mutableStateOf(GroupBy.CATEGORY) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var showAll by remember { mutableStateOf(setOf<String>()) }
    var openTool by rememberSaveable { mutableStateOf<String?>(null) } // "log" | "import"

    // Quick log / import state
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var smsDays by remember { mutableStateOf(30) }
    var smsBlocked by remember { mutableStateOf(false) }
    val scanningSms by viewModel.isScanningSms.collectAsState()
    val importedCount by viewModel.importedCount.collectAsState()
    var confirmRemoveAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var actionFor by remember { mutableStateOf<ExpenseEntity?>(null) }
    var splitFor by remember { mutableStateOf<ExpenseEntity?>(null) }
    var categoryFor by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editBudgets by remember { mutableStateOf(false) }
    val moneyPrefs = remember { MoneyPrefs(context) }
    var budgets by remember { mutableStateOf(moneyPrefs.budgets) }
    var remindersOn by remember { mutableStateOf(moneyPrefs.renewalReminders) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { moneyPrefs.renewalReminders = true; remindersOn = true }
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsBlocked = !granted
        if (granted) viewModel.scanSmsInboxForUpi(smsDays)
    }

    // ── Derived data ─────────────────────────────────────────────
    val range = remember(period, expenses) { periodRange(period, expenses) }
    val inPeriod = remember(range, expenses) { expenses.inRange(range) }
    val shown = remember(inPeriod, filter) { inPeriod.applyFilter(filter) }
    val total = shown.sumOf { it.amount }
    val prevTotal = remember(period, expenses, filter) {
        previousRange(period)?.let { expenses.inRange(it).applyFilter(filter).sumOf { e -> e.amount } }
    }
    val change = prevTotal?.let { percentChange(total, it) }
    val trend = remember(shown, range) { shown.buckets(range) }
    // Each dashboard ignores its own dimension so the other slices stay visible (dimmed).
    val forCategories = remember(inPeriod, filter) { inPeriod.applyFilter(filter.copy(categories = emptySet())) }
    val catSlices = remember(forCategories) {
        forCategories.breakdown { it.category }.map { (k, v) -> Slice(k, v, categoryIcon(k).second) }
    }
    val forChannels = remember(inPeriod, filter) { inPeriod.applyFilter(filter.copy(channels = emptySet())) }
    val chSlices = remember(forChannels) {
        forChannels.breakdown { channelOf(it.sourceMethod) }.map { (k, v) -> Slice(k, v, channelColor(k)) }
    }
    val merchants = remember(inPeriod, filter) { inPeriod.applyFilter(filter.copy(merchant = null)).topMerchants(5) }
    val groups = remember(shown, groupBy) { shown.grouped(groupBy) }
    val subs = remember(expenses) { expenses.detectRecurring() }
    val reminders by viewModel.reminders.collectAsState()
    val todosAll by viewModel.todos.collectAsState()
    val comingUp = remember(reminders, todosAll, subs) {
        com.keepsakeai.app.ui.spend.ComingUp.build(reminders, todosAll, subs, System.currentTimeMillis())
    }
    val budgetRows = remember(expenses, budgets) {
        val cal = java.util.Calendar.getInstance()
        val daysLeft = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - cal.get(java.util.Calendar.DAY_OF_MONTH) + 1
        budgetStatuses(expenses.inRange(periodRange(SpendPeriod.THIS_MONTH, expenses)), budgets, daysLeft)
    }

    fun toggleCategory(k: String) {
        filter = filter.copy(categories = if (k in filter.categories) filter.categories - k else filter.categories + k)
    }
    fun toggleChannel(k: String) {
        filter = filter.copy(channels = if (k in filter.channels) filter.channels - k else filter.channels + k)
    }

    // ── Dialogs ──────────────────────────────────────────────────
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text("Remove imported payments?") },
            text = { Text("This deletes all $importedCount payments imported from messages, plus anything waiting for approval. Payments you logged yourself stay. You can scan again afterwards.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeImportedPayments(); confirmRemoveAll = false }) {
                    Text("Remove", color = Flow.Rose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveAll = false }) { Text("Cancel") } }
        )
    }
    confirmDelete?.let { exp ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this payment?") },
            text = { Text("${exp.merchant} · ${money(exp.currency, exp.amount)}") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExpense(exp); confirmDelete = null }) {
                    Text("Delete", color = Flow.Rose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    cleanupPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCleanup() },
            title = { Text("Remove ${plan.duplicates.size} duplicate${if (plan.duplicates.size == 1) "" else "s"}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Gap.xs)) {
                    if (plan.transfersMoved > 0) Text("${plan.transfersMoved} already moved to transfers.")
                    Text("The same payment was saved more than once. The best copy (bank message first) stays.")
                    plan.duplicates.take(6).forEach { d ->
                        Text("${d.remove.merchant} · ${money(d.remove.currency, d.remove.amount)} · ${dayLabel(startOfDay(d.remove.timestamp))}", fontSize = Ts.meta, color = LocalFlow.current.muted)
                    }
                    if (plan.duplicates.size > 6) Text("and ${plan.duplicates.size - 6} more", fontSize = Ts.meta, color = LocalFlow.current.muted)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.applyCleanup() }) { Text("Remove", color = Flow.Rose, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissCleanup() }) { Text("Keep all") } }
        )
    }

    actionFor?.let { exp ->
        PaymentActionsDialog(
            title = "${exp.merchant} · ${money(exp.currency, exp.amount)}",
            onSplit = { splitFor = exp; actionFor = null },
            onDelete = { confirmDelete = exp; actionFor = null },
            onDismiss = { actionFor = null },
            onTransfer = { viewModel.setExpenseTransfer(exp, true); actionFor = null },
            onInvestment = { viewModel.setExpenseInvestment(exp, true); actionFor = null },
            category = exp.category,
            onCategory = { categoryFor = exp; actionFor = null }
        )
    }
    splitFor?.let { exp -> SplitDialog(exp) { splitFor = null } }
    categoryFor?.let { exp ->
        CategoryDialog(
            merchant = exp.merchant, current = exp.category, categories = categories,
            onPick = { cat, all -> viewModel.setExpenseCategory(exp, cat, all); categoryFor = null },
            onDismiss = { categoryFor = null }
        )
    }
    if (editBudgets) {
        BudgetDialog(
            current = budgets,
            categories = categories.filter { it != "Cash" },
            onSave = { moneyPrefs.budgets = it; budgets = it; editBudgets = false; viewModel.refreshWidget() },
            onDismiss = { editBudgets = false }
        )
    }

    val refreshing by viewModel.isRefreshing.collectAsState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refreshAll(scanSms = true) },
        modifier = modifier
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gap.l, end = Gap.l, top = Gap.s, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(Gap.m)
    ) {
        item {
            FlowHeader(
                eyebrow = "receipts & UPI",
                title = "",
                gradientSuffix = "spend",
                joinSuffix = true,
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.WALLET) }
            ) {
                GlassButton(
                    "alerts",
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                    icon = Icons.Rounded.NotificationsActive,
                    tint = Flow.Emerald
                )
            }
        }

        // Tools: log an expense / import from SMS, folded away until needed
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                Pill("log expense", openTool == "log", { openTool = if (openTool == "log") null else "log" }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                Pill("import sms", openTool == "import", { openTool = if (openTool == "import") null else "import" }, Modifier.weight(1f), icon = Icons.Rounded.Sms)
            }
        }
        item {
            Column {
                AnimatedVisibility(openTool == "log", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    QuickLogCard(
                        merchant, { merchant = it }, amountText, { amountText = it }, category, { category = it },
                        onAdd = { amt ->
                            viewModel.addManualExpense(merchant, amt, category)
                            merchant = ""; amountText = ""; openTool = null
                        }
                    )
                }
                AnimatedVisibility(openTool == "import", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ImportCard(
                        smsDays = smsDays, onDays = { smsDays = it },
                        scanning = scanningSms, blocked = smsBlocked,
                        canRemove = importedCount > 0 || pendingUpi.isNotEmpty(),
                        onScan = {
                            if (hasSmsPermission(context)) viewModel.scanSmsInboxForUpi(smsDays)
                            else smsPermission.launch(Manifest.permission.READ_SMS)
                        },
                        onOpenAppInfo = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        },
                        onRemoveAll = { confirmRemoveAll = true },
                        cleaning = cleaning,
                        onTidy = {
                            if (hasSmsPermission(context)) viewModel.startCleanup(smsDays)
                            else smsPermission.launch(Manifest.permission.READ_SMS)
                        }
                    )
                }
            }
        }

        if (pendingUpi.isNotEmpty()) {
            item { PendingCard(viewModel, currencyFallback = currency) }
        }

        item { OwedCard(currency) }

        if (investments.isNotEmpty()) {
            item {
                ExcludedCard(
                    "${investments.size} investment${if (investments.size == 1) "" else "s"} · ${money(currency, investments.sumOf { it.amount })} · excluded",
                    investments, currency, Flow.Purple, "not an investment"
                ) { viewModel.setExpenseInvestment(it, false) }
            }
        }

        if (transfers.isNotEmpty()) {
            item { TransfersCard(transfers, viewModel, currency) }
        }

        // Period
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                SpendPeriod.entries.forEach { p ->
                    Pill(p.label, period == p, { period = p }, Modifier.weight(1f))
                }
            }
        }

        // Hero: total, change, average, trend
        item {
            GradientCard(Flow.spendGradient, Modifier.fillMaxWidth(), glow = Flow.Emerald) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (filter.isActive) "${period.label} · filtered" else "spent ${period.label}",
                        color = Color(0xFFD1FAE5), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    )
                    if (change != null) {
                        val up = change > 0
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = Gap.s, vertical = Gap.xs)
                        ) {
                            Text(
                                "${if (up) "▲" else "▼"} ${kotlin.math.abs(change)}% vs ${if (period == SpendPeriod.THIS_MONTH) "last month" else "before"}",
                                color = Color.White, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                val shownTotal by androidx.compose.animation.core.animateFloatAsState(
                    total.toFloat(), androidx.compose.animation.core.tween(700), label = "total"
                )
                Text(money(currency, Math.round(shownTotal).toDouble()), color = Color.White, fontSize = Ts.hero, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Row {
                    HeroStat(moneyShort(currency, total / range.days), "per day")
                    Spacer(Modifier.width(Gap.l))
                    HeroStat("${shown.size}", if (shown.size == 1) "payment" else "payments")
                    Spacer(Modifier.width(Gap.l))
                    val biggest = shown.maxByOrNull { it.amount }
                    if (biggest != null) HeroStat(moneyShort(currency, biggest.amount), "largest")
                }
                Spacer(Modifier.height(Gap.m))
                TrendBars(trend, Color.White, height = 56.dp)
                Spacer(Modifier.height(Gap.xs))
                Row {
                    val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
                    val faint = Color.White.copy(alpha = 0.75f)
                    Text(fmt.format(Date(range.start)).lowercase(), fontSize = Ts.caption, color = faint, modifier = Modifier.weight(1f))
                    Text(if (range.days <= 45) "daily" else "weekly", fontSize = Ts.caption, color = faint, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(fmt.format(Date(range.end - 1)).lowercase(), fontSize = Ts.caption, color = faint, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }

        item { BudgetsCard(budgetRows, onEdit = { editBudgets = true }) }
        if (expenses.isNotEmpty()) {
            item { AskMoneyCard(onAsk = onAskPilot) }
        }
        if (comingUp.isNotEmpty()) {
            item {
                com.keepsakeai.app.ui.spend.ComingUpCard(
                    comingUp,
                    onOpen = { d ->
                        if (d.memoryId != null) viewModel.openMemory(d.memoryId)
                        else if (d.kind == com.keepsakeai.app.ui.spend.DueKind.RENEWAL) { filter = SpendFilter(merchant = d.title); period = SpendPeriod.ALL; groupBy = GroupBy.DAY }
                    },
                    onDone = { d ->
                        when (d.kind) {
                            com.keepsakeai.app.ui.spend.DueKind.BILL -> reminders.firstOrNull { it.id == d.refId }?.let { viewModel.dismissReminder(it) }
                            com.keepsakeai.app.ui.spend.DueKind.TODO -> viewModel.toggleTodo(d.refId)
                            else -> {}
                        }
                    }
                )
            }
        }
        if (subs.isNotEmpty()) {
            item {
                SubscriptionsCard(
                    subs, remindersOn,
                    onToggleReminders = {
                        if (remindersOn) { moneyPrefs.renewalReminders = false; remindersOn = false }
                        else if (android.os.Build.VERSION.SDK_INT >= 33 && !com.keepsakeai.app.notify.Notifier.canNotify(context)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else { moneyPrefs.renewalReminders = true; remindersOn = true }
                    },
                    onTap = { s -> filter = SpendFilter(merchant = s.name); period = SpendPeriod.ALL; groupBy = GroupBy.DAY }
                )
            }
        }

        if (inPeriod.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Nothing spent ${period.label} yet. Log an expense or import your SMS to see the breakdown.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            // Where it went: category donut
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    DashTitle("where it went", "tap to filter")
                    Spacer(Modifier.height(Gap.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val catTotal = catSlices.sumOf { it.value }
                        val sel = catSlices.filter { it.key in filter.categories }
                        Donut(
                            slices = catSlices,
                            selected = filter.categories,
                            centerTop = moneyShort(currency, if (sel.isEmpty()) catTotal else sel.sumOf { it.value }),
                            centerBottom = when {
                                sel.isEmpty() -> "${catSlices.size} categories"
                                sel.size == 1 -> sel.first().key.lowercase()
                                else -> "${sel.size} selected"
                            },
                            onTap = { toggleCategory(it) },
                            size = 132.dp
                        )
                        Spacer(Modifier.width(Gap.m))
                        Column(Modifier.weight(1f)) {
                            catSlices.take(6).forEach { s ->
                                LegendRow(
                                    s.color, s.key, moneyShort(currency, s.value), pct(s.value, catTotal),
                                    selected = s.key in filter.categories,
                                    dimmed = filter.categories.isNotEmpty() && s.key !in filter.categories,
                                    onClick = { toggleCategory(s.key) }
                                )
                            }
                            if (catSlices.size > 6) {
                                Text("+${catSlices.size - 6} more", fontSize = Ts.meta, color = c.muted, modifier = Modifier.padding(start = Gap.s, top = Gap.xxs))
                            }
                        }
                    }
                }
            }

            // How you paid: channel split
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    DashTitle("how you paid", null)
                    Spacer(Modifier.height(Gap.m))
                    StackedBar(chSlices, filter.channels)
                    Spacer(Modifier.height(Gap.s))
                    val chTotal = chSlices.sumOf { it.value }
                    chSlices.forEach { s ->
                        LegendRow(
                            s.color, s.key, moneyShort(currency, s.value), pct(s.value, chTotal),
                            selected = s.key in filter.channels,
                            dimmed = filter.channels.isNotEmpty() && s.key !in filter.channels,
                            onClick = { toggleChannel(s.key) }
                        )
                    }
                }
            }

            // Top merchants
            if (merchants.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        DashTitle("top merchants", null)
                        Spacer(Modifier.height(Gap.s))
                        val max = merchants.first().total
                        merchants.forEachIndexed { i, m ->
                            val sel = filter.merchant != null && filter.merchant.equals(m.name, ignoreCase = true)
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Flow.Indigo.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { filter = filter.copy(merchant = if (sel) null else m.name) }
                                    .padding(horizontal = Gap.s, vertical = Gap.s)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo, modifier = Modifier.width(18.dp))
                                    Text(m.name, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${m.count}×", fontSize = Ts.meta, color = c.muted)
                                    Spacer(Modifier.width(Gap.s))
                                    Text(moneyShort(currency, m.total), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
                                }
                                Spacer(Modifier.height(Gap.xs))
                                Box(Modifier.padding(start = Gap.l).fillMaxWidth().height(4.dp).clip(CircleShape).background(c.divider)) {
                                    Box(
                                        Modifier.fillMaxWidth((m.total / max).toFloat()).fillMaxHeight().clip(CircleShape)
                                            .background(Brush.horizontalGradient(listOf(Flow.Indigo, Flow.Sky)))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Transactions: search, active filters, grouping
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Gap.m)) {
                    SectionLabel("transactions", trailing = "${shown.size} · ${moneyShort(currency, total)}")
                    GlassField(
                        filter.query, { filter = filter.copy(query = it) }, "Search merchant, category or note",
                        Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Search
                    )
                    if (filter.isActive) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                            items(filter.categories.toList()) { k -> RemovableChip(k, categoryIcon(k).second) { toggleCategory(k) } }
                            items(filter.channels.toList()) { k -> RemovableChip(k, channelColor(k)) { toggleChannel(k) } }
                            filter.merchant?.let { m -> item { RemovableChip(m, Flow.Indigo) { filter = filter.copy(merchant = null) } } }
                            if (filter.query.isNotBlank()) item { RemovableChip("“${filter.query}”", Flow.Sky) { filter = filter.copy(query = "") } }
                            item {
                                Text(
                                    "clear all", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Rose,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { filter = SpendFilter() }.padding(horizontal = Gap.m, vertical = Gap.s)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("group by", fontSize = Ts.meta, color = c.muted, modifier = Modifier.padding(end = Gap.s))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                            items(GroupBy.entries) { g ->
                                Pill(g.label, groupBy == g, { groupBy = g; expanded = emptySet(); showAll = emptySet() })
                            }
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("No payments match these filters.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            items(groups, key = { "${groupBy.name}:${it.key}" }) { g ->
                val isOpen = g.key in expanded || groups.size == 1
                GroupCard(
                    group = g,
                    groupBy = groupBy,
                    total = total,
                    currency = currency,
                    open = isOpen,
                    showAll = g.key in showAll,
                    onToggle = { expanded = if (g.key in expanded) expanded - g.key else expanded + g.key },
                    onShowAll = { showAll = showAll + g.key },
                    onDrill = {
                        filter = when (groupBy) {
                            GroupBy.CATEGORY -> filter.copy(categories = setOf(g.key))
                            GroupBy.CHANNEL -> filter.copy(channels = setOf(g.key))
                            GroupBy.MERCHANT -> filter.copy(merchant = g.label)
                            GroupBy.DAY -> filter
                        }
                    },
                    onLongPress = { actionFor = it }
                )
            }
        }
    }
}
}

// ── Pieces ───────────────────────────────────────────────────────────

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = Ts.title, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = Ts.meta)
    }
}

@Composable
private fun DashTitle(title: String, hint: String?) {
    val c = LocalFlow.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, fontSize = Ts.meta, color = c.muted)
    }
}

@Composable
private fun RemovableChip(label: String, color: Color, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).clickable(onClick = onRemove)
            .padding(start = Gap.m, end = Gap.s, top = Gap.s, bottom = Gap.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        Spacer(Modifier.width(Gap.xs))
        Icon(Icons.Rounded.Close, "Remove filter", tint = color, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun GroupCard(
    group: SpendGroup,
    groupBy: GroupBy,
    total: Double,
    currency: String,
    open: Boolean,
    showAll: Boolean,
    onToggle: () -> Unit,
    onShowAll: () -> Unit,
    onDrill: () -> Unit,
    onLongPress: (ExpenseEntity) -> Unit,
) {
    val c = LocalFlow.current
    val (icon, color) = when (groupBy) {
        GroupBy.CATEGORY -> categoryIcon(group.key)
        GroupBy.CHANNEL -> Icons.Rounded.Payments to channelColor(group.key)
        GroupBy.MERCHANT -> categoryIcon(group.items.first().category)
        GroupBy.DAY -> Icons.Rounded.Receipt to Flow.Indigo
    }
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Gap.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(Gap.m))
            Column(Modifier.weight(1f)) {
                Text(group.label, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Gap.xs))
                Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(CircleShape).background(c.divider)) {
                    Box(Modifier.fillMaxWidth(if (total > 0) (group.total / total).toFloat() else 0f).fillMaxHeight().clip(CircleShape).background(color))
                }
                Spacer(Modifier.height(Gap.xs))
                Text("${group.items.size} payment${if (group.items.size == 1) "" else "s"} · ${pct(group.total, total)}%", fontSize = Ts.meta, color = c.muted)
            }
            Spacer(Modifier.width(Gap.s))
            Text(money(currency, group.total), style = MaterialTheme.typography.titleSmall, color = c.text)
            Icon(Icons.Rounded.ExpandMore, null, tint = c.muted, modifier = Modifier.padding(start = Gap.xs).size(20.dp).rotate(if (open) 180f else 0f))
        }
        if (open) {
            HorizontalDivider(color = c.divider)
            val list = if (showAll) group.items else group.items.take(6)
            list.forEach { exp -> TxnRow(exp, groupBy, currency, onLongPress) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Gap.m, vertical = Gap.s), verticalAlignment = Alignment.CenterVertically) {
                if (!showAll && group.items.size > 6) {
                    Text(
                        "show all ${group.items.size}", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onShowAll).padding(horizontal = Gap.s, vertical = Gap.s)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (groupBy != GroupBy.DAY) {
                    Text(
                        "filter to this →", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = color,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDrill).padding(horizontal = Gap.s, vertical = Gap.s)
                    )
                }
            }
        }
    }
}

@Composable
private fun TxnRow(exp: ExpenseEntity, groupBy: GroupBy, currency: String, onLongPress: (ExpenseEntity) -> Unit) {
    val c = LocalFlow.current
    val ch = channelOf(exp.sourceMethod)
    val acct = accountOf(exp.sourceMethod)
    val sub = buildList {
        if (groupBy != GroupBy.DAY) add(dayLabel(startOfDay(exp.timestamp)))
        if (groupBy != GroupBy.CATEGORY) add(exp.category.lowercase())
        if (groupBy != GroupBy.CHANNEL) add(listOfNotNull(ch, acct).joinToString(" · "))
    }.joinToString("  ·  ")
    Row(
        Modifier.fillMaxWidth()
            .pointerInput(exp.id) { detectTapGestures(onLongPress = { onLongPress(exp) }) }
            .padding(horizontal = Gap.l, vertical = Gap.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(categoryIcon(exp.category).second))
        Spacer(Modifier.width(Gap.m))
        Column(Modifier.weight(1f)) {
            Text(
                if (groupBy == GroupBy.MERCHANT) SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(exp.timestamp)).lowercase() else exp.merchant,
                fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(sub, fontSize = Ts.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(money(exp.currency.ifBlank { currency }, exp.amount), fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
    }
}

@Composable
private fun QuickLogCard(
    merchant: String, onMerchant: (String) -> Unit,
    amountText: String, onAmount: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    onAdd: (Double) -> Unit,
) {
    val c = LocalFlow.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text("log an expense", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(Gap.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            GlassField(merchant, onMerchant, "Merchant", Modifier.weight(1.4f))
            GlassField(
                amountText, { onAmount(it.filter { ch -> ch.isDigit() || ch == '.' }) }, "Amount", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        Spacer(Modifier.height(Gap.s))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            items(categories) { cat -> Pill(cat, category == cat, { onCategory(cat) }) }
        }
        Spacer(Modifier.height(Gap.m))
        val amt = amountText.toDoubleOrNull() ?: 0.0
        GradientButton("Add", brush = Flow.spendGradient, enabled = amt > 0.0, onClick = { onAdd(amt) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportCard(
    smsDays: Int, onDays: (Int) -> Unit,
    scanning: Boolean, blocked: Boolean, canRemove: Boolean,
    onScan: () -> Unit, onOpenAppInfo: () -> Unit, onRemoveAll: () -> Unit,
    cleaning: Boolean = false, onTidy: () -> Unit = {},
) {
    val c = LocalFlow.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text("import from messages", style = MaterialTheme.typography.titleSmall, color = c.text)
        Text("UPI, card and bank debit SMS go to your review queue. Read on your phone only.", fontSize = Ts.meta, color = c.muted)
        Spacer(Modifier.height(Gap.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            listOf(30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, label) ->
                Pill(label, smsDays == d, { onDays(d) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Gap.m))
        GradientButton(
            if (scanning) "Reading messages…" else "Scan messages",
            enabled = !scanning, brush = Flow.spendGradient, icon = Icons.Rounded.Sms,
            onClick = onScan, modifier = Modifier.fillMaxWidth()
        )
        if (blocked) {
            Spacer(Modifier.height(Gap.s))
            Text(
                "SMS access is blocked. Open App info → ⋮ → Allow restricted settings, then Permissions → SMS → Allow.",
                fontSize = Ts.meta, color = Flow.Rose, modifier = Modifier.clickable(onClick = onOpenAppInfo)
            )
        }
        Text(
            if (cleaning) "Tidying up…" else "Tidy up: find duplicates and transfers",
            fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
            modifier = Modifier.padding(top = Gap.s).clip(RoundedCornerShape(50)).clickable(enabled = !cleaning, onClick = onTidy).padding(horizontal = Gap.s, vertical = Gap.s)
        )
        if (canRemove) {
            Text(
                "Remove imported", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Rose,
                modifier = Modifier.padding(top = Gap.s).clip(RoundedCornerShape(50)).clickable(onClick = onRemoveAll).padding(horizontal = Gap.s, vertical = Gap.s)
            )
        }
    }
}

/** Payments that only moved between the user's own accounts: listed here, left out of every spending total. */
@Composable
private fun TransfersCard(transfers: List<ExpenseEntity>, viewModel: MindSpaceViewModel, currency: String) =
    ExcludedCard("${transfers.size} transfers between your accounts · excluded", transfers, currency, null, "not a transfer") {
        viewModel.setExpenseTransfer(it, false)
    }

/** A folded list of payments kept out of spending (transfers, investments), each with a way to put it back. */
@Composable
private fun ExcludedCard(
    heading: String,
    transfers: List<ExpenseEntity>,
    currency: String,
    accent: Color?,
    undoLabel: String,
    onUndo: (ExpenseEntity) -> Unit,
) {
    val c = LocalFlow.current
    var open by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Gap.l)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(Gap.s))
            }
            Text(
                heading,
                style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ExpandMore, null, tint = c.muted, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f))
        }
        if (open) {
            transfers.take(40).forEach { t ->
                Row(Modifier.fillMaxWidth().padding(top = Gap.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.merchant, color = c.text, fontSize = Ts.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${money(t.currency.ifBlank { currency }, t.amount)} · ${dayLabel(startOfDay(t.timestamp))}", fontSize = Ts.meta, color = c.muted)
                    }
                    Text(
                        undoLabel, color = Flow.Emerald, fontWeight = FontWeight.SemiBold, fontSize = Ts.meta,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onUndo(t) }.padding(Gap.s)
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingCard(viewModel: MindSpaceViewModel, currencyFallback: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = LocalFlow.current
    val pendingUpi by viewModel.pendingUpi.collectAsState()
    val shape = RoundedCornerShape(26.dp)
    var open by remember { mutableStateOf(pendingUpi.size <= 3) }
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (c.dark) Flow.Amber.copy(alpha = 0.10f) else Color(0xCCFFFBEB))
            .border(1.dp, Flow.Amber.copy(alpha = 0.45f), shape)
            .padding(Gap.l),
        verticalArrangement = Arrangement.spacedBy(Gap.m)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Inbox, null, tint = Flow.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Gap.s))
                Text(
                    "to review (${pendingUpi.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (c.dark) Color(0xFFFCD34D) else Color(0xFF78350F)
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = Flow.Amber, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f))
            }
            Text(
                "clear", color = c.soft, fontWeight = FontWeight.SemiBold, fontSize = Ts.title,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.clearPendingUpi() }.padding(Gap.s)
            )
            Text(
                "approve all", color = Flow.Emerald, fontWeight = FontWeight.SemiBold, fontSize = Ts.title,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.approveAllPendingUpi() }.padding(Gap.s)
            )
        }
        if (open) {
            pendingUpi.forEach { item ->
                var selectedCat by remember(item.id) {
                    mutableStateOf(com.keepsakeai.app.data.preferences.MoneyPrefs(context).categoryFor(item.merchant) ?: item.suggestedCategory)
                }
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Gap.m)) {
                    Row {
                        Text(item.merchant, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(item.currency.ifBlank { currencyFallback }, item.amount), style = MaterialTheme.typography.titleSmall, color = Flow.Indigo)
                    }
                    Text("${item.bankOrApp} · ${dayLabel(startOfDay(item.receivedAt))}", fontSize = Ts.meta, color = c.muted)
                    val isTransfer = item.type == com.keepsakeai.app.data.local.TxnType.TRANSFER
                    Text(
                        if (isTransfer) "looks like a transfer between your accounts · tap if it's spending" else "tap if this is a transfer between your accounts",
                        fontSize = Ts.meta, fontWeight = FontWeight.SemiBold,
                        color = if (isTransfer) Flow.Amber else c.muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.setPendingTransfer(item, !isTransfer) }.padding(vertical = Gap.xs)
                    )
                    Spacer(Modifier.height(Gap.xs))
                    Text("“${item.rawMessage}”", fontSize = Ts.meta, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Gap.s))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        items(categories) { cat -> Pill(cat, selectedCat == cat, { selectedCat = cat }) }
                    }
                    Spacer(Modifier.height(Gap.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "dismiss", color = c.soft, fontWeight = FontWeight.SemiBold, fontSize = Ts.title,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.dismissPendingUpi(item) }.padding(Gap.m)
                        )
                        Spacer(Modifier.weight(1f))
                        GradientButton("Approve", onClick = { viewModel.approvePendingUpi(item, selectedCat) }, brush = Flow.spendGradient)
                    }
                }
            }
        }
    }
}

/** Money lent or borrowed ("Lent Ravi ₹2,000"), from notes and voice memos, until marked settled. */
@Composable
private fun OwedCard(currency: String) {
    val c = LocalFlow.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { com.keepsakeai.app.intents.OwedStore(context) }
    var all by remember { mutableStateOf(store.all()) }
    val open = com.keepsakeai.app.intents.OwedRules.open(all)
    if (open.isEmpty()) return
    val now = System.currentTimeMillis()
    val toMe = open.filter { it.owedToMe }.sumOf { it.amount ?: 0.0 }
    val iOwe = open.filter { !it.owedToMe }.sumOf { it.amount ?: 0.0 }
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Gap.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Flow.Amber))
            Spacer(Modifier.width(Gap.s))
            Text("money owed", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(
                    if (toMe > 0) "owed to you ${money(currency, toMe)}" else null,
                    if (iOwe > 0) "you owe ${money(currency, iOwe)}" else null,
                ).joinToString(" · "),
                fontSize = Ts.meta, color = c.muted
            )
        }
        open.forEach { o ->
            Spacer(Modifier.height(Gap.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (o.owedToMe) "${o.person} owes you" else "You owe ${o.person}") + (o.amount?.let { " " + money(currency, it) } ?: ""),
                        fontSize = Ts.body, fontWeight = FontWeight.SemiBold, color = c.text
                    )
                    val days = com.keepsakeai.app.intents.OwedRules.daysOpen(o, now)
                    Text(
                        (if (days == 0) "today" else "$days days ago") + (o.what.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        fontSize = Ts.meta, color = if (days >= com.keepsakeai.app.intents.OwedRules.NUDGE_DAYS) Flow.Amber else c.muted
                    )
                }
                Text(
                    "settled", color = Flow.Emerald, fontWeight = FontWeight.SemiBold, fontSize = Ts.meta,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { store.setSettled(o.id); all = store.all() }.padding(Gap.s)
                )
            }
        }
    }
}
