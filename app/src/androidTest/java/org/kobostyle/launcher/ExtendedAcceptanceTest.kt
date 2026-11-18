package org.kobostyle.launcher

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.testing.AcceptanceActivity
import java.util.concurrent.atomic.AtomicInteger

/** Real UI, isolated in-memory DB. Never inserts synthetic rows in the user's library.db. */
@RunWith(AndroidJUnit4::class)
class ExtendedAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$value\n") })
    private fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private fun shelf(activity: AcceptanceActivity) = children(activity.findViewById(android.R.id.content)).filterIsInstance<RecyclerView>().single()

    @Test fun thousandRowsAtBothFontScalesAndNoIdleRedraw() {
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            runBlocking {
                for (i in 0 until 1000) db.library().index(Book(
                    "ui-$i", "content://org.kobostyle.synthetic/$i", "synthetic", "$i",
                    "%04d".format(i) + " 繁體中文長書名與排版測試".repeat(8), "合成作者", "application/pdf", 0, 0
                ))
            }
            AcceptanceActivity.repository = LibraryRepository(context, db.library())
            for (font in listOf(1f, 1.3f)) {
                AcceptanceActivity.testFontScale = font
                val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
                try {
                    scenario.onActivity { a ->
                        children(a.findViewById(android.R.id.content)).filterIsInstance<Button>()
                            .single { it.text == a.getString(R.string.library) }.performClick()
                    }
                    var count = 0
                    repeat(50) {
                        if (count != 1001) {
                            SystemClock.sleep(100)
                            scenario.onActivity { count = shelf(it).adapter!!.itemCount }
                        }
                    }
                    assertEquals(1001, count)
                    for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                        scenario.onActivity { it.requestedOrientation = orientation }
                        SystemClock.sleep(1200)
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { a ->
                            assertEquals(font, a.resources.configuration.fontScale, 0.01f)
                            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) 2 else 1
                            assertEquals(expected, a.resources.configuration.orientation)
                            assertTrue(shelf(a).height >= 120 * a.resources.displayMetrics.density)
                        }
                        // Sample positions across the full list in both directions, forcing repeated recycling.
                        val start = SystemClock.elapsedRealtime()
                        for (position in (0..1000 step 25).toList() + (1000 downTo 0 step 25).toList()) {
                            scenario.onActivity { (shelf(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
                            instrumentation.waitForIdleSync()
                            SystemClock.sleep(30)
                            scenario.onActivity { a ->
                                val list = shelf(a)
                                val manager = list.layoutManager as LinearLayoutManager
                                if (position == 1000) assertEquals(1000, manager.findLastVisibleItemPosition())
                                else assertTrue("Requested book is not visible", position in manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition())
                                assertTrue(list.childCount < 25)
                                val holder = list.findViewHolderForAdapterPosition(position)!!
                                if (position > 0) assertTrue(children(holder.itemView).filterIsInstance<TextView>()
                                    .any { it.text.startsWith("%04d".format(position - 1)) })
                            }
                        }
                        report("UI_SCALE font=$font orientation=$orientation rows=$count traversalMs=${SystemClock.elapsedRealtime()-start} PASS")
                    }
                    // Synthetic standard Android page keys; not proof of BOOX's physical mapping.
                    scenario.onActivity { (shelf(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0) }
                    // waitForIdleSync alone can return before the requested RecyclerView frame layout.
                    var layoutReady = false
                    repeat(50) {
                        if (!layoutReady) {
                            SystemClock.sleep(20)
                            scenario.onActivity { a -> layoutReady = a.hasWindowFocus() && !shelf(a).isLayoutRequested &&
                                (shelf(a).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 0 }
                        }
                    }
                    assertTrue("Shelf layout/focus not ready for key injection", layoutReady)
                    instrumentation.waitForIdleSync()
                    AcceptanceActivity.keyCodes.clear()
                    fun awaitScroll(expected: Boolean) {
                        var reached = false
                        repeat(50) {
                            if (!reached) {
                                SystemClock.sleep(20)
                                scenario.onActivity { reached = shelf(it).canScrollVertically(-1) == expected && !shelf(it).isLayoutRequested }
                            }
                        }
                        assertTrue("Page key did not reach scroll=$expected; windowEvents=${AcceptanceActivity.keyCodes}", reached)
                    }
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_PAGE_DOWN)
                    awaitScroll(true)
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_PAGE_UP)
                    awaitScroll(false)
                    assertEquals(listOf(android.view.KeyEvent.KEYCODE_PAGE_DOWN, android.view.KeyEvent.KEYCODE_PAGE_UP), AcceptanceActivity.keyCodes.toList())
                    report("STANDARD_PAGE_KEYS font=$font downAndUp=PASS synthetic=true")
                    if (font == 1.3f) {
                        SystemClock.sleep(2000)
                        val draws = AtomicInteger()
                        val listener = android.view.ViewTreeObserver.OnDrawListener { draws.incrementAndGet() }
                        scenario.onActivity { it.window.decorView.viewTreeObserver.addOnDrawListener(listener) }
                        try {
                            val started = SystemClock.elapsedRealtime()
                            repeat(6) { SystemClock.sleep(10000); report("IDLE elapsedMs=${SystemClock.elapsedRealtime()-started} draws=${draws.get()}") }
                            assertEquals("Unexpected recurring drawing while untouched", 0, draws.get())
                        } finally { scenario.onActivity { it.window.decorView.viewTreeObserver.removeOnDrawListener(listener) } }
                    }
                } finally { scenario.finishInApp() }
            }
        } finally {
            AcceptanceActivity.repository = null
            db.close()
        }
    }
}
