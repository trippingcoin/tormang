package app.tormang.domain.model

import kotlinx.serialization.Serializable

@Serializable data class Manga(
    val id: String, val sourceId: String, val remoteId: String, val title: String,
    val cover: String = "", val alternativeTitles: List<String> = emptyList(),
    val author: String = "", val artist: String = "", val description: String = "",
    val genres: List<String> = emptyList(), val status: String = "Unknown",
    val inLibrary: Boolean = false, val favorite: Boolean = false,
    val addedAt: Long = 0, val updatedAt: Long = 0, val lastReadAt: Long = 0,
    val categoryIds: Set<String> = emptySet(), val attribution: String = "",
)
@Serializable data class Chapter(
    val id: String, val mangaId: String, val remoteId: String, val title: String,
    val number: Double, val uploadedAt: Long = 0, val page: Int = 0,
    val pageCount: Int = 0, val read: Boolean = false, val lastReadAt: Long = 0,
)
@Serializable data class Page(val index: Int, val url: String)
@Serializable data class Category(val id: String, val name: String)
@Serializable data class HistoryEntry(val mangaId: String, val chapterId: String, val lastReadAt: Long, val page: Int)
@Serializable data class UpdateEntry(val chapterId: String, val mangaId: String, val detectedAt: Long)
@Serializable enum class DownloadStatus { QUEUED, RUNNING, PAUSED, FAILED, COMPLETE, CANCELED }
@Serializable data class Download(
    val chapterId: String, val mangaId: String, val title: String,
    val status: DownloadStatus = DownloadStatus.QUEUED, val completed: Int = 0,
    val total: Int = 0, val bytes: Long = 0, val bytesPerSecond: Long = 0, val error: String? = null,
)
@Serializable data class Tracking(val mangaId: String, val status: String = "Reading", val score: Int = 0, val chaptersRead: Int = 0, val notes: String = "")
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
@Serializable enum class ReaderMode { VERTICAL, HORIZONTAL, RTL, LTR, WEBTOON }
@Serializable enum class LibrarySort { TITLE, LAST_READ, LAST_UPDATED, DATE_ADDED, UNREAD }
@Serializable data class Preferences(
    val theme: ThemeMode = ThemeMode.SYSTEM, val dynamicColors: Boolean = true,
    val readerMode: ReaderMode = ReaderMode.VERTICAL, val brightness: Float = -1f,
    val orientation: String = "system", val autoNext: Boolean = true,
    val grid: Boolean = true, val sort: LibrarySort = LibrarySort.TITLE,
    val autoUpdate: Boolean = true, val wifiOnly: Boolean = true,
    val notifications: Boolean = true, val incognito: Boolean = false,
    val downloadTree: String = "", val language: String = "system",
)
data class LibraryItem(val manga: Manga, val unread: Int, val lastChapter: String?)
data class LibraryFilter(val query: String = "", val categoryId: String? = null, val favoritesOnly: Boolean = false, val unreadOnly: Boolean = false)
data class SourceInfo(val id: String, val name: String, val language: String, val baseUrl: String, val capabilities: Set<String>, val filters: List<String> = emptyList())
data class SearchFilters(val genre: String? = null, val sort: String = "latest")
data class MangaPage(val items: List<Manga>, val hasNext: Boolean)
@Serializable data class CatalogRegistration(val id: String, val name: String, val url: String)
@Serializable data class OpenCatalog(val version: Int = 1, val name: String, val language: String = "en", val license: String, val manga: List<CatalogManga>)
@Serializable data class CatalogManga(val id: String, val title: String, val cover: String = "", val author: String = "", val artist: String = "", val description: String = "", val genres: List<String> = emptyList(), val status: String = "Ongoing", val chapters: List<CatalogChapter>)
@Serializable data class CatalogChapter(val id: String, val title: String, val number: Double, val uploadedAt: Long = 0, val pages: List<String>)
@Serializable data class Backup(
    val version: Int = 1, val createdAt: Long, val manga: List<Manga>, val chapters: List<Chapter>,
    val categories: List<Category>, val history: List<HistoryEntry>, val tracking: List<Tracking>,
    val preferences: Preferences, val sources: List<CatalogRegistration> = emptyList(),
)
data class BackupPreview(val backup: Backup, val missingLocalBooks: Int)
