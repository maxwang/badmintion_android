package com.badmintonledger.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.poster.renderPoster
import com.badmintonledger.app.poster.sharePoster
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.report.buildMonthlyPayload
import com.badmintonledger.domain.report.buildWeeklyPayload
import com.badmintonledger.domain.report.layoutPoster
import com.badmintonledger.domain.report.monthlyPosterLines
import com.badmintonledger.domain.report.reportOptions
import com.badmintonledger.domain.report.weeklyPosterLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("ReturnCount")
private fun buildPoster(
    data: LedgerData,
    week: Boolean,
    sessionId: String?,
    month: String?,
): Bitmap? {
    val lines =
        if (week) {
            val id = sessionId ?: return null
            weeklyPosterLines(buildWeeklyPayload(data, id))
        } else {
            val ym = month ?: return null
            monthlyPosterLines(buildMonthlyPayload(data, ym))
        }
    return renderPoster(layoutPoster(lines))
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ReportScreen(
    vm: LedgerViewModel,
    initialSessionId: String?,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var weekMode by remember { mutableStateOf(true) }
    var sessionId by remember { mutableStateOf(initialSessionId) }
    var month by remember { mutableStateOf<String?>(null) }
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var weekMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }

    val current = data
    val options = remember(current) { current?.let { reportOptions(it) } }
    LaunchedEffect(options) {
        val o = options ?: return@LaunchedEffect
        if (sessionId == null || o.weeks.none { it.sessionId == sessionId }) {
            sessionId = o.weeks.firstOrNull()?.sessionId
        }
        if (month == null || month !in o.months) month = o.months.firstOrNull()
        // arriving from a session save: auto-generate this week's poster
        if (initialSessionId != null && poster == null && current != null) {
            poster =
                withContext(Dispatchers.Default) {
                    buildPoster(current, week = true, sessionId = sessionId, month = month)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (current == null || options == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = weekMode,
                        onClick = {
                            weekMode = true
                            poster = null
                        },
                        label = { Text("Weekly") },
                    )
                    FilterChip(
                        selected = !weekMode,
                        onClick = {
                            weekMode = false
                            poster = null
                        },
                        label = { Text("Monthly") },
                    )
                }
            }
            item {
                if (weekMode) {
                    ExposedDropdownMenuBox(expanded = weekMenu, onExpandedChange = { weekMenu = it }) {
                        OutlinedTextField(
                            value =
                                options.weeks.firstOrNull { it.sessionId == sessionId }?.label
                                    ?: "No weeks recorded",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Week") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                            options.weeks.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.label) },
                                    onClick = {
                                        sessionId = w.sessionId
                                        poster = null
                                        weekMenu = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = monthMenu, onExpandedChange = { monthMenu = it }) {
                        OutlinedTextField(
                            value = month ?: "No months recorded",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                            options.months.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        month = m
                                        poster = null
                                        monthMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = if (weekMode) sessionId != null else month != null,
                        onClick = {
                            scope.launch {
                                val bmp =
                                    withContext(Dispatchers.Default) {
                                        buildPoster(current, weekMode, sessionId, month)
                                    }
                                if (bmp == null) {
                                    snackbar.showSnackbar("Nothing to generate yet")
                                } else {
                                    poster = bmp
                                }
                            }
                        },
                    ) { Text("Generate poster") }
                    OutlinedButton(
                        enabled = poster != null,
                        onClick = {
                            val bmp = poster ?: return@OutlinedButton
                            scope.launch { sharePoster(context, bmp) }
                        },
                    ) { Text("Share") }
                }
            }
            item {
                poster?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Poster preview",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}
