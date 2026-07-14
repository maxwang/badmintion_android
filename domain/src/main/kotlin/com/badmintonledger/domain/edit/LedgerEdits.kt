package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session

private fun isPositive(x: Long?): Boolean = x != null && x > 0

private fun isPositive(x: Double?): Boolean = x != null && x.isFinite() && x > 0

@Suppress("LongParameterList", "ReturnCount")
fun addRefill(
    data: LedgerData,
    id: String,
    date: String,
    paidCents: Long?,
    creditCents: Long?,
    contributions: List<Contribution>,
): EditResult<Refill> {
    if (!isPositive(paidCents) || !isPositive(creditCents)) {
        return EditResult.Err("Paid and credit amounts must be positive")
    }
    if (contributions.any { it.memberId.isEmpty() || it.amount.value <= 0 }) {
        return EditResult.Err("Contribution amounts must be positive")
    }
    if (contributions.sumOf { it.amount.value } != paidCents) {
        return EditResult.Err("Contributions must add up to the paid amount")
    }
    val r = Refill(id, date, Cents(paidCents!!), Cents(creditCents!!), contributions)
    return EditResult.Ok(data.copy(refills = data.refills + r), r)
}

@Suppress("ReturnCount")
fun addPayment(
    data: LedgerData,
    id: String,
    memberId: String,
    amountCents: Long?,
    date: String,
): EditResult<Payment> {
    if (memberId.isEmpty()) return EditResult.Err("Please select a member")
    if (!isPositive(amountCents)) return EditResult.Err("Amount must be positive")
    val p = Payment(id, memberId, Cents(amountCents!!), date)
    return EditResult.Ok(data.copy(payments = data.payments + p), p)
}

@Suppress("ReturnCount")
private fun validSessionFields(
    hours: Double?,
    rateCents: Long?,
    factor: Double?,
    playerIds: List<String>?,
    checkAll: Boolean,
): String? {
    if ((checkAll || hours != null) && !isPositive(hours)) return "Hours must be a positive number"
    if ((checkAll || rateCents != null) && !isPositive(rateCents)) return "Rate must be a positive number"
    if ((checkAll || factor != null) && !isPositive(factor)) return "Factor must be a positive number"
    if ((checkAll || playerIds != null) && playerIds.isNullOrEmpty()) return "Select at least one player"
    return null
}

@Suppress("LongParameterList", "ReturnCount")
fun addSession(
    data: LedgerData,
    id: String,
    date: String,
    hours: Double?,
    rateCents: Long?,
    factor: Double?,
    playerIds: List<String>,
): EditResult<Session> {
    validSessionFields(hours, rateCents, factor, playerIds, checkAll = true)
        ?.let { return EditResult.Err(it) }
    if (findSessionInWeek(data, date) != null) {
        return EditResult.Err("This week already has a record — edit the existing one")
    }
    val s = Session(id, date, hours!!, Cents(rateCents!!), factor!!, playerIds)
    return EditResult.Ok(data.copy(sessions = data.sessions + s), s)
}

data class SessionUpdate(
    val date: String? = null,
    val hours: Double? = null,
    val rateCents: Long? = null,
    val factor: Double? = null,
    val playerIds: List<String>? = null,
)

@Suppress("ReturnCount")
fun updateSession(
    data: LedgerData,
    id: String,
    update: SessionUpdate,
): EditResult<Session> {
    val s = data.sessions.firstOrNull { it.id == id } ?: return EditResult.Err("Record not found")
    validSessionFields(update.hours, update.rateCents, update.factor, update.playerIds, checkAll = false)
        ?.let { return EditResult.Err(it) }
    if (update.date != null && findSessionInWeek(data, update.date, id) != null) {
        return EditResult.Err("Another record already exists in the target week")
    }
    val updated =
        s.copy(
            date = update.date ?: s.date,
            hours = update.hours ?: s.hours,
            rate = update.rateCents?.let { Cents(it) } ?: s.rate,
            factor = update.factor ?: s.factor,
            playerIds = update.playerIds ?: s.playerIds,
        )
    return EditResult.Ok(
        data.copy(sessions = data.sessions.map { if (it.id == id) updated else it }),
        updated,
    )
}

fun deleteSession(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(sessions = data.sessions.filter { it.id != id })

fun deleteRefill(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(refills = data.refills.filter { it.id != id })

fun deletePayment(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(payments = data.payments.filter { it.id != id })
