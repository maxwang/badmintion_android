package com.badmintonledger.domain.backup

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.sessionShares
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealBackupTest {
    @Test
    fun `real WeChat export validates and balances obey the ledger identity`() {
        val file = File("../backups/real-backup.json")
        assumeTrue(file.exists(), "export from the WeChat app and save as backups/real-backup.json")

        val text = file.readText()
        assertIs<ImportResult.Ok>(BackupCodec.validate(text))
        val data = BackupCodec.decode(text)

        // ledger identity: sum of balances == contributions + payments - real session costs
        val bal = memberBalancesCents(data)
        val expected =
            data.refills.sumOf { r -> r.contributions.sumOf { it.amount.value } } +
                data.payments.sumOf { it.amount.value } -
                data.sessions.sumOf { sessionShares(it).totalCents }
        assertEquals(expected, bal.values.sum())

        // round trip is lossless
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
}
