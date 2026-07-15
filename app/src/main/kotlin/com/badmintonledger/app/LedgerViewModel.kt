package com.badmintonledger.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.backup.ImportResult
import com.badmintonledger.domain.edit.EditResult
import com.badmintonledger.domain.edit.SessionUpdate
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Config
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.dollarsToCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.badmintonledger.domain.edit.addMember as domainAddMember
import com.badmintonledger.domain.edit.addRefill as domainAddRefill
import com.badmintonledger.domain.edit.addSession as domainAddSession
import com.badmintonledger.domain.edit.removeMember as domainRemoveMember
import com.badmintonledger.domain.edit.renameMember as domainRenameMember
import com.badmintonledger.domain.edit.setGuest as domainSetGuest
import com.badmintonledger.domain.edit.settleDebtors as domainSettleDebtors
import com.badmintonledger.domain.edit.updateSession as domainUpdateSession

@Suppress("TooManyFunctions")
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as LedgerApplication).store

    private val _ledger = MutableStateFlow<LedgerData?>(null)
    val ledger: StateFlow<LedgerData?> = _ledger

    init {
        // compareAndSet: a mutation that lands before the first DataStore emission must not
        // be clobbered by the (stale) loaded document.
        viewModelScope.launch { _ledger.compareAndSet(null, store.data.first()) }
    }

    private var idCounter = 0

    private fun newId(prefix: String): String {
        idCounter += 1
        return "${prefix}_${System.currentTimeMillis()}_$idCounter"
    }

    /**
     * The in-memory document is authoritative for reads within this single-writer app:
     * it is updated synchronously so back-to-back mutations always see the latest state,
     * even before DataStore has finished (or started) persisting. DataStore is write-behind.
     */
    private fun persist(data: LedgerData) {
        _ledger.value = data
        viewModelScope.launch { store.save(data) }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = ledger.value ?: return
        persist(domainAddMember(current, newId("m"), trimmed, isGuest = false).data)
    }

    fun renameMember(
        id: String,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = ledger.value ?: return
        persist(domainRenameMember(current, id, trimmed))
    }

    fun setGuest(
        id: String,
        isGuest: Boolean,
    ) {
        val current = ledger.value ?: return
        persist(domainSetGuest(current, id, isGuest))
    }

    /** Returns null on success, or the refusal reason (member has records). */
    fun removeMember(id: String): String? {
        val current = ledger.value ?: return "Data is still loading"
        return when (val r = domainRemoveMember(current, id)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }

    /** Returns null on success, or an error message. All three must be positive. */
    @Suppress("ReturnCount", "ComplexCondition")
    fun saveConfig(
        rateDollars: Double?,
        paidDollars: Double?,
        creditDollars: Double?,
    ): String? {
        if (rateDollars == null || !rateDollars.isFinite() || rateDollars <= 0 ||
            paidDollars == null || !paidDollars.isFinite() || paidDollars <= 0 ||
            creditDollars == null || !creditDollars.isFinite() || creditDollars <= 0
        ) {
            return "Enter valid positive numbers"
        }
        val current = ledger.value ?: return "Data is still loading"
        persist(
            current.copy(
                config =
                    Config(
                        defaultRate = Cents(dollarsToCents(rateDollars)),
                        defaultPaid = Cents(dollarsToCents(paidDollars)),
                        defaultCredit = Cents(dollarsToCents(creditDollars)),
                    ),
            ),
        )
        return null
    }

    /** Creates this week's record or edits [editId]. Returns null on success, or the refusal reason. */
    @Suppress("LongParameterList", "ReturnCount")
    fun saveSession(
        editId: String?,
        date: String,
        hours: Double?,
        rateDollars: Double?,
        factor: Double?,
        playerIds: List<String>,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        if (hours == null) return "Hours must be a positive number"
        if (rateDollars == null) return "Rate must be a positive number"
        if (factor == null) return "Factor must be a positive number"
        val rateCents = dollarsToCents(rateDollars)
        val result =
            if (editId == null) {
                domainAddSession(current, newId("s"), date, hours, rateCents, factor, playerIds)
            } else {
                domainUpdateSession(current, editId, SessionUpdate(date, hours, rateCents, factor, playerIds))
            }
        return when (result) {
            is EditResult.Ok -> {
                persist(result.data)
                null
            }
            is EditResult.Err -> result.reason
        }
    }

    /** Returns null on success, or the refusal reason. Amounts arrive in dollars from the form. */
    fun addRefill(
        date: String,
        paidDollars: Double?,
        creditDollars: Double?,
        contributionsDollars: List<Pair<String, Double>>,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        val contributions =
            contributionsDollars.map { (memberId, dollars) -> Contribution(memberId, Cents(dollarsToCents(dollars))) }
        val result =
            domainAddRefill(
                current,
                newId("r"),
                date,
                paidDollars?.let(::dollarsToCents),
                creditDollars?.let(::dollarsToCents),
                contributions,
            )
        return when (result) {
            is EditResult.Ok -> {
                persist(result.data)
                null
            }
            is EditResult.Err -> result.reason
        }
    }

    /** Records one full-debt payment per selected member. Returns null on success. */
    fun settleDebtors(
        memberIds: List<String>,
        date: String,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        return when (val r = domainSettleDebtors(current, memberIds, memberIds.map { newId("p") }, date)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }

    /** Adds a guest member and returns it (so the caller can auto-select), or null when the name is blank. */
    @Suppress("ReturnCount")
    fun addGuest(name: String): Member? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = ledger.value ?: return null
        val r = domainAddMember(current, newId("m"), trimmed, isGuest = true)
        persist(r.data)
        return r.value
    }

    /** Reads and validates a backup off the main thread; the document is decoded exactly once. */
    suspend fun loadBackup(uri: Uri): BackupLoad =
        withContext(Dispatchers.IO) {
            val text =
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: return@withContext BackupLoad.CouldNotRead
            runCatching {
                when (val r = BackupCodec.validate(text)) {
                    is ImportResult.Ok -> BackupLoad.Ready(r.data, r.summary)
                    is ImportResult.Err -> BackupLoad.Invalid(r.reason)
                }
            }.getOrElse { BackupLoad.Invalid("Not a valid backup file") }
        }

    /** Replaces the whole document with an already-validated backup. */
    fun applyImport(data: LedgerData) {
        persist(data)
    }
}

sealed interface BackupLoad {
    data object CouldNotRead : BackupLoad

    data class Invalid(val reason: String) : BackupLoad

    data class Ready(
        val data: LedgerData,
        val summary: ImportResult.Summary,
    ) : BackupLoad
}
