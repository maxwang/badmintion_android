# Milestone 1: Skeleton + Domain Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multi-module Gradle project builds; `domain/` fully ported from the WeChat mini program with parity tests green; minimal Android app module assembles.

**Architecture:** Two Gradle modules. `domain/` is pure Kotlin/JVM (KMP-ready): immutable `LedgerData` document, all money as integer cents (`Long`) wrapped in a `Cents` value class whose serializer reads/writes dollars to match the frozen backup JSON v1 contract. `app/` is a minimal Compose Android app that depends on `domain`. Edits return new copies (`EditResult`), never mutate.

**Tech Stack:** Kotlin 2.1.21, Gradle 8.14 (wrapper), AGP 8.10.1, kotlinx-serialization-json 1.8.1, kotlinx-datetime 0.6.2, kotlin-test on JUnit 5, ktlint + detekt.

## Global Constraints

- `domain/` is pure Kotlin: no `android.*` imports ever; only deps are kotlinx-serialization-json and kotlinx-datetime.
- All money is integer cents (`Long`) inside `domain/`; the JSON contract carries dollars (`"defaultRate": 24`, `"amount": 25.6`) — conversion happens only in `CentsAsDollarsSerializer`.
- Backup JSON schema `version: 1` is a frozen contract — field names and dollar units exactly as in the spec (`docs/superpowers/specs/2026-07-11-android-app-design.md`).
- Parity: same fixtures and expected values as the WeChat repo tests (`E:\Code\ai\wechat\badminton\tests\*.test.js`). Fixture member names stay in Chinese (they are data); test names and error messages are English.
- Cost formula parity: JS computes `Math.round(hours * rateDollars * factor * 100)`; Kotlin computes `round(hours * rateCents * factor)` — algebraically identical, verified by the ported fixtures.
- Rounding: `kotlin.math.round(x).toLong()` (ties away from zero == JS `Math.round` for the positive amounts that ever get rounded here).
- Week starts Monday; at most one session per week; members with records cannot be deleted.
- min SDK 26, compileSdk = targetSdk = 36, JDK 17 toolchain.
- TDD: every task writes the failing test first. Conventional commits.
- Never commit real backup JSON (`backups/` is gitignored).
- Package root: `com.badmintonledger.domain` (module `domain/`), `com.badmintonledger.app` (module `app/`).

**Intentionally not ported** (impossible or meaningless under static typing — do not "fix" this later):
- `yuanToCents('600')` string-input coercion — Kotlin signature is `Double`.
- `updateSession` ignoring unknown fields (`{hour: 2}`) — the type system prevents unknown fields.
- `newId()` / `todayStr()` impurity — `domain/` takes ids and dates as parameters; the app layer generates them (Milestone 2+).
- Auto-recompute-after-mutation test — `LedgerData` is immutable; the ported test edits via `copy()` and asserts recomputation.

## File Structure

```
badminton-ledger/
  settings.gradle.kts                    Task 1 (Task 10 adds :app)
  build.gradle.kts                       Task 1 (root: ktlint/detekt)
  gradle.properties                      Task 1
  gradle/libs.versions.toml              Task 1
  gradle/wrapper/*                       Task 1 (generated)
  .editorconfig                          Task 1
  config/detekt/detekt.yml               Task 1
  domain/build.gradle.kts                Task 1
  domain/src/main/kotlin/com/badmintonledger/domain/
    model/Cents.kt                       Task 2 — Cents value class, dollars conversion, serializer
    model/LedgerData.kt                  Task 2 — Member/Config/Contribution/Refill/Payment/Session/LedgerData
    calc/Calc.kt                         Tasks 3–4 — costs, shares, balances, pool, factor, month, hasContributed
    edit/EditResult.kt                   Task 5 — Ok/Err result type
    edit/MemberEdits.kt                  Task 5 — add/rename/setGuest/remove, memberReferenced
    edit/Weeks.kt                        Task 6 — weekStart, findSessionInWeek
    edit/LedgerEdits.kt                  Task 7 — addRefill/addPayment/addSession/updateSession/deletes
    backup/BackupCodec.kt                Task 8 — validate/decode/encode, exportFileName
    report/Report.kt                     Task 9 — balanceRows, breakdown rows, weekly/monthly payloads
  domain/src/test/kotlin/com/badmintonledger/domain/
    SanityTest.kt                        Task 1
    model/CentsTest.kt                   Task 2
    model/LedgerDataJsonTest.kt          Task 2
    calc/SessionCostTest.kt              Task 3
    calc/LedgerCalcTest.kt               Task 4
    edit/MemberEditTest.kt               Task 5
    edit/WeekTest.kt                     Task 6
    edit/LedgerEditTest.kt               Task 7
    backup/BackupCodecTest.kt            Task 8
    report/ReportTest.kt                 Task 9
    backup/BackupRoundTripTest.kt        Task 11
    backup/RealBackupTest.kt             Task 11
  app/build.gradle.kts                   Task 10
  app/src/main/AndroidManifest.xml       Task 10
  app/src/main/kotlin/com/badmintonledger/app/MainActivity.kt  Task 10
```

---

### Task 1: Gradle skeleton (domain module, wrapper, quality gates)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.editorconfig`, `config/detekt/detekt.yml`, `domain/build.gradle.kts`
- Create: `domain/src/test/kotlin/com/badmintonledger/domain/SanityTest.kt`
- Generated: `gradlew.bat`, `gradlew`, `gradle/wrapper/*` (commit these)

**Interfaces:**
- Produces: a building `:domain` Kotlin/JVM module with kotlin-test on JUnit 5; `gradlew.bat :domain:test`, `ktlintCheck`, `detekt` all green. Version catalog aliases used by every later task: `libs.kotlinx.serialization.json`, `libs.kotlinx.datetime`, plugins `kotlin-jvm`, `kotlin-serialization`, `kotlin-android`, `kotlin-compose`, `android-application`, `ktlint`, `detekt`.

- [ ] **Step 1: Write the build files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "badminton-ledger"
include(":domain")
```

`gradle/libs.versions.toml`:
```toml
[versions]
kotlin = "2.1.21"
agp = "8.10.1"
kotlinxSerialization = "1.8.1"
kotlinxDatetime = "0.6.2"
ktlint = "12.1.1"
detekt = "1.23.7"
composeBom = "2025.05.00"
activityCompose = "1.10.1"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```
(If a pinned version fails to resolve at execution time, bump to the nearest stable and note it in the commit message.)

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`.editorconfig`:
```ini
root = true

[*.{kt,kts}]
indent_size = 4
max_line_length = 120
ktlint_function_naming_ignore_when_annotated_with = Composable
```

`config/detekt/detekt.yml`:
```yaml
style:
  MagicNumber:
    active: false
```

`domain/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the sanity test**

`domain/src/test/kotlin/com/badmintonledger/domain/SanityTest.kt`:
```kotlin
package com.badmintonledger.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SanityTest {
    @Test
    fun `gradle and kotlin-test are wired up`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 3: Bootstrap the Gradle wrapper** (no Gradle is installed globally)

```powershell
$scratch = 'E:\Users\Max\Temp\claude\E--Code-ai-badminton-ledger\65974d58-49ec-4677-a672-e49a0bda1954\scratchpad'
Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.14-bin.zip' -OutFile "$scratch\gradle-8.14-bin.zip"
Expand-Archive "$scratch\gradle-8.14-bin.zip" -DestinationPath "$scratch\gradle-dist"
& "$scratch\gradle-dist\gradle-8.14\bin\gradle.bat" wrapper --gradle-version 8.14
```
Run from the repo root. Expected: `gradlew.bat`, `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` created.

- [ ] **Step 4: Run the sanity test**

Run: `gradlew.bat :domain:test`
Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 5: Run quality gates**

Run: `gradlew.bat ktlintCheck detekt`
Expected: BUILD SUCCESSFUL (run `gradlew.bat ktlintFormat` first if formatting complaints appear, then re-check).

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "feat: gradle multi-module skeleton with domain module and quality gates"
```

