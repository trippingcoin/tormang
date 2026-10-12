package app.tormang.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.tormang.core.database.*
import app.tormang.data.remote.readBytesLimited
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import app.tormang.domain.usecase.BackupValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class Backups @Inject constructor(private val db: TormangDatabase, private val dao: LibraryDao, private val preferences: PreferencesRepository, @ApplicationContext private val context: Context) : BackupRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    override suspend fun export(uri: String) = withContext(Dispatchers.IO) {
        val prefs = preferences.preferences.first()
        val backup = db.withTransaction { Backup(createdAt = System.currentTimeMillis(), manga = dao.allManga().map { it.value }, chapters = dao.allChapters().map { it.value }, categories = dao.categories().map { it.value }, history = dao.history().map { it.value }, tracking = dao.tracking().map { it.value }, preferences = prefs.copy(downloadTree = ""), sources = dao.sources().map { it.value }) }
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.bufferedWriter()?.use { it.write(json.encodeToString(backup)) } ?: error("Не удалось сохранить файл")
    }
    override suspend fun preview(uri: String): BackupPreview = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytesLimited(32 * 1024 * 1024) } ?: error("Не удалось открыть резервную копию")
        val backup = json.decodeFromString<Backup>(bytes.decodeToString())
        BackupValidator.validate(backup)
        BackupPreview(backup, backup.manga.count { it.sourceId == "local" })
    }
    override suspend fun restore(preview: BackupPreview) {
        val b = preview.backup; BackupValidator.validate(b)
        db.withTransaction {
            b.categories.forEach { dao.putCategory(CategoryRow(it.id, it)) }
            b.manga.forEach { book ->
                val existing = dao.manga(book.id)?.value
                val cover = if (book.sourceId == "local") existing?.cover.orEmpty() else book.cover.takeIf { it.startsWith("https://") }.orEmpty()
                dao.putManga(book.copy(cover = cover).row())
            }
            b.chapters.forEach { dao.putChapter(it.row()) }
            b.history.forEach { dao.putHistory(HistoryRow(it.chapterId, it.lastReadAt, it)) }
            b.tracking.forEach { dao.putTracking(TrackingRow(it.mangaId, it)) }
            b.sources.forEach { dao.putSource(SourceRow(it.id, it)) }
        }
        preferences.update { b.preferences.copy(downloadTree = it.downloadTree) }
    }
}
class TrackingStore @Inject constructor(private val dao: LibraryDao) : TrackingRepository {
    override fun observe(mangaId: String) = dao.trackingFlow(mangaId).map { it?.value }
    override suspend fun save(tracking: Tracking) { require(tracking.score in 0..10 && tracking.chaptersRead >= 0); dao.putTracking(TrackingRow(tracking.mangaId, tracking.copy(chaptersRead = dao.chapters(tracking.mangaId).count { it.value.read }))) }
}
