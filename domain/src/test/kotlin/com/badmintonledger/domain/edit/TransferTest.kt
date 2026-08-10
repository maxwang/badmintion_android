package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransferTest {
    // A has a 10000-cent balance (contributed 100, never played); B and the guest G have none.
    private fun fixture(): LedgerData {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "G", "客串", true).data
        return data.copy(
            refills =
                listOf(
                    Refill(
                        "r1",
                        "2026-07-01",
                        Cents(10000),
                        Cents(12500),
                        listOf(Contribution("A", Cents(10000))),
                    ),
                ),
        )
    }

    @Test
    fun `rejects missing or guest members, same-person, non-positive amount, bad date`() {
        val data = fixture()
        assertEquals(EditResult.Err("请选择转出成员"), addTransfer(data, "t1", "nope", "B", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转出成员"), addTransfer(data, "t1", "G", "B", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转入成员"), addTransfer(data, "t1", "A", "nope", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转入成员"), addTransfer(data, "t1", "A", "G", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("转出转入不能是同一人"), addTransfer(data, "t1", "A", "A", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("金额需为正数"), addTransfer(data, "t1", "A", "B", -1, "2026-07-05"))
        assertEquals(EditResult.Err("金额需为正数"), addTransfer(data, "t1", "A", "B", null, "2026-07-05"))
        assertEquals(EditResult.Err("日期格式不正确"), addTransfer(data, "t1", "A", "B", 1000, "07/05/2026"))
        assertEquals(emptyList(), data.transfers)
    }

    @Test
    fun `rejects an amount exceeding the sender's current balance, allows the exact balance`() {
        val data = fixture() // A has 10000, B has 0
        assertEquals(EditResult.Err("转出成员余额不足"), addTransfer(data, "t1", "A", "B", 10001, "2026-07-05"))
        assertEquals(EditResult.Err("转出成员余额不足"), addTransfer(data, "t1", "B", "A", 1, "2026-07-05"))
        assertIs<EditResult.Ok<Transfer>>(addTransfer(data, "t1", "A", "B", 10000, "2026-07-05"))
    }

    @Test
    fun `succeeds - balance moves from sender to receiver, sum and pool unaffected`() {
        val data = fixture()
        val before = memberBalancesCents(data)
        val r = addTransfer(data, "t1", "A", "B", 4000, "2026-07-05")
        assertIs<EditResult.Ok<Transfer>>(r)
        assertEquals(Transfer("t1", "A", "B", Cents(4000), "2026-07-05"), r.value)
        assertEquals(1, r.data.transfers.size)

        val after = memberBalancesCents(r.data)
        assertEquals(before.getValue("A") - 4000, after["A"])
        assertEquals(before.getValue("B") + 4000, after["B"])
        assertEquals(before.values.sum(), after.values.sum())
        assertEquals(poolRemainingCents(data), poolRemainingCents(r.data))
    }

    @Test
    fun `delete removes the entry and balances revert as if it never happened`() {
        val data = fixture()
        val added = addTransfer(data, "t1", "A", "B", 4000, "2026-07-05")
        assertIs<EditResult.Ok<Transfer>>(added)
        val reverted = deleteTransfer(added.data, "t1")
        assertEquals(emptyList(), reverted.transfers)
        assertEquals(memberBalancesCents(data), memberBalancesCents(reverted))
    }
}
