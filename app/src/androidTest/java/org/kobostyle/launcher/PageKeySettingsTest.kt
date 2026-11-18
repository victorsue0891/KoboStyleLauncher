package org.kobostyle.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

@RunWith(AndroidJUnit4::class)
class PageKeySettingsTest {
    @Test fun volumeKeysOptInReversePersistAndDoNotChangeAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("acceptance_controls", 0)
        preferences.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        runBlocking {
            repeat(50) { i -> db.library().index(Book("key-$i", "content://synthetic/$i", "synthetic", "$i", "Book %03d".format(i), "", "application/pdf", 0, 0)) }
        }
        AcceptanceActivity.repository = LibraryRepository(context, db.library())
        val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
        fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        fun click(id: Int) = scenario.onActivity { a ->
            descendants(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text == a.getString(id) }.performClick()
        }
        fun shelf(a: AcceptanceActivity) = descendants(a.findViewById(android.R.id.content)).filterIsInstance<RecyclerView>().single()
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val streams = listOf(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_RING, android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.STREAM_NOTIFICATION)
        val levels = streams.map { audio.getStreamVolume(it) }
        try {
            scenario.onActivity { assertFalse(it.acceptsKeyForTest(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))) }
            click(R.string.settings)
            click(R.string.volume_paging_off)
            assertTrue(preferences.getBoolean("volume_paging", false))
            scenario.onActivity { assertFalse(it.acceptsKeyForTest(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))) } // Settings keeps ordinary behavior
            click(R.string.library)
            var ready = false
            repeat(50) {
                if (!ready) {
                    android.os.SystemClock.sleep(100)
                    scenario.onActivity { ready = shelf(it).adapter!!.itemCount == 51 }
                }
            }
            assertTrue("Test library did not load", ready)
            instrumentation.waitForIdleSync()
            scenario.onActivity { (shelf(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0) }
            instrumentation.waitForIdleSync()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue(shelf(it).canScrollVertically(-1)) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertFalse(shelf(it).canScrollVertically(-1)) }
            click(R.string.settings)
            click(R.string.page_direction_normal)
            scenario.recreate()
            assertTrue(preferences.getBoolean("reverse_paging", false))
            click(R.string.library)
            instrumentation.waitForIdleSync()
            scenario.onActivity { (shelf(it).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0) }
            instrumentation.waitForIdleSync()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue(shelf(it).canScrollVertically(-1)) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertFalse(shelf(it).canScrollVertically(-1)) }
            assertEquals(levels, streams.map { audio.getStreamVolume(it) })
            click(R.string.settings)
            click(R.string.volume_paging_on)
            click(R.string.library)
            scenario.onActivity { assertFalse(it.acceptsKeyForTest(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPAGE_KEY_SETTINGS optIn=PASS reverse=PASS recreation=PASS disable=PASS audioUnchanged=PASS\n") })
        } finally {
            scenario.finishInApp()
            preferences.edit().clear().commit()
            AcceptanceActivity.repository = null
            db.close()
        }
    }
}
