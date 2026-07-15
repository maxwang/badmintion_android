package com.badmintonledger.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.domain.report.buildHistoryRows
import java.time.LocalDate

private sealed interface HistoryAction {
    data class SessionMenu(val id: String, val label: String) : HistoryAction

    data class ConfirmDelete(val label: String, val delete: () -> Unit) : HistoryAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun HistoryScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
    onEditSession: (String) -> Unit,
) {
    val data by vm.ledger.collectAsState()
    var action by remember { mutableStateOf<HistoryAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = data ?: return@Scaffold
        val cutoff = remember { LocalDate.now().minusDays(365).toString() }
        val rows = remember(current) { buildHistoryRows(current, cutoff) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "周记录（近12个月，点击可编辑/删除）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(rows.sessions, key = { "s" + it.id }) { s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { action = HistoryAction.SessionMenu(s.id, s.date) }
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.date, style = MaterialTheme.typography.titleSmall)
                        Text("$${s.realDollars}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(s.desc, style = MaterialTheme.typography.bodySmall)
                    Text(
                        s.names,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Text("充值记录（点击可删除）", style = MaterialTheme.typography.titleMedium) }
            items(rows.refills, key = { "r" + it.id }) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete("充值") { vm.deleteRefill(r.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(r.date, style = MaterialTheme.typography.titleSmall)
                    Text(r.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("收款记录（点击可删除）", style = MaterialTheme.typography.titleMedium) }
            items(rows.payments, key = { "p" + it.id }) { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete("收款") { vm.deletePayment(p.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(p.date, style = MaterialTheme.typography.titleSmall)
                    Text(p.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    when (val a = action) {
        is HistoryAction.SessionMenu ->
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("周记录 ${a.label}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            action = null
                            onEditSession(a.id)
                        },
                    ) { Text("编辑") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            action = HistoryAction.ConfirmDelete("周") { vm.deleteSession(a.id) }
                        },
                    ) { Text("删除") }
                },
            )
        is HistoryAction.ConfirmDelete ->
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("删除${a.label}记录") },
                text = { Text("删除后所有余额自动重算，确定？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            a.delete()
                            action = null
                        },
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { action = null }) { Text("取消") }
                },
            )
        null -> Unit
    }
}
