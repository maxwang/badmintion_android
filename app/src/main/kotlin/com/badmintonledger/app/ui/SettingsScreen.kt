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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.BackupLoad
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.backup.shareBackup
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.model.Member
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Member?>(null) }
    var deleteTarget by remember { mutableStateOf<Member?>(null) }

    var paid by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    LaunchedEffect(data?.config) {
        data?.config?.let {
            paid = dollarsText(it.defaultPaid.value)
            credit = dollarsText(it.defaultCredit.value)
        }
    }

    var rateDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var rateValue by remember { mutableStateOf("") }

    var pendingImport by remember { mutableStateOf<BackupLoad.Ready?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val importPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                when (val load = vm.loadBackup(uri)) {
                    BackupLoad.CouldNotRead -> snackbar.showSnackbar("无法读取文件")
                    is BackupLoad.Invalid -> snackbar.showSnackbar(load.reason)
                    is BackupLoad.Ready -> pendingImport = load
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "成员管理",
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
                    TextButton(onClick = { renameTarget = member }) { Text("重命名") }
                    Text("补位", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = member.isGuest,
                        onCheckedChange = { vm.setGuest(member.id, it) },
                    )
                    IconButton(onClick = { deleteTarget = member }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除${member.name}")
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
                        label = { Text("新成员姓名") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            vm.addMember(newName)
                            newName = ""
                        },
                    ) { Text("添加") }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("球馆单价", style = MaterialTheme.typography.titleMedium) }
            items(current.rates.sortedByDescending { it.date }, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${r.date} 起")
                    Text("$${dollarsText(r.rate.value)}/小时")
                }
            }
            item {
                DateField(
                    label = "生效日期",
                    value = rateDate,
                    onChange = { rateDate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = rateValue,
                    onValueChange = { rateValue = it },
                    label = { Text("单价（$/小时）") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                Button(
                    onClick = {
                        val reason = vm.addRateChange(rateDate, rateValue.toDoubleOrNull())
                        if (reason == null) rateValue = ""
                        scope.launch { snackbar.showSnackbar(reason ?: "已记录") }
                    },
                ) { Text("记录价格变更") }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("默认充值参数", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text("充值实付（$）") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("充值到账额度（$）") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                Button(
                    onClick = {
                        val err =
                            vm.saveConfig(
                                paid.toDoubleOrNull(),
                                credit.toDoubleOrNull(),
                            )
                        scope.launch { snackbar.showSnackbar(err ?: "已保存") }
                    },
                ) { Text("保存默认参数") }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("数据备份", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                        Text("导入数据")
                    }
                    OutlinedButton(
                        onClick = {
                            val current = data ?: return@OutlinedButton
                            scope.launch {
                                shareBackup(context, current, LocalDate.now().toString())
                            }
                        },
                    ) { Text("导出数据") }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清空全部数据") }
            }
        }
    }

    renameTarget?.let { member ->
        var name by remember(member.id) { mutableStateOf(member.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名成员") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameMember(member.id, name)
                        renameTarget = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除成员") },
            text = { Text("删除 ${member.name}？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = vm.removeMember(member.id)
                        deleteTarget = null
                        if (reason != null) {
                            scope.launch { snackbar.showSnackbar(reason) }
                        }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    pendingImport?.let { load ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入数据") },
            text = {
                Text(
                    "备份包含 ${load.summary.members} 位成员、" +
                        "${load.summary.sessions} 条周记录、${load.summary.refills} 条充值。" +
                        "导入将覆盖全部现有数据，继续？",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.applyImport(load.data)
                        pendingImport = null
                        scope.launch { snackbar.showSnackbar("导入成功") }
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("清空全部数据") },
            text = { Text("将清空全部成员、记录与设置，且无法恢复，建议先导出备份。确定清空？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.resetAllData()
                        showResetConfirm = false
                        scope.launch { snackbar.showSnackbar("已清空") }
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}
