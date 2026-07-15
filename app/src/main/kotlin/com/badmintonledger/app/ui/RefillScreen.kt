package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.model.centsToDollars
import com.badmintonledger.domain.model.dollarsToCents
import com.badmintonledger.domain.report.refillFactorText
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun RefillScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var paid by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    val amounts = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(data) {
        val current = data ?: return@LaunchedEffect
        if (initialized) return@LaunchedEffect
        initialized = true
        paid = dollarsText(current.config.defaultPaid.value)
        credit = dollarsText(current.config.defaultCredit.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = data ?: return@Scaffold
        val funders = current.members.filter { !it.isGuest }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DateField(
                    label = "Date",
                    value = date,
                    onChange = { date = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text("Paid ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Credit ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Factor (paid ÷ credit): " +
                        refillFactorText(
                            paid.toDoubleOrNull()?.let(::dollarsToCents),
                            credit.toDoubleOrNull()?.let(::dollarsToCents),
                        ),
                )
            }
            item {
                Text(
                    "Contributions (must total the paid amount)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(funders, key = { it.id }) { member ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(member.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = amounts[member.id] ?: "",
                        onValueChange = { amounts[member.id] = it },
                        label = { Text("Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                val totalCents =
                    funders.sumOf { m ->
                        amounts[m.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let(::dollarsToCents) ?: 0L
                    }
                Text("Total  $${centsToDollars(totalCents)}", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Button(
                    onClick = {
                        val contributions =
                            funders.mapNotNull { m ->
                                amounts[m.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { m.id to it }
                            }
                        val err = vm.addRefill(date, paid.toDoubleOrNull(), credit.toDoubleOrNull(), contributions)
                        if (err == null) onBack() else scope.launch { snackbar.showSnackbar(err) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) { Text("Save refill") }
            }
        }
    }
}
