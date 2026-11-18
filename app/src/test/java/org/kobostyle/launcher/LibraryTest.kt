package org.kobostyle.launcher

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.kobostyle.launcher.data.db.*
import org.kobostyle.launcher.data.books.LibraryRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LibraryTest {
    @Test fun missingPersistentGrantMarksSourceAndBooksForReauthorization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        try {
            val source = "content://provider/tree/missing"
            db.library().saveSource(Source(source, true))
            db.library().index(Book("id", "content://provider/tree/missing/document/book", source, "book", "Book", "", "application/pdf", 0, 1))
            LibraryRepository(context, db.library()).refreshAccess()
            assertEquals("reauthorize", db.library().sources().single().status)
            assertEquals("unavailable", db.library().book("id")!!.scanStatus)
        } finally { db.close() }
    }
    @Test fun rescanPreservesOpenHistoryAndDeduplicatesDocument() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.library()
            val book = Book("identity", "content://provider/document/1", "source", "1", "Title", "", "application/pdf", 1, 100)
            dao.index(book); dao.opened(book.identity, 123)
            dao.index(book.copy(title = "Updated title"))
            assertEquals(123L, dao.book(book.identity)!!.lastOpenedAt)
            assertEquals("Updated title", dao.book(book.identity)!!.title)
            dao.markMissing("source", "next-scan")
            assertEquals("unavailable", dao.book(book.identity)!!.scanStatus)
            dao.index(book)
            assertEquals("ok", dao.book(book.identity)!!.scanStatus)
            assertEquals(123L, dao.book(book.identity)!!.lastOpenedAt)
        } finally { db.close() }
    }
    @Test fun sameFilenameDifferentDocumentDoesNotCollide() {
        assertNotEquals(LibraryRepository.identity("provider", "A/book.pdf"), LibraryRepository.identity("provider", "B/book.pdf"))
        assertNotEquals(LibraryRepository.identity("provider", "book"), LibraryRepository.identity("other", "book"))
    }
    @Test fun mimeFallbackIsConservative() {
        assertEquals("application/epub+zip", LibraryRepository.supportedMime("application/octet-stream", "BOOK.EPUB"))
        assertNull(LibraryRepository.supportedMime("application/x-executable", "BOOK.pdf"))
        assertNull(LibraryRepository.supportedMime("", "book.exe"))
    }
}
