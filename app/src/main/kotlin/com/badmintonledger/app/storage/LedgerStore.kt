package com.badmintonledger.app.storage

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.model.LedgerData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.ledgerDataStore by preferencesDataStore(
    name = "badminton_ledger",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val DATA_KEY = stringPreferencesKey("badminton_data_v1")

/**
 * Single-document persistence: the whole LedgerData is stored as one JSON string in
 * the same format as backup files (BackupCodec). A missing or unreadable document
 * falls back to the default empty ledger instead of crashing.
 */
class LedgerStore(private val context: Context) {
    val data: Flow<LedgerData> =
        context.ledgerDataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                prefs[DATA_KEY]?.let { text ->
                    runCatching { BackupCodec.decode(text) }.getOrNull()
                } ?: LedgerData()
            }

    suspend fun save(data: LedgerData) {
        // Write-behind: a failed disk write keeps the in-memory document authoritative;
        // surfacing persistent-storage errors to the UI is deferred until export exists (M5).
        runCatching {
            context.ledgerDataStore.edit { prefs ->
                prefs[DATA_KEY] = BackupCodec.encode(data)
            }
        }
    }
}
