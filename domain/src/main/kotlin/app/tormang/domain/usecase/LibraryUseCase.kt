package app.tormang.domain.usecase

import app.tormang.domain.model.*

class FilterLibrary {
    operator fun invoke(items: List<LibraryItem>, filter: LibraryFilter, sort: LibrarySort): List<LibraryItem> {
        val filtered = items.filter {
            (filter.query.isBlank() || it.manga.title.contains(filter.query, true)) &&
                (filter.categoryId == null || filter.categoryId in it.manga.categoryIds) &&
                (!filter.favoritesOnly || it.manga.favorite) && (!filter.unreadOnly || it.unread > 0)
        }
        return when (sort) {
            LibrarySort.TITLE -> filtered.sortedBy { it.manga.title.lowercase() }
            LibrarySort.LAST_READ -> filtered.sortedByDescending { it.manga.lastReadAt }
            LibrarySort.LAST_UPDATED -> filtered.sortedByDescending { it.manga.updatedAt }
            LibrarySort.DATE_ADDED -> filtered.sortedByDescending { it.manga.addedAt }
            LibrarySort.UNREAD -> filtered.sortedByDescending { it.unread }
        }
    }
}
object ReadingProgress {
    fun apply(chapter: Chapter, page: Int, count: Int, now: Long): Chapter {
        require(count > 0) { "Chapter has no pages" }
        val safePage = page.coerceIn(0, count - 1)
        return chapter.copy(page = safePage, pageCount = count, read = chapter.read || safePage == count - 1, lastReadAt = now)
    }
    fun next(chapters: List<Chapter>, current: String): Chapter? {
        val sorted = chapters.sortedBy { it.number }
        val index = sorted.indexOfFirst { it.id == current }
        return if (index < 0) null else sorted.getOrNull(index + 1)
    }
}
object BackupValidator {
    fun validate(backup: Backup) {
        require(backup.version == 1) { "Unsupported backup version: ${backup.version}" }
        require(backup.manga.size <= 100_000 && backup.chapters.size <= 1_000_000) { "Backup is too large" }
        val books = backup.manga.map { it.id }.toSet()
        val chapters = backup.chapters.map { it.id }.toSet()
        val categories = backup.categories.map { it.id }.toSet()
        require(books.size == backup.manga.size && chapters.size == backup.chapters.size && categories.size == backup.categories.size) { "Duplicate identifiers" }
        require(backup.manga.all { it.id.isNotBlank() && it.sourceId.isNotBlank() && it.remoteId.isNotBlank() && it.title.isNotBlank() && categories.containsAll(it.categoryIds) }) { "Invalid library metadata" }
        require(backup.chapters.all { it.mangaId in books && it.page >= 0 && it.pageCount >= 0 && (if (it.pageCount == 0) it.page == 0 else it.page < it.pageCount) && it.number.isFinite() }) { "Invalid chapters" }
        require(backup.history.all { h -> backup.chapters.any { it.id == h.chapterId && it.mangaId == h.mangaId } && h.page >= 0 }) { "Invalid history references" }
        require(backup.tracking.all { it.mangaId in books && it.score in 0..10 && it.chaptersRead >= 0 }) { "Invalid tracking" }
        require(backup.preferences.brightness == -1f || backup.preferences.brightness in 0f..1f) { "Invalid brightness" }
        require(backup.preferences.orientation in listOf("system", "portrait", "landscape")) { "Invalid orientation" }
        require(backup.preferences.language in listOf("system", "ru", "en")) { "Invalid language" }
        require(backup.sources.map { it.id }.distinct().size == backup.sources.size) { "Duplicate sources" }
        require(backup.sources.all { it.url.startsWith("https://") && it.id !in listOf("local", "xkcd") }) { "Invalid sources" }
    }
}
