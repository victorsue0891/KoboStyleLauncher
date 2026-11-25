package org.kobostyle.launcher.testing

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.ui.LibraryViewModel
import org.kobostyle.launcher.ui.MainActivity

/** Debug-only, non-exported harness. No intent extras can supply a repository or override settings. */
class AcceptanceActivity : MainActivity() {
    private val isolatedRuntime by lazy {
        spiritTestRuntime ?: org.kobostyle.launcher.spirits.SpiritRuntime(application,
            androidx.room.Room.inMemoryDatabaseBuilder(application, org.kobostyle.launcher.spirits.SpiritDatabase::class.java)
                .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver()).addCallback(org.kobostyle.launcher.spirits.SpiritDatabase.SEARCH).build(),
            org.kobostyle.launcher.librarian.CuratorTransport { _, _ -> error("No network in acceptance Activity") },
            org.kobostyle.launcher.spirits.SpiritSettings(getSharedPreferences("acceptance_v4_legacy", 0)))
    }
    override fun spiritRuntime() = isolatedRuntime
    override fun librarianModelFactory(): ViewModelProvider.Factory = librarianFactory ?: super.librarianModelFactory()
    override fun controlPreferences(): android.content.SharedPreferences = getSharedPreferences("acceptance_controls", MODE_PRIVATE)
    fun acceptsKeyForTest(event: android.view.KeyEvent): Boolean = handleContentKey(event)
    private fun probing(code: Int) = probeKeys && code !in listOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_HOME, android.view.KeyEvent.KEYCODE_POWER)
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // Public Window.Callback sees events before PhoneWindow consumes volume keys.
        val original = window.callback
        window.callback = object : android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (probing(event.keyCode)) {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) keyCodes.add(event.keyCode)
                    return true
                }
                if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                    event.keyCode in listOf(android.view.KeyEvent.KEYCODE_PAGE_UP, android.view.KeyEvent.KEYCODE_PAGE_DOWN)) keyCodes.add(event.keyCode)
                return original.dispatchKeyEvent(event)
            }
        }
    }
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply { fontScale = testFontScale }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun libraryModelFactory() = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass == LibraryViewModel::class.java)
            return LibraryViewModel(application, checkNotNull(repository), isolatedRuntime.settings.enabled) as T
        }
    }
    companion object {
        var spiritTestRuntime: org.kobostyle.launcher.spirits.SpiritRuntime? = null
        var librarianFactory: ViewModelProvider.Factory? = null
        var repository: LibraryRepository? = null
        var testFontScale = 1f
        @Volatile var probeKeys = false
        val keyCodes = java.util.concurrent.CopyOnWriteArrayList<Int>()
    }
}
