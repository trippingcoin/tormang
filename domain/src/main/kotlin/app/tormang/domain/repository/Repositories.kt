package app.tormang.domain.repository

import app.tormang.domain.model.*
import kotlinx.coroutines.flow.Flow

interface MangaSource {
    val info: SourceInfo
    suspend fun searchManga(query: String, page: Int, filters: SearchFilters = SearchFilters()): MangaPage
    suspend fun getPopularManga(page: Int): MangaPage
    suspend fun getLatestUpdates(page: Int): MangaPage
    suspend fun getMangaDetails(remoteId: String): Manga
    suspend fun getChapterList(remoteId: String): List<Chapter>
    suspend fun getChapterPages(mangaRemoteId: String, chapterRemoteId: String): List<Page>
}
interface SourceRepository {
    fun sources(): Flow<List<SourceInfo>>
    suspend fun source(id: String): MangaSource
    suspend fun addCatalog(name: String, url: String)
    suspend fun removeCatalog(id: String)
}
interface MangaRepository {
    fun observeLibrary(): Flow<List<LibraryItem>>
    fun observeManga(id: String): Flow<Manga?>
    fun observeChapters(mangaId: String): Flow<List<Chapter>>
    fun observeCategories(): Flow<List<Category>>
    fun observeHistory(): Flow<List<HistoryEntry>>
    fun observeUpdates(): Flow<List<UpdateEntry>>
    suspend fun getManga(id: String): Manga
    suspend fun getChapter(id: String): Chapter
    suspend fun cache(manga: List<Manga>)
    suspend fun refresh(id: String): Int
    suspend fun setLibrary(id: String, saved: Boolean)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun addCategory(name: String)
    suspend fun renameCategory(id: String, name: String)
    suspend fun deleteCategory(id: String)
    suspend fun setCategories(id: String, categories: Set<String>)
    suspend fun pages(chapterId: String): List<Page>
    suspend fun saveProgress(chapterId: String, page: Int, pageCount: Int)
    suspend fun markRead(chapterId: String, read: Boolean)
    suspend fun removeHistory(chapterId: String?)
    suspend fun refreshLibrary(): Int
    suspend fun importArchive(uri: String, series: String): String
}
interface PreferencesRepository {
    val preferences: Flow<Preferences>
    suspend fun update(transform: (Preferences) -> Preferences)
}
interface DownloadsRepository {
    val downloads: Flow<List<Download>>
    suspend fun enqueue(chapterIds: List<String>)
    suspend fun pause(chapterId: String)
    suspend fun resume(chapterId: String)
    suspend fun cancel(chapterId: String)
    suspend fun delete(chapterId: String)
    suspend fun storageBytes(): Long
    suspend fun clearCache()
}
interface BackupRepository {
    suspend fun export(uri: String)
    suspend fun preview(uri: String): BackupPreview
    suspend fun restore(preview: BackupPreview)
}
interface TrackingRepository {
    fun observe(mangaId: String): Flow<Tracking?>
    suspend fun save(tracking: Tracking)
}
interface UpdateScheduler { fun schedule(enabled: Boolean, wifiOnly: Boolean); fun refreshNow() }
