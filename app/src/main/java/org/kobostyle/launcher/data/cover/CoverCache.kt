package org.kobostyle.launcher.data.cover

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.security.MessageDigest

class CoverCache(private val context: Context) {
    private val directory = File(context.cacheDir, "covers").apply { mkdirs() }
    fun epub(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) return null
        var sample = 1
        while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 480) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun pdf(uri: Uri): Bitmap? = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        // Descriptor access above may fail due to SAF permissions. Once opened, a
        // SecurityException from PdfRenderer means the PDF itself cannot be decoded
        // (for example password protection), not that the SAF grant was revoked.
        val pdf = try { PdfRenderer(descriptor) } catch (e: SecurityException) {
            throw IllegalArgumentException("PDF decoding requires unsupported credentials", e)
        }
        pdf.use { renderer ->
            if (renderer.pageCount == 0) return@use null
            renderer.openPage(0).use { page ->
                require(page.width > 0 && page.height > 0)
                val scale = minOf(240f / page.width, 360f / page.height)
                val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (e: Exception) { bitmap.recycle(); throw e }
            }
        }
    }
    fun store(identity: String, input: Bitmap): String {
        val gray = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(gray)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
            canvas.drawBitmap(input, 0f, 0f, paint)
            val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, "$key.png")
            file.outputStream().use { check(gray.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            trim()
            return file.absolutePath
        } finally { input.recycle(); gray.recycle() }
    }
    private fun trim() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= 32L * 1024 * 1024) break
            val length = file.length()
            if (file.delete()) bytes -= length
        }
    }
}
