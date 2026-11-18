package org.kobostyle.launcher

import android.os.Bundle
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.LibraryDatabase
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Read-only SAF diagnosis; reports counts/error types without paths or book names. */
@RunWith(AndroidJUnit4::class)
class SourceDiagnosticTest {
    @Test fun inspectGrantedSources() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val grants = resolver.persistedUriPermissions
        val timer = Executors.newSingleThreadScheduledExecutor()
        fun report(text: String) = instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\n$text\n")
        })
        try {
            LibraryDatabase.get(context).library().sources().forEachIndexed { index, source ->
                val root = android.net.Uri.parse(source.uri)
                val granted = grants.any { it.uri == root && it.isReadPermission }
                report("SOURCE index=$index tree=${source.tree} status=${source.status} readGrant=$granted")
                if (!granted) return@forEachIndexed
                val signal = CancellationSignal()
                val timeout = timer.schedule({ signal.cancel() }, 30, TimeUnit.SECONDS)
                try {
                    val uri = if (source.tree) DocumentsContract.buildChildDocumentsUriUsingTree(
                        root, DocumentsContract.getTreeDocumentId(root)
                    ) else root
                    val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val cursor = resolver.query(uri, columns, null, null, null, signal)
                    var total = 0; var books = 0; var folders = 0
                    checkNotNull(cursor).use {
                        while (it.moveToNext()) {
                            total++
                            val mime = it.getString(1).orEmpty()
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) folders++
                            if (LibraryRepository.supportedMime(mime, it.getString(0).orEmpty()) != null) books++
                            check(total <= 100000) { "Diagnostic row bound" }
                        }
                    }
                    report("SOURCE index=$index query=OK children=$total supported=$books folders=$folders")
                } catch (e: Exception) {
                    report("SOURCE index=$index queryError=${e.javaClass.simpleName} timedOut=${signal.isCanceled}")
                } finally { timeout.cancel(false) }
            }
        } finally { timer.shutdownNow() }
    }
}
