package org.kobostyle.launcher.librarian

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LibrarianViewModelTest {
    private lateinit var db: LibraryDatabase
    private lateinit var model: LibrarianViewModel
    private var networkCalls = 0
    private fun setup(readable: suspend (Book) -> Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { repeat(20) { db.library().index(sampleBook(it)) } }
        val prefs = AiPreferences(app.getSharedPreferences("vm-ai-test", 0).apply { edit().clear().commit() })
        model = LibrarianViewModel(LibraryRepository(app, db.library()), prefs, FakeVault(null),
            CuratorService(prefs, FakeVault(null), CuratorTransport { _, _ -> networkCalls++; error("No API") }, { false }), readable)
    }
    private fun start() { model.dispatch(DialogueAction.QUERY, "故事"); model.dispatch(DialogueAction.OFFLINE) }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (!condition() && System.nanoTime() < deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Asynchronous work did not reach expected state", condition())
    }
    @After fun cleanup() { model.clearSessionAndCache(); Shadows.shadowOf(Looper.getMainLooper()).idle(); db.close() }
    @Test fun cancelledSlowSourceCannotPublishStaleCardsAfterReset() {
        val entered = AtomicBoolean(false)
        val gate = CompletableDeferred<Boolean>()
        setup { entered.set(true); gate.await() }
        start(); await { entered.get() }
        model.leavePage()
        assertEquals(DialogueStage.CANDIDATES, model.state.value.stage)
        model.clearSessionAndCache(); gate.complete(true)
        repeat(20) { Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(DialogueState(), model.state.value); assertEquals(0, networkCalls)
    }
    @Test fun inaccessibleDocumentsAreExcludedAndEmptyLibraryIsRecoverable() {
        setup { false }
        start(); await { model.state.value.stage == DialogueStage.CARDS }
        assertTrue(model.state.value.result!!.cards.isEmpty())
        assertEquals(0, networkCalls)
        model.dispatch(DialogueAction.BACK); assertEquals(DialogueStage.CANDIDATES, model.state.value.stage)
    }
    @Test fun candidateSelectionUsesOnlyDocumentsThatCanStillBeOpened() {
        setup { it.identity == sampleBook(17).identity }
        start(); await { model.state.value.stage == DialogueStage.CARDS }
        assertEquals(listOf(sampleBook(17).identity), model.state.value.result!!.cards.map { it.book.identity })
        assertEquals(0, networkCalls)
    }
}
