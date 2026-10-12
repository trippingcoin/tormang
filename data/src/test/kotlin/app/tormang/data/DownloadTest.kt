package app.tormang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tormang.core.database.*
import app.tormang.data.local.DownloadStore
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.io.File
import android.net.Uri

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class DownloadTest {
    private lateinit var db: TormangDatabase
    private lateinit var server: MockWebServer
    private lateinit var store: DownloadStore
    private lateinit var context: Context
    private lateinit var pages: List<Page>
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TormangDatabase::class.java).allowMainThreadQueries().build()
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer(); server.useHttps(serverTls.sslSocketFactory(), false); server.start()
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        pages = listOf(Page(0, server.url("/1.png").toString()), Page(1, server.url("/2.png").toString()))
        val repository = Proxy.newProxyInstance(MangaRepository::class.java.classLoader, arrayOf(MangaRepository::class.java)) { _, method, _ -> when (method.name) { "pages" -> pages; else -> error("Unexpected call: ${method.name}") } } as MangaRepository
        val prefs = object : PreferencesRepository { override val preferences = flowOf(Preferences()); override suspend fun update(transform: (Preferences) -> Preferences) = Unit }
        store = DownloadStore(db, db.library(), repository, prefs, client, context)
    }
    @After fun cleanup() { db.close(); server.shutdown(); File(context.filesDir, "downloads").deleteRecursively() }
    private suspend fun queued() { db.library().putPages(PagesRow("c", pages)); db.library().putDownload(DownloadRow("c", Download("c", "m", "Chapter"))) }
    @Test fun completeDownloadPersistsLocalPages() = runTest {
        queued(); repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("test image bytes")) }
        assertTrue(store.drain()); val result = db.library().download("c")!!.value
        assertEquals(DownloadStatus.COMPLETE, result.status); assertEquals(2, result.completed)
        val local = db.library().pages("c")!!.local
        assertEquals(2, local.size); assertTrue(local.all { File(Uri.parse(it.url).path!!).exists() })
        assertEquals(2, server.requestCount)
    }
    @Test fun serverFailureIsVisibleAndNeverComplete() = runTest {
        queued(); server.enqueue(MockResponse().setResponseCode(503))
        store.drain(); val result = db.library().download("c")!!.value
        assertEquals(DownloadStatus.FAILED, result.status); assertNotNull(result.error); assertTrue(db.library().pages("c")!!.local.isEmpty())
    }
    @Test fun pausedQueueMakesNoRequests() = runTest { queued(); store.pause("c"); assertTrue(store.drain()); assertEquals(0, server.requestCount); assertEquals(DownloadStatus.PAUSED, db.library().download("c")!!.value.status) }
    @Test fun interruptedDownloadReusesCompletedPages() = runTest {
        queued(); val first = File(context.filesDir, "already-downloaded.png"); first.writeBytes(byteArrayOf(1, 2, 3))
        db.library().putPages(PagesRow("c", pages, listOf(Page(0, Uri.fromFile(first).toString()))))
        db.library().putDownload(DownloadRow("c", Download("c", "m", "Chapter", DownloadStatus.RUNNING, completed = 1, total = 2, bytes = 99)))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("image"))
        store.drain(); assertEquals(1, server.requestCount); assertEquals(DownloadStatus.COMPLETE, db.library().download("c")!!.value.status); assertEquals(8L, db.library().download("c")!!.value.bytes)
        first.delete()
    }
    @Test fun htmlIsRejectedAsAnImage() = runTest { queued(); server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("error page")); store.drain(); assertEquals(DownloadStatus.FAILED, db.library().download("c")!!.value.status) }
}
