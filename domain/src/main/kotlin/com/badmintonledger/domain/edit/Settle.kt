package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Payment

/**
 * Records one full-settlement payment per selected debtor (WeChat payment page:
 * checking a member pays their entire debt). All-or-nothing: the returned document
 * only exists when every payment was valid.
 */
@Suppress("ReturnCount")
fun settleDebtors(
    data: LedgerData,
    memberIds: List<String>,
    paymentIds: List<String>,
    date: String,
): EditResult<List<Payment>> {
    require(paymentIds.size == memberIds.size) { "one payment id per member" }
    if (memberIds.isEmpty()) return EditResult.Err("Please select a member")
    var doc = data
    val created = mutableListOf<Payment>()
    for ((i, memberId) in memberIds.withIndex()) {
        val owedCents = -(memberBalancesCents(doc)[memberId] ?: 0L)
        if (owedCents <= 0) return EditResult.Err("Nothing owing for the selected member")
        when (val r = addPayment(doc, paymentIds[i], memberId, owedCents, date)) {
            is EditResult.Ok -> {
                doc = r.data
                created += r.value
            }
            is EditResult.Err -> return r
        }
    }
    return EditResult.Ok(doc, created)
}
