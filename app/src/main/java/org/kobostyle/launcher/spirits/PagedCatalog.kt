package org.kobostyle.launcher.spirits

import androidx.room.RoomRawQuery
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kobostyle.launcher.data.books.BookFilename
import org.kobostyle.launcher.data.db.*
import org.kobostyle.launcher.librarian.LocalCurator
import java.security.MessageDigest

fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
fun bookVersion(book: Book) = digest("${book.identity}|${book.lastModified}|${book.size}")
fun sql(query: String, args: List<Any?> = emptyList()) = RoomRawQuery(query) { s -> args.forEachIndexed { i, v ->
    when (v) { null -> s.bindNull(i + 1); is Int -> s.bindLong(i + 1, v.toLong()); is Long -> s.bindLong(i + 1, v); else -> s.bindText(i + 1, v.toString()) }
} }

data class CatalogFilter(val query: String = "", val category: String? = null, val author: String? = null, val recent: Boolean = false)
data class BookPage(val books: List<Book>, val count: Int, val offset: Int)

/** Rebuildable projection. Each transaction is acknowledged only after the destination write succeeds. */
class PagedCatalog(private val library: LibraryDao, private val dao: SpiritDao) {
    companion object { private val syncing = Mutex() }
    suspend fun sync() = syncing.withLock {
        // A new projection also supports databases created by test factories without tracking triggers.
        if (dao.count() == 0 && library.changes().isEmpty()) {
            var after = ""
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = library.page(after); if (page.isEmpty()) break
                page.forEach { project(it) }; after = page.last().identity
            }
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val changes = library.changes(); if (changes.isEmpty()) break
            for (change in changes) {
                currentCoroutineContext().ensureActive()
                val book = library.book(change.identity)
                if (book == null) dao.removeCatalog(change.identity) else project(book)
                library.ack(change.identity, change.revision)
            }
        }
    }
    private suspend fun project(book: Book) {
        val parts = BookFilename.parse(BookFilename.name(book))
        dao.catalog(CatalogEntry(book.identity, parts.title ?: book.title, parts.author ?: book.author, parts.category.orEmpty(),
            book.lastOpenedAt, book.scanStatus != "unavailable" && !LocalCurator.isAcceptanceFixture(book), bookVersion(book)))
    }
    private fun where(filter: CatalogFilter): Pair<String, List<Any?>> {
        val clauses = mutableListOf<String>(); val args = mutableListOf<Any?>()
        filter.category?.let { clauses += "category = ?"; args += it }
        filter.author?.let { clauses += "author = ?"; args += it }
        if (filter.recent) clauses += "lastOpened > 0"
        val query = filter.query.trim().take(200)
        if (query.isNotEmpty()) {
            // Trigram MATCH supports CJK substring search. Short terms use bounded SQL substring fallback.
            if (query.codePointCount(0, query.length) >= 3) {
                clauses += "identity IN (SELECT identity FROM catalog_fts WHERE catalog_fts MATCH ?)"
                args += "\"${query.replace("\"", "\"\"")}\""
            } else { clauses += "(instr(lower(title),lower(?))>0 OR instr(lower(author),lower(?))>0)"; args += query; args += query }
        }
        return (if (clauses.isEmpty()) "1" else clauses.joinToString(" AND ")) to args
    }
    suspend fun page(filter: CatalogFilter = CatalogFilter(), offset: Int = 0): BookPage {
        val (where, args) = where(filter)
        val count = dao.number(sql("SELECT COUNT(*) FROM catalog WHERE $where", args))
        val safeOffset = offset.coerceIn(0, maxOf(0, ((count - 1) / 50) * 50))
        val rows = dao.catalogQuery(sql("SELECT * FROM catalog WHERE $where ORDER BY lastOpened DESC,title,identity LIMIT 50 OFFSET ?", args + safeOffset))
        return BookPage(rows.mapNotNull { library.book(it.identity) }, count, safeOffset)
    }
    suspend fun categories() = dao.groups(sql("SELECT category AS name,COUNT(*) AS count FROM catalog GROUP BY category"))
    suspend fun authors(query: String, offset: Int) = dao.groups(sql("SELECT author AS name,COUNT(*) AS count FROM catalog WHERE instr(lower(author),lower(?))>0 GROUP BY author ORDER BY author LIMIT 50 OFFSET ?", listOf(query.take(200), offset.coerceAtLeast(0))))
    suspend fun candidates(query: String, preferUnopened: Boolean, round: Int, mood: String = "surprise"): Pair<Int, List<Book>> {
        val args = mutableListOf<Any?>()
        val scores = mutableListOf(if (preferUnopened) "CASE WHEN lastOpened=0 THEN 4 ELSE 0 END" else "0")
        val categories = when (mood) { "relax" -> listOf("小說", "文學", "休閒旅遊"); "learn" -> listOf("科普", "教育", "語言", "數學", "商管", "理財"); "deep" -> listOf("心理", "文學", "社科"); else -> emptyList() }
        if (categories.isNotEmpty()) { scores += "CASE WHEN category IN (${categories.joinToString(",") { "?" }}) THEN 3 ELSE 0 END"; args.addAll(categories) }
        val terms = (listOf(query.trim()) + query.split(Regex("[\\s，、,；;。]+"))).filter { it.length >= 2 }.distinct().take(6)
        if (terms.isNotEmpty()) {
            scores += "CASE WHEN (" + terms.joinToString(" OR ") { "instr(lower(title || ' ' || author || ' ' || category),lower(?))>0" } + ") THEN 8 ELSE 0 END"
            args.addAll(terms)
        }
        val count = dao.number(sql("SELECT COUNT(*) FROM catalog WHERE available=1"))
        val offset = if (count == 0) 0 else (round.toLong() * 3 % count).toInt()
        val order = "(${scores.joinToString(" + ")}) DESC,lastOpened,title,identity"
        val rows = dao.catalogQuery(sql("SELECT * FROM catalog WHERE available=1 ORDER BY $order LIMIT 60 OFFSET ?", args + offset))
        return count to rows.mapNotNull { library.book(it.identity) }
    }
}
