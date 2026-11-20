package org.kobostyle.launcher.librarian

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyVault {
    fun exists(): Boolean
    fun save(value: String)
    fun read(): String?
    fun delete()
}

interface VaultKeys {
    fun get(create: Boolean): SecretKey
    fun delete()
}
class AndroidVaultKeys(private val alias: String = "kobostyle.librarian.aes.v1") : VaultKeys {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun get(create: Boolean): SecretKey {
        (store().getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun delete() { store().deleteEntry(alias) }
}

/** File is outside backup; AES key material is owned by Android Keystore. No plaintext fallback. */
class AndroidKeyVault(context: Context, private val keys: VaultKeys = AndroidVaultKeys()) : KeyVault {
    private val file = File(context.noBackupFilesDir, "librarian-key.bin")
    private val aad = "kobostyle.librarian.aes.v1".toByteArray(Charsets.UTF_8)
    @Synchronized override fun exists() = file.exists()
    @Synchronized override fun save(value: String) {
        require(value.length in 16..512 && value.all { it.code in 33..126 }) { "Invalid key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keys.get(true)); updateAAD(aad) }
        val bytes = value.toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val payload = byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + encrypted
        // Same-directory atomic replacement throws on failure; never silently retain an old key.
        // Only ciphertext reaches either file, and both stay outside Android backup.
        val pending = Files.createTempFile(file.parentFile!!.toPath(), "librarian-key-", ".pending")
        try {
            FileOutputStream(pending.toFile()).use { output -> output.write(payload); output.fd.sync() }
            Files.move(pending, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            check(file.readBytes().contentEquals(payload)) { "Key storage unavailable" }
        } finally { Files.deleteIfExists(pending) }
    }
    @Synchronized override fun read(): String? {
        if (!exists()) return null
        val data = file.inputStream().use { input ->
            val buffer = ByteArray(1025)
            var size = 0
            while (size < buffer.size) { val read = input.read(buffer, size, buffer.size - size); if (read < 0) break; size += read }
            buffer.copyOf(size)
        }
        require(data.size in 30..1024 && data[0].toInt() == 1 && data[1].toInt() == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, keys.get(false), GCMParameterSpec(128, data.copyOfRange(2, 14))); updateAAD(aad)
        }
        val plain = cipher.doFinal(data.copyOfRange(14, data.size))
        return try { String(plain, Charsets.UTF_8) } finally { plain.fill(0) }
    }
    @Synchronized override fun delete() { Files.deleteIfExists(file.toPath()); keys.delete(); check(!exists()) }
}
