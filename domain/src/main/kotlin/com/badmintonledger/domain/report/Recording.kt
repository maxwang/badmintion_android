package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.membershipBalancesCents
import com.badmintonledger.domain.calc.sessionFaceCostCents
import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class SessionPreview(
    val faceDollars: String,
    val realDollars: String,
    val players: Int,
    val perPersonDollars: String,
)

/** Live cost preview for the session form (port of session.js recalc); null until every input is valid. */
@Suppress("ReturnCount")
fun buildSessionPreview(
    hours: Double?,
    rateCents: Long?,
    factor: Double?,
    playerCount: Int,
): SessionPreview? {
    if (hours == null || !hours.isFinite() || hours <= 0) return null
    if (rateCents == null || rateCents <= 0) return null
    if (factor == null || !factor.isFinite() || factor <= 0) return null
    if (playerCount < 1) return null
    val probe = Session("preview", "2026-01-05", hours, Cents(rateCents), factor, List(playerCount) { "p$it" })
    val realCents = sessionRealCostCents(probe)
    return SessionPreview(
        faceDollars = centsToDollars(sessionFaceCostCents(probe)),
        realDollars = centsToDollars(realCents),
        players = playerCount,
        perPersonDollars = centsToDollars(realCents / playerCount),
    )
}

private const val FACTOR_SCALE = 10_000L

/** "paid ÷ credit" to 4 decimals (port of refill.js factor line), or an em dash while inputs are invalid. */
@Suppress("ReturnCount")
fun refillFactorText(
    paidCents: Long?,
    creditCents: Long?,
): String {
    if (paidCents == null || paidCents <= 0) return "—"
    if (creditCents == null || creditCents <= 0) return "—"
    val scaled = (paidCents * FACTOR_SCALE + creditCents / 2) / creditCents
    return "${scaled / FACTOR_SCALE}.${(scaled % FACTOR_SCALE).toString().padStart(4, '0')}"
}

data class DebtorRow(val id: String, val name: String, val owedCents: Long, val owedDollars: String)

data class PaymentMemberRow(
    val id: String,
    val name: String,
    val isGuest: Boolean,
    val owes: Boolean,
    val absDollars: String,
)

data class MembershipDebtorRow(val id: String, val name: String, val owedDollars: String)

data class PaymentSummary(
    val debtors: List<DebtorRow>,
    val rows: List<PaymentMemberRow>,
    val membershipDebtors: List<MembershipDebtorRow>,
)

/** Port of payment.js onShow: debtor chips plus the all-members reference balance list. */
fun buildPaymentSummary(data: LedgerData): PaymentSummary {
    val bal = memberBalancesCents(data)
    val rows =
        data.members.map { m ->
            val c = bal[m.id] ?: 0L
            PaymentMemberRow(m.id, m.name, m.isGuest, owes = c < 0, absDollars = centsToDollars(abs(c)))
        }
    val debtors =
        data.members.mapNotNull { m ->
            val c = bal[m.id] ?: 0L
            if (c < 0) DebtorRow(m.id, m.name, -c, centsToDollars(-c)) else null
        }
    // 会员年费欠费与球馆余额完全独立，单独一份「谁欠年费」清单
    val membershipBal = membershipBalancesCents(data)
    val membershipDebtors =
        data.members.mapNotNull { m ->
            val c = membershipBal[m.id] ?: 0L
            if (c < 0L) MembershipDebtorRow(m.id, m.name, centsToDollars(-c)) else null
        }
    return PaymentSummary(debtors, rows, membershipDebtors)
}
