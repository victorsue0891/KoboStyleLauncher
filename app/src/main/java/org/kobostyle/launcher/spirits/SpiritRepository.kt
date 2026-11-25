package org.kobostyle.launcher.spirits

import org.kobostyle.launcher.data.books.BookFilename
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.data.db.LibraryDao

class SpiritRepository(val db: SpiritDatabase, private val books: LibraryDao) {
    suspend fun book(id: String) = books.book(id)
    suspend fun summon(id: String): Spirit {
        val book = checkNotNull(books.book(id))
        val existing = db.dao().spirit(id)
        if (existing != null && existing.version == bookVersion(book)) return existing
        val category = BookFilename.parse(BookFilename.name(book)).category
        val tone = when (category) { "小說", "文學" -> "故事型"; "心理", "教育" -> "溫柔型"; "數學", "科普" -> "探索型"; else -> "好奇型" }
        val name = listOf("拾光", "墨芽", "知葉", "書影")[digest(id).take(4).toInt(16) % 4]
        val spirit = Spirit(id, "spirit-${digest(id)}", name, tone, "我是$name，陪你探索這本書。我目前只知道書名與書目，還沒有讀過全書。", bookVersion(book))
        db.dao().spirit(spirit)
        return spirit
    }
    suspend fun validPack(book: Book) = db.dao().pack(book.identity)?.takeIf { it.version == bookVersion(book) }
    suspend fun boundary(id: String) = db.dao().boundary(id) ?: ReadingBoundary(id)
    suspend fun setBoundary(value: ReadingBoundary) { require(value.chapter == null || value.chapter in 0..100_000); db.dao().boundary(value) }
    suspend fun delete(id: String) = db.transaction {
        db.dao().deleteSections(id); db.dao().deletePack(id); db.dao().deleteBoundary(id); db.dao().deleteSpirit(id)
    }
    suspend fun removePack(id: String) = db.transaction { db.dao().deleteSections(id); db.dao().deletePack(id) }
    suspend fun evidence(book: Book, question: String): List<KnowledgeSection> {
        if (validPack(book) == null) return emptyList()
        val boundary = boundary(book.identity)
        if (!boundary.spoilers && boundary.chapter == null) return emptyList()
        val args = mutableListOf<Any?>(book.identity, if (boundary.spoilers) 100_000 else boundary.chapter)
        val text = question.trim().take(200)
        val filter = if (text.codePointCount(0, text.length) >= 3) {
            args += "\"${text.replace("\"", "\"\"")}\""
            " AND sectionId IN (SELECT sectionId FROM knowledge_fts WHERE bookId=sections.bookId AND knowledge_fts MATCH ?)"
        } else ""
        var rows = db.dao().evidence(sql("SELECT * FROM sections WHERE bookId=? AND chapter<=?$filter ORDER BY chapter,sectionId LIMIT 5", args))
        // Generic actions have no textual match; still stay inside this book and its manual boundary.
        if (rows.isEmpty()) rows = db.dao().evidence(sql("SELECT * FROM sections WHERE bookId=? AND chapter<=? ORDER BY chapter,sectionId LIMIT 5", args.take(2)))
        var remainingBytes = 1500 // deliberately conservative ~token upper estimate; never an exact tokenizer
        return rows.mapNotNull { section ->
            if (remainingBytes <= 0) null else {
                var end = 0; var bytes = 0
                while (end < section.content.length) {
                    val cp = section.content.codePointAt(end); val units = Character.charCount(cp)
                    val length = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                    if (bytes + length > remainingBytes) break
                    bytes += length; end += units
                }
                remainingBytes -= bytes
                if (end == 0) null else section.copy(content = section.content.substring(0, end))
            }
        }
    }
}
