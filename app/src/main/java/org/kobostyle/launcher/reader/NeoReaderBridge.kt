package org.kobostyle.launcher.reader

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import org.kobostyle.launcher.R
import java.util.UUID

/** No NeoReader component is pinned. Discovery never counts as compatibility proof. */
class NeoReaderBridge(private val activity: Activity) {
    private val resolver = ReaderResolver(activity)
    private val pending = mutableMapOf<String, Pair<PendingIntent, () -> Unit>>()
    private val action = activity.packageName + ".CHOOSER_SELECTED"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val token = intent.data?.lastPathSegment ?: return
            val entry = pending.remove(token) ?: return
            // The receiver is not exported. Only the one-shot app PendingIntent can reach it externally.
            entry.first.cancel()
            entry.second.invoke()
        }
    }
    init {
        val filter = IntentFilter(action).apply { addDataScheme("kobostyle") }
        androidx.core.content.ContextCompat.registerReceiver(activity, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    fun close() { pending.values.forEach { it.first.cancel() }; pending.clear(); activity.unregisterReceiver(receiver) }
    fun open(uri: Uri, mime: String, forceChooser: Boolean, opened: () -> Unit): Int? {
        return try {
            val handlers = resolver.discover(uri, mime)
            if (handlers.isEmpty()) return R.string.no_reader
            val intent = ReaderResolver.viewIntent(uri, mime)
            if (!forceChooser && resolver.systemDefault(uri, mime, handlers) != null) {
                // Implicit dispatch respects the current system default.
                activity.startActivity(intent)
                opened()
            } else {
                // Immutable callback already contains all data needed. Cancellation emits no callback.
                pending.values.forEach { it.first.cancel() }; pending.clear()
                val token = UUID.randomUUID().toString()
                val callback = Intent(action).setPackage(activity.packageName).setData(Uri.parse("kobostyle://chosen/$token"))
                val pi = PendingIntent.getBroadcast(activity, 0, callback, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
                pending[token] = pi to opened
                val chooser = Intent.createChooser(intent, activity.getString(R.string.chooser), pi.intentSender)
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                chooser.clipData = intent.clipData
                try { activity.startActivity(chooser) }
                catch (e: Exception) { pending.remove(token); pi.cancel(); throw e }
            }
            null
        } catch (_: ActivityNotFoundException) { if (!forceChooser) open(uri, mime, true, opened) else R.string.open_failed }
        catch (_: SecurityException) { if (!forceChooser) open(uri, mime, true, opened) else R.string.open_failed }
        catch (_: IllegalArgumentException) { R.string.open_failed }
    }
}
