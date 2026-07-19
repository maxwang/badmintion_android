package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Membership
import com.badmintonledger.domain.model.dollarsToCents

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

@Suppress("LongParameterList", "ReturnCount")
fun addMembershipFee(
    data: LedgerData,
    id: String,
    memberId: String,
    year: Int,
    date: String,
    amountCents: Long?,
): EditResult<Membership> {
    if (memberId.isEmpty()) return EditResult.Err("请选择成员")
    if (amountCents == null || amountCents <= 0) return EditResult.Err("金额需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    if (year <= 0) return EditResult.Err("年份不正确")
    if (data.memberships.any { it.memberId == memberId && it.year == year }) {
        return EditResult.Err("该成员该年度已收取会费")
    }
    val mf = Membership(id, memberId, year, date, Cents(amountCents))
    return EditResult.Ok(data.copy(memberships = data.memberships + mf), mf)
}

fun deleteMembershipFee(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(memberships = data.memberships.filter { it.id != id })

@Suppress("ReturnCount")
fun setMembershipFeePaid(
    data: LedgerData,
    id: String,
    paid: Boolean,
    date: String?,
): EditResult<Unit> {
    val mf = data.memberships.firstOrNull { it.id == id } ?: return EditResult.Err("记录不存在")
    if (paid && (date == null || !DATE_RE.matches(date))) return EditResult.Err("日期格式不正确")
    val updated = mf.copy(paidDate = if (paid) date else null)
    return EditResult.Ok(data.copy(memberships = data.memberships.map { if (it.id == id) updated else it }), Unit)
}

data class MembershipChargeResult(val chargedNames: List<String>, val skippedNames: List<String>)

// ids 需与本次符合条件（正式且启用）的成员数一一对应，通常由调用方先算好人数
// （见 calc.membershipStatus 的 eligible）再生成同等数量的 id。
// amount 为俱乐部本年度会费总额，按候选人数均分（末位吸收取整余数，与 calc.sessionShares 的分摊规则一致）；
// 已开单过的成员跳过，不重新计入其分摊。
@Suppress("LongParameterList", "ReturnCount")
fun chargeAnnualMembershipFee(
    data: LedgerData,
    ids: List<String>,
    year: Int,
    totalAmountDollars: Double?,
    date: String,
): EditResult<MembershipChargeResult> {
    if (totalAmountDollars == null || !totalAmountDollars.isFinite() || totalAmountDollars <= 0) {
        return EditResult.Err("金额需为正数")
    }
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    if (year <= 0) return EditResult.Err("年份不正确")
    val candidates = data.members.filter { !it.isGuest && it.active }
    require(ids.size == candidates.size) { "one id per eligible member (see calc.membershipStatus.eligible)" }
    if (candidates.isEmpty()) return EditResult.Ok(data, MembershipChargeResult(emptyList(), emptyList()))
    val totalCents = dollarsToCents(totalAmountDollars)
    val n = candidates.size
    val baseCents = totalCents / n
    var doc = data
    val charged = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    candidates.forEachIndexed { i, m ->
        if (doc.memberships.any { it.memberId == m.id && it.year == year }) {
            skipped += m.name
            return@forEachIndexed
        }
        val shareCents = if (i == n - 1) totalCents - baseCents * (n - 1) else baseCents
        when (val r = addMembershipFee(doc, ids[i], m.id, year, date, shareCents)) {
            is EditResult.Ok -> {
                doc = r.data
                charged += m.name
            }
            is EditResult.Err -> return r
        }
    }
    return EditResult.Ok(doc, MembershipChargeResult(charged, skipped))
}
