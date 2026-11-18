package org.kobostyle.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LaunchTest {
    @org.junit.Before fun installedPermissions() = installSignaturePermission()
    @Test fun activityStartsAndRecreatesWithoutSelectingHome() {
        val controller = org.robolectric.Robolectric.buildActivity(org.kobostyle.launcher.ui.MainActivity::class.java).setup()
        assertNotNull(controller.get().findViewById<android.view.View>(android.R.id.content))
        controller.recreate()
        assertFalse(controller.get().isFinishing)
        assertNull(org.robolectric.Shadows.shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }
    @Test fun launcherEntryWithOptInHomeAndNoPrivilegedPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.packageManager
        assertNotNull(manager.getLaunchIntentForPackage(context.packageName))
        val homes = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName), 0)
        assertTrue(homes.isEmpty())
        val disabledHomes = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName), PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals(listOf("org.kobostyle.launcher.ui.HomeEntry"), disabledHomes.map { it.activityInfo.name })
        assertFalse(disabledHomes.single().activityInfo.enabled)
        val activities = manager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
        assertEquals(listOf("org.kobostyle.launcher.ui.MainActivity"), activities.filter { it.exported }.map { it.name })
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, activities.single { it.name.endsWith(".ui.MainActivity") }.launchMode)
        val permissions = manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        for (forbidden in listOf("INTERNET", "MANAGE_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE")) assertFalse(permissions.contains("android.permission.$forbidden"))
    }
    @Test fun repeatedHomeIntentReturnsToHomeWithoutOpeningSystemUi() {
        val controller = org.robolectric.Robolectric.buildActivity(org.kobostyle.launcher.ui.MainActivity::class.java).setup()
        fun descendants(view: android.view.View): List<android.view.View> = listOf(view) + if (view is android.view.ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        descendants(controller.get().findViewById(android.R.id.content)).filterIsInstance<android.widget.Button>()
            .single { it.text == controller.get().getString(R.string.settings) }.performClick()
        val before = android.os.Bundle()
        controller.saveInstanceState(before)
        assertEquals("settings", before.getString("page"))
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        val after = android.os.Bundle()
        controller.saveInstanceState(after)
        assertEquals("home", after.getString("page"))
        assertNull(org.robolectric.Shadows.shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }
}
