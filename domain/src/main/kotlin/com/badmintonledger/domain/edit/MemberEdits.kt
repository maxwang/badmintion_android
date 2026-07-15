package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member

fun addMember(
    data: LedgerData,
    id: String,
    name: String,
    isGuest: Boolean,
): EditResult.Ok<Member> {
    val m = Member(id, name, isGuest)
    return EditResult.Ok(data.copy(members = data.members + m), m)
}

fun renameMember(
    data: LedgerData,
    id: String,
    name: String,
): LedgerData = data.copy(members = data.members.map { if (it.id == id) it.copy(name = name) else it })

fun setGuest(
    data: LedgerData,
    id: String,
    isGuest: Boolean,
): LedgerData = data.copy(members = data.members.map { if (it.id == id) it.copy(isGuest = isGuest) else it })

fun memberReferenced(
    data: LedgerData,
    id: String,
): Boolean =
    data.sessions.any { id in it.playerIds } ||
        data.payments.any { it.memberId == id } ||
        data.refills.any { r -> r.contributions.any { it.memberId == id } }

fun removeMember(
    data: LedgerData,
    id: String,
): EditResult<Unit> {
    if (memberReferenced(data, id)) return EditResult.Err("该成员已有记录，不能删除")
    return EditResult.Ok(data.copy(members = data.members.filter { it.id != id }), Unit)
}
