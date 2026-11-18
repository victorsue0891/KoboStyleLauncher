package org.kobostyle.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
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
import java.io.File

/** UI contract and screenshots with synthetic books in an isolated DB. No source grants or reader launch. */
@RunWith(AndroidJUnit4::class)
class ReaderUiAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
        (0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes).orEmpty() }
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$value\n") })

    @Test fun homeGridSearchAndReaderMenuSurviveRecreation() {
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val titles = listOf("山海之間", "靜讀時光", "日常的練習", "THE QUIET HOURS")
        runBlocking {
            titles.forEachIndexed { i, title ->
                db.library().index(Book("design-$i", "content://org.kobostyle.synthetic/design-$i", "synthetic", "$i", title,
                    if (i == 2) "Special Author" else "合成測試作者", "application/epub+zip", 0, 0))
                db.library().opened("design-$i", 1735689600000L - i * 86400000L)
            }
        }
        AcceptanceActivity.repository = LibraryRepository(context, db.library())
        AcceptanceActivity.testFontScale = 1.3f
        val automation = instrumentation.uiAutomation
        val oldInfo = automation.serviceInfo
        automation.serviceInfo = AccessibilityServiceInfo().apply { flags = oldInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; eventTypes = oldInfo.eventTypes; feedbackType = oldInfo.feedbackType }
        val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
        fun shelf(a: AcceptanceActivity) = a.findViewById<RecyclerView>(R.id.shelf)
        fun click(id: Int) = scenario.onActivity { a -> descendants(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text == a.getString(id) }.performClick() }
        fun awaitCount(expected: Int) {
            var ready = false
            repeat(60) {
                if (!ready) {
                    SystemClock.sleep(100)
                    scenario.onActivity { ready = shelf(it).adapter?.itemCount == expected && !shelf(it).isLayoutRequested }
                }
            }
            assertTrue("Expected $expected rows", ready)
            instrumentation.waitForIdleSync()
        }
        fun screenshot(name: String) {
            SystemClock.sleep(250)
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun dialogClick(id: Int) {
            val text = context.getString(id)
            var target: AccessibilityNodeInfo? = null
            repeat(20) { if (target == null) { SystemClock.sleep(100); target = automation.rootInActiveWindow?.let(::nodes)?.firstOrNull { it.text?.toString() == text && it.isClickable } } }
            assertNotNull("Dialog action $text", target)
            assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
        }
        try {
            for ((orientation, name) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                scenario.onActivity { it.requestedOrientation = orientation }
                SystemClock.sleep(1000)
                awaitCount(3)
                scenario.onActivity { a ->
                    val list = shelf(a)
                    list.scrollToPosition(0)
                    assertTrue(list.height > 120 * a.resources.displayMetrics.density)
                    val home = descendants(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text == a.getString(R.string.home) }
                    val listLocation = IntArray(2); val navLocation = IntArray(2)
                    list.getLocationOnScreen(listLocation); home.getLocationOnScreen(navLocation)
                    assertTrue("Fixed navigation overlaps viewport", navLocation[1] >= listLocation[1] + list.height)
                    assertTrue(home.height >= 48 * a.resources.displayMetrics.density)
                    assertEquals(1.3f, a.resources.configuration.fontScale, .01f)
                    val first = list.findViewHolderForAdapterPosition(0)!!
                    titles.forEach { title -> assertTrue("Missing featured title $title", descendants(first.itemView).filterIsInstance<TextView>().any { it.text.toString() == title }) }
                }
                screenshot("kobo-preview-home-$name")
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(1000)
            click(R.string.library)
            awaitCount(5)
            scenario.onActivity { assertTrue((shelf(it).layoutManager as GridLayoutManager).spanCount >= 2) }
            screenshot("kobo-preview-library-portrait")
            scenario.onActivity { a -> descendants(a.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString() == "⋯" }.performClick() }
            SystemClock.sleep(300)
            assertTrue(nodes(automation.rootInActiveWindow).any { it.text?.toString() == context.getString(R.string.choose_reader) })
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            fun search(query: String) {
                scenario.onActivity { a -> descendants(a.findViewById(android.R.id.content)).filterIsInstance<ImageButton>().single { it.contentDescription == a.getString(R.string.search_books) }.performClick() }
                SystemClock.sleep(250)
                val input = automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("${context.packageName}:id/search_input").single()
                assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query) }))
                dialogClick(R.string.search_action)
            }
            search("山海")
            awaitCount(2)
            scenario.onActivity { a -> assertTrue(descendants(shelf(a)).filterIsInstance<TextView>().any { it.text.toString() == "山海之間" }) }
            scenario.recreate()
            awaitCount(2)
            scenario.onActivity { a -> assertTrue(descendants(shelf(a)).filterIsInstance<TextView>().any { it.text.toString() == "山海之間" }) }
            search("special author")
            awaitCount(2)
            scenario.onActivity { a -> assertTrue(descendants(shelf(a)).filterIsInstance<TextView>().any { it.text.toString() == "日常的練習" }) }
            search("no-such-title")
            awaitCount(2)
            scenario.onActivity { a -> assertTrue(descendants(shelf(a)).filterIsInstance<TextView>().any { it.text == a.getString(R.string.no_matches) }) }
            click(R.string.show_all)
            awaitCount(5)
            report("READER_UI homeFourCovers=PASS fixedNavigation=PASS portraitLandscape=PASS font1.3=PASS grid=PASS readerMenu=PASS titleSearch=PASS authorSearch=PASS noMatches=PASS recreation=PASS showAll=PASS")
        } finally {
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            scenario.finishInApp()
            automation.serviceInfo = oldInfo
            AcceptanceActivity.repository = null
            AcceptanceActivity.testFontScale = 1f
            db.close()
        }
    }
}
