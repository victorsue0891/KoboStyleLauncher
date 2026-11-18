package org.kobostyle.launcher

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.kobostyle.launcher.reader.ReaderResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ReaderIntentTest {
    class FailingActivity : android.app.Activity() {
        val attempts = mutableListOf<Intent>()
        var failure: RuntimeException = SecurityException("Synthetic permission denial")
        var rejectChooser = false
        override fun startActivity(intent: Intent) {
            attempts += intent
            if (intent.action != Intent.ACTION_CHOOSER || rejectChooser) throw failure
        }
    }
    private fun handler(name: String = "test.reader") = android.content.pm.ResolveInfo().apply {
        activityInfo = android.content.pm.ActivityInfo().apply {
            packageName = name; this.name = "$name.ReaderActivity"; exported = true; enabled = true
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = name; enabled = true }
        }
        nonLocalizedLabel = "Synthetic reader"
    }
    @Test fun noHandlerAndMimeMismatchDoNotDispatchOrRecord() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val uri = Uri.parse("content://provider/document/no-handler")
        val info = handler().activityInfo
        val shadow = org.robolectric.Shadows.shadowOf(activity.packageManager)
        shadow.addOrUpdateActivity(info)
        shadow.addIntentFilterForActivity(android.content.ComponentName(info.packageName, info.name), android.content.IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("content"); addDataType("application/pdf")
        })
        assertEquals(1, ReaderResolver(activity).discover(uri, "application/pdf").size)
        val bridge = org.kobostyle.launcher.reader.NeoReaderBridge(activity)
        try {
            var events = 0
            assertEquals(R.string.no_reader, bridge.open(uri, "application/epub+zip", false) { events++ })
            assertEquals(R.string.open_failed, bridge.open(uri, "text/plain", false) { events++ })
            assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
            assertEquals(0, events)
        } finally { bridge.close(); activity.finish() }
    }
    @Test fun unavailableOrUnexportedOrProtectedReadersAreExcluded() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val uri = Uri.parse("content://provider/document/filter")
        val infos = listOf(
            handler("private.reader").apply { activityInfo.exported = false },
            handler("disabled.reader").apply { activityInfo.enabled = false },
            handler("disabled.app").apply { activityInfo.applicationInfo.enabled = false },
            handler("protected.reader").apply { activityInfo.permission = "test.permission.NOT_GRANTED" },
            handler(activity.packageName), handler("valid.reader")
        )
        val shadow = org.robolectric.Shadows.shadowOf(activity.packageManager)
        infos.forEach { shadow.addResolveInfoForIntent(ReaderResolver.viewIntent(uri, "application/pdf"), it) }
        assertEquals(listOf("valid.reader"), ReaderResolver(activity).discover(uri, "application/pdf").map { it.component.packageName })
        activity.finish()
    }
    @Test fun dispatchFailuresFallBackOnceWithoutFalseOpenEvent() {
        for (failure in listOf(SecurityException("Denied"), android.content.ActivityNotFoundException("Reader removed"))) {
            val activity = org.robolectric.Robolectric.buildActivity(FailingActivity::class.java).setup().get()
            activity.failure = failure
            val uri = Uri.parse("content://provider/document/race")
            org.robolectric.Shadows.shadowOf(activity.packageManager).addResolveInfoForIntent(
                ReaderResolver.viewIntent(uri, "application/pdf"), handler())
            val bridge = org.kobostyle.launcher.reader.NeoReaderBridge(activity)
            try {
                var events = 0
                assertNull(bridge.open(uri, "application/pdf", false) { events++ })
                assertEquals(listOf(Intent.ACTION_VIEW, Intent.ACTION_CHOOSER), activity.attempts.map { it.action })
                assertEquals(0, events)
                activity.attempts.clear(); activity.rejectChooser = true
                assertEquals(R.string.open_failed, bridge.open(uri, "application/pdf", false) { events++ })
                assertEquals(2, activity.attempts.size)
                assertEquals(0, events)
            } finally { bridge.close(); activity.finish() }
        }
    }
    @org.junit.Before fun installedPermissions() = installSignaturePermission()
    @Test fun chooserDoesNotRecordOpenBeforeSelection() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val uri = Uri.parse("content://provider/document/test")
        val intent = ReaderResolver.viewIntent(uri, "application/pdf")
        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "test.reader"; name = "test.reader.ReaderActivity"; exported = true; enabled = true
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "test.reader"; enabled = true }
            }
            nonLocalizedLabel = "Test reader"
        }
        org.robolectric.Shadows.shadowOf(activity.packageManager).addResolveInfoForIntent(intent, info)
        val bridge = org.kobostyle.launcher.reader.NeoReaderBridge(activity)
        try {
            var events = 0
            assertNull(bridge.open(uri, "application/pdf", true) { events++ })
            val chooser = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            assertEquals(0, events)
            val sender = chooser.getParcelableExtra<android.content.IntentSender>(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER)!!
            sender.sendIntent(activity, 0, null, null, null)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, events)
        } finally { bridge.close(); activity.finish() }
    }
    @Test fun onlyReadGrantWithClipDataAndNoPinnedReader() {
        val uri = Uri.parse("content://provider/document/book")
        for (mime in listOf("application/epub+zip", "application/pdf")) {
            val intent = ReaderResolver.viewIntent(uri, mime)
            assertEquals(Intent.ACTION_VIEW, intent.action); assertEquals(uri, intent.data); assertEquals(mime, intent.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertNull(intent.component); assertNull(intent.`package`)
        }
    }
    @Test fun refusesFileSchemeAndUnknownMime() {
        assertThrows(IllegalArgumentException::class.java) { ReaderResolver.viewIntent(Uri.parse("file:///storage/book.pdf"), "application/pdf") }
        assertThrows(IllegalArgumentException::class.java) { ReaderResolver.viewIntent(Uri.parse("content://provider/1"), "text/plain") }
    }
}
