package org.kobostyle.launcher

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals

/** Finish our Activity directly: BOOX may freeze the separate test package's EmptyActivity. */
fun <A : Activity> ActivityScenario<A>.finishInApp() {
    if (state != Lifecycle.State.DESTROYED) onActivity { it.finish() }
    repeat(40) { if (state != Lifecycle.State.DESTROYED) SystemClock.sleep(50) }
    assertEquals("Acceptance Activity did not finish", Lifecycle.State.DESTROYED, state)
    close()
}
