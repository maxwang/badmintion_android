package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Transfer

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

// 转账：将球馆余额从一名正式成员直接转移给另一名正式成员（线下已结清，仅由管理员在此登记）；
// 与会员年费账本无关，也不影响球馆额度（poolRemainingCents），纯粹是两人余额之间的再分配。
@Suppress("LongParameterList", "ReturnCount")
fun addTransfer(
    data: LedgerData,
    id: String,
    fromMemberId: String,
    toMemberId: String,
    amountCents: Long?,
    date: String,
): EditResult<Transfer> {
    val fromMember = data.members.firstOrNull { it.id == fromMemberId }
    if (fromMember == null || fromMember.isGuest) return EditResult.Err("请选择转出成员")
    val toMember = data.members.firstOrNull { it.id == toMemberId }
    if (toMember == null || toMember.isGuest) return EditResult.Err("请选择转入成员")
    if (fromMemberId == toMemberId) return EditResult.Err("转出转入不能是同一人")
    if (amountCents == null || amountCents <= 0) return EditResult.Err("金额需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    val fromBalance = memberBalancesCents(data)[fromMemberId] ?: 0L
    if (amountCents > fromBalance) return EditResult.Err("转出成员余额不足")
    val t = Transfer(id, fromMemberId, toMemberId, Cents(amountCents), date)
    return EditResult.Ok(data.copy(transfers = data.transfers + t), t)
}

fun deleteTransfer(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(transfers = data.transfers.filter { it.id != id })
