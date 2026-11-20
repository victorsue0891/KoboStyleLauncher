package org.kobostyle.launcher

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.librarian.*
import org.kobostyle.launcher.reader.ReaderResolver
import org.kobostyle.launcher.testing.AcceptanceActivity
import java.io.File

/** Reads only the previously authorized synthetic fixtures; production DB/key/settings are untouched. */
@RunWith(AndroidJUnit4::class)
class LibrarianDeviceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$value\n") })
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun nodes(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(n) + (0 until n.childCount).flatMap { n.getChild(it)?.let(::nodes).orEmpty() }
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
        assertTrue(label, condition())
    }
    private fun shot(name: String) {
        SystemClock.sleep(500)
        val bitmap = checkNotNull(automation.takeScreenshot())
        File(context.getExternalFilesDir(null), "cat-device-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun key(code: Int) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            assertTrue(automation.injectInputEvent(KeyEvent(time, SystemClock.uptimeMillis(), action, code, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD), true))
        }
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    @Test fun syntheticLibraryNavigationOfflineCardsAndOptionalReaderBridge() {
        val grant = context.contentResolver.persistedUriPermissions.single { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) &&
            DocumentsContract.getTreeDocumentId(it.uri) == "primary:Download/KoboStyleAcceptance-20261115/valid" }
        val files = DocumentFile.fromTreeUri(context, grant.uri)!!.listFiles().filter { it.name?.matches(Regex("Acceptance-(EPUB|PDF)-[0-9]{2}\\.(epub|pdf)")) == true }
        assertEquals(20, files.size)
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val books = files.map { file ->
            val parts = file.name!!.split('-', '.')
            Book("cat-${parts[2]}-${parts[1]}", file.uri.toString(), grant.uri.toString(), DocumentsContract.getDocumentId(file.uri),
                "合成 ${parts[1]} ${parts[2]}", "合成測試作者", if (parts[1] == "EPUB") "application/epub+zip" else "application/pdf", file.lastModified(), file.length(),
                displayName = "[小說]-合成${parts[1]}${parts[2]}-合成測試作者.${parts[3]}")
        }
        runBlocking { books.forEach { db.library().index(it) } }
        val repository = LibraryRepository(context, db.library())
        val prefs = AiPreferences(context.getSharedPreferences("acceptance_librarian", 0).apply { edit().clear().commit() })
        val vault = object : KeyVault { override fun exists() = false; override fun read(): String? = null; override fun save(value: String) = error("No real key in offline tests"); override fun delete() = Unit }
        var networkCalls = 0
        val model = LibrarianViewModel(repository, prefs, vault, CuratorService(prefs, vault, CuratorTransport { _, _ -> networkCalls++; error("Offline test must not call API") }, { false }), includeAcceptanceBooks = true) {
            context.contentResolver.openFileDescriptor(Uri.parse(it.uri), "r")?.use { true } == true
        }
        AcceptanceActivity.repository = repository
        AcceptanceActivity.librarianFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
        }
        AcceptanceActivity.testFontScale = 1.3f
        val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
        fun press(label: String) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { a -> views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text.toString() == label && it.isShown }.performClick() }
            instrumentation.waitForIdleSync()
        }
        fun stage(expected: DialogueStage) = await("Expected $expected") { model.state.value.stage == expected }
        var failure: Throwable? = null
        try {
            press(context.getString(R.string.settings)); press(context.getString(R.string.apps))
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            press(context.getString(R.string.librarian)); stage(DialogueStage.WELCOME)
            shot("welcome")
            for (label in listOf("陪我選一本", "輕鬆一下", "15 分鐘", "優先尚未從書房開啟", "這次只用離線選書")) press(label)
            stage(DialogueStage.CARDS); assertEquals(3, model.state.value.result!!.cards.size)
            assertEquals("offline", model.state.value.result!!.source); assertEquals(0, networkCalls)
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                scenario.onActivity { it.requestedOrientation = orientation }; SystemClock.sleep(800)
                scenario.onActivity { a ->
                    val panel = a.findViewById<LibrarianPanel>(R.id.librarian_panel)
                    val choices = a.findViewById<ViewGroup>(R.id.librarian_choices)
                    assertEquals(4, choices.childCount)
                    views(choices).filterIsInstance<Button>().forEach { assertTrue(it.height >= 48 * a.resources.displayMetrics.density) }
                    panel.scrollTo(0, panel.getChildAt(0).height)
                    assertTrue(panel.scrollY + panel.height >= panel.getChildAt(0).height)
                    panel.scrollTo(0, 0)
                }
                shot(if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) "cards-portrait" else "cards-landscape")
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }; SystemClock.sleep(700)
            press("下一本"); assertEquals(1, model.state.value.index)
            scenario.recreate(); stage(DialogueStage.CARDS); assertEquals(1, model.state.value.index)
            scenario.onActivity { assertTrue(it.acceptsKeyForTest(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))) }
            assertEquals(2, model.state.value.index)
            press("上一本"); press("上一本")
            press("看看這本"); stage(DialogueStage.DETAIL); press("就讀這本"); stage(DialogueStage.CONFIRM)
            scenario.onActivity { assertEquals(LibrarianPanel.expressionDescription(CatExpression.HAPPY), it.findViewById<ImageView>(R.id.librarian_character).contentDescription) }
            assertEquals(0, networkCalls)
            assertTrue(runBlocking { db.library().observeBooks().first().all { it.lastOpenedAt == 0L } })
            report("CAT_UI fixtures=20 offlineCards=3 appsUnderMore=PASS recreation=PASS font1.3PortraitLandscape=PASS standardPageKey=PASS networkCalls=0")
            if (InstrumentationRegistry.getArguments().getString("openReaders") == "true") {
                // Procedure must be shown to the user before explicitly enabling this argument.
                for (index in 0..2) {
                    if (index > 0) { press("返回"); press("返回"); press("下一本"); press("看看這本"); press("就讀這本") }
                    val card = model.state.value.card!!.book
                    val handlers = ReaderResolver(context).discover(Uri.parse(card.uri), card.mime)
                    assertEquals("Expected a single verified handler for synthetic fixture", 1, handlers.size)
                    val handler = handlers.single()
                    if (index == 2) {
                        press("使用其他閱讀器"); SystemClock.sleep(600)
                        key(KeyEvent.KEYCODE_BACK); SystemClock.sleep(600)
                        assertEquals(0L, runBlocking { db.library().book(card.identity)!!.lastOpenedAt })
                        press("使用其他閱讀器")
                        fun matches(n: AccessibilityNodeInfo): Boolean {
                            val text = n.text?.toString().orEmpty().trimEnd('.', '…', ' ')
                            return text.length >= 4 && handler.label.startsWith(text)
                        }
                        await("Chooser label") { automation.rootInActiveWindow?.let(::nodes)?.any(::matches) == true }
                        // The OEM chooser truncates labels and may ignore accessibility ACTION_CLICK.
                        tap(nodes(automation.rootInActiveWindow).first(::matches))
                    } else press("開始閱讀")
                    await("Reader foreground") { automation.rootInActiveWindow?.packageName?.toString() == handler.component.packageName }
                    SystemClock.sleep(8000)
                    // This previously exercised two-page PDF resumes on page 2. Move back within
                    // the synthetic fixture before testing next-page, avoiding the OEM end-of-book overlay.
                    if (card.mime == "application/pdf") { key(KeyEvent.KEYCODE_PAGE_UP); SystemClock.sleep(700) }
                    shot("reader-$index-before")
                    key(KeyEvent.KEYCODE_PAGE_DOWN)
                    SystemClock.sleep(700); shot("reader-$index-after")
                    key(KeyEvent.KEYCODE_BACK)
                    await("Return to launcher") { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
                    stage(DialogueStage.CONFIRM)
                    assertTrue(runBlocking { db.library().book(card.identity)!!.lastOpenedAt > 0L })
                    report("CAT_READER index=$index mime=${card.mime} handler=${handler.component.packageName} returned=PASS openEvent=PASS screenshots=PENDING_VISUAL_REVIEW")
                }
            }
            press("重新開始"); stage(DialogueStage.WELCOME)
            assertEquals(0, networkCalls)
        } catch (e: Throwable) { failure = e; throw e } finally {
            model.leavePage()
            try {
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                scenario.finishInApp()
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
            AcceptanceActivity.repository = null; AcceptanceActivity.librarianFactory = null; AcceptanceActivity.testFontScale = 1f
            db.close()
        }
    }
}
