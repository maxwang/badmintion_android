package com.badmintonledger.domain.backup

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Membership
import com.badmintonledger.domain.model.RateChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("LongParameterList")
private fun fixtureV3(
    membershipsJson: String = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50}]""",
    membersJson: String = """[
        {"id":"A","name":"阿安","isGuest":false},
        {"id":"G","name":"客串","isGuest":true}
    ]""",
    configJson: String = """{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50}""",
): String =
    """{"version":3,"members":$membersJson,"config":$configJson,
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
        "contributions":[{"memberId":"A","amount":600}]}],
    "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
    "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
        "factor":0.8,"playerIds":["A","G"]}],
    "memberships":$membershipsJson}"""

private const val V3_NO_MEMBERSHIPS =
    """{"version":3,"members":[{"id":"A","name":"阿安","isGuest":false}],
    "config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[],"payments":[],"sessions":[]}"""

private const val V3_NO_FEE =
    """{"version":3,"members":[{"id":"A","name":"阿安","isGuest":false}],
    "config":{"defaultPaid":2000,"defaultCredit":2500},
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[],"payments":[],"sessions":[],"memberships":[]}"""

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

private fun fixtureV2(ratesJson: String = """[{"id":"rt1","date":"2026-01-01","rate":24}]"""): String =
    """{"version":2,"members":[
        {"id":"A","name":"阿安","isGuest":false},
        {"id":"G","name":"客串","isGuest":true}
    ],"config":{"defaultPaid":2000,"defaultCredit":2500},
    "rates":$ratesJson,
    "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
        "contributions":[{"memberId":"A","amount":600}]}],
    "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
    "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
        "factor":0.8,"playerIds":["A","G"]}]}"""

@Suppress("LongParameterList")
private fun fixtureV4(
    transfersJson: String = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":5,"date":"2026-07-06"}]""",
    membershipsJson: String = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50}]""",
    membersJson: String = """[
        {"id":"A","name":"阿安","isGuest":false},
        {"id":"G","name":"客串","isGuest":true}
    ]""",
    configJson: String = """{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50}""",
): String =
    """{"version":4,"members":$membersJson,"config":$configJson,
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
        "contributions":[{"memberId":"A","amount":600}]}],
    "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
    "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
        "factor":0.8,"playerIds":["A","G"]}],
    "memberships":$membershipsJson,
    "transfers":$transfersJson}"""

private const val V4_NO_TRANSFERS =
    """{"version":4,"members":[{"id":"A","name":"阿安","isGuest":false}],
    "config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[],"payments":[],"sessions":[],"memberships":[]}"""

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
            ImportResult.Err("备份文件版本不兼容"),
            BackupCodec.validate(fixture(version = "5")),
        )
        // fixture() is a v1 shape (no "rates" key); version 2/3/4 alone doesn't fail, but the
        // missing rate history does.
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "2")))
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "3")))
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "4")))
    }

    @Test
    @Suppress("LongMethod")
    fun `rejects broken structures`() {
        // missing members array entirely
        val noMembers = """{"version":1,"config":{"defaultRate":24,"defaultPaid":2000,
            "defaultCredit":2500},"refills":[],"payments":[],"sessions":[]}"""
        assertEquals(ImportResult.Err("备份文件缺少成员数据"), BackupCodec.validate(noMembers))

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
            ImportResult.Err("备份数据引用了不存在的成员"),
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

    @Test
    fun `pretty export is indented, valid and round-trips`() {
        val data = BackupCodec.decode(fixture())
        val pretty = BackupCodec.encodePretty(data)
        assertTrue(pretty.contains("\n  \"version\""))
        assertIs<ImportResult.Ok>(BackupCodec.validate(pretty))
        assertEquals(data, BackupCodec.decode(pretty))
    }

    @Test
    fun `v2 backup passes, broken rate history rejected`() {
        val ok = BackupCodec.validate(fixtureV2())
        assertEquals(ImportResult.Ok::class, ok::class)
        assertEquals(
            ImportResult.Summary(members = 2, sessions = 1, refills = 1),
            (ok as ImportResult.Ok).summary,
        )
        assertEquals(
            ImportResult.Err("单价历史数据不完整"),
            BackupCodec.validate(fixtureV2(ratesJson = "[]")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixtureV2(ratesJson = """[{"id":"rt1","date":"2026-01-01","rate":-1}]""")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixtureV2(ratesJson = """[{"id":"rt1","date":"07/01/2026","rate":24}]""")),
        )
    }

    @Test
    fun `v1 and v2 import migrate through v3 to v4 in one decode`() {
        val r1 = BackupCodec.validate(fixture()) // v1 fixture
        assertIs<ImportResult.Ok>(r1)
        assertEquals(4, r1.data.version)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), r1.data.rates)
        assertEquals(emptyList(), r1.data.memberships)
        assertEquals(Cents(5000), r1.data.config.membershipFee)
        assertEquals(emptyList(), r1.data.transfers)
        val out = BackupCodec.encode(r1.data)
        assertTrue(out.contains("\"version\":4") || out.contains("\"version\": 4"))
        assertIs<ImportResult.Ok>(BackupCodec.validate(out))

        val r2 = BackupCodec.validate(fixtureV2()) // v2 fixture
        assertIs<ImportResult.Ok>(r2)
        assertEquals(4, r2.data.version)
        assertEquals(emptyList(), r2.data.memberships)
        assertEquals(Cents(5000), r2.data.config.membershipFee)
        assertEquals(emptyList(), r2.data.transfers)
    }

    @Test
    fun `v3 backup passes, missing or broken membership data rejected`() {
        val ok = BackupCodec.validate(fixtureV3())
        assertIs<ImportResult.Ok>(ok)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), (ok as ImportResult.Ok).summary)

        assertEquals(ImportResult.Err("会员年费数据不完整"), BackupCodec.validate(V3_NO_MEMBERSHIPS))
        assertEquals(ImportResult.Err("配置数据不完整"), BackupCodec.validate(V3_NO_FEE))
        val zeroAmount = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":0}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = zeroAmount)))
        val fractionalYear = """[{"id":"mf1","memberId":"A","year":2026.5,"date":"2026-07-01","amount":50}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = fractionalYear)))
        val badDate = """[{"id":"mf1","memberId":"A","year":2026,"date":"07/01/2026","amount":50}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = badDate)))
        val ghostMember = """[{"id":"mf1","memberId":"X","year":2026,"date":"2026-07-01","amount":50}]"""
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV3(membershipsJson = ghostMember)),
        )
    }

    @Test
    fun `membership paidDate optional but must be a valid date if present`() {
        val goodPaidDate =
            """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50,"paidDate":"2026-07-15"}]"""
        assertIs<ImportResult.Ok>(BackupCodec.validate(fixtureV3(membershipsJson = goodPaidDate)))

        val badPaidDate =
            """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50,"paidDate":"07/15/2026"}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = badPaidDate)))
    }

    @Test
    fun `member active optional but must be boolean if present`() {
        val withActive = """[{"id":"A","name":"阿安","isGuest":false,"active":false},
            {"id":"G","name":"客串","isGuest":true}]"""
        assertIs<ImportResult.Ok>(BackupCodec.validate(fixtureV3(membersJson = withActive)))

        val badActive = """[{"id":"A","name":"阿安","isGuest":false,"active":"no"},
            {"id":"G","name":"客串","isGuest":true}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membersJson = badActive)))
    }

    @Test
    fun `v4 backup passes, missing or broken transfer data rejected`() {
        val ok = BackupCodec.validate(fixtureV4())
        assertIs<ImportResult.Ok>(ok)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), (ok as ImportResult.Ok).summary)

        assertEquals(ImportResult.Err("转账数据不完整"), BackupCodec.validate(V4_NO_TRANSFERS))
        val zeroAmount = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":0,"date":"2026-07-06"}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV4(transfersJson = zeroAmount)))
        val badDate = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":5,"date":"07/06/2026"}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV4(transfersJson = badDate)))
        val ghostFrom = """[{"id":"tr1","fromMemberId":"X","toMemberId":"G","amount":5,"date":"2026-07-06"}]"""
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV4(transfersJson = ghostFrom)),
        )
        val ghostTo = """[{"id":"tr1","fromMemberId":"A","toMemberId":"X","amount":5,"date":"2026-07-06"}]"""
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV4(transfersJson = ghostTo)),
        )
    }

    @Test
    fun `v1 through v3 import migrate to v4 in one decode`() {
        val r1 = BackupCodec.validate(fixture()) // v1 fixture
        assertIs<ImportResult.Ok>(r1)
        assertEquals(4, r1.data.version)
        assertEquals(emptyList(), r1.data.transfers)

        val r2 = BackupCodec.validate(fixtureV2()) // v2 fixture
        assertIs<ImportResult.Ok>(r2)
        assertEquals(4, r2.data.version)
        assertEquals(emptyList(), r2.data.transfers)

        val r3 = BackupCodec.validate(fixtureV3()) // v3 fixture
        assertIs<ImportResult.Ok>(r3)
        assertEquals(4, r3.data.version)
        assertEquals(emptyList(), r3.data.transfers)
        assertEquals(1, r3.data.memberships.size)
        assertEquals(
            Membership("mf1", "A", 2026, "2026-07-01", Cents(5000)),
            r3.data.memberships[0],
        )

        val out = BackupCodec.encode(r1.data)
        assertTrue(out.contains("\"version\":4") || out.contains("\"version\": 4"))
        assertIs<ImportResult.Ok>(BackupCodec.validate(out))
    }
}
