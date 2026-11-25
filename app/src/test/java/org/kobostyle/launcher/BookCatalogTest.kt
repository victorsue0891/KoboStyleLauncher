package org.kobostyle.launcher

import org.junit.Assert.*
import org.junit.Test
import org.kobostyle.launcher.data.books.BookCatalog
import org.kobostyle.launcher.data.books.BookFilename
import org.kobostyle.launcher.data.db.Book

class BookCatalogTest {
    private fun book(id: String, name: String, author: String = "內嵌作者") = Book(id,
        "content://provider/document/$id", "source", id, "內嵌書名", author, "application/epub+zip", 1, 10, displayName = name)

    @Test fun filenameWithBracketsWhitespaceUppercaseExtensionAndHyphenatedTitle() {
        val parsed = BookFilename.parse(" [小說 ] - 書名-下冊 - 作者甲.EPUB ")
        assertEquals("小說", parsed.category)
        assertEquals("書名-下冊", parsed.title)
        assertEquals("作者甲", parsed.author)
        assertEquals("心理", BookFilename.parse("【心理】-書名-作者.pdf").category)
    }
    @Test fun allRequestedCategoriesAndNoGuessedTypoAliases() {
        assertEquals(11, BookFilename.categories.size)
        BookFilename.categories.forEach { assertEquals(it, BookFilename.parse("[$it]-書名-作者.pdf").category) }
        assertNull(BookFilename.parse("[心裡]-書名-作者.epub").category)
        assertNull(BookFilename.parse("[小說]-書名-作者.exe").category)
        assertNull(BookFilename.parse("not-a-category.pdf").category)
    }
    @Test fun incompleteFilenameRetainsValidCategoryWithoutInventingAuthor() {
        assertEquals("小說", BookFilename.parse("[小說]書名.pdf").category)
        assertNull(BookFilename.parse("[小說]-書名-.pdf").author)
        assertNull(BookFilename.parse("[小說]-.pdf").title)
    }
    @Test fun groupsUseFilenameAuthorAndKeepUnclassifiedAndUnknownBooksReachable() {
        val books = listOf(book("a", "[小說]-故事-作者甲.epub"), book("b", "[心理]-心情-作者甲.pdf"),
            book("c", "[錯字]-作品-作者乙.pdf"), book("d", "plain.pdf", ""), book("e", "[文學]作品.pdf", "後備作者"))
        val catalog = BookCatalog(books)
        assertEquals(2, catalog.categoryCount(""))
        assertEquals(listOf("a"), catalog.select(category = "小說").map { it.identity })
        assertEquals(listOf("a", "b"), catalog.select(author = "作者甲").map { it.identity })
        assertEquals("後備作者", catalog.author(books.last()))
        assertEquals(listOf("d"), catalog.select(author = "").map { it.identity })
        assertEquals(5, catalog.authors.sumOf { it.second })
        assertEquals("", catalog.authors.last().first)
        assertEquals(5, catalog.select().size)
    }
    @Test fun searchIsScopedAndIncludesFilenameTitleAuthorAndRecentFilter() {
        val a = book("a", "[小說]-不同書名-Author A.epub").copy(lastOpenedAt = 123)
        val b = book("b", "[心理]-不同書名-Author B.pdf")
        val catalog = BookCatalog(listOf(a, b))
        assertEquals(listOf(a), catalog.select(category = "小說", query = "不同"))
        assertEquals(listOf(a), catalog.select(query = "author a"))
        assertEquals(listOf(a), catalog.select(recent = true))
        assertTrue(catalog.select(category = "心理", query = "author a").isEmpty())
    }
    @Test fun legacyExternalStorageWorksWithoutRescanButOpaqueIdsAreNotFilenames() {
        val legacy = book("a", "").copy(uri = "content://com.android.externalstorage.documents/document/encoded",
            documentId = "ABCD-1234:Books/[數學]-書名-作者.pdf")
        assertEquals(1, BookCatalog(listOf(legacy)).categoryCount("數學"))
        val opaque = legacy.copy(uri = "content://other.provider/document/encoded")
        assertEquals(1, BookCatalog(listOf(opaque)).categoryCount(""))
        assertEquals(1, BookCatalog(listOf(opaque.copy(displayName = "[語言]-書名-作者.pdf"))).categoryCount("語言"))
    }
    @Test fun displayNameTakesPriorityOverLegacyPathAndNoMutationOfBookMetadata() {
        val book = book("a", "[教育]-書名-作者.pdf").copy(uri = "content://com.android.externalstorage.documents/document/old",
            documentId = "primary:Books/[小說]-舊名-舊作者.pdf")
        val catalog = BookCatalog(listOf(book))
        assertEquals(1, catalog.categoryCount("教育"))
        assertEquals(0, catalog.categoryCount("小說"))
        assertEquals("內嵌作者", catalog.books.single().author)
    }
}
