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

/** Isolated synthetic library: no real files, grants, open events, or reader launches. */
@RunWith(AndroidJUnit4::class)
class CategoryUiAcceptanceTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun nodes(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(n) +
        (0 until n.childCount).flatMap { n.getChild(it)?.let(::nodes).orEmpty() }

    @Test fun categoriesAuthorsSearchBackAndRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val filenames = listOf("[小說]-合成故事甲-作者甲.epub", "[心理]-合成心理-作者甲.pdf", "[小說]-合成故事乙-作者乙.pdf", "plain.pdf")
        runBlocking { filenames.forEachIndexed { i, name ->
            db.library().index(Book("category-$i", "content://synthetic/$i", "synthetic", "$i", "合成測試 $i", "",
                "application/pdf", 0, 0, displayName = name))
        } }
        AcceptanceActivity.repository = LibraryRepository(context, db.library())
        AcceptanceActivity.testFontScale = 1.3f
        val automation = instrumentation.uiAutomation
        val oldInfo = automation.serviceInfo
        automation.serviceInfo = AccessibilityServiceInfo().apply { flags = oldInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; eventTypes = oldInfo.eventTypes; feedbackType = oldInfo.feedbackType }
        val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
        fun shelf(a: AcceptanceActivity) = a.findViewById<RecyclerView>(R.id.shelf)
        fun awaitRows(count: Int) {
            var ready = false
            repeat(80) { if (!ready) { SystemClock.sleep(100); scenario.onActivity { ready = shelf(it).adapter?.itemCount == count && !shelf(it).isLayoutRequested } } }
            assertTrue("Expected $count rows", ready)
            instrumentation.waitForIdleSync()
        }
        fun click(id: Int) = scenario.onActivity { a -> views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text == a.getString(id) }.performClick() }
        fun rowButton(position: Int, action: (Button) -> Unit) {
            scenario.onActivity { shelf(it).scrollToPosition(position) }
            var ready = false
            repeat(40) { if (!ready) { SystemClock.sleep(50); scenario.onActivity { ready = shelf(it).findViewHolderForAdapterPosition(position) != null } } }
            assertTrue(ready)
            scenario.onActivity { a -> action(views(shelf(a).findViewHolderForAdapterPosition(position)!!.itemView).filterIsInstance<Button>().single()) }
        }
        fun shot() {
            scenario.onActivity { shelf(it).scrollToPosition(0) }
            SystemClock.sleep(300)
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.getExternalFilesDir(null), "category-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            awaitRows(3)
            scenario.onActivity { a -> views(shelf(a)).single { it.isClickable && it.contentDescription?.toString()?.startsWith(a.getString(R.string.book_categories) + " · ") == true }.performClick() }
            awaitRows(15)
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                scenario.onActivity { it.requestedOrientation = orientation }
                SystemClock.sleep(700)
                awaitRows(15)
                context.resources.getStringArray(R.array.book_category_labels).forEachIndexed { i, label ->
                    rowButton(i + 2) { button ->
                        assertTrue(button.text.toString().startsWith(label + "  ·  "))
                        assertTrue(button.height >= 48 * button.resources.displayMetrics.density)
                    }
                }
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(700)
            shot()
            rowButton(2) { it.performClick() } // Fiction: only the two matching books.
            awaitRows(3)
            scenario.recreate()
            awaitRows(3)
            scenario.onActivity { a -> assertTrue(views(shelf(a)).filterIsInstance<TextView>().any { it.text.toString().startsWith("小說  ·  2") }) }
            click(R.string.back_to_parent)
            awaitRows(15)
            rowButton(5) { it.performClick() } // Empty leisure/travel category.
            awaitRows(2)
            click(R.string.back_to_parent)
            awaitRows(15)
            rowButton(1) { it.performClick() }
            awaitRows(4) // Header + two named authors + unknown author.
            scenario.onActivity { a -> views(a.findViewById(android.R.id.content)).filterIsInstance<ImageButton>().single { it.contentDescription == a.getString(R.string.search_books) }.performClick() }
            SystemClock.sleep(300)
            val input = automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("${context.packageName}:id/search_input").single()
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "作者甲") }))
            assertTrue(nodes(automation.rootInActiveWindow).single { it.isClickable && it.text?.toString() == context.getString(R.string.search_action) }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitRows(2)
            rowButton(1) { assertTrue(it.text.toString().contains("2")); it.performClick() }
            awaitRows(3)
            scenario.recreate()
            awaitRows(3)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitRows(2) // Author query survives the return.
            click(R.string.back_to_parent)
            awaitRows(15)
            rowButton(14) { it.performClick() } // Uncategorized is still accessible.
            awaitRows(2)
            click(R.string.back_to_parent)
            awaitRows(15)
            rowButton(13) { it.performClick() }
            awaitRows(5)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nCATEGORIES requestedButtons=PASS scopedBooks=PASS authorSearch=PASS unknown=PASS empty=PASS back=PASS recreation=PASS portraitLandscapeFont1.3=PASS allBooks=PASS\n") })
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
