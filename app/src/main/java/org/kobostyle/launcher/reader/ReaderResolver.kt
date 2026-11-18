package org.kobostyle.launcher.reader

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri

data class ReaderHandler(val component: ComponentName, val label: String)
class ReaderResolver(private val context: Context) {
    fun discover(uri: Uri, mime: String): List<ReaderHandler> {
        val manager = context.packageManager
        return manager.queryIntentActivities(viewIntent(uri, mime), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled &&
                it.activityInfo.packageName != context.packageName &&
                (it.activityInfo.permission == null || context.checkSelfPermission(it.activityInfo.permission) == PackageManager.PERMISSION_GRANTED) }
            .map { ReaderHandler(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(manager).toString()) }
            .distinctBy { it.component }
    }
    fun systemDefault(uri: Uri, mime: String, handlers: List<ReaderHandler>): ReaderHandler? {
        val info = context.packageManager.resolveActivity(viewIntent(uri, mime), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        return handlers.firstOrNull { it.component == ComponentName(info.packageName, info.name) }
    }
    companion object {
        fun viewIntent(uri: Uri, mime: String): Intent {
            require(uri.scheme == "content")
            require(mime in setOf("application/epub+zip", "application/pdf"))
            return Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Book", uri)
            }
        }
    }
}
