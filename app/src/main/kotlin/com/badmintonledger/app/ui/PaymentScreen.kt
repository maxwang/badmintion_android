package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.app.ui.components.MemberBalanceRow
import com.badmintonledger.domain.report.buildPaymentSummary
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun PaymentScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var saving by remember { mutableStateOf(false) }
    val membershipSelected = remember { mutableStateMapOf<String, Boolean>() }
    var savingMembership by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收款") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = data ?: return@Scaffold
        val summary = remember(current) { buildPaymentSummary(current) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateField(
                label = "收款日期",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "谁交钱了？（勾选即全额结清）",
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.debtors.isEmpty()) {
                Text("当前无人欠款 🎉")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.debtors.forEach { d ->
                        FilterChip(
                            selected = selected[d.id] == true,
                            onClick = { selected[d.id] = selected[d.id] != true },
                            label = { Text("${d.name} 欠 $${d.owedDollars}") },
                        )
                    }
                }
            }
            if (summary.membershipDebtors.isNotEmpty()) {
                Text(
                    "谁交年费了？（勾选即结清会员年费，与球馆余额无关）",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.membershipDebtors.forEach { d ->
                        FilterChip(
                            selected = membershipSelected[d.id] == true,
                            onClick = { membershipSelected[d.id] = membershipSelected[d.id] != true },
                            label = { Text("${d.name} 欠年费 \$${d.owedDollars}") },
                        )
                    }
                }
                Button(
                    enabled = summary.membershipDebtors.any { membershipSelected[it.id] == true } && !savingMembership,
                    onClick = {
                        savingMembership = true
                        val picked =
                            summary.membershipDebtors.filter { membershipSelected[it.id] == true }.map { it.id }
                        val err = vm.settleMembershipDebtors(picked, date)
                        if (err == null) {
                            onBack()
                        } else {
                            savingMembership = false
                            scope.launch { snackbar.showSnackbar(err) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存年费收款") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前余额（参考）", style = MaterialTheme.typography.titleMedium)
                    summary.rows.forEach { row ->
                        MemberBalanceRow(
                            name = row.name,
                            isGuest = row.isGuest,
                            owes = row.owes,
                            absDollars = row.absDollars,
                        )
                    }
                }
            }
            Button(
                enabled = summary.debtors.any { selected[it.id] == true } && !saving,
                onClick = {
                    saving = true
                    val picked = summary.debtors.filter { selected[it.id] == true }.map { it.id }
                    val err = vm.settleDebtors(picked, date)
                    if (err == null) {
                        onBack()
                    } else {
                        saving = false
                        scope.launch { snackbar.showSnackbar(err) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("保存收款") }
        }
    }
}
