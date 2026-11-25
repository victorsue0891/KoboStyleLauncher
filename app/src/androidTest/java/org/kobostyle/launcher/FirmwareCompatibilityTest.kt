package org.kobostyle.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.security.NetworkSecurityPolicy
import android.view.inputmethod.InputMethodManager
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.platform.HomeRoleHelper
import org.kobostyle.launcher.reader.ReaderResolver
import javax.net.ssl.SSLContext

/** Capability checks in the actual app UID. No settings writes, reader launches or network calls. */
@RunWith(AndroidJUnit4::class)
class FirmwareCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$value\n") })

    @Test fun publicIntentsAndRecoveryRemainDiscoverable() {
        val manager = context.packageManager
        assertTrue("Requires Android 11 or later", Build.VERSION.SDK_INT >= 30)
        val launch = checkNotNull(manager.getLaunchIntentForPackage(context.packageName))
        assertTrue(manager.resolveActivity(launch, 0)!!.activityInfo.exported)

        val document = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
        val tree = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        for ((name, intent) in listOf("document" to document, "tree" to tree)) {
            val info = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            assertNotNull("SAF $name resolver not visible to app UID", info)
            assertTrue(info!!.exported && info.enabled && info.applicationInfo.enabled)
            assertTrue("SAF $name requires an unavailable permission", info.permission == null ||
                context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
            report("FW_SAF $name=${info.packageName}/${info.name} discovery=PASS selection=NOT_EXERCISED")
        }

        val helper = HomeRoleHelper(context)
        // Existing recovery configuration is read only. No alias or preferred activity is changed.
        val original = helper.originalIntent()
        val homes = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
        assertTrue("No usable HOME candidates", homes.isNotEmpty())
        report("FW_HOME candidates=${homes.size} appDefault=${helper.isDefault()} savedRecoveryValid=${original != null} homeSettingsVisible=${helper.settingsIntent() != null}")

        val probe = Uri.parse("content://org.kobostyle.probe/document/compatibility")
        for (mime in listOf("application/epub+zip", "application/pdf")) {
            val handlers = ReaderResolver(context).discover(probe, mime)
            assertTrue("No exported content URI reader for $mime", handlers.isNotEmpty())
            for (handler in handlers) {
                val info = manager.getPackageInfo(handler.component.packageName, 0)
                report("FW_READER mime=$mime component=${handler.component.flattenToShortString()} version=${info.versionName} code=${info.longVersionCode} discovery=PASS open=NOT_EXERCISED")
            }
        }
        val ime = context.getSystemService(InputMethodManager::class.java)
        assertTrue("No enabled input method", ime.enabledInputMethodList.isNotEmpty())
        report("FW_PLATFORM model=${Build.MODEL} android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} process64=${Process.is64Bit()} abis=${Build.SUPPORTED_ABIS.joinToString(",")} enabledImeCount=${ime.enabledInputMethodList.size}")
    }

    @Test fun bundledFtsAndTlsCapabilities() {
        // Open only a throwaway in-memory database, never the user's library.
        BundledSQLiteDriver().open(":memory:").use { db ->
            db.execSQL("CREATE VIRTUAL TABLE compat_fts USING fts5(title, tokenize='trigram')")
            db.prepare("INSERT INTO compat_fts(title) VALUES (?)").use { statement ->
                statement.bindText(1, "合成韌體相容性驗證"); statement.step()
            }
            db.prepare("SELECT count(*) FROM compat_fts WHERE compat_fts MATCH ?").use { statement ->
                statement.bindText(1, "韌體相容"); assertTrue(statement.step()); assertEquals(1L, statement.getLong(0))
            }
            db.prepare("SELECT sqlite_version()").use { statement ->
                assertTrue(statement.step()); report("FW_SQLITE bundledVersion=${statement.getText(0)} nativeLoad=PASS fts5ChineseTrigram=PASS")
            }
        }
        // Inspect the local TLS provider without connecting or extracting a credential.
        val tls = SSLContext.getDefault()
        assertTrue(tls.defaultSSLParameters.protocols.contains("TLSv1.2"))
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("api.openai.com"))
        report("FW_TLS provider=${tls.provider.name} protocols=${tls.defaultSSLParameters.protocols.joinToString(",")} cleartextBlocked=PASS networkCalls=0")
    }
}
