package com.badmintonledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.backup.ImportResult
import com.badmintonledger.domain.edit.EditResult
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Config
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.dollarsToCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.badmintonledger.domain.edit.addMember as domainAddMember
import com.badmintonledger.domain.edit.removeMember as domainRemoveMember
import com.badmintonledger.domain.edit.renameMember as domainRenameMember
import com.badmintonledger.domain.edit.setGuest as domainSetGuest

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as LedgerApplication).store

    private val _ledger = MutableStateFlow<LedgerData?>(null)
    val ledger: StateFlow<LedgerData?> = _ledger

    init {
        viewModelScope.launch { _ledger.value = store.data.first() }
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

    fun validateBackup(text: String): ImportResult = BackupCodec.validate(text)

    /** Replaces the whole document. Call only after validateBackup returned Ok. */
    fun applyImport(text: String) {
        persist(BackupCodec.decode(text))
    }
}
