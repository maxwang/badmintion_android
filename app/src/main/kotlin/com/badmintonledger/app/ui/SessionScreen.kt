package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import com.badmintonledger.app.SaveSessionResult
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.calc.currentFactor
import com.badmintonledger.domain.edit.findSessionInWeek
import com.badmintonledger.domain.model.dollarsToCents
import com.badmintonledger.domain.report.buildSessionPreview
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun SessionScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var hours by remember { mutableStateOf("4") }
    var rate by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var guestName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // Port of session.js onLoad: an existing record for the current week is edited in place.
    LaunchedEffect(data) {
        val current = data ?: return@LaunchedEffect
        if (initialized) return@LaunchedEffect
        initialized = true
        val existing = findSessionInWeek(current, LocalDate.now().toString())
        if (existing == null) {
            rate = dollarsText(current.config.defaultRate.value)
            factor = factorText(currentFactor(current))
        } else {
            editId = existing.id
            date = existing.date
            hours = numberText(existing.hours)
            rate = dollarsText(existing.rate.value)
            factor = numberText(existing.factor)
            existing.playerIds.forEach { selected[it] = true }
            snackbar.showSnackbar("This week already has a record — editing it")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record This Week") },
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
                label = "Date",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = hours,
                onValueChange = { hours = it },
                label = { Text("Hours") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text("Rate ($/hour)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = factor,
                onValueChange = { factor = it },
                label = { Text("Factor (paid ÷ credit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Players", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.members.forEach { m ->
                    FilterChip(
                        selected = selected[m.id] == true,
                        onClick = { selected[m.id] = selected[m.id] != true },
                        label = { Text(m.name + if (m.isGuest) " (guest)" else "") },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    label = { Text("Guest name") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = guestName.isNotBlank(),
                    onClick = {
                        vm.addGuest(guestName)?.let { selected[it.id] = true }
                        guestName = ""
                    },
                ) { Text("Add guest") }
            }
            val preview =
                buildSessionPreview(
                    hours.toDoubleOrNull(),
                    rate.toDoubleOrNull()?.let(::dollarsToCents),
                    factor.toDoubleOrNull(),
                    current.members.count { selected[it.id] == true },
                )
            if (preview != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Court fee $${preview.faceDollars} → actual $${preview.realDollars}")
                        Text("${preview.players} players · about $${preview.perPersonDollars} each")
                        Text(
                            "The last player absorbs the rounding remainder, so the total is exact.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    val playerIds = current.members.filter { selected[it.id] == true }.map { it.id }
                    when (
                        val r =
                            vm.saveSession(
                                editId,
                                date,
                                hours.toDoubleOrNull(),
                                rate.toDoubleOrNull(),
                                factor.toDoubleOrNull(),
                                playerIds,
                            )
                    ) {
                        is SaveSessionResult.Saved -> onSaved(r.sessionId)
                        is SaveSessionResult.Rejected -> {
                            saving = false
                            scope.launch { snackbar.showSnackbar(r.reason) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text(if (editId == null) "Save this week" else "Save changes") }
        }
    }
}
