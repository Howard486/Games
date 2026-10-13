package com.howardaistudio.subscriptionhub.data.export

import android.content.Context
import android.net.Uri
import com.howardaistudio.subscriptionhub.domain.export.SubscriptionCsvExporter
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStreamWriter
import javax.inject.Inject

/** Writes every subscription to a user-chosen document (SA FR-026). */
interface SubscriptionExporter {
    /** Returns the number of exported rows; throws [IOException] when the target cannot be written. */
    suspend fun export(target: Uri): Int
}

/**
 * Streams the CSV into a Storage Access Framework document, so no broad storage permission is
 * needed (SA §10.2). Encoding is UTF-8 and the BOM comes from the encoder (FR-027).
 */
class CsvExportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptions: SubscriptionRepository,
    private val timeSource: TimeSource,
) : SubscriptionExporter {

    override suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val rows = subscriptions.getAll().sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        val stream = context.contentResolver.openOutputStream(target) ?: throw IOException("Cannot open export target")
        OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { writer ->
            SubscriptionCsvExporter.write(rows, timeSource.today(), writer)
        }
        rows.size
    }
}
