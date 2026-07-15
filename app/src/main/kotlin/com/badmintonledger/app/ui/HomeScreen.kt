package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.MemberBalanceRow
import com.badmintonledger.app.ui.components.PoolCard
import com.badmintonledger.domain.report.buildHomeSummary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
fun HomeScreen(
    vm: LedgerViewModel,
    onOpenSettings: () -> Unit,
    onRecordSession: () -> Unit,
    onOpenRefill: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("羽毛球记账") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        val current = data
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val summary = remember(current) { buildHomeSummary(current) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PoolCard(
                    poolDollars = summary.poolDollars,
                    warn = summary.poolWarn,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onRecordSession) { Text("记录本周") }
                    Button(onClick = onOpenRefill) { Text("充值") }
                    Button(onClick = onOpenPayment) { Text("收款") }
                    OutlinedButton(onClick = onOpenReport) { Text("报告") }
                    OutlinedButton(onClick = onOpenHistory) { Text("历史") }
                }
            }
            if (summary.empty) {
                item {
                    Text(
                        "还没有成员，请先到「设置」添加成员",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(summary.rows, key = { it.id }) { row ->
                MemberBalanceRow(
                    name = row.name,
                    isGuest = row.isGuest,
                    owes = row.owes,
                    absDollars = row.absDollars,
                )
            }
        }
    }
}
