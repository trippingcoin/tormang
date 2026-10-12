package app.tormang.data.remote

import app.tormang.core.common.stableId
import app.tormang.core.database.*
import app.tormang.core.network.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class XkcdSource @Inject constructor(private val api: XkcdApi) : MangaSource {
    override val info = SourceInfo("xkcd", "xkcd · Randall Munroe", "en", "https://xkcd.com", setOf("latest", "search-number", "download"), listOf("Номер комикса или заголовок в просматриваемых страницах"))
    private val limiter = Semaphore(3)
    private var latestNumber = 0
    private var fetchedAt = 0L
    private suspend fun latest(): Int {
        if (System.currentTimeMillis() - fetchedAt > 300_000) { latestNumber = api.latest().num; fetchedAt = System.currentTimeMillis() }
        return latestNumber
    }
    private fun XkcdComic.manga() = Manga("xkcd:$num", "xkcd", num.toString(), title, img, author = "Randall Munroe", artist = "Randall Munroe", description = alt, genres = listOf("Webcomic", "Science", "Humor"), status = "Completed", attribution = "© Randall Munroe · CC BY-NC 2.5 · https://xkcd.com/$num/")
    override suspend fun searchManga(query: String, page: Int, filters: SearchFilters): MangaPage {
        require(page > 0)
        query.trim().toIntOrNull()?.let { return MangaPage(if (page == 1) listOf(api.comic(it).manga()) else emptyList(), false) }
        val batch = getLatestUpdates(page)
        return batch.copy(items = batch.items.filter { it.title.contains(query, true) })
    }
    override suspend fun getPopularManga(page: Int) = getLatestUpdates(page)
    override suspend fun getLatestUpdates(page: Int): MangaPage = coroutineScope {
        require(page > 0)
        val high = latest() - (page - 1) * 18
        if (high < 1) return@coroutineScope MangaPage(emptyList(), false)
        val low = (high - 17).coerceAtLeast(1)
        val results = (high downTo low).filter { it != 404 }.map { id -> async { limiter.withPermit { api.comic(id).manga() } } }.awaitAll()
        MangaPage(results, low > 1)
    }
    override suspend fun getMangaDetails(remoteId: String) = api.comic(remoteId.toInt()).manga()
    override suspend fun getChapterList(remoteId: String): List<Chapter> {
        val comic = api.comic(remoteId.toInt())
        val time = LocalDate.of(comic.year.toInt(), comic.month.toInt(), comic.day.toInt()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return listOf(Chapter("xkcd:$remoteId:chapter", "xkcd:$remoteId", remoteId, comic.title, remoteId.toDouble(), time))
    }
    override suspend fun getChapterPages(mangaRemoteId: String, chapterRemoteId: String) = listOf(Page(0, api.comic(chapterRemoteId.toInt()).img))
}

class CatalogSource(private val registration: CatalogRegistration, private val client: OkHttpClient) : MangaSource {
    override val info = SourceInfo(registration.id, registration.name, "multi", registration.url, setOf("search", "latest", "genres", "download"), listOf("genre", "title", "latest"))
    private var cached: OpenCatalog? = null
    private var fetchedAt = 0L
    suspend fun catalog(): OpenCatalog = withContext(Dispatchers.IO) {
        cached?.takeIf { System.currentTimeMillis() - fetchedAt < 60_000 }?.let { return@withContext it }
        val url = registration.url.toHttpUrl()
        require(url.isHttps && url.username.isBlank() && url.password.isBlank()) { "Catalog requires a public HTTPS URL without credentials" }
        val data = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Источник вернул HTTP ${response.code}" }
            check(response.request.url.isHttps) { "Unsafe redirect" }
            val body = response.body ?: error("Empty catalog")
            val bytes = body.byteStream().readBytesLimited(8 * 1024 * 1024)
            Json { ignoreUnknownKeys = true }.decodeFromString<OpenCatalog>(bytes.decodeToString())
        }
        require(data.version == 1 && data.license.isNotBlank()) { "Catalog must declare version 1 and a content license" }
        require(data.manga.map { it.id }.distinct().size == data.manga.size) { "Duplicate catalog IDs" }
        data.manga.forEach { book ->
            require(book.id.isNotBlank() && book.chapters.map { it.id }.distinct().size == book.chapters.size) { "Invalid catalog identifiers" }
            require(book.cover.isBlank() || book.cover.toHttpUrl().isHttps) { "Cover must use HTTPS" }
            book.chapters.forEach { chapter ->
                require(chapter.number.isFinite() && chapter.pages.isNotEmpty() && chapter.pages.size <= 5000) { "Invalid chapter" }
                require(chapter.pages.all { it.toHttpUrl().isHttps }) { "Pages must use HTTPS" }
            }
        }
        cached = data; fetchedAt = System.currentTimeMillis(); data
    }
    private fun CatalogManga.model(license: String) = Manga("${info.id}:$id", info.id, id, title, cover, author = author, artist = artist, description = description, genres = genres, status = status, attribution = license)
    override suspend fun searchManga(query: String, page: Int, filters: SearchFilters): MangaPage {
        require(page > 0)
        val catalog = catalog()
        val filtered = catalog.manga.filter { it.title.contains(query, true) && (filters.genre.isNullOrBlank() || it.genres.any { genre -> genre.equals(filters.genre, true) }) }
        val sorted = if (filters.sort == "title") filtered.sortedBy { it.title.lowercase() } else filtered.sortedByDescending { it.chapters.maxOfOrNull { chapter -> chapter.uploadedAt } ?: 0 }
        return MangaPage(sorted.drop((page - 1) * 24).take(24).map { it.model(catalog.license) }, page * 24 < sorted.size)
    }
    override suspend fun getPopularManga(page: Int) = searchManga("", page, SearchFilters(sort = "title"))
    override suspend fun getLatestUpdates(page: Int) = searchManga("", page, SearchFilters())
    override suspend fun getMangaDetails(remoteId: String): Manga { val c = catalog(); return c.manga.first { it.id == remoteId }.model(c.license) }
    override suspend fun getChapterList(remoteId: String) = catalog().manga.first { it.id == remoteId }.chapters.map { Chapter("${info.id}:$remoteId:${it.id}", "${info.id}:$remoteId", it.id, it.title, it.number, it.uploadedAt) }
    override suspend fun getChapterPages(mangaRemoteId: String, chapterRemoteId: String) = catalog().manga.first { it.id == mangaRemoteId }.chapters.first { it.id == chapterRemoteId }.pages.mapIndexed { index, url -> Page(index, url) }
}
class LocalSource(private val dao: LibraryDao) : MangaSource {
    override val info = SourceInfo("local", "На устройстве · CBZ / ZIP", "multi", "", setOf("search", "offline", "import"))
    override suspend fun searchManga(query: String, page: Int, filters: SearchFilters): MangaPage {
        val items = dao.allManga().map { it.value }.filter { it.sourceId == "local" && it.title.contains(query, true) }.sortedBy { it.title }
        return MangaPage(items.drop((page - 1) * 24).take(24), page * 24 < items.size)
    }
    override suspend fun getPopularManga(page: Int) = searchManga("", page, SearchFilters())
    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
    override suspend fun getMangaDetails(remoteId: String) = dao.manga("local:$remoteId")?.value ?: error("Локальная книга не найдена")
    override suspend fun getChapterList(remoteId: String) = dao.chapters("local:$remoteId").map { it.value }
    override suspend fun getChapterPages(mangaRemoteId: String, chapterRemoteId: String) = dao.pages(chapterRemoteId)?.local?.takeIf { it.isNotEmpty() } ?: error("Импортируйте исходный архив на этом устройстве")
}
@Singleton class Sources @Inject constructor(private val dao: LibraryDao, private val xkcd: XkcdSource, private val client: OkHttpClient) : SourceRepository {
    private val instances = ConcurrentHashMap<String, CatalogSource>()
    override fun sources(): Flow<List<SourceInfo>> = dao.sourcesFlow().map { listOf(LocalSource(dao).info, xkcd.info) + it.map { row -> CatalogSource(row.value, client).info } }
    override suspend fun source(id: String): MangaSource = when (id) {
        "local" -> LocalSource(dao)
        "xkcd" -> xkcd
        else -> {
            val registration = dao.sources().firstOrNull { it.id == id }?.value ?: error("Источник не подключён. Добавьте каталог в разделе «Обзор».")
            instances.getOrPut(id) { CatalogSource(registration, client) }
        }
    }
    override suspend fun addCatalog(name: String, url: String) {
        require(name.isNotBlank()) { "Введите название" }
        val registration = CatalogRegistration(stableId(url.trim()), name.trim(), url.trim())
        val source = CatalogSource(registration, client)
        source.catalog()
        dao.putSource(SourceRow(registration.id, registration)); instances[registration.id] = source
    }
    override suspend fun removeCatalog(id: String) { dao.deleteSource(id); instances.remove(id) }
}
fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) { val count = read(buffer); if (count < 0) break; require(out.size() + count <= limit) { "File exceeds size limit" }; out.write(buffer, 0, count) }
    return out.toByteArray()
}
