package org.kobostyle.launcher.data.books

import org.kobostyle.launcher.data.db.Book
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Filename-derived browsing only. Never changes the document or its embedded metadata. */
object BookFilename {
    val categories = listOf("小說", "心理", "文學", "休閒旅遊", "社科", "科普", "商管", "教育", "理財", "語言", "數學")
    data class Parts(val category: String?, val title: String?, val author: String?)
    private val prefix = Regex("^(?:\\[([^\\]]+)\\]|【([^】]+)】)\\s*-?\\s*(.*)$")
    fun normalized(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
    fun parse(filename: String): Parts {
        val name = normalized(filename)
        if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in listOf("epub", "pdf")) return Parts(null, null, null)
        val match = prefix.matchEntire(name.substringBeforeLast('.')) ?: return Parts(null, null, null)
        val category = normalized(match.groupValues[1].ifEmpty { match.groupValues[2] }).takeIf { it in categories }
        val remainder = match.groupValues[3]
        // The final separator starts the author; hyphens inside a title are preserved.
        val split = remainder.lastIndexOf('-')
        if (split <= 0 || split == remainder.lastIndex) return Parts(category, null, null)
        val title = normalized(remainder.substring(0, split)).takeIf { it.isNotEmpty() }
        val author = normalized(remainder.substring(split + 1)).takeIf { it.isNotEmpty() }
        return Parts(category, title, author)
    }
    fun name(book: Book): String = book.displayName.ifBlank {
        // Legacy v1 records: only Android's external-storage provider has this known path form.
        // Never interpret an arbitrary provider's opaque document ID or EPUB title as a filename.
        if (book.uri.startsWith("content://com.android.externalstorage.documents/") && '/' in book.documentId)
            book.documentId.substringAfterLast('/') else ""
    }
}

class BookCatalog(val books: List<Book>) {
    private val parsed = books.associate { it.identity to BookFilename.parse(BookFilename.name(it)) }
    private val byCategory = books.groupBy { parsed[it.identity]?.category.orEmpty() }
    private val byAuthor = books.groupBy { author(it) }
    private val collator = Collator.getInstance(Locale.TRADITIONAL_CHINESE)
    val authors: List<Pair<String, Int>> = byAuthor.map { it.key to it.value.size }.sortedWith { a, b ->
        when { a.first.isEmpty() -> if (b.first.isEmpty()) 0 else 1; b.first.isEmpty() -> -1
            else -> collator.compare(a.first, b.first) }
    }
    fun author(book: Book): String = parsed[book.identity]?.author ?: BookFilename.normalized(book.author)
    fun categoryCount(category: String) = byCategory[category].orEmpty().size
    fun select(category: String? = null, author: String? = null, recent: Boolean = false, query: String = ""): List<Book> {
        val candidates = when { category != null -> byCategory[category].orEmpty(); author != null -> byAuthor[author].orEmpty(); else -> books }
        return candidates.filter { book -> (!recent || book.lastOpenedAt > 0) &&
            (query.isBlank() || book.title.contains(query, true) || author(book).contains(query, true) ||
                parsed[book.identity]?.title?.contains(query, true) == true) }
    }
}
