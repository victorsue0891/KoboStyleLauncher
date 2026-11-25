package org.kobostyle.launcher.spirits

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.installSignaturePermission
import org.kobostyle.launcher.librarian.*
import org.kobostyle.launcher.ui.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

class SpiritTestActivity : MainActivity() {
    override fun spiritRuntime() = runtime
    override fun libraryModelFactory(): ViewModelProvider.Factory = factory { libraryModels++; LibraryViewModel(application, repository) }
    override fun librarianModelFactory(): ViewModelProvider.Factory = factory {
        val prefs = AiPreferences(application.getSharedPreferences("librarian_ai", 0)).apply { save(AiConfig(true)) }
        val vault = FakeVault()
        val service = CuratorService(prefs, vault, CuratorTransport { _, _ -> calls++; error("Unexpected API") }, { true })
        LibrarianViewModel(repository, prefs, vault, service) { true }
    }
    private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
    companion object { lateinit var runtime: SpiritRuntime; lateinit var repository: LibraryRepository; var calls = 0; var libraryModels = 0 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "zh-rTW-w674dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpiritUiTest {
    private lateinit var library: LibraryDatabase
    private lateinit var db: SpiritDatabase
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Before fun setup() {
        installSignaturePermission()
        val app = ApplicationProvider.getApplicationContext<Application>()
        library = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).addCallback(LibraryDatabase.TRACKING).allowMainThreadQueries().build()
        db = Room.inMemoryDatabaseBuilder(app, SpiritDatabase::class.java).setDriver(BundledSQLiteDriver()).addCallback(SpiritDatabase.SEARCH).build()
        SpiritTestActivity.repository = LibraryRepository(app, library.library()); SpiritTestActivity.calls = 0; SpiritTestActivity.libraryModels = 0
        SpiritTestActivity.runtime = SpiritRuntime(app, db, CuratorTransport { _, _ -> SpiritTestActivity.calls++; error("Unexpected API") }).also { it.settings.enabled = true; it.settings.paidSpirits = true }
    }
    @After fun close() { db.close(); library.close() }
    private fun seed(count: Int) = runBlocking {
        val raw = library.openHelper.writableDatabase; raw.beginTransaction()
        try {
            repeat(count) { i -> raw.execSQL("INSERT INTO books VALUES(?,?,?,?,?,?,?,1,10,0,NULL,'ok','','')", arrayOf("id-%05d".format(i), "content://test/$i", "source", "doc$i", "Synthetic $i", "Author", "application/pdf")) }
            raw.setTransactionSuccessful()
        } finally { raw.endTransaction() }
        PagedCatalog(library.library(), db.dao()).sync()
    }
    private fun pump(root: View) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(1264, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1680, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1264, 1680)
    }
    private fun zeroLaunches(count: Int) {
        seed(count)
        repeat(10) {
            val controller = Robolectric.buildActivity(SpiritTestActivity::class.java).setup().visible()
            val root = controller.get().findViewById<View>(android.R.id.content)
            pump(root)
            all(root).filterIsInstance<Button>().single { it.text.toString() == "喵館員" }.performClick(); pump(root)
            all(root).filterIsInstance<Button>().single { it.text.toString() == "陪我選一本" }.performClick(); pump(root)
            controller.pause().stop().start().resume(); pump(root)
            controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); pump(root)
            controller.recreate(); pump(controller.get().findViewById(android.R.id.content))
            assertEquals(0, SpiritTestActivity.calls)
            assertNull(Shadows.shadowOf(controller.get()).nextStartedActivity)
            controller.pause().stop().destroy(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(0, SpiritTestActivity.calls)
    }
    @Test fun zeroBooksTenLaunchResumeHomeRecreationsNoApi() = zeroLaunches(0)
    @Test fun threeHundredBooksTenLaunchResumeHomeRecreationsNoApi() = zeroLaunches(300)
    @Test fun thirteenThousandBooksTenLaunchResumeHomeRecreationsNoApi() = zeroLaunches(13618)
    @Test fun twentyThousandBooksTenLaunchResumeHomeRecreationsNoApi() = zeroLaunches(20000)
    @Test fun rotationRebindsConsentAndSettingsRecreateTheLibraryMode() = runBlocking {
        seed(10)
        val controller = Robolectric.buildActivity(SpiritTestActivity::class.java).setup().visible()
        fun root() = controller.get().findViewById<View>(android.R.id.content)
        fun awaitUi(done: () -> Boolean) {
            repeat(300) { pump(root()); if (done()) return; Thread.sleep(10) }
            error("UI did not settle")
        }
        fun click(text: String) {
            awaitUi { all(root()).filterIsInstance<Button>().any { it.text.toString() == text && it.isShown } }
            all(root()).filterIsInstance<Button>().first { it.text.toString() == text && it.isShown }.performClick()
        }
        try {
            controller.recreate(); pump(root())
            assertEquals(1, SpiritTestActivity.libraryModels) // Ordinary rotation retains data work.
            val consent = async(Dispatchers.IO) { SpiritTestActivity.runtime.confirm(CostPreview("ask", "fake-model", 20, 300, 1, "2026-11-25")) }
            awaitUi { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            awaitUi { consent.isCompleted }; assertTrue(consent.await())
            fun switchMode(enabled: Boolean) {
                click("更多"); click("書靈與費用設定"); pump(root())
                val dialog = ShadowAlertDialog.getLatestAlertDialog()
                all(dialog.window!!.decorView).filterIsInstance<CheckBox>().single { it.text.toString() == "啟用 V4 書靈與分頁書庫" }.isChecked = enabled
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
                assertEquals(enabled, SpiritTestActivity.runtime.settings.enabled)
                controller.recreate(); pump(root())
            }
            switchMode(false); assertEquals(2, SpiritTestActivity.libraryModels)
            click("首頁")
            awaitUi { all(root()).filterIsInstance<TextView>().any { it.text.toString() == "Synthetic 0" && it.isShown } }
            switchMode(true); assertEquals(3, SpiritTestActivity.libraryModels)
            click("喵館員"); click("書靈小屋 · 選一本書")
            awaitUi { all(root()).filterIsInstance<TextView>().any { it.text.toString() == "Synthetic 0" && it.isShown } }
            assertEquals(0, SpiritTestActivity.calls)
        } finally { controller.pause().stop().destroy(); Shadows.shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun summonThroughBookMenuAndFeatureRollback() = runBlocking {
        seed(10)
        val controller = Robolectric.buildActivity(SpiritTestActivity::class.java).setup().visible()
        fun root() = controller.get().findViewById<View>(android.R.id.content)
        fun waitFor(label: String): Button {
            repeat(200) {
                pump(root())
                all(root()).filterIsInstance<Button>().firstOrNull { it.text.toString() == label && it.isShown }?.let { return it }
                Thread.sleep(10)
            }
            error("Missing button: $label")
        }
        fun shot(name: String) {
            pump(root()); val bitmap = Bitmap.createBitmap(1264, 1680, Bitmap.Config.ARGB_8888); root().draw(Canvas(bitmap))
            val dir = File("build/reports/v4-preview").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        try {
            waitFor("喵館員").performClick(); shot("welcome")
            waitFor("書靈小屋 · 選一本書").performClick()
            waitFor("⋯").performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(dialog.listView.getChildAt(2), 2, 2)
            waitFor("免費建立 Lite 書靈").performClick()
            waitFor("這章重點"); shot("lite")
            waitFor("這章重點").performClick()
            repeat(30) { pump(root()); Thread.sleep(10) }
            assertEquals(0, SpiritTestActivity.calls)
            assertNotNull(db.dao().spirit("id-00000"))
            controller.recreate(); waitFor("這章重點")
            SpiritTestActivity.runtime.settings.enabled = false
            controller.recreate(); pump(root())
            assertFalse(all(root()).filterIsInstance<Button>().any { it.text.toString() == "書靈小屋 · 選一本書" && it.isShown })
            assertNotNull(db.dao().spirit("id-00000")); assertEquals(10, library.library().count())
        } finally { controller.pause().stop().destroy(); Shadows.shadowOf(Looper.getMainLooper()).idle() }
    }
}
