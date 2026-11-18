package org.kobostyle.launcher

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.books.ScanProgress
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.data.db.Source
import org.kobostyle.launcher.ui.MainActivity
import java.io.File

/** Explicitly run against synthetic fixtures only. Never reads the user's production Room DB. */
@RunWith(AndroidJUnit4::class)
class DeviceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun report(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$message\n") })

    @Test fun scanSyntheticLibraryAndCancel() = runBlocking {
        val expected = InstrumentationRegistry.getArguments().getString("expectedBooks")!!.toInt()
        val root = context.contentResolver.persistedUriPermissions.single {
            it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(it.uri) &&
                DocumentsContract.getTreeDocumentId(it.uri) == "primary:Download/KoboStyleAcceptance-20261115/valid"
        }.uri
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val repository = LibraryRepository(context, db.library())
            db.library().saveSource(Source(root.toString(), true))
            var result = ScanProgress()
            val start = SystemClock.elapsedRealtime()
            repository.scan {
                result = it
                if (it.running && it.books % 100 == 0) report("PROGRESS indexed=${it.books} failures=${it.failures}")
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val books = db.library().observeBooks().first()
            assertEquals(expected, result.books)
            assertEquals(expected, books.size)
            assertEquals(3, result.failures) // deliberate corrupt EPUB/PDF and password-protected PDF
            assertEquals(3, books.count { it.scanStatus == "metadata_error" })
            assertEquals(expected - 3, books.count { it.scanStatus == "ok" })
            assertEquals(expected, books.map { it.identity }.distinct().size)
            assertFalse(result.running)
            val epub = books.first { it.mime == "application/epub+zip" && it.scanStatus == "ok" }
            assertEquals("合成測試作者", epub.author)
            assertNotNull(epub.coverPath)
            val bitmap = android.graphics.BitmapFactory.decodeFile(epub.coverPath)
            assertNotNull(bitmap)
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
            assertEquals(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel))
            assertEquals(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
            bitmap.recycle()
            db.library().opened(epub.identity, 12345)
            // Reindex transaction preserves our event without re-rendering the entire fixture set.
            db.library().index(epub.copy(title = epub.title + " test"))
            assertEquals(12345L, db.library().book(epub.identity)!!.lastOpenedAt)
            report("SCAN expected=$expected indexed=${books.size} failures=${result.failures} elapsedMs=$elapsed grayscale=PASS")
        } finally { db.close() }

        val cancelDb = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val repository = LibraryRepository(context, cancelDb.library())
            cancelDb.library().saveSource(Source(root.toString(), true))
            val scan = launch {
                repository.scan { if (it.books >= 10) cancel("Acceptance cancellation") }
            }
            scan.join()
            assertTrue(scan.isCancelled)
            val imported = cancelDb.library().observeBooks().first().size
            assertTrue(imported in 10 until expected)
            report("CANCEL imported=$imported expectedTotal=$expected cancelled=PASS")
        } finally { cancelDb.close() }
    }

    @Test fun activityRotatesWithoutChangingSystemSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
                if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            scenario.onActivity {
                previous = it.requestedOrientation
                descendants(it.findViewById(android.R.id.content)).filterIsInstance<android.widget.Button>()
                    .single { button -> button.text == it.getString(R.string.library) }.performClick()
            }
            try {
                for ((orientation, expected, name) in listOf(
                    Triple(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE, "landscape"),
                    Triple(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT, "portrait")
                )) {
                    scenario.onActivity { it.requestedOrientation = orientation }
                    var matches = false
                    for (attempt in 0 until 20) {
                        SystemClock.sleep(200)
                        scenario.onActivity { matches = it.resources.configuration.orientation == expected }
                        if (matches) break
                    }
                    assertTrue("OEM did not apply requested activity orientation: $name", matches)
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertFalse(it.isFinishing)
                        val view = it.findViewById<android.view.View>(android.R.id.content)
                        assertTrue(view.width > 0 && view.height > 0)
                        val shelf = descendants(view).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().single()
                        assertTrue("Book list squeezed by controls", shelf.height >= 120 * it.resources.displayMetrics.density)
                        report("ROTATION $name width=${view.width} height=${view.height} shelfHeight=${shelf.height} fontScale=${it.resources.configuration.fontScale}")
                    }
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        val file = File(context.getExternalFilesDir(null), "acceptance-$name.png")
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
            } finally {
                scenario.onActivity { it.requestedOrientation = previous }
                scenario.finishInApp()
            }
        }
    }
}
