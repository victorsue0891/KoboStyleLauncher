package org.kobostyle.launcher.librarian

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.kobostyle.launcher.R
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.installSignaturePermission
import org.kobostyle.launcher.ui.LibraryViewModel
import org.kobostyle.launcher.ui.MainActivity
import org.kobostyle.launcher.ui.ReaderIcon
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

class LibrarianTestActivity : MainActivity() {
    override fun libraryModelFactory(): ViewModelProvider.Factory = factory { LibraryViewModel(application, repository) }
    override fun librarianModelFactory(): ViewModelProvider.Factory = factory { librarianModel }
    private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
    companion object { lateinit var repository: LibraryRepository; lateinit var librarianModel: LibrarianViewModel }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "zh-rTW-w674dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibrarianUiTest {
    private lateinit var db: LibraryDatabase
    private lateinit var controller: ActivityController<LibrarianTestActivity>
    private lateinit var model: LibrarianViewModel
    private var networkCalls = 0
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun root() = controller.get().findViewById<View>(android.R.id.content)
    private fun measure() {
        val view = root()
        view.measure(View.MeasureSpec.makeMeasureSpec(1264, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1680, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1264, 1680)
    }
    private fun pump() { Shadows.shadowOf(Looper.getMainLooper()).idle(); measure() }
    private fun press(label: String) {
        pump()
        descendants(root()).filterIsInstance<Button>().single { it.text.toString() == label && it.isShown }.performClick()
        pump()
    }
    @Before fun setup() {
        installSignaturePermission()
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { repeat(20) { db.library().index(sampleBook(it)) } }
        val repository = LibraryRepository(app, db.library())
        val prefs = AiPreferences(app.getSharedPreferences("ui-ai-test", 0).apply { edit().clear().commit() })
        val vault = FakeVault(null)
        val service = CuratorService(prefs, vault, CuratorTransport { _, _ -> networkCalls++; error("No live API") }, { false })
        model = LibrarianViewModel(repository, prefs, vault, service) { true }
        LibrarianTestActivity.repository = repository; LibrarianTestActivity.librarianModel = model
        controller = Robolectric.buildActivity(LibrarianTestActivity::class.java).setup().visible()
        pump()
    }
    @After fun teardown() { controller.pause().stop().destroy(); Shadows.shadowOf(Looper.getMainLooper()).idle(); db.close() }
    private fun shot(name: String) {
        pump()
        val bitmap = Bitmap.createBitmap(1264, 1680, Bitmap.Config.ARGB_8888)
        root().draw(Canvas(bitmap))
        val dir = File("build/reports/librarian-preview").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun navigationMovesAppsIntoMoreAndCatIsThirdTab() {
        val tabs = descendants(root()).filterIsInstance<Button>().filter { it.tag is ReaderIcon.Kind }
        assertEquals(listOf(ReaderIcon.Kind.HOME, ReaderIcon.Kind.BOOKS, ReaderIcon.Kind.CAT, ReaderIcon.Kind.MORE), tabs.map { it.tag })
        assertFalse(tabs.any { it.text == controller.get().getString(R.string.apps) })
        press(controller.get().getString(R.string.settings)); press(controller.get().getString(R.string.apps))
        controller.get().onBackPressedDispatcher.onBackPressed(); pump()
        val state = android.os.Bundle(); controller.saveInstanceState(state); assertEquals("settings", state.getString("page"))
        assertTrue(descendants(root()).filterIsInstance<Button>().any { it.text == controller.get().getString(R.string.home_recovery) })
    }
    @Test fun twentyBookOfflineFlowCardsRecreationAndSixStaticImages() {
        press(controller.get().getString(R.string.librarian)); shot("welcome")
        assertEquals(CatExpression.entries.size, CatExpression.entries.map(LibrarianPanel::expressionResource).distinct().size)
        for (expression in CatExpression.entries) {
            val bitmap = android.graphics.BitmapFactory.decodeResource(controller.get().resources, LibrarianPanel.expressionResource(expression))
            assertEquals(320, bitmap.width); assertEquals(320, bitmap.height); bitmap.recycle()
        }
        for (label in listOf("陪我選一本", "輕鬆一下", "15 分鐘", "優先尚未從書房開啟", "這次只用離線選書")) {
            val choices = controller.get().findViewById<ViewGroup>(R.id.librarian_choices)
            assertTrue(choices.childCount in 1..4); press(label)
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (model.state.value.stage == DialogueStage.LOADING && System.currentTimeMillis() < deadline) { Thread.sleep(20); pump() }
        assertEquals(DialogueStage.CARDS, model.state.value.stage); assertEquals(3, model.state.value.result!!.cards.size)
        shot("offline-cards"); press("下一本")
        assertEquals(1, model.state.value.index)
        controller.recreate(); pump(); assertEquals(1, model.state.value.index)
        press("看看這本"); assertEquals(DialogueStage.DETAIL, model.state.value.stage)
        press("就讀這本"); assertEquals(DialogueStage.CONFIRM, model.state.value.stage); shot("confirmation")
        val cat = controller.get().findViewById<ImageView>(R.id.librarian_character)
        assertEquals(LibrarianPanel.expressionDescription(CatExpression.HAPPY), cat.contentDescription)
        assertNull(Shadows.shadowOf(controller.get()).nextStartedActivity)
        assertEquals(0, networkCalls)
        press("重新開始"); assertEquals(DialogueState(), model.state.value)
    }
    @Test fun keyIsAskedOnlyAfterOptInAndNeverSavedInViewState() {
        press(controller.get().getString(R.string.librarian)); press("AI 設定")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val input = dialog.findViewById<EditText>(R.id.librarian_api_key)
        assertFalse(input.isShown); assertFalse(input.isSaveEnabled)
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().single().isChecked = true
        assertEquals(View.VISIBLE, (input.parent as View).visibility)
        assertFalse(model.preferences.config().enabled)
        input.setText("test-key-not-real-123"); dialog.dismiss(); pump(); assertEquals("", input.text.toString())
        assertEquals(0, networkCalls)
    }
    @Test fun freeTextRejectsOverLimitWithoutStartingRequest() {
        press(controller.get().getString(R.string.librarian)); press("我想自己說")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val input = dialog.findViewById<EditText>(R.id.librarian_query)
        input.setText("書".repeat(201)); dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing); assertNotNull(input.error); assertEquals(DialogueStage.WELCOME, model.state.value.stage)
        input.setText("輕鬆的故事"); dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick(); pump()
        assertEquals(DialogueStage.CANDIDATES, model.state.value.stage); assertEquals(0, networkCalls)
    }
    @Test fun landscapeLargeTextKeepsAllChoicesReachable() {
        val config = android.content.res.Configuration(controller.get().resources.configuration).apply {
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 896; screenHeightDp = 674; fontScale = 1.3f
        }
        val context = controller.get().createConfigurationContext(config)
        val panel = LibrarianPanel(context, { _, _ -> }, {})
        val choice = Choice(minutes = 30)
        val books = (0..2).map { sampleBook(it).copy(title = "較長的合成書名，用來驗證大字與橫向畫面的換行能力 ".repeat(3)) }
        panel.render(DialogueState(stage = DialogueStage.CARDS, result = LocalCurator.offline(books, choice)), AiConfig())
        panel.measure(View.MeasureSpec.makeMeasureSpec(1680, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1264, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, 1680, 1264)
        val choices = panel.findViewById<ViewGroup>(R.id.librarian_choices)
        assertEquals(4, choices.childCount)
        for (button in descendants(choices).filterIsInstance<Button>()) {
            assertTrue(button.height >= (48 * context.resources.displayMetrics.density).toInt())
            assertTrue(button.right <= choices.width)
            assertEquals(0, button.layout.getEllipsisCount(0))
        }
        val before = panel.scrollY
        panel.scrollTo(0, panel.getChildAt(0).height)
        assertTrue(panel.scrollY > before)
        assertEquals(panel.getChildAt(0).height - panel.height, panel.scrollY)
        panel.scrollTo(0, 0)
        val bitmap = Bitmap.createBitmap(1680, 1264, Bitmap.Config.ARGB_8888)
        panel.setBackgroundColor(android.graphics.Color.WHITE); panel.draw(Canvas(bitmap))
        val dir = File("build/reports/librarian-preview").apply { mkdirs() }
        File(dir, "landscape-large-text.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
