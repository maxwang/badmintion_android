package com.badmintonledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.BackupLoad
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.domain.model.Member
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun SettingsScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Member?>(null) }
    var deleteTarget by remember { mutableStateOf<Member?>(null) }

    var rate by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    LaunchedEffect(data?.config) {
        data?.config?.let {
            rate = dollarsText(it.defaultRate.value)
            paid = dollarsText(it.defaultPaid.value)
            credit = dollarsText(it.defaultCredit.value)
        }
    }

    var pendingImport by remember { mutableStateOf<BackupLoad.Ready?>(null) }
    val importPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                when (val load = vm.loadBackup(uri)) {
                    BackupLoad.CouldNotRead -> snackbar.showSnackbar("Could not read the file")
                    is BackupLoad.Invalid -> snackbar.showSnackbar(load.reason)
                    is BackupLoad.Ready -> pendingImport = load
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Members",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(current.members, key = { it.id }) { member ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        member.name,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { renameTarget = member }) { Text("Rename") }
                    Text("Guest", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = member.isGuest,
                        onCheckedChange = { vm.setGuest(member.id, it) },
                    )
                    IconButton(onClick = { deleteTarget = member }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${member.name}")
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New member name") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            vm.addMember(newName)
                            newName = ""
                        },
                    ) { Text("Add") }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("Defaults", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Hourly rate ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text("Typical refill paid ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Typical refill credit ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                Button(
                    onClick = {
                        val err =
                            vm.saveConfig(
                                rate.toDoubleOrNull(),
                                paid.toDoubleOrNull(),
                                credit.toDoubleOrNull(),
                            )
                        scope.launch { snackbar.showSnackbar(err ?: "Saved") }
                    },
                ) { Text("Save defaults") }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("Data", style = MaterialTheme.typography.titleMedium) }
            item {
                Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                    Text("Import backup")
                }
            }
        }
    }

    renameTarget?.let { member ->
        var name by remember(member.id) { mutableStateOf(member.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename member") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameMember(member.id, name)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete member") },
            text = { Text("Delete ${member.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = vm.removeMember(member.id)
                        deleteTarget = null
                        if (reason != null) {
                            scope.launch { snackbar.showSnackbar(reason) }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    pendingImport?.let { load ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import backup") },
            text = {
                Text(
                    "This backup contains ${load.summary.members} members, " +
                        "${load.summary.sessions} weekly records and ${load.summary.refills} refills. " +
                        "Importing will replace ALL current data. Continue?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.applyImport(load.data)
                        pendingImport = null
                        scope.launch { snackbar.showSnackbar("Import successful") }
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }
}
