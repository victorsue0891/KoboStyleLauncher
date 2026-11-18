package org.kobostyle.launcher

import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.LibraryDatabase
import org.kobostyle.launcher.testing.AcceptanceActivity

@RunWith(AndroidJUnit4::class)
class HardwareKeyAcceptanceTest {
    @Test fun observePhysicalKeys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("manualKeys") == "yes")
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LibraryDatabase::class.java).build()
        AcceptanceActivity.repository = LibraryRepository(instrumentation.targetContext, db.library())
        AcceptanceActivity.probeKeys = true
        AcceptanceActivity.keyCodes.clear()
        try {
            val scenario = ActivityScenario.launch(AcceptanceActivity::class.java)
            try {
                repeat(12) {
                    if (AcceptanceActivity.keyCodes.size < 2) {
                        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nKEY_PROBE waiting; received=${AcceptanceActivity.keyCodes.size}\n") })
                        SystemClock.sleep(10000)
                    }
                }
                val keys = AcceptanceActivity.keyCodes.toList()
                assertTrue("No pair of physical key events delivered", keys.size >= 2)
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPHYSICAL_KEYS codes=$keys\n") })
            } finally { scenario.finishInApp() }
        } finally {
            AcceptanceActivity.probeKeys = false
            AcceptanceActivity.repository = null
            db.close()
        }
    }
}
