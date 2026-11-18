package org.kobostyle.launcher.data.metadata

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipFile

data class EpubMetadata(val title: String?, val author: String?, val cover: ByteArray?)

object EpubParser {
    const val MAX_ARCHIVE = 128L * 1024 * 1024
    const val MAX_XML = 1024 * 1024
    const val MAX_COVER = 4 * 1024 * 1024
    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size().toLong() + count <= limit) { "Entry exceeds limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    fun resolvePath(base: String, href: String): String {
        require(!href.contains('\\') && !href.startsWith('/'))
        val relative = try { URI(href) } catch (e: java.net.URISyntaxException) { throw IllegalArgumentException("Invalid archive path", e) }
        require(!relative.isAbsolute && relative.rawAuthority == null && relative.query == null && relative.fragment == null)
        val path = URI(base).resolve(relative).normalize().path
        require(path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it == ".." || it == "." })
        return path
    }
    private data class Element(val name: String, val attrs: Map<String, String>, val text: String)
    private fun xml(bytes: ByteArray): List<Element> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), null)
        data class Open(val name: String, val attrs: Map<String, String>, val text: StringBuilder = StringBuilder())
        val stack = ArrayDeque<Open>()
        val elements = mutableListOf<Element>()
        var tokens = 0
        while (true) {
            val event = parser.nextToken()
            require(++tokens <= 50000 && parser.depth <= 64) { "XML complexity limit" }
            when (event) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.DOCDECL -> error("DOCTYPE prohibited")
                XmlPullParser.START_TAG -> stack.addLast(Open(parser.name, (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }))
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    if (event == XmlPullParser.ENTITY_REF) require(parser.text != null) { "Unresolved entity" }
                    stack.lastOrNull()?.text?.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> {
                    val entry = stack.removeLast()
                    elements += Element(entry.name, entry.attrs, entry.text.toString().trim())
                }
            }
        }
        return elements
    }
    private fun checkDirectory(file: File) {
        // Bound the central directory before ZipFile allocates its entry index. ZIP64 is outside this MVP.
        require(file.length() in 22..MAX_ARCHIVE)
        java.io.RandomAccessFile(file, "r").use { input ->
            val tail = ByteArray(minOf(file.length(), 65557L).toInt())
            input.seek(file.length() - tail.size); input.readFully(tail)
            fun u16(i: Int) = (tail[i].toInt() and 255) or ((tail[i + 1].toInt() and 255) shl 8)
            fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
            val end = (tail.size - 22 downTo 0).firstOrNull { i ->
                u32(i) == 0x06054b50L && i + 22 + u16(i + 20) == tail.size
            } ?: error("Missing ZIP end record")
            require(u16(end + 4) == 0 && u16(end + 6) == 0) { "Split archive prohibited" }
            val entries = u16(end + 10)
            require(entries in 1..4096 && u16(end + 8) == entries)
            val size = u32(end + 12); val offset = u32(end + 16)
            require(size <= 4L * 1024 * 1024 && offset + size == file.length() - tail.size + end) { "Invalid or oversized central directory" }
        }
    }
    fun parse(file: File): EpubMetadata {
        checkDirectory(file)
        return ZipFile(file).use { zip ->
        val names = hashSetOf<String>()
        var expanded = 0L
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            require(names.size < 4096 && names.add(entry.name)) { "Too many or duplicate entries" }
            require(!entry.name.startsWith('/') && !entry.name.contains('\\') && !entry.name.contains(':') && entry.name.split('/').none { it == ".." })
            require(entry.size >= 0 && entry.size <= 128L * 1024 * 1024)
            expanded += entry.size
            require(expanded <= 256L * 1024 * 1024) { "Expanded archive limit" }
        }
        fun entry(path: String, max: Int): ByteArray {
            val item = requireNotNull(zip.getEntry(path)) { "Missing entry" }
            require(item.size <= max && !item.isDirectory)
            require(item.size <= maxOf(1L, item.compressedSize) * 200) { "Compression ratio limit" }
            return zip.getInputStream(item).use { readLimited(it, max) }
        }
        val container = xml(entry("META-INF/container.xml", MAX_XML))
        val packagePath = resolvePath("", requireNotNull(container.firstOrNull { it.name == "rootfile" }?.attrs?.get("full-path")))
        val opf = xml(entry(packagePath, MAX_XML))
        val legacyId = opf.firstOrNull { it.name == "meta" && it.attrs["name"] == "cover" }?.attrs?.get("content")
        val coverItem = opf.firstOrNull { it.name == "item" && (it.attrs["properties"].orEmpty().split(Regex("\\s+")).contains("cover-image") || (legacyId != null && it.attrs["id"] == legacyId)) }
        val cover = coverItem?.attrs?.get("href")?.let { entry(resolvePath(packagePath, it), MAX_COVER) }
        EpubMetadata(opf.firstOrNull { it.name == "title" }?.text?.take(500), opf.firstOrNull { it.name == "creator" }?.text?.take(500), cover)
        }
    }
}
