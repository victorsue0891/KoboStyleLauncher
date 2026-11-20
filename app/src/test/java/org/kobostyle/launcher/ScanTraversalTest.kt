package org.kobostyle.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.kobostyle.launcher.data.books.*
import org.kobostyle.launcher.data.db.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ScanTraversalTest {
    private val authority = "org.kobostyle.test.documents"
    private val root = Uri.parse("content://$authority/tree/root")

    private class Documents : ContentProvider() {
        var textFiles = 0
        var failure: RuntimeException? = null
        var modified = 100L
        var denyRead = false
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (denyRead) throw SecurityException("Synthetic document denial")
            val file = java.io.File(context!!.cacheDir, "readable-scan-fixture").apply { writeText("synthetic") }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val result = MatrixCursor(projection)
            if (uri.lastPathSegment == "children") {
                failure?.let { throw it }
                repeat(textFiles) { result.addRow(arrayOf<Any>("root/text-$it", "text-$it.txt", "text/plain", 1L, 100L)) }
                // Oversized synthetic PDF avoids opening any real file; still indexed with fallback.
                result.addRow(arrayOf<Any>("root/new", "new.pdf", "application/pdf", Long.MAX_VALUE, modified))
            } else {
                result.addRow(arrayOf<Any>("root", "root", DocumentsContract.Document.MIME_TYPE_DIR, 0L, 100L))
            }
            return result
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only test")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test")
    }

    private fun fixture(block: suspend (LibraryDao, Documents, Context) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = Documents()
        provider.attachInfo(context, ProviderInfo().apply { authority = this@ScanTraversalTest.authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        context.contentResolver.takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.library().saveSource(Source(root.toString(), true))
            block(db.library(), provider, context)
        } finally { db.close() }
    }

    private fun book() = Book(LibraryRepository.identity(authority, "root/new"),
        DocumentsContract.buildDocumentUriUsingTree(root, "root/new").toString(), root.toString(),
        "root/new", "Cached title", "Author", "application/pdf", 100, Long.MAX_VALUE, lastOpenedAt = 123)

    @Test fun mixedFolderAboveOldLimitIncludesNewBook() = fixture { dao, provider, context ->
        provider.textFiles = 15_000
        var result = ScanProgress()
        LibraryRepository(context, dao).scan { result = it }
        assertEquals(1, result.books)
        assertFalse(result.limited)
        assertEquals("ok", dao.sources().single().status)
        assertNotNull(dao.book(book().identity))
    }

    @Test fun exceedingBoundPreservesExistingBookAndGrantState() = fixture { dao, provider, context ->
        provider.textFiles = 10
        dao.index(book())
        dao.opened(book().identity, 123)
        var result = ScanProgress()
        LibraryRepository(context, dao, maxDocuments = 10).scan { result = it }
        assertTrue(result.limited)
        assertEquals("partial", dao.sources().single().status)
        assertEquals("ok", dao.book(book().identity)!!.scanStatus)
        assertEquals(123L, dao.book(book().identity)!!.lastOpenedAt)
    }

    @Test fun exactBoundCompletes() = fixture { dao, provider, context ->
        provider.textFiles = 8 // Root + eight unsupported files + one PDF = ten entries.
        var result = ScanProgress()
        LibraryRepository(context, dao, maxDocuments = 10).scan { result = it }
        assertFalse(result.limited)
        assertEquals(1, result.books)
        assertEquals("ok", dao.sources().single().status)
    }

    @Test fun providerFailureDoesNotMarkAllBooksUnavailable() = fixture { dao, provider, context ->
        dao.index(book())
        dao.opened(book().identity, 123)
        provider.failure = IllegalStateException("Synthetic provider failure")
        LibraryRepository(context, dao).scan { }
        assertEquals("scan_error", dao.sources().single().status)
        assertEquals("ok", dao.book(book().identity)!!.scanStatus)
    }

    @Test fun genuinePermissionFailureStillRequiresReauthorization() = fixture { dao, provider, context ->
        dao.index(book())
        dao.opened(book().identity, 123)
        provider.failure = SecurityException("Synthetic denial")
        LibraryRepository(context, dao).scan { }
        assertEquals("reauthorize", dao.sources().single().status)
        assertEquals("unavailable", dao.book(book().identity)!!.scanStatus)
    }

    @Test fun unchangedMetadataReusedButChangedDocumentReparsed() = fixture { dao, provider, context ->
        dao.index(book())
        dao.opened(book().identity, 123)
        val repository = LibraryRepository(context, dao)
        repository.scan { }
        assertEquals("Cached title", dao.book(book().identity)!!.title)
        assertEquals("ok", dao.book(book().identity)!!.scanStatus)
        assertEquals("new.pdf", dao.book(book().identity)!!.displayName)
        provider.modified++
        repository.scan { }
        assertEquals("new", dao.book(book().identity)!!.title)
        assertEquals("new.pdf", dao.book(book().identity)!!.displayName)
        assertEquals("metadata_error", dao.book(book().identity)!!.scanStatus)
        assertEquals(123L, dao.book(book().identity)!!.lastOpenedAt)
    }

    @Test fun evictedThumbnailDoesNotForceMetadataReparse() = fixture { dao, _, context ->
        dao.index(book().copy(coverPath = "/nonexistent/evicted-cover.png"))
        LibraryRepository(context, dao).scan { }
        assertEquals("ok", dao.book(book().identity)!!.scanStatus)
        assertEquals("Cached title", dao.book(book().identity)!!.title)
    }

    @Test fun previouslyDecodedUnavailableBookRequiresFreshReadAccess() = fixture { dao, provider, context ->
        dao.index(book().copy(coverPath = "/nonexistent/evicted-cover.png", scanStatus = "unavailable"))
        val repository = LibraryRepository(context, dao)
        repository.scan { }
        assertEquals("ok", dao.book(book().identity)!!.scanStatus)
        assertEquals("Cached title", dao.book(book().identity)!!.title)
        dao.bookStatus(book().identity, "unavailable")
        provider.denyRead = true
        repository.scan { }
        assertNotEquals("ok", dao.book(book().identity)!!.scanStatus)
    }
}
