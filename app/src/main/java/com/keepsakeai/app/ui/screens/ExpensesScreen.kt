package com.keepsakeai.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.ui.MindSpaceViewModel
import java.util.Locale

@Composable
fun ExpensesScreen(
    viewModel: MindSpaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val pendingUpi by viewModel.pendingUpi.collectAsState()

    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }

    val total = expenses.sumOf { it.amount }
    val byCategory = expenses.groupBy { it.category }.mapValues { entry ->
        entry.value.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receipts & Spend", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }) {
                    Text("UPI Listener")
                }
            }
        }

        // Pending UPI Preview & Approve Queue
        if (pendingUpi.isNotEmpty()) {
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pending UPI Review (${pendingUpi.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.approveAllPendingUpi() }) {
                                Text("Approve All")
                            }
                        }

                        pendingUpi.forEach { item ->
                            var selectedCat by remember(item.id) { mutableStateOf(item.suggestedCategory) }
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.merchant, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${item.currency} ${String.format(Locale.getDefault(), "%.2f", item.amount)}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "${item.bankOrApp}${item.utrRef?.let { " • UTR $it" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = item.rawMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = selectedCat,
                                            onValueChange = { selectedCat = it },
                                            label = { Text("Category") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.dismissPendingUpi(item) }) {
                                            Text("Dismiss")
                                        }
                                        Button(onClick = { viewModel.approvePendingUpi(item, selectedCat) }) {
                                            Text("Approve")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Approved Spend", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f", total),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    byCategory.forEach { (cat, sum) ->
                        val pct = if (total > 0) (sum / total).toFloat() else 0f
                        Text("$cat: ${String.format(Locale.getDefault(), "%.2f", sum)}", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Log Expense", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant / Item") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0.0) {
                                viewModel.addManualExpense(merchant, amt, category)
                                merchant = ""
                                amountText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Expense")
                    }
                }
            }
        }

        items(expenses, key = { it.id }) { exp ->
            ListItem(
                headlineContent = { Text(exp.merchant, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("${exp.category} • ${exp.sourceMethod}") },
                trailingContent = {
                    Text(
                        "${exp.currency} ${String.format(Locale.getDefault(), "%.2f", exp.amount)}",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
            HorizontalDivider()
        }
    }
}