---

### Task 2: model/ — Cents, money conversion, LedgerData + JSON contract

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/model/Cents.kt`
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/model/LedgerData.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/model/CentsTest.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/model/LedgerDataJsonTest.kt`

**Interfaces:**
- Produces (used by every later task):
  - `value class Cents(val value: Long)` — serializes as a dollar number.
  - `fun dollarsToCents(dollars: Double): Long`, `fun centsToDollars(cents: Long): String` (formats `"76.80"`, `"-9.60"`; no `$` sign — UI adds it).
  - `data class Member(id: String, name: String, isGuest: Boolean)`
  - `data class Config(defaultRate: Cents, defaultPaid: Cents, defaultCredit: Cents)`
  - `data class Contribution(memberId: String, amount: Cents)`
  - `data class Refill(id: String, date: String, paid: Cents, credit: Cents, contributions: List<Contribution>)`
  - `data class Payment(id: String, memberId: String, amount: Cents, date: String)`
  - `data class Session(id: String, date: String, hours: Double, rate: Cents, factor: Double, playerIds: List<String>)`
  - `data class LedgerData(version: Int = 1, members, config, refills, payments, sessions)` — no-arg constructor equals the WeChat `DEFAULT_DATA` (rate $24, paid $2000, credit $2500).

- [ ] **Step 1: Write the failing tests**

`CentsTest.kt` (ports the money-conversion cases from `calc.test.js`):
```kotlin
package com.badmintonledger.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CentsTest {
    @Test
    fun `dollars to cents conversion`() {
        assertEquals(7680L, dollarsToCents(76.8))
        assertEquals(60000L, dollarsToCents(600.0))
        assertEquals(2560L, dollarsToCents(25.6))
    }

    @Test
    fun `cents to dollars formatting`() {
        assertEquals("76.80", centsToDollars(7680))
        assertEquals("0.00", centsToDollars(0))
        assertEquals("-9.60", centsToDollars(-960))
        assertEquals("2404.00", centsToDollars(240400))
    }
}
```

`LedgerDataJsonTest.kt` (the JSON contract — dollars on the wire, cents in memory):
```kotlin
package com.badmintonledger.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerDataJsonTest {
    private val backupJson = """
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
    fun `backup JSON decodes with dollar amounts becoming cents`() {
        val data = Json.decodeFromString<LedgerData>(backupJson)
        assertEquals(1, data.version)
        assertEquals(Cents(2400), data.config.defaultRate)
        assertEquals(Cents(60000), data.refills[0].contributions[0].amount)
        assertEquals(Cents(75000), data.refills[0].credit)
        assertEquals(Cents(2560), data.payments[0].amount)
        assertEquals(Cents(2400), data.sessions[0].rate)
        assertEquals(4.0, data.sessions[0].hours)
        assertEquals(0.8, data.sessions[0].factor)
        assertEquals(listOf("A", "G"), data.sessions[0].playerIds)
    }

    @Test
    fun `round trip preserves the document exactly`() {
        val data = Json.decodeFromString<LedgerData>(backupJson)
        val reparsed = Json.decodeFromString<LedgerData>(Json.encodeToString(LedgerData.serializer(), data))
        assertEquals(data, reparsed)
    }

    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA`() {
        val d = LedgerData()
        assertEquals(1, d.version)
        assertEquals(Cents(2400), d.config.defaultRate)
        assertEquals(Cents(200000), d.config.defaultPaid)
        assertEquals(Cents(250000), d.config.defaultCredit)
        assertEquals(emptyList(), d.members)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test`
Expected: FAIL — compilation errors (`Cents`, `LedgerData` unresolved).

- [ ] **Step 3: Implement**

`Cents.kt`:
```kotlin
package com.badmintonledger.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.round

/** Money as integer cents. On the JSON wire (backup contract v1) it is a dollar number. */
@JvmInline
@Serializable(with = CentsAsDollarsSerializer::class)
value class Cents(val value: Long)

fun dollarsToCents(dollars: Double): Long = round(dollars * 100).toLong()

fun centsToDollars(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val a = abs(cents)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

object CentsAsDollarsSerializer : KSerializer<Cents> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Cents", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Cents) {
        encoder.encodeDouble(value.value / 100.0)
    }

    override fun deserialize(decoder: Decoder): Cents = Cents(dollarsToCents(decoder.decodeDouble()))
}
```

`LedgerData.kt`:
```kotlin
package com.badmintonledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(val id: String, val name: String, val isGuest: Boolean)

@Serializable
data class Config(val defaultRate: Cents, val defaultPaid: Cents, val defaultCredit: Cents)

@Serializable
data class Contribution(val memberId: String, val amount: Cents)

@Serializable
data class Refill(
    val id: String,
    val date: String,
    val paid: Cents,
    val credit: Cents,
    val contributions: List<Contribution>,
)

@Serializable
data class Payment(val id: String, val memberId: String, val amount: Cents, val date: String)

@Serializable
data class Session(
    val id: String,
    val date: String,
    val hours: Double,
    val rate: Cents,
    val factor: Double,
    val playerIds: List<String>,
)

@Serializable
data class LedgerData(
    val version: Int = 1,
    val members: List<Member> = emptyList(),
    val config: Config = Config(
        defaultRate = Cents(2400),
        defaultPaid = Cents(200000),
        defaultCredit = Cents(250000),
    ),
    val refills: List<Refill> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val sessions: List<Session> = emptyList(),
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): Cents money type and LedgerData models on backup JSON v1 contract"
```

---

### Task 3: calc/ — session costs and shares

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/calc/Calc.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/calc/SessionCostTest.kt`

**Interfaces:**
- Consumes: `Session`, `Cents` from Task 2.
- Produces:
  - `fun sessionRealCostCents(s: Session): Long`
  - `fun sessionFaceCostCents(s: Session): Long`
  - `data class SessionShares(val totalCents: Long, val shares: Map<String, Long>)`
  - `fun sessionShares(s: Session): SessionShares`

- [ ] **Step 1: Write the failing tests** (ports from `calc.test.js`)

`SessionCostTest.kt`:
```kotlin
package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

fun testSession(
    id: String = "s",
    date: String = "2026-07-04",
    hours: Double,
    rateCents: Long,
    factor: Double,
    playerIds: List<String>,
) = Session(id, date, hours, Cents(rateCents), factor, playerIds)

class SessionCostTest {
    @Test
    fun `weekly cost - 4 hours at 24 dollars x 0_8 gives 76_80 real and 96 face`() {
        val s1 = testSession(hours = 4.0, rateCents = 2400, factor = 0.8, playerIds = listOf("A", "B", "D"))
        assertEquals(7680L, sessionRealCostCents(s1))
        assertEquals(9600L, sessionFaceCostCents(s1))

        // even split: 7680 / 3 = 2560 exactly
        val r1 = sessionShares(s1)
        assertEquals(7680L, r1.totalCents)
        assertEquals(mapOf("A" to 2560L, "B" to 2560L, "D" to 2560L), r1.shares)
    }

    @Test
    fun `rounding - last player absorbs the remainder so the sum is exact`() {
        // 100 cents / 3 players -> 33, 33, 34
        val s = testSession(date = "2026-07-11", hours = 1.0, rateCents = 125, factor = 0.8, playerIds = listOf("A", "B", "C"))
        assertEquals(100L, sessionRealCostCents(s))
        assertEquals(mapOf("A" to 33L, "B" to 33L, "C" to 34L), sessionShares(s).shares)
    }

    @Test
    fun `fractional hours - 1_5 hours at 23 dollars x 0_8 gives 27_60`() {
        val s = testSession(date = "2026-07-18", hours = 1.5, rateCents = 2300, factor = 0.8, playerIds = listOf("A"))
        assertEquals(2760L, sessionRealCostCents(s))
    }

    @Test
    fun `empty player list does not crash`() {
        val s = testSession(hours = 2.0, rateCents = 2400, factor = 0.8, playerIds = emptyList())
        assertEquals(emptyMap(), sessionShares(s).shares)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.calc.SessionCostTest"`
