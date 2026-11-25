package org.kobostyle.launcher

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kobostyle.launcher.librarian.AndroidKeyVault
import org.kobostyle.launcher.librarian.AndroidVaultKeys
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Synthetic credential and isolated alias/directory; never touches a user's saved API key. */
@RunWith(AndroidJUnit4::class)
class LibrarianKeyStoreTest {
    @Test fun deviceKeystoreEncryptsReplacesRejectsTamperingAndDeletes() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val directory = File(base.noBackupFilesDir, "vault-test-$id").apply { check(mkdirs()) }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = directory }
        val alias = "kobostyle.librarian.test.$id"
        val keys = AndroidVaultKeys(alias)
        val vault = AndroidKeyVault(context, keys)
        val file = File(directory, "librarian-key.bin")
        try {
            vault.save("synthetic-test-key-never-valid-123")
            val original = file.readBytes()
            assertFalse(String(original, Charsets.UTF_8).contains("synthetic-test-key"))
            assertEquals("synthetic-test-key-never-valid-123", AndroidKeyVault(context, keys).read())
            vault.save("synthetic-test-key-never-valid-123")
            assertFalse(original.contentEquals(file.readBytes()))
            vault.save("synthetic-replacement-key-never-valid-456")
            assertEquals("synthetic-replacement-key-never-valid-456", AndroidKeyVault(context, keys).read())
            val damaged = file.readBytes().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            var rejected = false
            try { vault.read() } catch (_: Exception) { rejected = true }
            assertTrue("Tampered ciphertext must not decrypt", rejected)
            vault.delete()
            assertFalse(vault.exists())
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        } finally {
            vault.delete()
            // Unique, test-owned directory; remove only direct temporary ciphertext files.
            directory.listFiles()?.filter { it.isFile && it.name.startsWith("librarian-key-") && it.extension == "pending" }?.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }
}
