package com.badmintonledger.app.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.model.LedgerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a pretty-printed backup JSON to cache and opens the system share sheet. */
suspend fun shareBackup(
    context: Context,
    data: LedgerData,
    dateStr: String,
) {
    val uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, BackupCodec.exportFileName(dateStr))
            file.writeText(BackupCodec.encodePretty(data))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, "Share backup"))
}