Expected: FAIL — compilation error (unresolved `sessionRealCostCents` etc.).

- [ ] **Step 3: Implement** — start `Calc.kt`:

```kotlin
package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Session
import kotlin.math.round

// JS parity: Math.round(hours * rateDollars * factor * 100) == round(hours * rateCents * factor)
fun sessionRealCostCents(s: Session): Long = round(s.hours * s.rate.value * s.factor).toLong()

fun sessionFaceCostCents(s: Session): Long = round(s.hours * s.rate.value).toLong()

data class SessionShares(val totalCents: Long, val shares: Map<String, Long>)

// Even split; the last player absorbs the rounding remainder so the sum is exact.
fun sessionShares(s: Session): SessionShares {
    val total = sessionRealCostCents(s)
    val n = s.playerIds.size
    if (n == 0) return SessionShares(total, emptyMap())
    val base = total / n
    val shares = s.playerIds.mapIndexed { i, id ->
        id to if (i == n - 1) total - base * (n - 1) else base
    }.toMap()
    return SessionShares(total, shares)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.calc.SessionCostTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): session cost and share calculation with exact-sum rounding"
```

---

### Task 4: calc/ — balances, pool, factor, month summary

**Files:**
- Modify: `domain/src/main/kotlin/com/badmintonledger/domain/calc/Calc.kt` (append)
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/calc/LedgerCalcTest.kt`

**Interfaces:**
- Consumes: models from Task 2, `sessionShares`/`sessionFaceCostCents` from Task 3.
- Produces:
  - `fun memberBalancesCents(data: LedgerData, excludeSessionId: String? = null): Map<String, Long>`
  - `fun poolRemainingCents(data: LedgerData): Long`
  - `fun currentFactor(data: LedgerData): Double`
  - `data class MemberMonth(val count: Int, val shareCents: Long)`
  - `data class MonthSummary(val weeks: Int, val totalCents: Long, val perMember: Map<String, MemberMonth>)`
  - `fun monthSummary(data: LedgerData, ym: String): MonthSummary`
  - `fun hasContributed(data: LedgerData, memberId: String): Boolean`

- [ ] **Step 1: Write the failing tests** (ports from `calc.test.js`)

`LedgerCalcTest.kt`:
```kotlin
package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Config
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerCalcTest {
    @Test
    fun `hasContributed - only members who funded a refill count, cash payments do not`() {
        val data = LedgerData(
            members = listOf(Member("A", "阿安", false), Member("D", "大东", false)),
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(60000), Cents(75000),
                    listOf(Contribution("A", Cents(60000)))),
            ),
            payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
        )
        assertEquals(true, hasContributed(data, "A"))
        assertEquals(false, hasContributed(data, "D")) // cash payment is not a contribution
        assertEquals(false, hasContributed(data, "X"))
    }

    @Test
    fun `member balances - contributions plus cash minus shares (600-600-800 scenario)`() {
        // A/B/C fund 600/600/800 (refill 2000 pays for 2500 credit), D funds nothing
        // week 1: A, B, D play (4h x 24 x 0.8 = 76.80 -> 25.60 each); D pays 25.60 cash
        val data = LedgerData(
            members = listOf(
                Member("A", "阿安", false), Member("B", "小波", false),
                Member("C", "陈叔", false), Member("D", "大东", false),
            ),
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(200000), Cents(250000), listOf(
                    Contribution("A", Cents(60000)),
                    Contribution("B", Cents(60000)),
                    Contribution("C", Cents(80000)),
                )),
            ),
            payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "D"))),
        )
        val bal = memberBalancesCents(data)
        assertEquals(60000L - 2560, bal["A"]) // 574.40 left
        assertEquals(60000L - 2560, bal["B"])
        assertEquals(80000L, bal["C"])        // did not play, untouched
        assertEquals(0L, bal["D"])            // owed 25.60, paid in full

        // pool remaining = 2500 credit - 96 face = 2404
        assertEquals(250000L - 9600, poolRemainingCents(data))
    }

    @Test
    fun `payer runs dry into debt - recompute after editing history is automatic`() {
        val data = LedgerData(
            members = listOf(Member("E", "尔文", false)),
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(200000), Cents(250000),
                    listOf(Contribution("E", Cents(100)))),
            ),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("E"))),
        )
        assertEquals(100L - 7680, memberBalancesCents(data)["E"]) // negative = owes

        // derived, not stored: editing hours recomputes correctly (2h x 24 x 0.8 = 38.40)
        val edited = data.copy(sessions = listOf(data.sessions[0].copy(hours = 2.0)))
        assertEquals(100L - 3840, memberBalancesCents(edited)["E"])
    }

    @Test
    fun `current factor - default config without refills, latest refill by date otherwise`() {
        assertEquals(0.8, currentFactor(LedgerData()))
        val data = LedgerData(
            refills = listOf(
                Refill("r1", "2026-01-01", Cents(200000), Cents(250000), emptyList()),
                Refill("r2", "2026-06-01", Cents(180000), Cents(240000), emptyList()),
            ),
        )
        assertEquals(0.75, currentFactor(data))
    }

    @Test
    fun `multiple refills accumulate the pool`() {
        val data = LedgerData(
            refills = listOf(
                Refill("r1", "2026-01-01", Cents(200000), Cents(250000), emptyList()),
                Refill("r2", "2026-06-01", Cents(180000), Cents(240000), emptyList()),
            ),
        )
        assertEquals(490000L, poolRemainingCents(data))
    }

    @Test
    fun `month summary counts only that month, absent members count zero`() {
        val data = LedgerData(
            members = listOf(
                Member("A", "阿安", false), Member("B", "小波", false), Member("G", "客串", true),
            ),
            sessions = listOf(
                Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "G")), // 7680 -> 2560 x3
                Session("s2", "2026-07-11", 2.0, Cents(3000), 0.8, listOf("A", "B")),      // 4800 -> 2400 x2
                Session("s3", "2026-08-01", 4.0, Cents(2400), 0.8, listOf("A")),           // other month
            ),
        )
        val m = monthSummary(data, "2026-07")
        assertEquals(2, m.weeks)
        assertEquals(12480L, m.totalCents)
        assertEquals(MemberMonth(2, 4960L), m.perMember["A"])
        assertEquals(MemberMonth(2, 4960L), m.perMember["B"])
        assertEquals(MemberMonth(1, 2560L), m.perMember["G"])

        val empty = monthSummary(data, "2026-06")
        assertEquals(0, empty.weeks)
        assertEquals(MemberMonth(0, 0L), empty.perMember["A"])
    }

    @Test
    fun `balances can exclude one session - as if that week never happened`() {
        val data = LedgerData(
            members = listOf(Member("A", "阿安", false), Member("B", "小波", false)),
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(10000), Cents(12500),
                    listOf(Contribution("A", Cents(10000)))),
            ),
            sessions = listOf(
                Session("s1", "2026-07-04", 1.0, Cents(2400), 0.8, listOf("A", "B")),
                Session("s2", "2026-07-11", 1.0, Cents(2400), 0.8, listOf("A")),
            ),
        )
        val all = memberBalancesCents(data)
        // s1 costs 19.20 split two ways at 9.60; s2 costs 19.20 all on A
        assertEquals(10000L - 960 - 1920, all["A"])
        val excl = memberBalancesCents(data, "s2")
        assertEquals(10000L - 960, excl["A"])
        assertEquals(-960L, excl["B"])
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.calc.LedgerCalcTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement** — append to `Calc.kt`:

