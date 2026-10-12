package app.tormang.core.common

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveTest {
    private fun archive(vararg names: String): ByteArray { val out = ByteArrayOutputStream(); ZipOutputStream(out).use { zip -> names.forEach { zip.putNextEntry(ZipEntry(it)); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry() } }; return out.toByteArray() }
    @Test fun pagesUseNaturalOrder() { val dir = Files.createTempDirectory("pages").toFile(); try { val pages = ArchiveExtractor.extract(ByteArrayInputStream(archive("10.jpg", "2.jpg", "1.jpg")), dir); assertEquals(listOf("2.jpg", "1.jpg", "0.jpg"), pages.map { it.name }) } finally { dir.deleteRecursively() } }
    @Test fun traversalIsRejectedAndCleaned() { val dir = Files.createTempDirectory("bad").toFile(); try { ArchiveExtractor.extract(ByteArrayInputStream(archive("../escape.jpg")), dir); fail("Traversal accepted") } catch (_: IllegalArgumentException) { assertFalse(dir.exists()) } }
    @Test fun emptyArchiveIsRejected() { val dir = Files.createTempDirectory("empty").toFile(); try { ArchiveExtractor.extract(ByteArrayInputStream(archive("readme.txt")), dir); fail("Empty accepted") } catch (_: IllegalArgumentException) { assertFalse(dir.exists()) } }
    @Test fun identifiersAreStableAndFilesystemSafe() { val id = stableId("../../comic"); assertEquals(64, id.length); assertEquals(id, stableId("../../comic")); assertFalse(id.contains('/')) }
}
