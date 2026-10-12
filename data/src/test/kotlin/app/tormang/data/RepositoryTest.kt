package app.tormang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tormang.core.database.*
import app.tormang.data.repository.LibraryRepository
import app.tormang.data.repository.Backups
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: TormangDatabase
    private lateinit var repo: LibraryRepository
    private val book = Manga("m", "test", "m", "Book", inLibrary = true)
    private val chapter = Chapter("c", "m", "c", "One", 1.0)
    private var sourceChapters = listOf(chapter)
    private val preferences = object : PreferencesRepository { val state = MutableStateFlow(Preferences()); override val preferences = state; override suspend fun update(transform: (Preferences) -> Preferences) { state.value = transform(state.value) } }
    private val source = object : MangaSource {
        override val info = SourceInfo("test", "Fixture", "en", "https://example.org", emptySet())
        override suspend fun searchManga(query: String, page: Int, filters: SearchFilters) = MangaPage(listOf(book), false)
        override suspend fun getPopularManga(page: Int) = MangaPage(listOf(book), false)
        override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
        override suspend fun getMangaDetails(remoteId: String) = book.copy(title = "Refreshed")
        override suspend fun getChapterList(remoteId: String) = sourceChapters
        override suspend fun getChapterPages(mangaRemoteId: String, chapterRemoteId: String) = listOf(Page(0, "https://example.org/a.png"), Page(1, "https://example.org/b.png"))
    }
    private val sources = object : SourceRepository { override fun sources() = flowOf(listOf(source.info)); override suspend fun source(id: String) = source; override suspend fun addCatalog(name: String, url: String) = Unit; override suspend fun removeCatalog(id: String) = Unit }
    @Before fun setup() { val context = ApplicationProvider.getApplicationContext<Context>(); db = Room.inMemoryDatabaseBuilder(context, TormangDatabase::class.java).allowMainThreadQueries().build(); repo = LibraryRepository(db, db.library(), sources, preferences, context) }
    @After fun close() { db.close() }
    @Test fun refreshPreservesProgressAndDetectsOnlyNewChapters() = runTest {
        repo.cache(listOf(book)); repo.refresh("m"); repo.saveProgress("c", 0, 3)
        sourceChapters = listOf(chapter, chapter.copy(id = "second", number = 2.0))
        assertEquals(1, repo.refresh("m")); assertEquals(3, repo.getChapter("c").pageCount)
        assertEquals(0, repo.refresh("m")); assertEquals(1, repo.observeUpdates().first().size)
    }
    @Test fun clearingHistoryPreservesPosition() = runTest { repo.cache(listOf(book)); repo.refresh("m"); repo.saveProgress("c", 1, 4); repo.removeHistory(null); assertTrue(repo.observeHistory().first().isEmpty()); assertEquals(1, repo.getChapter("c").page) }
    @Test fun categoryDeletionRemovesAssignments() = runTest { repo.cache(listOf(book)); repo.addCategory("Weekend"); val c = repo.observeCategories().first().single(); repo.setCategories("m", setOf(c.id)); repo.deleteCategory(c.id); assertTrue(repo.getManga("m").categoryIds.isEmpty()) }
    @Test fun incognitoKeepsProgressWithoutHistory() = runTest { repo.cache(listOf(book)); repo.refresh("m"); preferences.update { it.copy(incognito = true) }; repo.saveProgress("c", 1, 2); assertTrue(repo.getChapter("c").read); assertTrue(repo.observeHistory().first().isEmpty()) }
    @Test fun partialDownloadCannotTruncateChapter() = runTest { repo.cache(listOf(book)); repo.refresh("m"); val remote = source.getChapterPages("m", "c"); db.library().putPages(PagesRow("c", remote, listOf(Page(0, "file:///missing")))); assertEquals(2, repo.pages("c").size) }
    @Test fun invalidRestoreMakesNoDatabaseChanges() = runTest {
        repo.cache(listOf(book)); val backup = Backup(version = 9, createdAt = 0, manga = emptyList(), chapters = emptyList(), categories = emptyList(), history = emptyList(), tracking = emptyList(), preferences = Preferences())
        val service = Backups(db, db.library(), preferences, ApplicationProvider.getApplicationContext())
        try { service.restore(BackupPreview(backup, 0)); fail("Invalid restore accepted") } catch (_: IllegalArgumentException) { assertEquals("Book", repo.getManga("m").title) }
    }
    @Test fun backupExportsValidatesAndRestoresMetadata() = runTest {
        repo.cache(listOf(book)); repo.refresh("m"); repo.saveProgress("c", 1, 3)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Backups(db, db.library(), preferences, context)
        val file = java.io.File(context.cacheDir, "roundtrip.json")
        val uri = android.net.Uri.fromFile(file).toString()
        try {
            service.export(uri); val preview = service.preview(uri)
            repo.markRead("c", true); repo.setLibrary("m", false)
            service.restore(preview)
            assertTrue(repo.getManga("m").inLibrary)
            assertFalse(repo.getChapter("c").read)
            assertEquals(1, repo.getChapter("c").page)
            assertEquals(1, repo.observeHistory().first().size)
        } finally { file.delete() }
    }
    @Test fun manualReadStatusUpdatesTrackingCount() = runTest {
        repo.cache(listOf(book)); repo.refresh("m")
        db.library().putTracking(TrackingRow("m", Tracking("m")))
        repo.markRead("c", true)
        assertEquals(1, db.library().tracking().single().value.chaptersRead)
        repo.markRead("c", false)
        assertEquals(0, db.library().tracking().single().value.chaptersRead)
    }

}
