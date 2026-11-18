package org.kobostyle.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.kobostyle.launcher.data.metadata.EpubParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class EpubParserTest {
    private fun archive(entries: Map<String, ByteArray>, action: (File) -> Unit) {
        val file = File.createTempFile("epub-test", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, content) -> zip.putNextEntry(ZipEntry(path)); zip.write(content); zip.closeEntry() } }
            action(file)
        } finally { file.delete() }
    }
    private val container = "<container><rootfiles><rootfile full-path='OPS/package.opf'/></rootfiles></container>".toByteArray()
    @Test fun alternateNamespaceAndRelativeCover() {
        val opf = """<package xmlns:d='http://purl.org/dc/elements/1.1/'><metadata><d:title>繁體書名 &amp; Test</d:title><d:creator>作者</d:creator></metadata><manifest><item id='cover' properties='nav cover-image' href='../images/cover.jpg'/></manifest></package>"""
        archive(mapOf("META-INF/container.xml" to container, "OPS/package.opf" to opf.toByteArray(), "images/cover.jpg" to byteArrayOf(1, 2, 3))) {
            val book = EpubParser.parse(it)
            assertEquals("繁體書名 & Test", book.title); assertEquals("作者", book.author); assertArrayEquals(byteArrayOf(1, 2, 3), book.cover)
        }
    }
    @Test fun epub2LegacyCover() {
        val opf = "<package><metadata><meta name='cover' content='front'/></metadata><manifest><item id='front' href='cover.png'/></manifest></package>"
        archive(mapOf("META-INF/container.xml" to container, "OPS/package.opf" to opf.toByteArray(), "OPS/cover.png" to byteArrayOf(7))) {
            assertArrayEquals(byteArrayOf(7), EpubParser.parse(it).cover)
        }
    }
    @Test fun rejectsDoctypeAndExternalEntity() {
        val malicious = "<!DOCTYPE container [<!ENTITY xxe SYSTEM 'file:///nonexistent'>]><container>&xxe;</container>"
        archive(mapOf("META-INF/container.xml" to malicious.toByteArray())) { assertThrows(Exception::class.java) { EpubParser.parse(it) } }
    }
    @Test fun rejectsZipTraversal() { archive(mapOf("../outside" to byteArrayOf(1))) { assertThrows(IllegalArgumentException::class.java) { EpubParser.parse(it) } } }
    @Test fun rejectsHighCompressionRatio() {
        archive(mapOf("META-INF/container.xml" to ByteArray(200000) { 65 })) { assertThrows(IllegalArgumentException::class.java) { EpubParser.parse(it) } }
    }
    @Test fun rejectsOversizedReadWithoutTrustingDeclaredSize() { assertThrows(IllegalArgumentException::class.java) { EpubParser.readLimited(ByteArray(101).inputStream(), 100) } }
    @Test fun rejectsExternalAndEscapingCoverReferences() {
        listOf("../../escape", "https://example.com/cover", "/root", "%2e%2e/%2e%2e/file", "..\\evil", "//host/path").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { EpubParser.resolvePath("OPS/package.opf", path) }
        }
    }
    @Test fun rejectsMalformedXml() { archive(mapOf("META-INF/container.xml" to "<container><bad></container>".toByteArray())) { assertThrows(Exception::class.java) { EpubParser.parse(it) } } }
    @Test fun rejectsTooManyEntriesBeforeIndexingZip() {
        archive((0..4096).associate { "entry-$it" to byteArrayOf(0) }) { assertThrows(IllegalArgumentException::class.java) { EpubParser.parse(it) } }
    }
}