```kotlin
// Balance per member (cents): contributions + cash payments - session shares.
// Positive = money left; negative = owes. excludeSessionId gives the balance "before" that week.
fun memberBalancesCents(data: LedgerData, excludeSessionId: String? = null): Map<String, Long> {
    val bal = mutableMapOf<String, Long>()
    data.members.forEach { bal[it.id] = 0L }
    data.refills.forEach { r ->
        r.contributions.forEach { c -> bal[c.memberId] = (bal[c.memberId] ?: 0L) + c.amount.value }
    }
    data.payments.forEach { p -> bal[p.memberId] = (bal[p.memberId] ?: 0L) + p.amount.value }
    data.sessions.forEach { s ->
        if (s.id == excludeSessionId) return@forEach
        sessionShares(s).shares.forEach { (id, share) -> bal[id] = (bal[id] ?: 0L) - share }
    }
    return bal
}

// Venue pool remaining (cents) = total refill credits - total session face costs.
fun poolRemainingCents(data: LedgerData): Long =
    data.refills.sumOf { it.credit.value } - data.sessions.sumOf { sessionFaceCostCents(it) }

// Latest refill's paid/credit ratio; default config ratio when there are no refills.
fun currentFactor(data: LedgerData): Double {
    val latest = data.refills.maxByOrNull { it.date }
        ?: return data.config.defaultPaid.value.toDouble() / data.config.defaultCredit.value
    return latest.paid.value.toDouble() / latest.credit.value
}

data class MemberMonth(val count: Int, val shareCents: Long)

data class MonthSummary(val weeks: Int, val totalCents: Long, val perMember: Map<String, MemberMonth>)

fun monthSummary(data: LedgerData, ym: String): MonthSummary {
    val sessions = data.sessions.filter { it.date.startsWith(ym) }
    val per = mutableMapOf<String, MemberMonth>()
    data.members.forEach { per[it.id] = MemberMonth(0, 0L) }
    var total = 0L
    sessions.forEach { s ->
        val r = sessionShares(s)
        total += r.totalCents
        r.shares.forEach { (id, share) ->
            val prev = per[id] ?: MemberMonth(0, 0L)
            per[id] = MemberMonth(prev.count + 1, prev.shareCents + share)
        }
    }
    return MonthSummary(sessions.size, total, per)
}

// True only if the member funded any refill; paying off debt in cash does not count.
fun hasContributed(data: LedgerData, memberId: String): Boolean =
    data.refills.any { r -> r.contributions.any { it.memberId == memberId && it.amount.value > 0 } }
```
(Imports to add at top of `Calc.kt`: `com.badmintonledger.domain.model.LedgerData`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.calc.LedgerCalcTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): balances, pool, current factor and month summary"
```

---

### Task 5: edit/ — EditResult and member operations

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/edit/EditResult.kt`
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/edit/MemberEdits.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/edit/MemberEditTest.kt`

**Interfaces:**
- Consumes: models from Task 2.
- Produces:
  - `sealed interface EditResult<out T>` with `data class Ok<T>(val data: LedgerData, val value: T) : EditResult<T>` and `data class Err(val reason: String) : EditResult<Nothing>`
  - `fun addMember(data: LedgerData, id: String, name: String, isGuest: Boolean): EditResult.Ok<Member>`
  - `fun renameMember(data: LedgerData, id: String, name: String): LedgerData`
  - `fun setGuest(data: LedgerData, id: String, isGuest: Boolean): LedgerData`
  - `fun memberReferenced(data: LedgerData, id: String): Boolean`
  - `fun removeMember(data: LedgerData, id: String): EditResult<Unit>` — Err "This member has records and cannot be deleted"
  - Ids are caller-supplied (`domain/` is pure; the app generates ids).

- [ ] **Step 1: Write the failing tests** (ports member cases from `data.test.js`)

`MemberEditTest.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MemberEditTest {
    @Test
    fun `add rename setGuest remove`() {
        var data = LedgerData()
        val a = addMember(data, "m1", "阿安", false)
        data = a.data
        val g = addMember(data, "m2", "客串", true)
        data = g.data
        assertEquals(2, data.members.size)
        assertEquals(true, g.value.isGuest)

        data = renameMember(data, "m1", "安哥")
        assertEquals("安哥", data.members[0].name)
        data = setGuest(data, "m2", false)
        assertEquals(false, data.members[1].isGuest)

        val removed = removeMember(data, "m2")
        assertIs<EditResult.Ok<Unit>>(removed)
        assertEquals(1, removed.data.members.size)
    }

    @Test
    fun `rename and setGuest on unknown id are no-ops`() {
        val data = addMember(LedgerData(), "m1", "阿安", false).data
        assertEquals(data, renameMember(data, "nope", "x"))
        assertEquals(data, setGuest(data, "nope", true))
    }
}
```
(The referenced-member-cannot-be-deleted case needs sessions/payments/refills — it is ported in Task 7 where those edits exist.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.MemberEditTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`EditResult.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData

/** Ledger mutations return a new document (never mutate) or a human-readable refusal. */
sealed interface EditResult<out T> {
    data class Ok<T>(val data: LedgerData, val value: T) : EditResult<T>

    data class Err(val reason: String) : EditResult<Nothing>
}
```

`MemberEdits.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member

fun addMember(data: LedgerData, id: String, name: String, isGuest: Boolean): EditResult.Ok<Member> {
    val m = Member(id, name, isGuest)
    return EditResult.Ok(data.copy(members = data.members + m), m)
}

fun renameMember(data: LedgerData, id: String, name: String): LedgerData =
    data.copy(members = data.members.map { if (it.id == id) it.copy(name = name) else it })

fun setGuest(data: LedgerData, id: String, isGuest: Boolean): LedgerData =
    data.copy(members = data.members.map { if (it.id == id) it.copy(isGuest = isGuest) else it })

fun memberReferenced(data: LedgerData, id: String): Boolean =
    data.sessions.any { id in it.playerIds } ||
        data.payments.any { it.memberId == id } ||
        data.refills.any { r -> r.contributions.any { it.memberId == id } }

fun removeMember(data: LedgerData, id: String): EditResult<Unit> {
    if (memberReferenced(data, id)) return EditResult.Err("This member has records and cannot be deleted")
    return EditResult.Ok(data.copy(members = data.members.filter { it.id != id }), Unit)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.MemberEditTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): EditResult and member add/rename/guest/remove operations"
```

---

### Task 6: edit/ — week logic (Monday start)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/edit/Weeks.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/edit/WeekTest.kt`

**Interfaces:**
- Consumes: `LedgerData`, `Session` from Task 2; kotlinx-datetime.
- Produces:
  - `fun weekStart(dateStr: String): String` — Monday of that date's week, `YYYY-MM-DD`
  - `fun findSessionInWeek(data: LedgerData, dateStr: String, excludeId: String? = null): Session?`

- [ ] **Step 1: Write the failing tests** (ports from `data.test.js`)

