package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Member
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TransferScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var fromMember by remember { mutableStateOf<Member?>(null) }
    var toMember by remember { mutableStateOf<Member?>(null) }
    var amount by remember { mutableStateOf("") }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("转账") },
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
        val candidates = current.members.filter { !it.isGuest }
        val fromBalanceCents = fromMember?.let { memberBalancesCents(current)[it.id] ?: 0L } ?: 0L

        LaunchedEffect(fromMember) {
            fromMember?.let { amount = dollarsText(maxOf(fromBalanceCents, 0L)) }
        }

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
                label = "转账日期",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ExposedDropdownMenuBox(expanded = fromMenu, onExpandedChange = { fromMenu = it }) {
                OutlinedTextField(
                    value = fromMember?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("转出成员") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                    candidates.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                fromMember = m
                                if (toMember?.id == m.id) toMember = null
                                fromMenu = false
                            },
                        )
                    }
                }
            }
            if (fromMember != null) {
                Text("最多可转 \$${dollarsText(maxOf(fromBalanceCents, 0L))}", style = MaterialTheme.typography.bodySmall)
            }
            ExposedDropdownMenuBox(expanded = toMenu, onExpandedChange = { toMenu = it }) {
                OutlinedTextField(
                    value = toMember?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("转入成员") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                    candidates.filter { it.id != fromMember?.id }.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                toMember = m
                                toMenu = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("金额（$）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = fromMember != null && toMember != null && !saving,
                onClick = {
                    val from = fromMember ?: return@Button
                    val to = toMember ?: return@Button
                    saving = true
                    val err = vm.addTransfer(from.id, to.id, amount.toDoubleOrNull(), date)
                    if (err == null) {
                        onBack()
                    } else {
                        saving = false
                        scope.launch { snackbar.showSnackbar(err) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("保存转账") }
        }
    }
}
