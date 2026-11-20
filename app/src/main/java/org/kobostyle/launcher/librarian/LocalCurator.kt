package org.kobostyle.launcher.librarian

import org.kobostyle.launcher.data.books.BookFilename
import org.kobostyle.launcher.data.db.Book

object LocalCurator {
    fun rank(books: List<Book>, choice: Choice): List<Book> {
        val categories = when (choice.mood) {
            "relax" -> setOf("小說", "文學", "休閒旅遊")
            "learn" -> setOf("科普", "教育", "語言", "數學", "商管", "理財")
            "deep" -> setOf("心理", "文學", "社科")
            else -> emptySet()
        }
        val query = choice.query.trim()
        val words = query.split(Regex("[\\s，、,；;。]+" )).filter { it.length >= 2 }
        val sorted = books.asSequence().filter { it.scanStatus != "unavailable" && it.uri.startsWith("content://") }
            .distinctBy { it.identity }.map { book ->
                val name = BookFilename.parse(BookFilename.name(book))
                val text = listOf(book.title, name.title.orEmpty(), book.author, name.author.orEmpty(), name.category.orEmpty()).joinToString(" ")
                val explicitCategory = name.category?.let { query.contains(it, true) } == true
                val namedAuthor = (name.author ?: book.author).takeIf { it.length >= 2 }?.let { query.contains(it, true) } == true
                val matches = query.isNotEmpty() && (text.contains(query, true) || words.any { text.contains(it, true) })
                val score = (if (choice.preferUnopened && book.lastOpenedAt == 0L) 4 else 0) +
                    (if (name.category in categories) 3 else 0) + (if (matches) 8 else 0) +
                    (if (explicitCategory) 8 else 0) + (if (namedAuthor) 8 else 0)
                book to score
            }.sortedWith(compareByDescending<Pair<Book, Int>> { it.second }.thenBy { it.first.lastOpenedAt }.thenBy { it.first.identity })
            .map { it.first }.toList()
        if (sorted.isEmpty()) return sorted
        val offset = (choice.round.toLong() * 3 % sorted.size).toInt()
        return sorted.drop(offset) + sorted.take(offset)
    }
    fun offline(candidates: List<Book>, choice: Choice, notice: String = ""): CuratorResult {
        val cards = candidates.take(3).map { book ->
            val category = BookFilename.parse(BookFilename.name(book)).category
            val reason = if (category != null) "這本在你的「$category」分類中，這次依分類與書房開啟紀錄挑選。現有資料只有書目資訊，我尚未取得全文，因此不推測劇情或保證內容符合期待。"
                else "這本來自你已匯入的館藏，這次依書目與書房開啟紀錄挑選。它的分類資料還不完整，我尚未取得全文，可以先翻閱目錄，再決定是否繼續讀。"
            CuratedBook(book, reason, "先留 ${choice.minutes} 分鐘讀一小段；這是你選的閱讀時間，不是讀完全書的估計。")
        }
        return CuratorResult(cards, if (cards.isEmpty()) "暫時找不到可讀的館藏。請先加入書籍，或確認資料夾與記憶卡授權。"
            else "我從可讀的館藏挑了 ${cards.size} 本。先看看哪一本吸引你？", "offline", notice)
    }
}
