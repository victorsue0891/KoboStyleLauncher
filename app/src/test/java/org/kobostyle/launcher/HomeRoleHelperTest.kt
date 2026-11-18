package org.kobostyle.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.platform.HomeRoleHelper
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeRoleHelperTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.packageManager
    private val shadow = Shadows.shadowOf(manager)
    private val helper = HomeRoleHelper(context)
    private fun home() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private fun handler(pkg: String, name: String = "$pkg.Home") = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg; this.name = name; enabled = true; exported = true
            applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
        }
    }
    private fun settings() { shadow.addResolveInfoForIntent(Intent(Settings.ACTION_HOME_SETTINGS), handler("test.settings")) }
    @Test fun unavailableRecoveryOrSettingsNeverEnablesCandidate() {
        assertFalse(helper.enableCandidate())
        settings()
        assertFalse(helper.enableCandidate())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, manager.getComponentEnabledSetting(helper.entry))
        assertNull(helper.originalIntent())
    }
    @Test fun explicitOptInPreservesVerifiedHomeAndOnlyEnablesOurAlias() {
        val original = handler("test.original")
        shadow.addResolveInfoForIntent(home(), original)
        settings()
        assertTrue(helper.rememberOriginal())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, manager.getComponentEnabledSetting(helper.entry))
        assertTrue(helper.enableCandidate())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, manager.getComponentEnabledSetting(helper.entry))
        assertEquals(ComponentName("test.original", "test.original.Home"), helper.originalIntent()!!.component)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, manager.getComponentEnabledSetting(helper.originalIntent()!!.component!!))
        assertFalse(helper.isDefault()) // enabling eligibility does not set the preferred HOME
        assertEquals(Settings.ACTION_HOME_SETTINGS, helper.settingsIntent()!!.action)
    }
    @Test fun unavailableSavedHomeIsNeverLaunchedOrUsedForOptIn() {
        context.getSharedPreferences("home_recovery", 0).edit().putString("original_home", "missing.home/.Home").commit()
        settings()
        assertNull(helper.originalIntent())
        assertFalse(helper.enableCandidate())
    }
    @Test fun settingsFallbackAndPrivateHomeAreNotRecoveryTargets() {
        val fallback = handler("test.settings")
        shadow.addResolveInfoForIntent(home(), fallback)
        settings()
        assertFalse(helper.rememberOriginal())
        shadow.removeResolveInfosForIntent(home(), "test.settings")
        shadow.addResolveInfoForIntent(home(), handler("test.private").apply { activityInfo.exported = false })
        assertFalse(helper.rememberOriginal())
    }
    @Test fun becomingDefaultDoesNotOverwriteOriginalHome() {
        shadow.addResolveInfoForIntent(home(), handler("test.original"))
        assertTrue(helper.rememberOriginal())
        shadow.removeResolveInfosForIntent(home(), "test.original")
        shadow.addResolveInfoForIntent(home(), handler(context.packageName, helper.entry.className))
        shadow.addResolveInfoForIntent(home(), handler("test.original"))
        assertTrue(helper.isDefault())
        assertTrue(helper.rememberOriginal())
        assertEquals("test.original", helper.originalIntent()!!.component!!.packageName)
    }
}
