package org.kobostyle.launcher.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Public package/settings APIs only. Installing or starting the app never enables HOME. */
class HomeRoleHelper(private val context: Context) {
    private val manager = context.packageManager
    private val preferences = context.getSharedPreferences("home_recovery", Context.MODE_PRIVATE)
    val entry = ComponentName(context.packageName, "${context.packageName}.ui.HomeEntry")
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private fun externalHomes() = manager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo }.filter { it.exported && it.enabled && it.applicationInfo.enabled && it.packageName != context.packageName }
    fun rememberOriginal(): Boolean {
        if (originalIntent() != null) return true
        val current = manager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return false
        val settingsPackage = manager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
        val homeSettingsPackage = manager.resolveActivity(Intent(Settings.ACTION_HOME_SETTINGS), 0)?.activityInfo?.packageName
        // Never save the chooser or Android's setup fallback as the recovery launcher.
        val verified = externalHomes().firstOrNull { it.packageName == current.packageName && it.name == current.name &&
            it.packageName != settingsPackage && it.packageName != homeSettingsPackage } ?: return false
        return preferences.edit().putString("original_home", ComponentName(verified.packageName, verified.name).flattenToString()).commit()
    }
    fun originalIntent(): Intent? {
        val component = preferences.getString("original_home", null)?.let(ComponentName::unflattenFromString) ?: return null
        if (externalHomes().none { it.packageName == component.packageName && it.name == component.className }) return null
        return homeIntent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun isDefault(): Boolean = manager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    fun settingsIntent(): Intent? = Intent(Settings.ACTION_HOME_SETTINGS).takeIf { manager.resolveActivity(it, 0) != null }
    fun enableCandidate(): Boolean {
        if (settingsIntent() == null || !rememberOriginal()) return false
        // Only our own opt-in alias changes. Stock packages and preferred activities are untouched.
        manager.setComponentEnabledSetting(entry, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        return true
    }
}
