package app.tormang

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tormang.core.designsystem.l10n
import app.tormang.domain.repository.MangaRepository
import app.tormang.domain.repository.PreferencesRepository
import app.tormang.domain.model.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test-only generated artwork. No fixture content is packaged in the application. */
@RunWith(AndroidJUnit4::class)
class OfflineReadingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val access get() = EntryPointAccessors.fromApplication(context, ReadingTestAccess::class.java)
    private var bookId: String? = null
    @After fun cleanup() { runBlocking { bookId?.let { access.repository().setLibrary(it, false) }; access.repository().removeHistory(null); access.preferences().update { Preferences() } } }
    @Test fun importedArchiveReadsOfflineAndRestoresProgress() {
        val archive = File(context.cacheDir, "test-${System.currentTimeMillis()}.cbz")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for (page in 1..3) {
                val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(239, 241, 227))
                canvas.drawText("Tormang test page $page", 45f, 250f, Paint().apply { color = Color.rgb(30, 70, 50); textSize = 36f })
                zip.putNextEntry(ZipEntry("$page.png")); bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry(); bitmap.recycle()
            }
        }
        val title = "Offline reading test ${System.currentTimeMillis()}"
        val chapter = runBlocking {
            access.preferences().update { it.copy(language = "ru", dynamicColors = false) }
            bookId = access.repository().importArchive(Uri.fromFile(archive).toString(), title)
            access.repository().observeChapters(bookId!!).first().single()
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText(l10n("Читать")).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Страница 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Страница 1").performTouchInput { swipe(start = androidx.compose.ui.geometry.Offset(center.x, height * 0.70f), end = androidx.compose.ui.geometry.Offset(center.x, height * 0.25f), durationMillis = 500) }
        try { compose.waitUntil(15_000) { runBlocking { access.repository().getChapter(chapter.id).page == 1 } } } catch (e: Exception) {
            compose.onRoot().printToLog("TormangReaderTest")
            throw AssertionError("Expected page 2, actual persisted state: " + runBlocking { access.repository().getChapter(chapter.id) }, e)
        }
        compose.onNodeWithContentDescription(l10n("Назад")).performClick()
        compose.onNodeWithText(l10n("Продолжить")).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Страница 2").assertExists()
        compose.onNodeWithContentDescription("Страница 2").performTouchInput { doubleClick() }
        compose.onNodeWithContentDescription("Страница 2").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "250%"))
        compose.onNodeWithContentDescription("Страница 2").performTouchInput { doubleClick() }
        compose.onNodeWithContentDescription("Страница 2").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "100%"))
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        val stored = runBlocking { access.repository().getChapter(chapter.id) }
        assertEquals(1, stored.page); assertFalse(stored.read)
        assertTrue(runBlocking { access.repository().pages(chapter.id).all { it.url.startsWith("file:") } })
        archive.delete()
    }
}
