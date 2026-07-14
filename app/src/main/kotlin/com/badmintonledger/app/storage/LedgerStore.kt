package com.badmintonledger.app.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.model.LedgerData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ledgerDataStore by preferencesDataStore(name = "badminton_ledger")
private val DATA_KEY = stringPreferencesKey("badminton_data_v1")

/**
 * Single-document persistence: the whole LedgerData is stored as one JSON string in
 * the same format as backup files (BackupCodec). A missing or unreadable document
 * falls back to the default empty ledger instead of crashing.
 */
class LedgerStore(private val context: Context) {
    val data: Flow<LedgerData> =
        context.ledgerDataStore.data.map { prefs ->
            prefs[DATA_KEY]?.let { text ->
                runCatching { BackupCodec.decode(text) }.getOrNull()
            } ?: LedgerData()
        }

    suspend fun save(data: LedgerData) {
        context.ledgerDataStore.edit { prefs ->
            prefs[DATA_KEY] = BackupCodec.encode(data)
        }
    }
}
