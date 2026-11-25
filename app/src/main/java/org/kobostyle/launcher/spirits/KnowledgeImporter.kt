package org.kobostyle.launcher.spirits

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.kobostyle.launcher.data.db.Book
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.DigestInputStream
import java.security.MessageDigest

data class PackPreview(val packId: String, val title: String, val author: String, val fingerprint: String?,
    val source: String, val count: Int, val digest: String)

/** Streaming, closed v1 contract. Nothing is executed; section locators are display text only. */
class KnowledgeImporter(private val db: SpiritDatabase) {
    suspend fun inspect(input: InputStream) = parse(input) { }
    suspend fun import(book: Book, expected: PackPreview, input: InputStream) = db.transaction {
        require(expected.fingerprint == null || expected.fingerprint == "metadata-v1:${bookVersion(book)}") { "Fingerprint mismatch" }
        db.dao().deleteSections(book.identity)
        val actual = parse(input) { db.dao().sections(listOf(it.copy(bookId = book.identity))) }
        require(actual == expected) { "Pack changed" }
        db.dao().pack(KnowledgePack(book.identity, actual.packId, actual.digest, bookVersion(book), actual.title, actual.author, actual.source))
    }
    private suspend fun parse(input: InputStream, section: suspend (KnowledgeSection) -> Unit): PackPreview {
        val hash = MessageDigest.getInstance("SHA-256")
        val bounded = object : FilterInputStream(input) {
            var total = 0L
            fun count(n: Int) { if (n > 0) { total += n; require(total <= 10_000_000) { "Pack too large" } } }
            override fun read(): Int = `in`.read().also { if (it >= 0) count(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also(::count)
        }
        var id = ""; var title = ""; var author = ""; var fingerprint: String? = null; var source = ""; var count = 0
        val ids = hashSetOf<String>()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        JsonReader(InputStreamReader(DigestInputStream(bounded, hash), decoder)).use { r ->
            r.isLenient = false
            suspend fun obj(required: Set<String>, optional: Set<String> = emptySet(), read: suspend (String) -> Unit) {
                r.beginObject(); val seen = hashSetOf<String>()
                while (r.hasNext()) { currentCoroutineContext().ensureActive(); val key = r.nextName(); require(seen.add(key) && key in required + optional); read(key) }
                r.endObject(); require(seen.containsAll(required))
            }
            fun text(max: Int, min: Int = 0): String {
                require(r.peek() == JsonToken.STRING)
                val s = r.nextString(); require(s.codePointCount(0, s.length) in min..max && '\u0000' !in s); return s
            }
            fun number(): Int { require(r.peek() == JsonToken.NUMBER); val s = r.nextString(); require(s.matches(Regex("0|[1-9][0-9]*"))); return s.toInt() }
            fun nullable(max: Int): String? = if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else text(max)
            val identifier = Regex("[A-Za-z0-9._-]+")
            obj(setOf("schemaVersion", "packId", "source", "bookMatch", "sections")) { key -> when (key) {
                "schemaVersion" -> require(number() == 1)
                "packId" -> { id = text(128, 1); require(id.matches(identifier)) }
                "source" -> obj(setOf("type", "toolVersion"), setOf("generatedAtUtc")) { name -> when (name) {
                    "type" -> { source = text(40); require(source in setOf("book-to-skill", "manual", "local-notes")) }
                    "toolVersion" -> text(100)
                    "generatedAtUtc" -> java.time.OffsetDateTime.parse(text(100))
                } }
                "bookMatch" -> obj(setOf("title"), setOf("author", "fingerprint")) { name -> when (name) {
                    "title" -> title = text(400)
                    "author" -> author = text(200)
                    "fingerprint" -> fingerprint = nullable(128)
                } }
                "sections" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        currentCoroutineContext().ensureActive(); require(++count <= 500)
                        var sid = ""; var chapter = 0; var heading = ""; var content = ""; var locator = ""; var sha = ""
                        obj(setOf("sectionId", "chapterOrder", "title", "content", "contentSha256"), setOf("sourceLocator")) { name -> when (name) {
                            "sectionId" -> { sid = text(120, 1); require(sid.matches(identifier) && ids.add(sid)) }
                            "chapterOrder" -> { chapter = number(); require(chapter <= 100_000) }
                            "title" -> heading = text(300)
                            "content" -> { content = text(10_000, 1); require(content.toByteArray(Charsets.UTF_8).size <= 20_000) }
                            "sourceLocator" -> { locator = nullable(300).orEmpty(); require(!locator.startsWith('/') && ':' !in locator && '\\' !in locator && locator.split('/').none { it == ".." }) }
                            "contentSha256" -> { sha = text(64); require(sha.matches(Regex("[a-f0-9]{64}"))) }
                        } }
                        require(digest(content) == sha)
                        section(KnowledgeSection("", sid, chapter, heading, content, locator, sha))
                    }
                    r.endArray(); require(count >= 1)
                }
            } }
            require(r.peek() == JsonToken.END_DOCUMENT)
        }
        return PackPreview(id, title, author, fingerprint, source, count, hash.digest().joinToString("") { "%02x".format(it) })
    }
}