`WeekTest.kt`:
```kotlin
package com.badmintonledger.domain.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekTest {
    @Test
    fun `week starts on Monday`() {
        // 2026-07-04 is Saturday, 2026-07-05 is Sunday -> same week (Monday 2026-06-29)
        assertEquals("2026-06-29", weekStart("2026-07-04"))
        assertEquals("2026-06-29", weekStart("2026-07-05"))
        // 2026-07-06 is Monday -> a new week
        assertEquals("2026-07-06", weekStart("2026-07-06"))
    }
}
```
(`findSessionInWeek` hit/exclude cases are ported in Task 7's one-session-per-week test, where sessions exist.)

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.WeekTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`Weeks.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** Monday of the week containing dateStr, as YYYY-MM-DD. */
fun weekStart(dateStr: String): String {
    val d = LocalDate.parse(dateStr)
    return d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).toString()
}

fun findSessionInWeek(data: LedgerData, dateStr: String, excludeId: String? = null): Session? {
    val wk = weekStart(dateStr)
    return data.sessions.firstOrNull { it.id != excludeId && weekStart(it.date) == wk }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.WeekTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): Monday-start week logic"
```

---

### Task 7: edit/ — refill, payment, session mutations with validation

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/edit/LedgerEdits.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/edit/LedgerEditTest.kt`

**Interfaces:**
- Consumes: Tasks 2, 5, 6.
- Produces:
  - `fun addRefill(data: LedgerData, id: String, date: String, paidCents: Long?, creditCents: Long?, contributions: List<Contribution>): EditResult<Refill>`
  - `fun addPayment(data: LedgerData, id: String, memberId: String, amountCents: Long?, date: String): EditResult<Payment>`
  - `fun addSession(data: LedgerData, id: String, date: String, hours: Double?, rateCents: Long?, factor: Double?, playerIds: List<String>): EditResult<Session>`
  - `data class SessionUpdate(val date: String? = null, val hours: Double? = null, val rateCents: Long? = null, val factor: Double? = null, val playerIds: List<String>? = null)` — null means "not provided"
  - `fun updateSession(data: LedgerData, id: String, update: SessionUpdate): EditResult<Session>`
  - `fun deleteSession(data: LedgerData, id: String): LedgerData`, `fun deleteRefill(...)`, `fun deletePayment(...)`
  - Nullable amount parameters model unparseable user input (JS `NaN`); `null` and non-positive are both rejected.

- [ ] **Step 1: Write the failing tests** (ports from `data.test.js`)

`LedgerEditTest.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerEditTest {
    private fun withMembers(): LedgerData {
        var data = LedgerData()
        data = addMember(data, "mA", "阿安", false).data
        data = addMember(data, "mG", "客串", true).data
        return data
    }

    @Test
    fun `record edit delete - members with records cannot be deleted`() {
        var data = withMembers()

        val r = addRefill(data, "r1", "2026-07-01", 200000, 250000,
            listOf(Contribution("mA", Cents(200000))))
        assertIs<EditResult.Ok<*>>(r)
        data = r.data
        val p = addPayment(data, "p1", "mG", 2560, "2026-07-05")
        assertIs<EditResult.Ok<*>>(p)
        data = p.data
        val s = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA", "mG"))
        assertIs<EditResult.Ok<*>>(s)
        data = s.data

        // members with records cannot be deleted
        assertIs<EditResult.Err>(removeMember(data, "mA"))

        val u = updateSession(data, "s1", SessionUpdate(hours = 2.0, playerIds = listOf("mA")))
        assertIs<EditResult.Ok<*>>(u)
        data = u.data
        assertEquals(2.0, data.sessions[0].hours)
        assertEquals(listOf("mA"), data.sessions[0].playerIds)

        data = deleteSession(data, "s1")
        data = deletePayment(data, "p1")
        data = deleteRefill(data, "r1")
        assertEquals(0, data.sessions.size + data.payments.size + data.refills.size)

        // with records gone the member can be deleted
        assertIs<EditResult.Ok<*>>(removeMember(data, "mA"))
    }

    @Test
    fun `refill validation - contributions must sum to paid, amounts must be positive`() {
        val data = withMembers()

        // sum != paid -> rejected
        val bad1 = addRefill(data, "r1", "2026-07-01", 200000, 250000,
            listOf(Contribution("mA", Cents(190000))))
        assertIs<EditResult.Err>(bad1)
        assertTrue(bad1.reason.isNotEmpty())

        // null / non-positive -> rejected
        assertIs<EditResult.Err>(addRefill(data, "r1", "2026-07-01", null, 250000, emptyList()))
        assertIs<EditResult.Err>(addRefill(data, "r1", "2026-07-01", 200000, 0, emptyList()))
        assertIs<EditResult.Err>(
            addRefill(data, "r1", "2026-07-01", 200000, 250000,
                listOf(Contribution("mA", Cents(0)))),
        )

        // cent-exact comparison: 600.50 + 600.50 + 799.00 = 2000.00
        val ok = addRefill(data, "r1", "2026-07-01", 200000, 250000, listOf(
            Contribution("mA", Cents(60050)),
            Contribution("mA", Cents(60050)),
            Contribution("mA", Cents(79900)),
        ))
        assertIs<EditResult.Ok<*>>(ok)
    }

    @Test
    fun `payment validation - positive amount and member required`() {
        val data = withMembers()
        assertIs<EditResult.Err>(addPayment(data, "p1", "mA", -500, "2026-07-05"))
        assertIs<EditResult.Err>(addPayment(data, "p1", "mA", 0, "2026-07-05"))
        assertIs<EditResult.Err>(addPayment(data, "p1", "", 1000, "2026-07-05"))
        assertIs<EditResult.Ok<*>>(addPayment(data, "p1", "mA", 1000, "2026-07-05"))
    }

    @Test
    fun `session validation - positive hours rate factor, at least one player`() {
        val data = withMembers()
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 0.0, 2400, 0.8, listOf("mA")))
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 4.0, null, 0.8, listOf("mA")))
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, emptyList()))
    }

    @Test
    fun `updateSession - invalid values rejected leaving original intact, unknown id rejected`() {
        var data = withMembers()
        val added = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(added)
        data = added.data

        assertIs<EditResult.Err>(updateSession(data, "s1", SessionUpdate(hours = -1.0)))
        assertIs<EditResult.Err>(updateSession(data, "s1", SessionUpdate(playerIds = emptyList())))
        assertEquals(4.0, data.sessions[0].hours)

        assertIs<EditResult.Err>(updateSession(data, "nope", SessionUpdate(hours = 2.0)))
    }

    @Test
    fun `one session per week - duplicates rejected, next week fine, week moves checked`() {
        var data = withMembers()
        val s1 = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(s1)
        data = s1.data
        // same week (Sunday) -> rejected
        val dup = addSession(data, "s2", "2026-07-05", 2.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Err>(dup)
        assertTrue(dup.reason.isNotEmpty())
        assertEquals(1, data.sessions.size)
        // next week (Monday) -> fine
        val s2 = addSession(data, "s2", "2026-07-06", 2.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(s2)
        data = s2.data
        // findSessionInWeek: hit, and exclude-self
        assertEquals("s1", findSessionInWeek(data, "2026-07-05")?.id)
        assertNull(findSessionInWeek(data, "2026-07-05", "s1"))
        // moving into an occupied week -> rejected; new date within own week -> fine
        assertIs<EditResult.Err>(updateSession(data, "s2", SessionUpdate(date = "2026-07-03")))
        assertIs<EditResult.Ok<*>>(updateSession(data, "s2", SessionUpdate(date = "2026-07-07")))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.LedgerEditTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`LedgerEdits.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session

private fun isPositive(x: Long?): Boolean = x != null && x > 0

private fun isPositive(x: Double?): Boolean = x != null && x.isFinite() && x > 0

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

fun addPayment(data: LedgerData, id: String, memberId: String, amountCents: Long?, date: String): EditResult<Payment> {
    if (memberId.isEmpty()) return EditResult.Err("Please select a member")
    if (!isPositive(amountCents)) return EditResult.Err("Amount must be positive")
    val p = Payment(id, memberId, Cents(amountCents!!), date)
    return EditResult.Ok(data.copy(payments = data.payments + p), p)
}

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

fun updateSession(data: LedgerData, id: String, update: SessionUpdate): EditResult<Session> {
    val s = data.sessions.firstOrNull { it.id == id } ?: return EditResult.Err("Record not found")
    validSessionFields(update.hours, update.rateCents, update.factor, update.playerIds, checkAll = false)
        ?.let { return EditResult.Err(it) }
    if (update.date != null && findSessionInWeek(data, update.date, id) != null) {
        return EditResult.Err("Another record already exists in the target week")
    }
    val updated = s.copy(
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

fun deleteSession(data: LedgerData, id: String): LedgerData =
    data.copy(sessions = data.sessions.filter { it.id != id })

fun deleteRefill(data: LedgerData, id: String): LedgerData =
    data.copy(refills = data.refills.filter { it.id != id })

fun deletePayment(data: LedgerData, id: String): LedgerData =
    data.copy(payments = data.payments.filter { it.id != id })
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.LedgerEditTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): refill, payment and session mutations with ledger invariants"
```

---

### Task 8: backup/ — import validation, decode/encode, export file name

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/backup/BackupCodec.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupCodecTest.kt`

**Interfaces:**
- Consumes: `LedgerData` from Task 2.
- Produces:
  - `sealed interface ImportResult` with `data class Ok(val summary: Summary) : ImportResult`, `data class Err(val reason: String) : ImportResult`, and `data class Summary(val members: Int, val sessions: Int, val refills: Int)` (nested in `ImportResult`)
  - `object BackupCodec` with:
    - `fun validate(text: String): ImportResult` — full structural validation on raw JSON, mirroring WeChat `validateImport`; returns English reasons
    - `fun decode(text: String): LedgerData` — call only after `validate` returns Ok
    - `fun encode(data: LedgerData): String`
    - `fun exportFileName(dateStr: String): String` — `badminton-backup-YYYY-MM-DD.json`
  - Exact reason strings (asserted by tests and reused in UI later):
    - `"Not a valid backup file"` / `"Unsupported backup version"`
    - `"Backup is missing member data"`, `"... config data"`, `"... refill data"`, `"... payment data"`, `"... session data"`
    - `"Member data is incomplete"`, `"Config data is incomplete"`, `"Refill data is incomplete"`, `"Payment data is incomplete"`, `"Session data is incomplete"`
    - `"Backup references a missing member"`

- [ ] **Step 1: Write the failing tests** (ports import/export cases from `data.test.js`)

`BackupCodecTest.kt`:
```kotlin
package com.badmintonledger.domain.backup

import com.badmintonledger.domain.model.LedgerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun `complete backup passes with a summary`() {
        val r = BackupCodec.validate(fixture())
        assertEquals(ImportResult.Ok(ImportResult.Summary(members = 2, sessions = 1, refills = 1)), r)
    }

    @Test
    fun `default empty data passes too`() {
        val r = BackupCodec.validate(BackupCodec.encode(LedgerData()))
        assertEquals(ImportResult.Ok(ImportResult.Summary(0, 0, 0)), r)
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
    fun `rejects broken structures`() {
        // missing members array entirely
        val noMembers = """{"version":1,"config":{"defaultRate":24,"defaultPaid":2000,
            "defaultCredit":2500},"refills":[],"payments":[],"sessions":[]}"""
        assertEquals(ImportResult.Err("Backup is missing member data"), BackupCodec.validate(noMembers))

        // empty member name
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(membersJson = """[
                {"id":"A","name":"","isGuest":false},
                {"id":"G","name":"客串","isGuest":true}
            ]""")),
        )
        // duplicate member ids
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(membersJson = """[
                {"id":"A","name":"阿安","isGuest":false},
                {"id":"A","name":"重复","isGuest":true}
            ]""")),
        )
        // negative config default
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(configJson = """{"defaultRate":-1,"defaultPaid":2000,"defaultCredit":2500}""")),
        )
        // wrong date format
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(sessionsJson = """[{"id":"s1","date":"07/04/2026","hours":4,
                "rate":24,"factor":0.8,"playerIds":["A","G"]}]""")),
        )
        // zero hours
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":0,
                "rate":24,"factor":0.8,"playerIds":["A","G"]}]""")),
        )
        // empty player list
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":4,
                "rate":24,"factor":0.8,"playerIds":[]}]""")),
        )
        // zero contribution amount
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(refillsJson = """[{"id":"r1","date":"2026-07-01","paid":600,
                "credit":750,"contributions":[{"memberId":"A","amount":0}]}]""")),
        )
    }

    @Test
    fun `rejects references to missing members`() {
        assertEquals(
            ImportResult.Err("Backup references a missing member"),
            BackupCodec.validate(fixture(sessionsJson = """[{"id":"s1","date":"2026-07-04","hours":4,
                "rate":24,"factor":0.8,"playerIds":["A","X"]}]""")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(paymentsJson = """[{"id":"p1","memberId":"X","amount":25.6,
                "date":"2026-07-05"}]""")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixture(refillsJson = """[{"id":"r1","date":"2026-07-01","paid":600,
                "credit":750,"contributions":[{"memberId":"X","amount":600}]}]""")),
        )
    }

    @Test
    fun `export file name`() {
        assertEquals("badminton-backup-2026-07-06.json", BackupCodec.exportFileName("2026-07-06"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.backup.BackupCodecTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`BackupCodec.kt`:
```kotlin
package com.badmintonledger.domain.backup

import com.badmintonledger.domain.model.LedgerData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

sealed interface ImportResult {
    data class Summary(val members: Int, val sessions: Int, val refills: Int)

    data class Ok(val summary: Summary) : ImportResult

    data class Err(val reason: String) : ImportResult
}

object BackupCodec {
    // encodeDefaults: the frozen contract requires every key present even for a default
    // document (version, empty arrays, default config) — WeChat validateImport rejects omissions.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dateRe = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun exportFileName(dateStr: String): String = "badminton-backup-$dateStr.json"

    fun encode(data: LedgerData): String = json.encodeToString(LedgerData.serializer(), data)

    /** Call only after validate() returned Ok. */
    fun decode(text: String): LedgerData = json.decodeFromString(LedgerData.serializer(), text)

    /** Full structural validation before any write — port of WeChat validateImport. */
    fun validate(text: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return ImportResult.Err("Not a valid backup file")
        }
        return validate(root)
    }

    fun validate(root: JsonElement): ImportResult {
        val obj = root as? JsonObject ?: return ImportResult.Err("Not a valid backup file")
        if ((obj["version"] as? JsonPrimitive)?.intOrNull != 1) {
            return ImportResult.Err("Unsupported backup version")
        }
        val members = obj["members"] as? JsonArray ?: return ImportResult.Err("Backup is missing member data")
        val config = obj["config"] as? JsonObject ?: return ImportResult.Err("Backup is missing config data")
        val refills = obj["refills"] as? JsonArray ?: return ImportResult.Err("Backup is missing refill data")
        val payments = obj["payments"] as? JsonArray ?: return ImportResult.Err("Backup is missing payment data")
        val sessions = obj["sessions"] as? JsonArray ?: return ImportResult.Err("Backup is missing session data")

        val ids = mutableSetOf<String>()
        for (m in members) {
            val mo = m as? JsonObject ?: return ImportResult.Err("Member data is incomplete")
            val id = mo.stringOrNull("id")
            val name = mo.stringOrNull("name")
            val isGuest = (mo["isGuest"] as? JsonPrimitive)?.booleanOrNull
            if (id.isNullOrEmpty() || name.isNullOrEmpty() || isGuest == null) {
                return ImportResult.Err("Member data is incomplete")
            }
            if (!ids.add(id)) return ImportResult.Err("Member data is incomplete")
        }
        if (!config.positive("defaultRate") || !config.positive("defaultPaid") || !config.positive("defaultCredit")) {
            return ImportResult.Err("Config data is incomplete")
        }
        for (r in refills) {
            val ro = r as? JsonObject ?: return ImportResult.Err("Refill data is incomplete")
            val contributions = ro["contributions"] as? JsonArray
            if (ro.stringOrNull("id").isNullOrEmpty() || !ro.dateOk("date") ||
                !ro.positive("paid") || !ro.positive("credit") || contributions == null
            ) {
                return ImportResult.Err("Refill data is incomplete")
            }
            for (c in contributions) {
                val co = c as? JsonObject ?: return ImportResult.Err("Refill data is incomplete")
                if (!co.positive("amount")) return ImportResult.Err("Refill data is incomplete")
                if (co.stringOrNull("memberId") !in ids) return ImportResult.Err("Backup references a missing member")
            }
        }
        for (p in payments) {
            val po = p as? JsonObject ?: return ImportResult.Err("Payment data is incomplete")
            if (po.stringOrNull("id").isNullOrEmpty() || !po.dateOk("date") || !po.positive("amount")) {
                return ImportResult.Err("Payment data is incomplete")
            }
            if (po.stringOrNull("memberId") !in ids) return ImportResult.Err("Backup references a missing member")
        }
        for (s in sessions) {
            val so = s as? JsonObject ?: return ImportResult.Err("Session data is incomplete")
            val playerIds = so["playerIds"] as? JsonArray
            if (so.stringOrNull("id").isNullOrEmpty() || !so.dateOk("date") ||
                !so.positive("hours") || !so.positive("rate") || !so.positive("factor") ||
                playerIds == null || playerIds.isEmpty()
            ) {
                return ImportResult.Err("Session data is incomplete")
            }
            for (pid in playerIds) {
                if ((pid as? JsonPrimitive)?.stringContentOrNull() !in ids) {
                    return ImportResult.Err("Backup references a missing member")
                }
            }
        }
        return ImportResult.Ok(ImportResult.Summary(members.size, sessions.size, refills.size))
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonPrimitive.stringContentOrNull(): String? = if (isString) content else null

    private fun JsonObject.positive(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.doubleOrNull?.let { it.isFinite() && it > 0 } == true

    private fun JsonObject.dateOk(key: String): Boolean =
        stringOrNull(key)?.matches(dateRe) == true
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.backup.BackupCodecTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): backup JSON v1 validation, decode/encode and export file name"
```

---

### Task 9: report/ — weekly and monthly poster payloads

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/Report.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/ReportTest.kt`

**Interfaces:**
- Consumes: models (Task 2), calc functions (Tasks 3–4), `centsToDollars` (Task 2).
- Produces:
  - `data class BalanceRow(val name: String, val owes: Boolean, val absDollars: String)`
  - `data class PlayerRow(val name: String, val beforeDollars: String, val owesBefore: Boolean, val shareDollars: String, val afterDollars: String, val owesAfter: Boolean)`
  - `data class WeeklyPayload(val date: String, val hours: Double, val rate: Cents, val factorText: String, val faceDollars: String, val realDollars: String, val players: List<PlayerRow>, val balances: List<BalanceRow>, val poolDollars: String)`
  - `data class MonthlyRow(val name: String, val count: Int, val shareDollars: String, val owes: Boolean, val absDollars: String)`
  - `data class MonthlyPayload(val ym: String, val weeks: Int, val totalDollars: String, val rows: List<MonthlyRow>, val poolDollars: String)`
  - `fun memberName(data: LedgerData, id: String): String` (unknown → `"Unknown"`)
  - `fun balanceRows(data: LedgerData, excludeIds: List<String> = emptyList()): List<BalanceRow>`
  - `fun sessionBreakdownRows(data: LedgerData, session: Session): List<PlayerRow>`
  - `fun buildWeeklyPayload(data: LedgerData, sessionId: String): WeeklyPayload`
  - `fun buildMonthlyPayload(data: LedgerData, ym: String): MonthlyPayload`
  - `factorText`: `factor.toString().removeSuffix(".0")` so `0.8 → "0.8"`, `1.0 → "1"` (JS `String(1)` parity).

- [ ] **Step 1: Write the failing tests** (ports from `report.test.js`)

`ReportTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun fixture() = LedgerData(
    members = listOf(
        Member("A", "阿安", false),
        Member("B", "小波", false),
        Member("G", "客串", true),
        Member("H", "路人", true),
    ),
    refills = listOf(
        Refill("r1", "2026-07-01", Cents(200000), Cents(250000), listOf(
            Contribution("A", Cents(100000)),
            Contribution("B", Cents(100000)),
        )),
    ),
    sessions = listOf(
        Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "G")),
    ),
)

class ReportTest {
    @Test
    fun `weekly poster payload`() {
        val w = buildWeeklyPayload(fixture(), "s1")
        assertEquals("2026-07-04", w.date)
        assertEquals("96.00", w.faceDollars)
        assertEquals("76.80", w.realDollars)
        assertEquals(3, w.players.size)
        // A: before = 1000 contributed, share 25.60, after 974.40
        assertEquals(
            PlayerRow("阿安", "1000.00", false, "25.60", "974.40", false),
            w.players[0],
        )
        // G: no contribution, before 0, owes 25.60 after
        assertEquals(
            PlayerRow("客串", "0.00", false, "25.60", "25.60", true),
            w.players[2],
        )
        // players already have their after-balance in the breakdown; no non-player has a
        // non-zero balance -> empty
        assertEquals(emptyList(), w.balances)
        assertEquals("2404.00", w.poolDollars)
    }

    @Test
    fun `weekly balance section lists only non-players with non-zero balance`() {
        var data = fixture()
        data = data.copy(
            members = data.members +
                Member("C", "零哥", false) + // zero balance, did not play -> hidden
                Member("D", "丁叔", false),  // contributed but did not play -> shown
            refills = listOf(
                data.refills[0].copy(
                    contributions = data.refills[0].contributions + Contribution("D", Cents(50000)),
                ),
            ),
        )
        val w = buildWeeklyPayload(data, "s1")
        assertEquals(listOf(BalanceRow("丁叔", false, "500.00")), w.balances)

        // debtors show in weeks they did not play: new week with only A and B playing
        data = data.copy(
            sessions = data.sessions + Session("s2", "2026-07-11", 1.0, Cents(2400), 1.0, listOf("A", "B")),
        )
        val w2 = buildWeeklyPayload(data, "s2")
        assertTrue(w2.balances.any { it.name == "客串" && it.owes })
        assertTrue(w2.balances.all { it.name != "阿安" && it.name != "小波" })
    }

    @Test
    fun `breakdown rows exclude the current week and last player absorbs remainder`() {
        // 1 hour x 25.61 x 1 = 25.61 -> three players 8.53 / 8.53 / 8.55
        val s = Session("s2", "2026-07-11", 1.0, Cents(2561), 1.0, listOf("A", "B", "G"))
        val data = fixture().copy(sessions = fixture().sessions + s)
        val rows = sessionBreakdownRows(data, s)
        // A's before-balance includes s1 (-25.60) but not s2: 1000 - 25.60 = 974.40
        assertEquals(PlayerRow("阿安", "974.40", false, "8.53", "965.87", false), rows[0])
        assertEquals("8.53", rows[1].shareDollars)
        // G already owes 25.60 from s1; last-position share 8.55; owes 34.15 after
        assertEquals(PlayerRow("客串", "25.60", true, "8.55", "34.15", true), rows[2])
    }

    @Test
    fun `monthly payload - absent guests hidden, playing members and debtors shown`() {
        val mo = buildMonthlyPayload(fixture(), "2026-07")
        assertEquals(1, mo.weeks)
        assertEquals("76.80", mo.totalDollars)
        val names = mo.rows.map { it.name }
        assertTrue("阿安" in names)
        assertTrue("客串" in names)
        assertTrue("路人" !in names)
        val g = mo.rows.first { it.name == "客串" }
        assertEquals(MonthlyRow("客串", 1, "25.60", true, "25.60"), g)
        assertEquals("2404.00", mo.poolDollars)
    }

    @Test
    fun `monthly payload - non-players without debt hidden, debtors shown even absent`() {
        var data = fixture()
        data = data.copy(members = data.members + Member("C", "零哥", false)) // zero balance
        // August has one session: only players or debtors appear (A plays, G owes); B/C/H hidden
        data = data.copy(
            sessions = data.sessions + Session("s8", "2026-08-01", 1.0, Cents(2400), 1.0, listOf("A")),
        )
        val names = buildMonthlyPayload(data, "2026-08").rows.map { it.name }
        assertTrue("阿安" in names)   // played
        assertTrue("小波" !in names)  // positive balance but no debt -> hidden
        assertTrue("客串" in names)   // did not play but owes
        assertTrue("零哥" !in names)  // zero balance
        assertTrue("路人" !in names)  // zero balance
    }

    @Test
    fun `monthly payload - empty month returns zeros`() {
        val mo = buildMonthlyPayload(fixture(), "2026-06")
        assertEquals(0, mo.weeks)
        assertEquals("0.00", mo.totalDollars)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.ReportTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`Report.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.monthSummary
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.calc.sessionFaceCostCents
import com.badmintonledger.domain.calc.sessionShares
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class BalanceRow(val name: String, val owes: Boolean, val absDollars: String)

data class PlayerRow(
    val name: String,
    val beforeDollars: String,
    val owesBefore: Boolean,
    val shareDollars: String,
    val afterDollars: String,
    val owesAfter: Boolean,
)

data class WeeklyPayload(
    val date: String,
    val hours: Double,
    val rate: Cents,
    val factorText: String,
    val faceDollars: String,
    val realDollars: String,
    val players: List<PlayerRow>,
    val balances: List<BalanceRow>,
    val poolDollars: String,
)

data class MonthlyRow(
    val name: String,
    val count: Int,
    val shareDollars: String,
    val owes: Boolean,
    val absDollars: String,
)

data class MonthlyPayload(
    val ym: String,
    val weeks: Int,
    val totalDollars: String,
    val rows: List<MonthlyRow>,
    val poolDollars: String,
)

fun memberName(data: LedgerData, id: String): String =
    data.members.firstOrNull { it.id == id }?.name ?: "Unknown"

// Balance section: non-zero balances only; excludeIds (this week's players, whose
// after-balance is already in the breakdown) are not repeated.
fun balanceRows(data: LedgerData, excludeIds: List<String> = emptyList()): List<BalanceRow> {
    val bal = memberBalancesCents(data)
    return data.members
        .filter { it.id !in excludeIds && (bal[it.id] ?: 0L) != 0L }
        .map {
            val c = bal[it.id] ?: 0L
            BalanceRow(it.name, owes = c < 0, absDollars = centsToDollars(abs(c)))
        }
}

// Per-player week detail: balance before - this week's share = balance after.
// The session must already be saved; the before-balance excludes the session itself.
fun sessionBreakdownRows(data: LedgerData, session: Session): List<PlayerRow> {
    val before = memberBalancesCents(data, session.id)
    val shares = sessionShares(session).shares
    return session.playerIds.map { id ->
        val b = before[id] ?: 0L
        val a = b - (shares[id] ?: 0L)
        PlayerRow(
            name = memberName(data, id),
            beforeDollars = centsToDollars(abs(b)),
            owesBefore = b < 0,
            shareDollars = centsToDollars(shares[id] ?: 0L),
            afterDollars = centsToDollars(abs(a)),
            owesAfter = a < 0,
        )
    }
}

fun buildWeeklyPayload(data: LedgerData, sessionId: String): WeeklyPayload {
    val s = data.sessions.first { it.id == sessionId }
    val r = sessionShares(s)
    return WeeklyPayload(
        date = s.date,
        hours = s.hours,
        rate = s.rate,
        factorText = s.factor.toString().removeSuffix(".0"),
        faceDollars = centsToDollars(sessionFaceCostCents(s)),
        realDollars = centsToDollars(r.totalCents),
        players = sessionBreakdownRows(data, s),
        balances = balanceRows(data, s.playerIds),
        poolDollars = centsToDollars(poolRemainingCents(data)),
    )
}

fun buildMonthlyPayload(data: LedgerData, ym: String): MonthlyPayload {
    val m = monthSummary(data, ym)
    val bal = memberBalancesCents(data)
    val rows = data.members
        .filter { mem ->
            val count = m.perMember[mem.id]?.count ?: 0
            count > 0 || (bal[mem.id] ?: 0L) < 0
        }
        .map { mem ->
            val pm = m.perMember[mem.id]
            val c = bal[mem.id] ?: 0L
            MonthlyRow(
                name = mem.name,
                count = pm?.count ?: 0,
                shareDollars = centsToDollars(pm?.shareCents ?: 0L),
                owes = c < 0,
                absDollars = centsToDollars(abs(c)),
            )
        }
    return MonthlyPayload(
        ym = ym,
        weeks = m.weeks,
        totalDollars = centsToDollars(m.totalCents),
        rows = rows,
        poolDollars = centsToDollars(poolRemainingCents(data)),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.ReportTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): weekly and monthly report payload builders"
```

---

### Task 10: app/ — minimal Compose Android module

**Files:**
- Modify: `settings.gradle.kts` (add `include(":app")`)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/badmintonledger/app/MainActivity.kt`

**Interfaces:**
- Consumes: `:domain` project dependency (proves module boundary compiles end-to-end).
- Produces: `gradlew.bat assembleDebug` builds an installable APK. No screens beyond a placeholder — Milestone 2 owns real UI.

- [ ] **Step 1: Add the app module**

`settings.gradle.kts` — change the last line block to:
```kotlin
include(":domain")
include(":app")
```

`app/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.badmintonledger.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.badmintonledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
}
```
(If AGP warns that compileSdk 36 is untested, add `android.suppressUnsupportedCompileSdk=36` to `gradle.properties`.)

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Badminton Ledger"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/kotlin/com/badmintonledger/app/MainActivity.kt`:
```kotlin
package com.badmintonledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultRate = LedgerData().config.defaultRate
        setContent {
            MaterialTheme {
                Surface {
                    Text("Badminton Ledger — default rate $${centsToDollars(defaultRate.value)}/h")
                }
            }
        }
    }
}
```
(The `domain` import is deliberate: it proves the module dependency direction compiles.)

- [ ] **Step 2: Build the APK**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Run all tests and quality gates**

Run: `gradlew.bat test ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add -A
git commit -m "feat(app): minimal Compose app module depending on domain"
```

---

### Task 11: Backup round-trip acceptance + real-data fixture hook

**Files:**
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupRoundTripTest.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/backup/RealBackupTest.kt`
- Create: `backups/README.md` (the folder is gitignored except this note — see step 1)

**Interfaces:**
- Consumes: everything.
- Produces: Milestone 1 acceptance evidence. `RealBackupTest` self-skips (JUnit assumption) until `backups/real-backup.json` exists, so `gradlew test` stays green headlessly; when Max exports from WeChat and drops the file in, the same test validates the real data.

- [ ] **Step 1: Write the tests**

`BackupRoundTripTest.kt` (synthetic end-to-end: decode → balances → encode → decode):
```kotlin
package com.badmintonledger.domain.backup

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupRoundTripTest {
    private val backupJson = """
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

        // session 76.80 split two ways = 38.40 each
        val bal = memberBalancesCents(data)
        assertEquals(60000L - 3840, bal["A"]) // contributed 600
        assertEquals(2560L - 3840, bal["G"])  // paid 25.60 cash, owes 12.80
        assertEquals(75000L - 9600, poolRemainingCents(data))

        val reimported = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data, reimported)
        assertEquals(bal, memberBalancesCents(reimported))
    }
}
```

`RealBackupTest.kt`:
```kotlin
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
        val expected = data.refills.sumOf { r -> r.contributions.sumOf { it.amount.value } } +
            data.payments.sumOf { it.amount.value } -
            data.sessions.sumOf { sessionShares(it).totalCents }
        assertEquals(expected, bal.values.sum())

        // round trip is lossless
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
}
```

`backups/README.md`:
```markdown
Drop real WeChat exports here (e.g. `real-backup.json`). Everything in this
folder except this file is gitignored — real group data must never be committed.
`RealBackupTest` picks up `real-backup.json` automatically when present.
```
Also fix `.gitignore`: git never descends into an ignored directory, so a negation
under `backups/` would be dead. Replace the `backups/` line with:
```
backups/*
!backups/README.md
```

- [ ] **Step 2: Run the full suite**

Run: `gradlew.bat test ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. `RealBackupTest` reports skipped (assumption) unless the real file is present.

- [ ] **Step 3: Verify domain purity** (spec hard rule)

Run: `Select-String -Path domain\src\main\kotlin -Pattern 'import android' -Recurse`
Expected: no matches.

- [ ] **Step 4: Commit**

```powershell
git add -A
git commit -m "test(domain): backup round-trip acceptance and real-data fixture hook"
```

---

## Milestone 1 Acceptance Checklist

- [ ] `gradlew.bat test` green (domain parity suite + round-trip test; RealBackupTest skips without the file)
- [ ] `gradlew.bat assembleDebug` produces an APK
- [ ] `gradlew.bat ktlintCheck detekt` green
- [ ] No `android.*` import anywhere under `domain/`
- [ ] Once Max provides `backups/real-backup.json`: `gradlew.bat :domain:test --tests "*.RealBackupTest"` runs (not skipped) and passes
