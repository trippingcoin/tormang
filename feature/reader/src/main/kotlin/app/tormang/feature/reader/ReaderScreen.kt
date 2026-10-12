package app.tormang.feature.reader

import app.tormang.core.designsystem.l10n
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import app.tormang.domain.usecase.ReadingProgress
import coil.Coil
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val chapter: Chapter, val pages: List<Page>, val next: Chapter?) : ReaderState
    data class Failed(val message: String) : ReaderState
}
@HiltViewModel class ReaderViewModel @Inject constructor(handle: SavedStateHandle, private val repository: MangaRepository, private val prefs: PreferencesRepository) : ActionViewModel() {
    private val id: String = checkNotNull(handle["chapterId"])
    val state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    private var progressJob: Job? = null
    init { load() }
    fun load() = launchAction {
        state.value = ReaderState.Loading
        try {
            val chapter = repository.getChapter(id)
            val pages = repository.pages(id)
            val chapters = repository.observeChapters(chapter.mangaId).first()
            state.value = ReaderState.Ready(chapter, pages, ReadingProgress.next(chapters, id))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { state.value = ReaderState.Failed(e.message ?: l10n("Не удалось открыть главу")) }
    }
    fun progress(page: Int) {
        val ready = state.value as? ReaderState.Ready ?: return
        progressJob = viewModelScope.launch { try { repository.saveProgress(ready.chapter.id, page, ready.pages.size) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error.value = l10n("Не удалось сохранить прогресс: ${e.message}", "Could not save progress: ${e.message}") } }
    }
    fun settings(change: (Preferences) -> Preferences) = launchAction { prefs.update(change) }
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReaderScreen(onBack: () -> Unit, onNext: (String) -> Unit, vm: ReaderViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle(); val prefs by vm.preferences.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle()
    var chrome by remember { mutableStateOf(true) }; var settings by remember { mutableStateOf(false) }
    val activity = LocalContext.current.activity()
    DisposableEffect(activity, prefs.orientation) {
        val old = activity?.requestedOrientation
        activity?.requestedOrientation = when (prefs.orientation) { "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT; "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        onDispose { if (old != null) activity.requestedOrientation = old }
    }
    DisposableEffect(activity, prefs.brightness, chrome) {
        val window = activity?.window
        val oldBrightness = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply { screenBrightness = prefs.brightness }
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { window?.attributes = window?.attributes?.apply { screenBrightness = oldBrightness ?: -1f }; controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = state) {
            ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is ReaderState.Failed -> Surface(Modifier.align(Alignment.Center)) { EmptyState(l10n("Глава недоступна"), current.message, l10n("Повторить"), { vm.load() }) }
            is ReaderState.Ready -> ReaderPages(current, prefs, chrome, { chrome = !chrome }, vm::progress, onNext, { settings = true }, onBack)
        }
        if (state !is ReaderState.Ready) TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) { Text(l10n("Назад")) }
        if (error != null) ErrorBanner(error, dismiss = vm::dismissError)
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        Text(l10n("Настройки чтения"), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        val labels = listOf(l10n("Вертикально · страницы"), l10n("Горизонтально"), l10n("Справа налево"), l10n("Слева направо"), l10n("Вебтун · непрерывно"))
        ChoiceRow(l10n("Режим"), labels[prefs.readerMode.ordinal], labels) { index -> vm.settings { it.copy(readerMode = ReaderMode.entries[index]) } }
        ChoiceRow(l10n("Ориентация"), prefs.orientation, listOf(l10n("Системная"), l10n("Портретная"), l10n("Альбомная"))) { index -> vm.settings { it.copy(orientation = listOf("system", "portrait", "landscape")[index]) } }
        ToggleRow(l10n("Автоматически к следующей главе"), prefs.autoNext, { checked -> vm.settings { it.copy(autoNext = checked) } })
        Text(l10n("Яркость"), Modifier.padding(horizontal = 24.dp))
        Slider(if (prefs.brightness < 0) 0.5f else prefs.brightness, { value -> vm.settings { it.copy(brightness = value) } }, Modifier.padding(horizontal = 24.dp))
        TextButton(onClick = { vm.settings { it.copy(brightness = -1f) } }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(l10n("Системная яркость")) }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
@Composable private fun ReaderPages(ready: ReaderState.Ready, prefs: Preferences, chrome: Boolean, toggle: () -> Unit, progress: (Int) -> Unit, next: (String) -> Unit, settings: () -> Unit, back: () -> Unit) {
    val pageCount = ready.pages.size
    var currentPage by remember(ready.chapter.id) { mutableIntStateOf(ready.chapter.page.coerceIn(0, pageCount - 1)) }
    val loaded = remember(ready.chapter.id) { mutableStateMapOf<Int, Boolean>() }
    val pager = rememberPagerState(initialPage = currentPage) { pageCount + if (ready.next != null) 1 else 0 }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    LaunchedEffect(prefs.readerMode) {
        if (prefs.readerMode == ReaderMode.WEBTOON) list.scrollToItem(currentPage) else pager.scrollToPage(currentPage)
    }
    LaunchedEffect(prefs.readerMode, ready.chapter.id) {
        if (prefs.readerMode == ReaderMode.WEBTOON) snapshotFlow { list.firstVisibleItemIndex to list.isScrollInProgress }.collect { (index, scrolling) ->
            currentPage = index.coerceAtMost(pageCount - 1)
            if (index >= pageCount && scrolling && prefs.autoNext && ready.next != null && loaded[pageCount - 1] == true) next(ready.next.id)
        }
        else snapshotFlow { pager.settledPage }.collect { position ->
            if (position < pageCount) currentPage = position
            else if (prefs.autoNext && ready.next != null) next(ready.next.id)
        }
    }
    LaunchedEffect(currentPage, loaded[currentPage]) { if (loaded[currentPage] == true) progress(currentPage) }
    DisposableEffect(currentPage, ready.chapter.id) {
        val requests = ready.pages.drop(currentPage + 1).take(2).map { Coil.imageLoader(context).enqueue(ImageRequest.Builder(context).data(it.url).size(1200, 2400).build()) }
        onDispose { requests.forEach { it.dispose() } }
    }
    val page: @Composable (Int, Boolean) -> Unit = { index, webtoon -> ZoomPage(ready.pages[index], webtoon, toggle) { loaded[index] = true } }
    if (prefs.readerMode == ReaderMode.WEBTOON) LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items(ready.pages, key = { it.index }) { p -> page(p.index, true) }
        item { Column(Modifier.fillMaxWidth().height(600.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(l10n("Глава прочитана"), color = Color.White); if (ready.next != null) Button(onClick = { next(ready.next.id) }) { Text(l10n("Следующая глава")) } } }
    } else if (prefs.readerMode == ReaderMode.VERTICAL) VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { index -> if (index < pageCount) page(index, false) else ChapterEnd(ready.next, next) }
    else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), reverseLayout = prefs.readerMode == ReaderMode.RTL, beyondViewportPageCount = 0) { index -> if (index < pageCount) page(index, false) else ChapterEnd(ready.next, next) }
    if (chrome) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Surface(color = Color.Black.copy(alpha = 0.85f), contentColor = Color.White) { Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, l10n("Назад")) }; Text(ready.chapter.title, Modifier.weight(1f), maxLines = 1); IconButton(onClick = settings) { Icon(Icons.Outlined.Tune, l10n("Настройки чтения")) }
        } }
        Surface(color = Color.Black.copy(alpha = 0.85f), contentColor = Color.White) { Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${currentPage + 1} / $pageCount"); TextButton(onClick = toggle) { Text(l10n("Полный экран")) }; if (ready.next != null) TextButton(onClick = { next(ready.next.id) }) { Text(l10n("Следующая")) } }
            if (pageCount > 1) Slider(currentPage.toFloat(), { value -> currentPage = value.toInt(); scope.launch { if (prefs.readerMode == ReaderMode.WEBTOON) list.scrollToItem(currentPage) else pager.scrollToPage(currentPage) } }, valueRange = 0f..(pageCount - 1).toFloat(), steps = (pageCount - 2).coerceAtLeast(0))
        } }
    }
}
@Composable private fun ChapterEnd(chapter: Chapter?, next: (String) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(l10n("Глава прочитана"), color = Color.White); if (chapter != null) Button(onClick = { next(chapter.id) }) { Text(l10n("Следующая глава")) } }
}
@Composable private fun ZoomPage(page: Page, webtoon: Boolean, toggle: () -> Unit, loaded: () -> Unit) {
    var zoom by remember(page.url) { mutableFloatStateOf(1f) }; var offset by remember(page.url) { mutableStateOf(Offset.Zero) }; var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val box = if (webtoon) Modifier.fillMaxWidth().heightIn(min = 160.dp) else Modifier.fillMaxSize()
    Box(box.semantics(mergeDescendants = true) { stateDescription = "${(zoom * 100).toInt()}%" }, contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(page.url).size(1200, if (webtoon) 6000 else 2400).setParameter("retry", retry).build(),
            contentDescription = l10n("Страница ${page.index + 1}", "Page ${page.index + 1}"), contentScale = if (webtoon) ContentScale.FillWidth else ContentScale.Fit,
            onSuccess = { loaded() },
            modifier = (if (webtoon) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .pointerInput(page.url) { detectTapGestures(onTap = { toggle() }, onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                .pointerInput(page.url) { awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || zoom > 1f) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                            val pan = event.calculatePan()
                            val maxX = size.width * (zoom - 1) / 2f; val maxY = size.height * (zoom - 1) / 2f
                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                } }
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y },
            loading = { Box(Modifier.heightIn(min = 160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(l10n("Страница не загрузилась"), color = Color.White); Button(onClick = { retry++ }) { Text(l10n("Повторить")) } } },
        )
    }
}
