package app.tormang.core.database

import android.content.Context
import androidx.room.*
import app.tormang.domain.model.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Entity(tableName = "manga", indices = [Index("inLibrary")])
data class MangaRow(@PrimaryKey val id: String, val inLibrary: Boolean, val value: Manga)
@Entity(tableName = "chapters", indices = [Index("mangaId"), Index(value = ["mangaId", "read"]), Index(value = ["mangaId", "lastReadAt"])], foreignKeys = [ForeignKey(entity = MangaRow::class, parentColumns = ["id"], childColumns = ["mangaId"], onDelete = ForeignKey.CASCADE)])
data class ChapterRow(@PrimaryKey val id: String, val mangaId: String, val number: Double, val value: Chapter, val read: Boolean = value.read, val lastReadAt: Long = value.lastReadAt, val title: String = value.title)
@Entity(tableName = "categories") data class CategoryRow(@PrimaryKey val id: String, val value: Category)
@Entity(tableName = "history", indices = [Index("lastReadAt")]) data class HistoryRow(@PrimaryKey val chapterId: String, val lastReadAt: Long, val value: HistoryEntry)
@Entity(tableName = "updates", indices = [Index("detectedAt")]) data class UpdateRow(@PrimaryKey val chapterId: String, val detectedAt: Long, val value: UpdateEntry)
@Entity(tableName = "downloads") data class DownloadRow(@PrimaryKey val chapterId: String, val value: Download)
@Entity(tableName = "pages") data class PagesRow(@PrimaryKey val chapterId: String, val remote: List<Page>, val local: List<Page> = emptyList())
@Entity(tableName = "tracking") data class TrackingRow(@PrimaryKey val mangaId: String, val value: Tracking)
@Entity(tableName = "sources") data class SourceRow(@PrimaryKey val id: String, val value: CatalogRegistration)

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    @TypeConverter fun manga(value: Manga) = json.encodeToString(value)
    @TypeConverter fun manga(value: String) = json.decodeFromString<Manga>(value)
    @TypeConverter fun chapter(value: Chapter) = json.encodeToString(value)
    @TypeConverter fun chapter(value: String) = json.decodeFromString<Chapter>(value)
    @TypeConverter fun category(value: Category) = json.encodeToString(value)
    @TypeConverter fun category(value: String) = json.decodeFromString<Category>(value)
    @TypeConverter fun history(value: HistoryEntry) = json.encodeToString(value)
    @TypeConverter fun history(value: String) = json.decodeFromString<HistoryEntry>(value)
    @TypeConverter fun update(value: UpdateEntry) = json.encodeToString(value)
    @TypeConverter fun update(value: String) = json.decodeFromString<UpdateEntry>(value)
    @TypeConverter fun download(value: Download) = json.encodeToString(value)
    @TypeConverter fun download(value: String) = json.decodeFromString<Download>(value)
    @TypeConverter fun pages(value: List<Page>) = json.encodeToString(value)
    @TypeConverter fun pages(value: String) = json.decodeFromString<List<Page>>(value)
    @TypeConverter fun tracking(value: Tracking) = json.encodeToString(value)
    @TypeConverter fun tracking(value: String) = json.decodeFromString<Tracking>(value)
    @TypeConverter fun source(value: CatalogRegistration) = json.encodeToString(value)
    @TypeConverter fun source(value: String) = json.decodeFromString<CatalogRegistration>(value)
}
data class LibrarySummary(@Embedded val row: MangaRow, val unread: Int, val lastChapter: String?)
@Dao interface LibraryDao {
    @Query("SELECT manga.*, (SELECT COUNT(*) FROM chapters WHERE chapters.mangaId = manga.id AND chapters.read = 0) AS unread, (SELECT title FROM chapters WHERE chapters.mangaId = manga.id AND lastReadAt > 0 ORDER BY lastReadAt DESC LIMIT 1) AS lastChapter FROM manga WHERE inLibrary = 1")
    fun librarySummaries(): Flow<List<LibrarySummary>>
    @Query("SELECT * FROM manga WHERE inLibrary = 1") fun library(): Flow<List<MangaRow>>
    @Query("SELECT * FROM manga WHERE id = :id") fun mangaFlow(id: String): Flow<MangaRow?>
    @Query("SELECT * FROM manga WHERE id = :id") suspend fun manga(id: String): MangaRow?
    @Query("SELECT * FROM manga") suspend fun allManga(): List<MangaRow>
    @Upsert suspend fun putManga(value: MangaRow)
    @Query("SELECT * FROM chapters WHERE mangaId = :id ORDER BY number") fun chaptersFlow(id: String): Flow<List<ChapterRow>>
    @Query("SELECT * FROM chapters") fun allChaptersFlow(): Flow<List<ChapterRow>>
    @Query("SELECT * FROM chapters") suspend fun allChapters(): List<ChapterRow>
    @Query("SELECT * FROM chapters WHERE mangaId = :id ORDER BY number") suspend fun chapters(id: String): List<ChapterRow>
    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun chapter(id: String): ChapterRow?
    @Upsert suspend fun putChapter(value: ChapterRow)
    @Query("SELECT * FROM categories") fun categoriesFlow(): Flow<List<CategoryRow>>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryRow>
    @Upsert suspend fun putCategory(value: CategoryRow)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("SELECT * FROM history ORDER BY lastReadAt DESC") fun historyFlow(): Flow<List<HistoryRow>>
    @Query("SELECT * FROM history ORDER BY lastReadAt DESC") suspend fun history(): List<HistoryRow>
    @Upsert suspend fun putHistory(value: HistoryRow)
    @Query("DELETE FROM history WHERE chapterId = :id") suspend fun removeHistory(id: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT * FROM updates ORDER BY detectedAt DESC LIMIT 1000") fun updatesFlow(): Flow<List<UpdateRow>>
    @Upsert suspend fun putUpdate(value: UpdateRow)
    @Query("DELETE FROM updates WHERE detectedAt < :before") suspend fun pruneUpdates(before: Long)
    @Query("SELECT * FROM downloads") fun downloadsFlow(): Flow<List<DownloadRow>>
    @Query("SELECT * FROM downloads") suspend fun downloads(): List<DownloadRow>
    @Query("SELECT * FROM downloads WHERE chapterId = :id") suspend fun download(id: String): DownloadRow?
    @Upsert suspend fun putDownload(value: DownloadRow)
    @Query("DELETE FROM downloads WHERE chapterId = :id") suspend fun deleteDownload(id: String)
    @Query("SELECT * FROM pages WHERE chapterId = :id") suspend fun pages(id: String): PagesRow?
    @Upsert suspend fun putPages(value: PagesRow)
    @Query("SELECT * FROM tracking WHERE mangaId = :id") fun trackingFlow(id: String): Flow<TrackingRow?>
    @Query("SELECT * FROM tracking") suspend fun tracking(): List<TrackingRow>
    @Upsert suspend fun putTracking(value: TrackingRow)
    @Query("SELECT * FROM sources") fun sourcesFlow(): Flow<List<SourceRow>>
    @Query("SELECT * FROM sources") suspend fun sources(): List<SourceRow>
    @Upsert suspend fun putSource(value: SourceRow)
    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSource(id: String)
}
@Database(entities = [MangaRow::class, ChapterRow::class, CategoryRow::class, HistoryRow::class, UpdateRow::class, DownloadRow::class, PagesRow::class, TrackingRow::class, SourceRow::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TormangDatabase : RoomDatabase() { abstract fun library(): LibraryDao }
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TormangDatabase = Room.databaseBuilder(context, TormangDatabase::class.java, "tormang.db").build()
    @Provides fun dao(db: TormangDatabase): LibraryDao = db.library()
}
fun Manga.row() = MangaRow(id, inLibrary, this)
fun Chapter.row() = ChapterRow(id, mangaId, number, this)
