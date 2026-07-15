package com.badmintonledger.domain.backup

import com.badmintonledger.domain.model.LedgerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("LongParameterList")
private fun fixture(
    version: String = "1",
    membersJson: String = """[
        {"id":"A","name":"阿安","isGuest":false},
        {"id":"G","name":"客串","isGuest":true}
    ]""",
    configJson: String = """{"defaultRate":24,"defaultPaid":2000,"defaultCredit":2500}""",
    refillsJson: String = """[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
        "contributions":[{"memberId":"A","amount":600}]}]""",
    paymentsJson: String = """[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}]""",
    sessionsJson: String = """[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
        "factor":0.8,"playerIds":["A","G"]}]""",
): String =
    """{"version":$version,"members":$membersJson,"config":$configJson,
        "refills":$refillsJson,"payments":$paymentsJson,"sessions":$sessionsJson}"""

class BackupCodecTest {
    @Test
    fun `complete backup passes with a summary and the decoded document`() {
        val text = fixture()
        val r = BackupCodec.validate(text)
        assertIs<ImportResult.Ok>(r)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), r.summary)
        assertEquals(BackupCodec.decode(text), r.data)
        assertEquals("阿安", r.data.members[0].name)
        assertEquals(60000L, r.data.refills[0].contributions[0].amount.value)
    }

    @Test
    fun `default empty data passes too`() {
        val r = BackupCodec.validate(BackupCodec.encode(LedgerData()))
        assertIs<ImportResult.Ok>(r)
        assertEquals(ImportResult.Summary(0, 0, 0), r.summary)
        assertEquals(LedgerData(), r.data)
    }

    @Test
    fun `rejects non-objects and wrong versions`() {
        assertIs<ImportResult.Err>(BackupCodec.validate("null"))
        assertIs<ImportResult.Err>(BackupCodec.validate("\"[]\""))
        assertIs<ImportResult.Err>(BackupCodec.validate("[]"))
        assertIs<ImportResult.Err>(BackupCodec.validate("not json at all"))
        assertEquals(
            ImportResult.Err("Unsupported backup version"),
            BackupCodec.validate(fixture(version = "2")),
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `rejects broken structures`() {
        // missing members array entirely
        val noMembers = """{"version":1,"config":{"defaultRate":24,"defaultPaid":2000,
            "defaultCredit":2500},"refills":[],"payments":[],"sessions":[]}"""
        assertEquals(ImportResult.Err("Backup is missing member data"), BackupCodec.validate(noMembers))

        // empty member name
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    membersJson = """[
                {"id":"A","name":"","isGuest":false},
                {"id":"G","name":"客串","isGuest":true}
            ]""",
                ),
            ),
        )
        // duplicate member ids
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    membersJson = """[
                {"id":"A","name":"阿安","isGuest":false},
                {"id":"A","name":"重复","isGuest":true}
            ]""",
                ),
            ),
        )
        // negative config default
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(configJson = """{"defaultRate":-1,"defaultPaid":2000,"defaultCredit":2500}"""),
            ),
        )
        // wrong date format
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    sessionsJson = """[{"id":"s1","date":"07/04/2026","hours":4,
                "rate":24,"factor":0.8,"playerIds":["A","G"]}]""",
                ),
            ),
        )
        // zero hours
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":0,
                "rate":24,"factor":0.8,"playerIds":["A","G"]}]""",
                ),
            ),
        )
        // empty player list
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":4,
                "rate":24,"factor":0.8,"playerIds":[]}]""",
                ),
            ),
        )
        // zero contribution amount
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    refillsJson = """[{"id":"r1","date":"2026-07-01","paid":600,
                "credit":750,"contributions":[{"memberId":"A","amount":0}]}]""",
                ),
            ),
        )
    }

    @Test
    fun `rejects references to missing members`() {
        assertEquals(
            ImportResult.Err("Backup references a missing member"),
            BackupCodec.validate(
                fixture(
                    sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":4,
                "rate":24,"factor":0.8,"playerIds":["A","X"]}]""",
                ),
            ),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    paymentsJson = """[{"id":"p1","memberId":"X","amount":25.6,
                "date":"2026-07-05"}]""",
                ),
            ),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    refillsJson = """[{"id":"r1","date":"2026-07-01","paid":600,
                "credit":750,"contributions":[{"memberId":"X","amount":600}]}]""",
                ),
            ),
        )
    }

    @Test
    fun `rejects string-typed scalars`() {
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":"4",
                "rate":24,"factor":0.8,"playerIds":["A","G"]}]""",
                ),
            ),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(
                fixture(
                    membersJson = """[
                {"id":"A","name":"阿安","isGuest":"true"},
                {"id":"G","name":"客串","isGuest":true}
            ]""",
                ),
            ),
        )
    }

    @Test
    fun `export file name`() {
        assertEquals("badminton-backup-2026-07-06.json", BackupCodec.exportFileName("2026-07-06"))
    }
}
