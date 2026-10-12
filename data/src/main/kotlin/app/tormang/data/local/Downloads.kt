package app.tormang.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import androidx.work.*
import app.tormang.core.common.*
import app.tormang.core.database.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import coil.Coil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class DownloadStore @Inject constructor(private val db: TormangDatabase, private val dao: LibraryDao, private val manga: MangaRepository, private val prefs: PreferencesRepository, private val client: OkHttpClient, @ApplicationContext private val context: Context) : DownloadsRepository {
    override val downloads = dao.downloadsFlow().map { it.map { row -> row.value } }
    private suspend fun kick() {
        val wifi = prefs.preferences.first().wifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("chapter-downloads", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    override suspend fun enqueue(chapterIds: List<String>) {
        for (id in chapterIds.distinct()) {
            val c = manga.getChapter(id)
            if (manga.getManga(c.mangaId).sourceId == "local") continue
            db.withTransaction {
                val old = dao.download(id)?.value
                if (old?.status !in listOf(DownloadStatus.COMPLETE, DownloadStatus.RUNNING, DownloadStatus.QUEUED)) dao.putDownload(DownloadRow(id, (old ?: Download(id, c.mangaId, c.title)).copy(status = DownloadStatus.QUEUED, error = null)))
            }
        }
        kick()
    }
    override suspend fun pause(chapterId: String) = status(chapterId, DownloadStatus.PAUSED)
    override suspend fun resume(chapterId: String) { status(chapterId, DownloadStatus.QUEUED); kick() }
    override suspend fun cancel(chapterId: String) = status(chapterId, DownloadStatus.CANCELED)
    private suspend fun status(id: String, status: DownloadStatus) = db.withTransaction { dao.download(id)?.value?.let { dao.putDownload(DownloadRow(id, it.copy(status = status, bytesPerSecond = 0, error = null))) }; Unit }
    override suspend fun delete(chapterId: String) {
        status(chapterId, DownloadStatus.CANCELED)
        withContext(Dispatchers.IO) {
            val pages = dao.pages(chapterId) ?: return@withContext
            pages.local.forEach { removeFile(it.url) }
            db.withTransaction { dao.putPages(pages.copy(local = emptyList())); dao.deleteDownload(chapterId) }
        }
    }
    private fun removeFile(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") { val file = File(uri.path.orEmpty()); if (file.canonicalPath.startsWith(File(context.filesDir, "downloads").canonicalPath + File.separator)) file.delete() }
        else if (uri.scheme == "content") DocumentFile.fromSingleUri(context, uri)?.delete()
    }
    override suspend fun storageBytes() = withContext(Dispatchers.IO) { dao.downloads().sumOf { it.value.bytes } + (File(context.filesDir, "local").walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
    override suspend fun clearCache() = withContext(Dispatchers.IO) { Coil.imageLoader(context).memoryCache?.clear(); Coil.imageLoader(context).diskCache?.clear(); client.cache?.evictAll(); Unit }
    suspend fun drain(): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 7 * 60_000
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val next = dao.downloads().firstOrNull { it.value.status in listOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING) }?.value ?: return@withContext true
            try { download(next, deadline) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                db.withTransaction { dao.download(next.chapterId)?.value?.takeIf { it.status in listOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED) }?.let { dao.putDownload(DownloadRow(it.chapterId, it.copy(status = DownloadStatus.FAILED, error = e.userMessage(), bytesPerSecond = 0))) } }
            }
        }
        false
    }
    private suspend fun active(id: String) = dao.download(id)?.value?.status in listOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)
    private suspend fun download(item: Download, deadline: Long) {
        val id = item.chapterId
        val urls = manga.pages(id)
        val oldPages = dao.pages(id)
        val local = oldPages?.local.orEmpty().toMutableList()
        var bytes = local.sumOf { page ->
            try { context.contentResolver.openAssetFileDescriptor(Uri.parse(page.url), "r")?.use { it.length.coerceAtLeast(0) } ?: 0L } catch (_: java.io.IOException) { 0L }
        }
        val start = System.currentTimeMillis(); val initialBytes = bytes
        val tree = prefs.preferences.first().downloadTree
        for ((index, page) in urls.withIndex()) {
            if (!active(id) || System.currentTimeMillis() >= deadline) return
            if (index < local.size) {
                val valid = try { context.contentResolver.openInputStream(Uri.parse(local[index].url))?.use { true } ?: false } catch (_: Exception) { false }
                if (valid) continue
                local.drop(index).forEach { removeFile(it.url) }; while (local.size > index) local.removeAt(local.lastIndex)
            }
            val folder = File(context.filesDir, "downloads/${stableId(id)}").apply { mkdirs() }
            val temp = File(folder, "$index.part")
            try {
                val request = Request.Builder().url(page.url).build()
                require(request.url.isHttps) { "Загрузка требует HTTPS" }
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful && response.request.url.isHttps) { "Ошибка загрузки HTTP ${response.code}" }
                    val body = response.body ?: error("Пустая страница")
                    val type = body.contentType()?.type
                    require(type == "image") { "Источник вернул не изображение" }
                    body.byteStream().use { input -> temp.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024); var written = 0L; var checkpoint = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            written += n
                            require(written <= ArchiveExtractor.MAX_PAGE_BYTES) { "Страница превышает 64 МБ" }
                            output.write(buffer, 0, n)
                            if (System.currentTimeMillis() - checkpoint > 500) {
                                if (!active(id)) return
                                checkpoint = System.currentTimeMillis()
                                progress(item, index, urls.size, bytes + written, (bytes + written - initialBytes) * 1000 / (checkpoint - start).coerceAtLeast(1))
                            }
                        }
                    } }
                }
                require(temp.length() > 0) { "Пустое изображение" }
                if (!active(id)) return
                val pageBytes = temp.length()
                val uri = if (tree.isBlank()) {
                    val target = File(folder, "$index.img")
                    check(temp.renameTo(target)) { "Не удалось сохранить страницу" }; Uri.fromFile(target)
                } else {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(tree)) ?: error("Папка недоступна")
                    val output = root.createFile("application/octet-stream", "tormang-${stableId(id)}-$index.img") ?: error("Нет доступа к папке загрузок")
                    try { context.contentResolver.openOutputStream(output.uri)?.use { target -> temp.inputStream().use { it.copyTo(target) } } ?: error("Нет доступа к файлу") } catch (e: Exception) { output.delete(); throw e }
                    output.uri
                }
                val accepted = db.withTransaction {
                    if (!active(id)) false else { local += Page(index, uri.toString()); dao.putPages(PagesRow(id, oldPages?.remote ?: urls, local.toList())); true }
                }
                if (!accepted) { removeFile(uri.toString()); return }
                bytes += pageBytes
                progress(item, index + 1, urls.size, bytes, (bytes - initialBytes) * 1000 / (System.currentTimeMillis() - start).coerceAtLeast(1))
            } finally { temp.delete() }
        }
        db.withTransaction { if (active(id)) dao.putDownload(DownloadRow(id, item.copy(status = DownloadStatus.COMPLETE, completed = urls.size, total = urls.size, bytes = bytes))) }
    }
    private suspend fun progress(item: Download, completed: Int, total: Int, bytes: Long, speed: Long) = db.withTransaction {
        if (active(item.chapterId)) dao.putDownload(DownloadRow(item.chapterId, item.copy(status = DownloadStatus.RUNNING, completed = completed, total = total, bytes = bytes, bytesPerSecond = speed, error = null)))
    }
}
