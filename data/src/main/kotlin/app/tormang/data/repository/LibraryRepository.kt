package app.tormang.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import app.tormang.core.common.*
import app.tormang.core.database.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import app.tormang.domain.usecase.ReadingProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class LibraryRepository @Inject constructor(
    private val db: TormangDatabase, private val dao: LibraryDao, private val sources: SourceRepository,
    private val preferences: PreferencesRepository, @ApplicationContext private val context: Context,
) : MangaRepository {
    private val refreshLock = Mutex()
    override fun observeLibrary(): Flow<List<LibraryItem>> = dao.librarySummaries().map { rows -> rows.map { LibraryItem(it.row.value, it.unread, it.lastChapter) } }
    override fun observeManga(id: String) = dao.mangaFlow(id).map { it?.value }
    override fun observeChapters(mangaId: String) = dao.chaptersFlow(mangaId).map { it.map { row -> row.value } }
    override fun observeCategories() = dao.categoriesFlow().map { it.map { row -> row.value } }
    override fun observeHistory() = dao.historyFlow().map { it.map { row -> row.value } }
    override fun observeUpdates() = dao.updatesFlow().map { it.map { row -> row.value } }
    override suspend fun getManga(id: String) = dao.manga(id)?.value ?: error("Произведение не найдено")
    override suspend fun getChapter(id: String) = dao.chapter(id)?.value ?: error("Глава не найдена")
    private suspend fun change(id: String, transform: (Manga) -> Manga) = db.withTransaction { dao.putManga(transform(getManga(id)).row()) }
    override suspend fun cache(manga: List<Manga>) = db.withTransaction {
        manga.forEach { fresh ->
            val old = dao.manga(fresh.id)?.value
            dao.putManga((if (old == null) fresh else fresh.copy(inLibrary = old.inLibrary, favorite = old.favorite, addedAt = old.addedAt, lastReadAt = old.lastReadAt, updatedAt = old.updatedAt, categoryIds = old.categoryIds)).row())
        }
    }
    override suspend fun refresh(id: String): Int = refreshLock.withLock {
        val manga = getManga(id)
        val source = sources.source(manga.sourceId)
        val detail = source.getMangaDetails(manga.remoteId)
        val incoming = source.getChapterList(manga.remoteId)
        var count = 0
        db.withTransaction {
            val old = dao.chapters(id).associateBy { it.id }
            val now = System.currentTimeMillis()
            incoming.forEach { chapter ->
                val saved = old[chapter.id]?.value
                dao.putChapter((if (saved == null) chapter else chapter.copy(page = saved.page, pageCount = saved.pageCount, read = saved.read, lastReadAt = saved.lastReadAt)).row())
                if (saved == null && old.isNotEmpty()) { dao.putUpdate(UpdateRow(chapter.id, now, UpdateEntry(chapter.id, id, now))); count++ }
            }
            cache(listOf(detail))
            if (count > 0 || old.isEmpty()) change(id) { it.copy(updatedAt = now) }
        }
        count
    }
    override suspend fun setLibrary(id: String, saved: Boolean) = change(id) { it.copy(inLibrary = saved, addedAt = if (saved && it.addedAt == 0L) System.currentTimeMillis() else it.addedAt) }
    override suspend fun setFavorite(id: String, favorite: Boolean) = change(id) { it.copy(favorite = favorite) }
    override suspend fun addCategory(name: String) { require(name.isNotBlank()); val category = Category(UUID.randomUUID().toString(), name.trim()); dao.putCategory(CategoryRow(category.id, category)) }
    override suspend fun renameCategory(id: String, name: String) { require(name.isNotBlank()); dao.putCategory(CategoryRow(id, Category(id, name.trim()))) }
    override suspend fun deleteCategory(id: String) = db.withTransaction {
        dao.allManga().filter { id in it.value.categoryIds }.forEach { dao.putManga(it.value.copy(categoryIds = it.value.categoryIds - id).row()) }
        dao.deleteCategory(id)
    }
    override suspend fun setCategories(id: String, categories: Set<String>) = change(id) { it.copy(categoryIds = categories) }
    override suspend fun pages(chapterId: String): List<Page> {
        val stored = dao.pages(chapterId)
        if (stored?.local?.isNotEmpty() == true && (stored.remote.isEmpty() || stored.local.size == stored.remote.size) && withContext(Dispatchers.IO) { stored.local.all { page ->
            try { context.contentResolver.openInputStream(Uri.parse(page.url))?.use { true } ?: false } catch (_: java.io.IOException) { false } catch (_: SecurityException) { false }
        } }) return stored.local
        if (stored?.remote?.isNotEmpty() == true) return stored.remote
        val chapter = getChapter(chapterId); val manga = getManga(chapter.mangaId)
        val pages = sources.source(manga.sourceId).getChapterPages(manga.remoteId, chapter.remoteId)
        require(pages.isNotEmpty()) { "В главе нет доступных страниц" }
        dao.putPages(PagesRow(chapterId, pages)); return pages
    }
    override suspend fun saveProgress(chapterId: String, page: Int, pageCount: Int) {
        val incognito = preferences.preferences.first().incognito
        db.withTransaction {
            val now = System.currentTimeMillis()
            val chapter = ReadingProgress.apply(getChapter(chapterId), page, pageCount, now)
            dao.putChapter(chapter.row())
            change(chapter.mangaId) { it.copy(lastReadAt = now) }
            if (!incognito) dao.putHistory(HistoryRow(chapterId, now, HistoryEntry(chapter.mangaId, chapterId, now, chapter.page)))
            val track = dao.tracking().firstOrNull { it.mangaId == chapter.mangaId }?.value
            if (track != null) dao.putTracking(TrackingRow(track.mangaId, track.copy(chaptersRead = dao.chapters(track.mangaId).count { it.value.read })))
        }
    }
    override suspend fun markRead(chapterId: String, read: Boolean) = db.withTransaction { val c = getChapter(chapterId); dao.putChapter(c.copy(read = read, page = if (read) (c.pageCount - 1).coerceAtLeast(0) else 0).row())
        dao.tracking().firstOrNull { it.mangaId == c.mangaId }?.value?.let { track -> dao.putTracking(TrackingRow(track.mangaId, track.copy(chaptersRead = dao.chapters(c.mangaId).count { it.value.read }))) }
        Unit
    }
    override suspend fun removeHistory(chapterId: String?) { if (chapterId == null) dao.clearHistory() else dao.removeHistory(chapterId) }
    override suspend fun refreshLibrary(): Int {
        var count = 0; val errors = mutableListOf<String>()
        dao.allManga().filter { it.inLibrary && it.value.sourceId != "local" && it.value.status != "Completed" }.forEach {
            try { count += refresh(it.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += it.value.title }
            delay(1500)
        }
        dao.pruneUpdates(System.currentTimeMillis() - 90L * 86400000)
        check(errors.isEmpty()) { "Не удалось обновить: ${errors.take(5).joinToString()}. Повторите позже." }
        return count
    }
    override suspend fun importArchive(uri: String, series: String): String = withContext(Dispatchers.IO) {
        require(series.isNotBlank()) { "Введите название серии" }
        val parsed = Uri.parse(uri)
        val name = context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: parsed.lastPathSegment ?: "Глава"
        val remoteId = stableId(series.trim().lowercase())
        val mangaId = "local:$remoteId"
        val chapterId = "local:$remoteId:${stableId(name)}"
        require(dao.chapter(chapterId) == null || dao.pages(chapterId)?.local.isNullOrEmpty()) { "Архив с таким именем уже импортирован" }
        val folder = File(context.filesDir, "local/${stableId(chapterId)}-${UUID.randomUUID()}")
        val input = context.contentResolver.openInputStream(parsed) ?: error("Не удалось открыть архив")
        val files = input.use { ArchiveExtractor.extract(it, folder) }
        try {
            db.withTransaction {
                val old = dao.manga(mangaId)?.value
                val manga = old?.copy(cover = if (old.cover.isBlank() || !File(Uri.parse(old.cover).path.orEmpty()).exists()) Uri.fromFile(files.first()).toString() else old.cover, inLibrary = true)
                    ?: Manga(mangaId, "local", remoteId, series.trim(), Uri.fromFile(files.first()).toString(), description = "Импортированный пользовательский архив", inLibrary = true, addedAt = System.currentTimeMillis())
                dao.putManga(manga.row())
                val saved = dao.chapter(chapterId)?.value
                val number = Regex("[0-9]+(?:\\.[0-9]+)?").find(name)?.value?.toDoubleOrNull() ?: ((dao.chapters(mangaId).maxOfOrNull { it.number } ?: 0.0) + 1)
                val chapter = saved?.copy(pageCount = files.size) ?: Chapter(chapterId, mangaId, chapterId, name.substringBeforeLast('.'), number, pageCount = files.size)
                dao.putChapter(chapter.row())
                dao.putPages(PagesRow(chapterId, emptyList(), files.mapIndexed { i, file -> Page(i, Uri.fromFile(file).toString()) }))
            }
        } catch (e: Exception) { folder.deleteRecursively(); throw e }
        mangaId
    }
}
