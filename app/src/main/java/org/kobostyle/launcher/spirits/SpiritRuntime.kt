package org.kobostyle.launcher.spirits

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kobostyle.launcher.librarian.OpenAiTransport
import org.kobostyle.launcher.librarian.CuratorTransport

class SpiritRuntime internal constructor(application: Application,
    val database: SpiritDatabase = SpiritDatabase.get(application), transport: CuratorTransport = OpenAiTransport()) {
    val settings = SpiritSettings(application.getSharedPreferences("spirits_v4", 0))
    val gateway = OpenAiGateway(database, settings, transport)
    private var consentOwner = WeakReference<ComponentActivity>(null)
    fun bindConsent(activity: ComponentActivity) { consentOwner = WeakReference(activity) }
    fun unbindConsent(activity: ComponentActivity) { if (consentOwner.get() === activity) consentOwner.clear() }
    suspend fun confirm(preview: CostPreview): Boolean = withContext(Dispatchers.Main) {
        val activity = consentOwner.get() ?: return@withContext false
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@withContext false
        SpiritDialogs.confirm(activity, preview)
    }
    companion object {
        @Volatile private var instance: SpiritRuntime? = null
        fun get(application: Application) = instance ?: synchronized(this) {
            instance ?: SpiritRuntime(application).also { instance = it }
        }
    }
}
