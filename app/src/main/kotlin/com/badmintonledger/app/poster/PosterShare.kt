package com.badmintonledger.app.poster

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val AUTHORITY = "com.badmintonledger.fileprovider"

/** Writes the poster PNG to cache and opens the system share sheet. */
suspend fun sharePoster(
    context: Context,
    bitmap: Bitmap,
) {
    val uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "posters").apply { mkdirs() }
            val file = File(dir, "poster.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, AUTHORITY, file)
        }
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, "Share poster"))
}
