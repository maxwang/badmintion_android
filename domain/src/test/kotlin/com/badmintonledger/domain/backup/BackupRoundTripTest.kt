package com.badmintonledger.domain.backup

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.model.Cents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupRoundTripTest {
    private val backupJson =
        """
        {
          "version": 1,
          "members": [
            { "id": "A", "name": "阿安", "isGuest": false },
            { "id": "G", "name": "客串", "isGuest": true }
          ],
          "config": { "defaultRate": 24, "defaultPaid": 2000, "defaultCredit": 2500 },
          "refills": [{
            "id": "r1", "date": "2026-07-01", "paid": 600, "credit": 750,
            "contributions": [{ "memberId": "A", "amount": 600 }]
          }],
          "payments": [{ "id": "p1", "memberId": "G", "amount": 25.6, "date": "2026-07-05" }],
          "sessions": [{ "id": "s1", "date": "2026-07-04", "hours": 4, "rate": 24,
                         "factor": 0.8, "playerIds": ["A", "G"] }]
        }
        """.trimIndent()

    @Test
    fun `import compute re-export preserves the ledger`() {
        assertIs<ImportResult.Ok>(BackupCodec.validate(backupJson))
        val data = BackupCodec.decode(backupJson)
        assertEquals(3, data.version)
        assertEquals(Cents(2400), data.rates.single().rate)
        assertEquals(emptyList(), data.memberships)
        assertEquals(Cents(5000), data.config.membershipFee)

        // session 76.80 split two ways = 38.40 each
        val bal = memberBalancesCents(data)
        assertEquals(60000L - 3840, bal["A"]) // contributed 600
        assertEquals(2560L - 3840, bal["G"]) // paid 25.60 cash, owes 12.80
        assertEquals(75000L - 9600, poolRemainingCents(data))

        val reimported = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data, reimported)
        assertEquals(bal, memberBalancesCents(reimported))
    }
}
