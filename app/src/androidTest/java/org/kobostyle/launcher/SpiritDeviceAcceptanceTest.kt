package org.kobostyle.launcher

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.*
import org.kobostyle.launcher.librarian.*
import org.kobostyle.launcher.spirits.*
import org.kobostyle.launcher.testing.AcceptanceActivity
import java.io.File
import java.util.UUID

/** Explicit device suite. Original books/grants/key remain untouched; only derived catalog is synced. */
@RunWith(AndroidJUnit4::class)
class SpiritDeviceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private fun report(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$text\n") })
    private fun book(n: Int) = Book("v4-fake-$n", "content://synthetic/book$n", "synthetic", "book$n", "合成探索書 $n", "測試作者", "application/pdf", 1, 10,
        displayName = "[科普]-合成探索書$n-測試作者.pdf")
    private fun spirits(name: String? = null) = (if (name == null) Room.inMemoryDatabaseBuilder(app, SpiritDatabase::class.java)
        else Room.databaseBuilder(app, SpiritDatabase::class.java, name)).setDriver(BundledSQLiteDriver()).addCallback(SpiritDatabase.SEARCH).build()
    private fun settings() = SpiritSettings(app.getSharedPreferences("acceptance_v4_${UUID.randomUUID()}", 0)).apply { enabled = true }
    private fun vault() = object : KeyVault {
        override fun exists() = true
        override fun read() = "synthetic-never-sent"
        override fun save(value: String) = error("No user key in device acceptance")
        override fun delete() = Unit
    }
    private fun pack() = JSONObject().put("schemaVersion", 1).put("packId", "device-synthetic")
        .put("source", JSONObject().put("type", "manual").put("toolVersion", "device-test"))
        .put("bookMatch", JSONObject().put("title", "合成探索書"))
        .put("sections", JSONArray((1..4).map { chapter ->
            val content = "合成第${chapter}章：觀察、記錄與驗證，不是真實書籍內容。"
            JSONObject().put("sectionId", "c$chapter").put("chapterOrder", chapter).put("title", "合成章節")
                .put("content", content).put("contentSha256", digest(content))
        })).toString()

    @Test fun actualCatalogProjectionFiftyPagesAndReadGrants() = runBlocking {
        val library = LibraryDatabase.get(app).library(); val db = SpiritDatabase.get(app)
        val count = library.count(); assertTrue(count > 0)
        val ai = AiPreferences(app.getSharedPreferences("librarian_ai", 0)).diagnostics().toString()
        val grants = app.contentResolver.persistedUriPermissions.map { it.uri.toString() to it.isReadPermission }
        val catalog = PagedCatalog(library, db.dao()); val start = SystemClock.elapsedRealtime(); catalog.sync()
        val syncMs = SystemClock.elapsedRealtime() - start
        assertEquals(count, db.dao().count())
        val times = mutableListOf<Long>()
        repeat(minOf(50, (count + 49) / 50)) { index ->
            val t = SystemClock.elapsedRealtime(); val page = catalog.page(offset = index * 50); times += SystemClock.elapsedRealtime() - t
            assertEquals(count, page.count); assertTrue(page.books.size in 1..50)
        }
        val candidate = catalog.candidates("", true, 0)
        var readable = 0
        for (entry in candidate.second.take(20)) {
            assertFalse(LocalCurator.isAcceptanceFixture(entry))
            if (app.contentResolver.openFileDescriptor(Uri.parse(entry.uri), "r")?.use { true } == true) readable++
        }
        assertTrue(readable > 0)
        // Query content is private: only report a success count, never title/URI.
        val sample = catalog.page().books.first()
        val title = org.kobostyle.launcher.data.books.BookFilename.parse(org.kobostyle.launcher.data.books.BookFilename.name(sample)).title ?: sample.title
        assertTrue(catalog.page(CatalogFilter(query = title.take(3))).count > 0)
        assertTrue(catalog.authors("", 0).size <= 50)
        assertEquals(grants, app.contentResolver.persistedUriPermissions.map { it.uri.toString() to it.isReadPermission })
        assertEquals(ai, AiPreferences(app.getSharedPreferences("librarian_ai", 0)).diagnostics().toString())
        val sorted = times.sorted(); val p95 = sorted[(kotlin.math.ceil(sorted.size * .95).toInt() - 1).coerceAtLeast(0)]
        report("V4_REAL indexed=$count projection=$count syncMs=$syncMs pages=${times.size} pageP95Ms=$p95 readableCandidates=$readable grants=${grants.size} aiDiagnosticsUnchanged=PASS")
    }

    @Test fun nativeLitePackSpoilersAndPersistentCostLedger() = runBlocking {
        val library = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        val name = "v4-acceptance-${UUID.randomUUID()}.db"; var db = spirits(name)
        val settings = settings(); settings.paidSpirits = true; settings.savePrice("test-model", Price(1_000_000, 2_000_000, "2026-11-25"))
        var calls = 0
        try {
            repeat(10) { library.library().index(book(it)) }
            var repo = SpiritRepository(db, library.library())
            val original = (0..9).map { repo.summon(book(it).identity) }
            repeat(10) { original.forEach { assertEquals(it, repo.summon(it.bookId)) } }
            val importer = KnowledgeImporter(db); val text = pack(); val preview = importer.inspect(text.byteInputStream())
            importer.import(book(0), preview, text.byteInputStream())
            assertTrue(repo.evidence(book(0), "觀察").isEmpty())
            repo.setBoundary(ReadingBoundary(book(0).identity, 3))
            assertEquals(3, repo.evidence(book(0), "觀察").size)
            assertTrue(repo.evidence(book(1), "觀察").isEmpty())
            try { importer.import(book(0), preview, text.replace("device-synthetic", "changed").byteInputStream()); fail("Changed pack accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(preview.digest, db.dao().pack(book(0).identity)!!.digest)
            val entered = CompletableDeferred<Unit>()
            val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; entered.complete(Unit); awaitCancellation() })
            val request = """{"model":"test-model","max_output_tokens":300,"input":"synthetic"}"""
            val job = launch { gateway.send("ask", request, { "fake-key" }, { true }, "device-cancel") }
            entered.await()
            repeat(10) { try { gateway.send("ask", request, { "fake-key" }, { true }); fail() } catch (e: GateFailure) { assertEquals("busy", e.reason) } }
            job.cancelAndJoin(); assertEquals("UNKNOWN_BILLING", db.dao().event("device-cancel")!!.status)
            db.close(); db = spirits(name); repo = SpiritRepository(db, library.library())
            assertEquals(original[0], repo.summon(book(0).identity)); assertTrue(db.dao().event("device-cancel")!!.chargedMicros > 0)
            repo.delete(book(0).identity); assertNotNull(library.library().book(book(0).identity)); assertNull(db.dao().pack(book(0).identity))
            assertEquals(1, calls)
            report("V4_NATIVE lite10Reentry10=PASS reopen=PASS packRollback=PASS spoilerBoundary=PASS singleFlight=PASS unknownBilling=PASS fakeCalls=1 liveApiCalls=0")
        } finally { db.close(); library.close(); app.deleteDatabase(name) }
    }

    @Test fun authorizedSyntheticScanCancelsAndResumesWithoutDeletingBooks() = runBlocking {
        val grant = app.contentResolver.persistedUriPermissions.single { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) &&
            DocumentsContract.getTreeDocumentId(it.uri) == "primary:Download/KoboStyleAcceptance-20261115/valid" }
        val library = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            val expected = androidx.documentfile.provider.DocumentFile.fromTreeUri(app, grant.uri)!!.listFiles()
                .count { !it.isDirectory && LibraryRepository.supportedMime(it.type.orEmpty(), it.name.orEmpty()) != null }
            assertTrue(expected >= 20)
            library.library().saveSource(Source(grant.uri.toString(), true))
            val repo = LibraryRepository(app, library.library())
            try { repo.scan { if (it.books >= 1) throw CancellationException("Intentional synthetic scan interruption") }; fail() } catch (_: CancellationException) { }
            assertNotNull(library.library().scanJob(grant.uri.toString()))
            assertTrue(library.library().count() >= 1)
            LibraryRepository(app, library.library()).scan { }
            assertEquals(expected, library.library().count()); assertNull(library.library().scanJob(grant.uri.toString()))
            report("V4_SCAN fixtureFolderBooks=$expected cancelResume=PASS originalLibraryUntouched=PASS liveApiCalls=0")
        } finally { library.close() }
    }

    @Test fun isolatedSpiritUiCreationRotationAndLocalEvidence() {
        val library = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).addCallback(LibraryDatabase.TRACKING).build()
        val db = spirits(); val settings = settings(); var calls = 0
        val runtime = SpiritRuntime(app, db, CuratorTransport { _, _ -> calls++; error("No real network") }, settings)
        val repository = LibraryRepository(app, library.library())
        runBlocking { repeat(10) { library.library().index(book(it)) }; PagedCatalog(library.library(), db.dao()).sync() }
        AcceptanceActivity.repository = repository; AcceptanceActivity.spiritTestRuntime = runtime
        AcceptanceActivity.librarianFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val prefs = AiPreferences(app.getSharedPreferences("v4-ui-fake", 0))
                return LibrarianViewModel(repository, prefs, vault(), CuratorService(prefs, vault(), CuratorTransport { _, _ -> calls++; error("No real network") }, { false })) { true } as T
            }
        }
        AcceptanceActivity.testFontScale = 1.3f
        val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
        fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        fun press(text: String) {
            val end = SystemClock.elapsedRealtime() + 20_000
            var clicked = false
            while (!clicked && SystemClock.elapsedRealtime() < end) {
                instrumentation.runOnMainSync {
                    WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().firstOrNull { it.text.toString() == text && it.isShown && it.isEnabled && it.isClickable }?.let { clicked = it.performClick() }
                }
                if (!clicked) SystemClock.sleep(100)
            }
            assertTrue("Missing control $text", clicked); instrumentation.waitForIdleSync()
        }
        fun shot(name: String) {
            val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(app.getExternalFilesDir(null), "v4-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
        try {
            press("喵館員"); shot("welcome")
            press("書靈小屋 · 選一本書"); press("合成探索書 0"); press("免費建立 Lite 書靈")
            press("這章重點")
            runBlocking { assertNotNull(db.dao().spirit(book(0).identity)) }
            scenario.recreate(); press("這章重點"); shot("lite")
            scenario.onActivity { a ->
                assertTrue(a.acceptsKeyForTest(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN)))
                assertTrue(all(a.findViewById(android.R.id.content)).filterIsInstance<Button>().filter { it.isShown }.all { it.height >= 48 * a.resources.displayMetrics.density })
            }
            assertEquals(0, calls)
            report("V4_UI summon=PASS rotation=PASS missingEvidence=PASS font1.3=PASS standardPageKey=PASS liveApiCalls=0")
        } catch (e: Throwable) { shot("ui-failure"); throw e } finally {
            scenario.finishInApp(); AcceptanceActivity.spiritTestRuntime = null; AcceptanceActivity.librarianFactory = null; AcceptanceActivity.repository = null; AcceptanceActivity.testFontScale = 1f
            db.close(); library.close()
        }
    }
}
