package io.github.ocpabdurrahim.fin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads and writes backup / CSV files the user picked through the system file picker. */
class BackupManager(
    private val context: Context,
    private val repository: FinRepository,
    private val settings: SettingsStore,
) {
    /** Returns the number of transactions written. */
    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val data = repository.snapshot().copy(
            currencyCode = s.currencyCode,
            currencySymbol = s.currencySymbol,
            grouping = s.grouping,
        )
        write(uri, BackupCodec.encode(data))
        data.transactions.size
    }

    /** Replaces all data with the file's contents. Returns the number of transactions restored. */
    suspend fun importJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Could not open that file.")
        val data = BackupCodec.decode(text)
        repository.replaceAll(data)
        val symbol = data.currencySymbol
        if (symbol != null) {
            settings.update {
                it.copy(
                    currencyCode = data.currencyCode ?: it.currencyCode,
                    currencySymbol = symbol,
                    grouping = data.grouping ?: it.grouping,
                )
            }
        }
        data.transactions.size
    }

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = repository.snapshot()
        write(uri, CsvExporter.build(data))
        data.transactions.size
    }

    private fun write(uri: Uri, text: String) {
        val resolver = context.contentResolver
        // "wt" truncates an existing file; some providers only accept plain "w".
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Could not write to that file.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
