package org.kobostyle.launcher.data.books

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import org.kobostyle.launcher.data.db.*
import org.kobostyle.launcher.data.metadata.EpubParser
import org.kobostyle.launcher.data.cover.CoverCache
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class ScanProgress(val books: Int = 0, val failures: Int = 0, val running: Boolean = false, val limited: Boolean = false)
class LibraryRepository(
    private val context: Context,
    val dao: LibraryDao,
    private val maxDocuments: Int = 50_000
) {
    init { require(maxDocuments > 0) }
    private class ScanLimitException : Exception()
    private val resolver = context.contentResolver
    private val covers = CoverCache(context)
    suspend fun addSource(uri: Uri, tree: Boolean, flags: Int) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri) || (uri.scheme == "content" && tree && DocumentsContract.isTreeUri(uri)))
        require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        check(resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
        dao.saveSource(Source(uri.toString(), tree))
    }
    suspend fun refreshAccess() = withContext(Dispatchers.IO) {
        val granted = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        dao.sources().filter { it.uri !in granted }.forEach {
            dao.saveSource(it.copy(status = "reauthorize")); dao.sourceStatus(it.uri, "unavailable")
        }
    }
    private data class Document(val uri: Uri, val id: String, val name: String, val mime: String, val size: Long, val modified: Long)
    private val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    private suspend fun query(uri: Uri, tree: Uri?, remaining: Int): List<Document> {
        val signal = CancellationSignal()
        return coroutineScope {
            // Cancel a blocking provider query promptly when the scan is cancelled.
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { signal.cancel() }
            }
            try {
                val result = mutableListOf<Document>()
                val cursor = resolver.query(uri, columns, null, null, null, signal) ?: error("Provider unavailable")
                cursor.use {
                    while (it.moveToNext()) {
                        ensureActive()
                        if (result.size >= remaining) throw ScanLimitException()
                        val id = it.getString(0) ?: error("No document ID")
                        val documentUri = if (tree == null) uri else DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        result += Document(documentUri, id, it.getString(1).orEmpty().take(500), it.getString(2).orEmpty(), it.getLong(3), it.getLong(4))
                    }
                }
                result
            } finally { watcher.cancelAndJoin() }
        }
    }
    suspend fun scan(progress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (context.getSharedPreferences("spirits_v4", 0).getBoolean("feature.meow_spirits_v4", false)) {
            checkpointScan(progress); return@withContext
        }
        var count = 0; var failures = 0; var visitedCount = 0; var limited = false
        refreshAccess()
        for (source in dao.sources()) {
            ensureActive()
            val root = Uri.parse(source.uri)
            if (!resolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission }) { failures++; continue }
            val scanToken = java.util.UUID.randomUUID().toString()
            var complete = true
            try {
                val queue = ArrayDeque<Pair<Document, Int>>()
                val rootDoc = if (source.tree) DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root)) else root
                query(rootDoc, if (source.tree) root else null, maxDocuments).forEach { queue.addLast(it to 0) }
                // The root is readable again. Do not retain a stale reauthorization warning
                // while an incomplete rescan is restoring the individual book states.
                dao.saveSource(source.copy(status = "partial"))
                val visited = hashSetOf<String>()
                while (queue.isNotEmpty()) {
                    ensureActive()
                    val (doc, depth) = queue.removeFirst()
                    if (!visited.add(doc.id)) continue
                    if (++visitedCount > maxDocuments || depth > 32) { limited = true; complete = false; break }
                    if (doc.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        check(source.tree)
                        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, doc.id)
                        query(children, root, maxOf(0, maxDocuments - visitedCount - queue.size)).forEach { queue.addLast(it to depth + 1) }
                        continue
                    }
                    val mime = supportedMime(doc.mime, doc.name) ?: continue
                    val identity = identity(doc.uri.authority.orEmpty(), doc.id)
                    try {
                        val book = metadata(doc, source, identity, mime)
                        dao.index(book.copy(scanToken = scanToken))
                        if (book.scanStatus != "ok") failures++
                        count++
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failures++; dao.bookStatus(identity, "unavailable") }
                    progress(ScanProgress(count, failures, true, limited))
                }
                if (complete) dao.markMissing(source.uri, scanToken)
                dao.saveSource(source.copy(status = if (complete) "ok" else "partial"))
            } catch (e: CancellationException) { throw e }
            catch (_: ScanLimitException) {
                // A bounded/incomplete traversal does not revoke access or prove books missing.
                limited = true
                dao.saveSource(source.copy(status = "partial"))
            }
            catch (_: SecurityException) {
                failures++; dao.saveSource(source.copy(status = "reauthorize")); dao.sourceStatus(source.uri, "unavailable")
            }
            catch (_: java.io.FileNotFoundException) {
                failures++; dao.saveSource(source.copy(status = "reauthorize")); dao.sourceStatus(source.uri, "unavailable")
            }
            catch (_: Exception) {
                // Provider/query failures are not evidence that every indexed book lost its grant.
                failures++; dao.saveSource(source.copy(status = "scan_error"))
            }
            if (limited) break
        }
        progress(ScanProgress(count, failures, false, limited))
    }
    /** A paused source is resumed only by a later explicit scan action, never at startup. */
    private suspend fun checkpointScan(progress: (ScanProgress) -> Unit) {
        refreshAccess()
        var total = 0; var failures = 0
        for (source in dao.sources()) {
            coroutineContext.ensureActive()
            val root = Uri.parse(source.uri)
            if (!resolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission }) { failures++; continue }
            var job = dao.scanJob(source.uri)
            try {
                if (job == null) {
                    val rootUri = if (source.tree) DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root)) else root
                    val doc = query(rootUri, if (source.tree) root else null, 1).single()
                    job = ScanJob(source.uri, java.util.UUID.randomUUID().toString())
                    dao.startScan(job, ScanNode(source.uri, doc.id, doc.uri.toString(), doc.name, doc.mime, doc.size, doc.modified, 0))
                }
                dao.saveSource(source.copy(status = "partial"))
                while (true) {
                    coroutineContext.ensureActive()
                    val node = dao.nextNode(source.uri) ?: break
                    if (node.depth > 32) throw ScanLimitException()
                    if (node.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        check(source.tree)
                        val signal = CancellationSignal()
                        coroutineScope {
                            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { signal.cancel() } }
                            try {
                                val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, node.id)
                                val cursor = resolver.query(children, columns, null, null, null, signal) ?: error("Provider unavailable")
                                cursor.use {
                                    var observed = 0
                                    while (it.moveToNext()) {
                                        ensureActive()
                                        if (++observed > maxDocuments) throw ScanLimitException()
                                        val id = it.getString(0) ?: error("No document ID")
                                        dao.enqueue(ScanNode(source.uri, id, DocumentsContract.buildDocumentUriUsingTree(root, id).toString(),
                                            it.getString(1).orEmpty().take(500), it.getString(2).orEmpty(), it.getLong(3), it.getLong(4), node.depth + 1))
                                        if (dao.nodeCount(source.uri) > maxDocuments) throw ScanLimitException()
                                    }
                                }
                            } finally { watcher.cancelAndJoin() }
                        }
                        dao.done(source.uri, node.id)
                    } else {
                        val mime = supportedMime(node.mime, node.name)
                        var book: Book? = null
                        if (mime != null) {
                            val doc = Document(Uri.parse(node.uri), node.id, node.name, node.mime, node.size, node.modified)
                            book = metadata(doc, source, identity(doc.uri.authority.orEmpty(), doc.id), mime).copy(scanToken = job!!.token)
                        }
                        job = job!!.copy(books = job!!.books + if (book != null) 1 else 0, failures = job!!.failures + if (book != null && book.scanStatus != "ok") 1 else 0)
                        dao.finishNode(node, job!!, book)
                        progress(ScanProgress(total + job!!.books, failures + job!!.failures, true))
                    }
                }
                dao.completeScan(source.uri, job!!.token)
                dao.saveSource(source.copy(status = "ok"))
                total += job!!.books; failures += job!!.failures
            } catch (e: CancellationException) { throw e }
            catch (_: ScanLimitException) {
                dao.saveSource(source.copy(status = "partial"))
                progress(ScanProgress(total + (job?.books ?: 0), failures + (job?.failures ?: 0), false, true)); return
            } catch (_: SecurityException) {
                failures++; dao.saveSource(source.copy(status = "reauthorize")); dao.sourceStatus(source.uri, "unavailable")
            } catch (_: Exception) {
                failures++; dao.saveSource(source.copy(status = "scan_error"))
            }
        }
        progress(ScanProgress(total, failures, false))
    }
    private suspend fun metadata(doc: Document, source: Source, id: String, mime: String): Book {
        val cached = dao.book(id)
        if (cached != null && doc.modified > 0 && doc.size > 0 &&
            cached.lastModified == doc.modified && cached.size == doc.size && cached.mime == mime) {
            // Thumbnail eviction does not invalidate previously parsed title/author metadata.
            // A saved cover path proves decoding once succeeded: metadata() only assigns it
            // after successful parsing/rendering. Recover a source-wide unavailable marker
            // only after a fresh read-access check; never reuse known metadata_error results.
            val recovered = cached.scanStatus == "unavailable" && cached.coverPath != null &&
                try { resolver.openFileDescriptor(doc.uri, "r")?.use { true } == true }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
            if (cached.scanStatus == "ok" || recovered) {
                return cached.copy(uri = doc.uri.toString(), sourceUri = source.uri, documentId = doc.id, displayName = doc.name, scanStatus = "ok")
            }
        }
        var title = doc.name.substringBeforeLast('.', doc.name).ifBlank { "EPUB / PDF" }
        var author = ""; var cover: String? = null; var status = "ok"
        try {
            if (mime == "application/epub+zip") {
                require(doc.size <= EpubParser.MAX_ARCHIVE)
                val temp = File.createTempFile("epub-", ".zip", context.cacheDir)
                try {
                    resolver.openInputStream(doc.uri).use { stream ->
                        requireNotNull(stream)
                        temp.outputStream().use { out ->
                            var total = 0L
                            val buffer = ByteArray(16384)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = stream.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= EpubParser.MAX_ARCHIVE)
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    val parsed = EpubParser.parse(temp)
                    title = parsed.title?.takeIf { it.isNotBlank() } ?: title
                    author = parsed.author.orEmpty()
                    parsed.cover?.let { bytes -> covers.epub(bytes)?.let { cover = covers.store(id, it) } }
                } finally { temp.delete() }
            } else {
                // PdfRenderer exposes no title/author API. Use the SAF filename honestly.
                require(doc.size <= 256L * 1024 * 1024)
                covers.pdf(doc.uri)?.let { cover = covers.store(id, it) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: SecurityException) { status = "unavailable" }
        catch (_: java.io.FileNotFoundException) { status = "unavailable" }
        catch (_: Exception) { status = "metadata_error" }
        return Book(id, doc.uri.toString(), source.uri, doc.id, title, author, mime, doc.modified, doc.size, coverPath = cover, scanStatus = status, displayName = doc.name)
    }
    companion object {
        fun identity(authority: String, documentId: String) = Uri.encode(authority) + ":" + Uri.encode(documentId)
        fun supportedMime(mime: String, name: String): String? = when {
            mime == "application/epub+zip" || mime == "application/pdf" -> mime
            mime.isBlank() || mime == "application/octet-stream" -> when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "epub" -> "application/epub+zip"; "pdf" -> "application/pdf"; else -> null
            }
            else -> null
        }
    }
}
