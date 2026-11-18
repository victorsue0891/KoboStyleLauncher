package org.kobostyle.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.books.ScanProgress
import org.kobostyle.launcher.data.db.*

/** Explicit invocation only. Restricts mutations to an exact synthetic test document grant. */
@RunWith(AndroidJUnit4::class)
class StorageAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$value\n") })
    private fun syntheticSingle(): Uri = resolver.persistedUriPermissions.single {
        it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents" &&
            !DocumentsContract.isTreeUri(it.uri) &&
            DocumentsContract.getDocumentId(it.uri).substringAfter(':') == "KoboStyleCardTest-20261115/Card-Test.pdf"
    }.uri

    @Test fun singleDocumentImportAndRevoke() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("allowSyntheticGrantRelease") == "yes")
        val uri = syntheticSingle()
        // A broader tree grant would invalidate this denial test. Never release a tree grant here.
        val volume = DocumentsContract.getDocumentId(uri).substringBefore(':')
        check(resolver.persistedUriPermissions.none {
            DocumentsContract.isTreeUri(it.uri) && DocumentsContract.getTreeDocumentId(it.uri).substringBefore(':') == volume
        }) { "Select single card PDF before granting any card tree" }
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val repository = LibraryRepository(context, db.library())
            db.library().saveSource(Source(uri.toString(), false))
            var result = ScanProgress()
            repository.scan { result = it }
            assertEquals(1, result.books); assertEquals(0, result.failures)
            val book = db.library().observeBooks().first().single()
            assertEquals("application/pdf", book.mime); assertNotNull(book.coverPath)
            db.library().opened(book.identity, 67890)
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertFalse(resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
            repository.refreshAccess()
            assertEquals("reauthorize", db.library().sources().single().status)
            assertEquals("unavailable", db.library().book(book.identity)!!.scanStatus)
            assertEquals(67890L, db.library().book(book.identity)!!.lastOpenedAt)
            repository.scan { result = it }
            assertEquals(0, result.books); assertEquals(1, result.failures)
            var denied = false
            try { resolver.openFileDescriptor(uri, "r")?.close() }
            catch (_: SecurityException) { denied = true }
            catch (_: java.io.FileNotFoundException) { denied = true }
            assertTrue("A temporary or broader grant still allows reading", denied)
            report("SINGLE_DOCUMENT import=PASS grantRelease=PASS readDenied=PASS indexPreserved=PASS")
        } finally { db.close() }
    }

    @Test fun cardAvailability() = runBlocking {
        val missing = InstrumentationRegistry.getArguments().getString("cardState") == "missing"
        val root = resolver.persistedUriPermissions.single {
            it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents" &&
                DocumentsContract.isTreeUri(it.uri) &&
                DocumentsContract.getTreeDocumentId(it.uri).substringAfter(':') == "KoboStyleCardTest-20261115"
        }.uri
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val repository = LibraryRepository(context, db.library())
            db.library().saveSource(Source(root.toString(), true))
            for ((name, mime) in listOf("Card-Test.pdf" to "application/pdf", "Card-Test.epub" to "application/epub+zip")) {
                val doc = DocumentsContract.getTreeDocumentId(root) + "/" + name
                val identity = LibraryRepository.identity(root.authority!!, doc)
                db.library().index(Book(identity, DocumentsContract.buildDocumentUriUsingTree(root, doc).toString(), root.toString(), doc, name, "", mime, 0, 0))
                db.library().opened(identity, 45678)
            }
            var result = ScanProgress()
            repository.scan { result = it }
            val books = db.library().observeBooks().first()
            assertEquals(2, books.size)
            assertTrue(books.all { it.lastOpenedAt == 45678L })
            if (missing) {
                assertEquals(0, result.books); assertTrue(result.failures > 0)
                assertTrue(books.all { it.scanStatus == "unavailable" })
            } else {
                assertEquals(2, result.books); assertEquals(0, result.failures)
                assertTrue(books.all { it.scanStatus == "ok" && it.coverPath != null })
            }
            report("CARD missing=$missing books=${result.books} failures=${result.failures} timestampsPreserved=PASS")
        } finally { db.close() }
    }
}
