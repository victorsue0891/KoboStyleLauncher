package org.kobostyle.launcher.librarian

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class KeyVaultTest {
    private class MemoryKeys : VaultKeys {
        var key: SecretKey? = null
        override fun get(create: Boolean): SecretKey {
            if (key == null && create) key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return checkNotNull(key)
        }
        override fun delete() { key = null }
    }
    private fun context(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "vault-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) { override fun getNoBackupFilesDir() = directory }
    }
    @Test fun ciphertextOnlyFreshIvAndDeleteRemovesBothKeyAndFile() {
        val context = context(); val keys = MemoryKeys(); val vault = AndroidKeyVault(context, keys)
        assertNull(vault.read())
        vault.save("test-key-not-real-123")
        val file = File(context.noBackupFilesDir, "librarian-key.bin")
        val first = file.readBytes()
        assertFalse(String(first).contains("test-key-not-real-123"))
        assertEquals("test-key-not-real-123", vault.read())
        vault.save("test-key-not-real-123")
        val second = file.readBytes()
        assertFalse("Fresh encryption must use a new IV: ${first.copyOfRange(2, 14).contentToString()} / ${second.copyOfRange(2, 14).contentToString()}", first.contentEquals(second))
        assertEquals("test-key-not-real-123", AndroidKeyVault(context, keys).read())
        vault.save("test-key-replacement-456")
        assertEquals("test-key-replacement-456", AndroidKeyVault(context, keys).read())
        assertEquals(listOf("librarian-key.bin"), context.noBackupFilesDir.listFiles()!!.map { it.name })
        vault.delete(); assertFalse(vault.exists()); assertNull(keys.key); assertNull(vault.read())
    }
    @Test fun tamperingMissingKeyAndOversizePayloadFailClosed() {
        val context = context(); val keys = MemoryKeys(); val vault = AndroidKeyVault(context, keys)
        vault.save("test-key-not-real-123")
        val file = File(context.noBackupFilesDir, "librarian-key.bin")
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertThrows(Exception::class.java) { vault.read() }
        vault.save("test-key-not-real-123"); keys.delete()
        assertThrows(Exception::class.java) { vault.read() }
        file.writeBytes(ByteArray(2000)); assertThrows(Exception::class.java) { vault.read() }
        assertThrows(IllegalArgumentException::class.java) { vault.save("short") }
        assertThrows(IllegalArgumentException::class.java) { vault.save("test-key-with\nnewline") }
    }
}
