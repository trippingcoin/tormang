package app.tormang.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tormang.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: TormangDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TormangDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    @Test fun upsertPreservesChaptersAndMetadata() = runTest {
        val dao = db.library(); val book = Manga("m", "s", "remote", "Title", categoryIds = setOf("one"), genres = listOf("Fantasy"))
        dao.putManga(book.row()); dao.putChapter(Chapter("c", "m", "c", "Chapter", 1.0, page = 4).row())
        dao.putManga(book.copy(title = "New title").row())
        assertEquals(4, dao.chapter("c")!!.value.page); assertEquals(setOf("one"), dao.manga("m")!!.value.categoryIds)
    }
    @Test fun pageUrisAndQueueSurviveRoundTrip() = runTest {
        val pages = PagesRow("c", listOf(Page(0, "https://example.org/1.png")), listOf(Page(0, "content://document/1")))
        db.library().putPages(pages); db.library().putDownload(DownloadRow("c", Download("c", "m", "Test", DownloadStatus.PAUSED)))
        assertEquals(pages, db.library().pages("c")); assertEquals(DownloadStatus.PAUSED, db.library().download("c")!!.value.status)
    }
}
