package app.tormang.domain

import app.tormang.domain.model.*
import app.tormang.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val chapter = Chapter("c", "m", "c", "Chapter", 1.0)
    private fun backup() = Backup(createdAt = 1, manga = listOf(Manga("m", "s", "m", "Book")), chapters = listOf(chapter), categories = emptyList(), history = emptyList(), tracking = emptyList(), preferences = Preferences())
    @Test fun progressClampsAndCompletes() { val c = ReadingProgress.apply(chapter, 99, 3, 5); assertEquals(2, c.page); assertTrue(c.read); assertEquals(5L, c.lastReadAt) }
    @Test fun rereadingDoesNotClearReadFlag() { assertTrue(ReadingProgress.apply(chapter.copy(read = true), 0, 3, 5).read) }
    @Test(expected = IllegalArgumentException::class) fun emptyChapterRejected() { ReadingProgress.apply(chapter, 0, 0, 0) }
    @Test fun nextChapterUsesNumericOrder() { val second = chapter.copy(id = "second", number = 2.0); assertEquals(second, ReadingProgress.next(listOf(second, chapter), "c")); assertNull(ReadingProgress.next(listOf(chapter), "missing")) }
    @Test fun filtersAndSortingCompose() {
        val a = LibraryItem(Manga("a", "s", "a", "Alpha", favorite = true, categoryIds = setOf("cat")), 2, null)
        val b = LibraryItem(Manga("b", "s", "b", "Beta"), 9, null)
        assertEquals(listOf(a), FilterLibrary()(listOf(b, a), LibraryFilter(query = "AL", categoryId = "cat", favoritesOnly = true, unreadOnly = true), LibrarySort.UNREAD))
        assertEquals(listOf(b, a), FilterLibrary()(listOf(a, b), LibraryFilter(), LibrarySort.UNREAD))
    }
    @Test fun validBackupAccepted() { BackupValidator.validate(backup()) }
    @Test(expected = IllegalArgumentException::class) fun futureBackupRejected() { BackupValidator.validate(backup().copy(version = 9)) }
    @Test(expected = IllegalArgumentException::class) fun danglingChapterRejected() { BackupValidator.validate(backup().copy(chapters = listOf(chapter.copy(mangaId = "absent")))) }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() { BackupValidator.validate(backup().copy(chapters = listOf(chapter, chapter))) }
    @Test(expected = IllegalArgumentException::class) fun danglingHistoryRejected() { BackupValidator.validate(backup().copy(history = listOf(HistoryEntry("m", "missing", 0, 0)))) }
    @Test(expected = IllegalArgumentException::class) fun invalidBrightnessRejected() { BackupValidator.validate(backup().copy(preferences = Preferences(brightness = Float.NaN))) }
    @Test(expected = IllegalArgumentException::class) fun outOfRangeBackupPageRejected() { BackupValidator.validate(backup().copy(chapters = listOf(chapter.copy(page = 5, pageCount = 2)))) }

}
