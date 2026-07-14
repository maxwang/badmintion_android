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
    private val json =
        Json {
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
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                return ImportResult.Err("Not a valid backup file")
            }
        return validate(root)
    }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "LongMethod", "ReturnCount")
    fun validate(root: JsonElement): ImportResult {
        val obj = root as? JsonObject ?: return ImportResult.Err("Not a valid backup file")
        if ((obj["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != 1) {
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
            val isGuest = (mo["isGuest"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
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
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { it.isFinite() && it > 0 } == true

    private fun JsonObject.dateOk(key: String): Boolean = stringOrNull(key)?.matches(dateRe) == true
}
