package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData

/** Ledger mutations return a new document (never mutate) or a human-readable refusal. */
sealed interface EditResult<out T> {
    data class Ok<T>(val data: LedgerData, val value: T) : EditResult<T>

    data class Err(val reason: String) : EditResult<Nothing>
}
