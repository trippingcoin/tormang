package app.tormang.feature.browse

import app.tormang.core.designsystem.l10n
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.designsystem.*
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class BrowseState(val source: String = "xkcd", val query: String = "", val genre: String = "", val sort: String = "latest", val items: List<Manga> = emptyList(), val page: Int = 0, val hasNext: Boolean = true)
@HiltViewModel class BrowseViewModel @Inject constructor(private val sourcesRepository: SourceRepository, private val manga: MangaRepository) : ActionViewModel() {
    val sources = sourcesRepository.sources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val state = MutableStateFlow(BrowseState())
    private var job: Job? = null
    init { load(reset = true) }
    fun select(id: String) { job?.cancel(); state.value = BrowseState(source = id); load(true) }
    fun search(query: String, genre: String, sort: String) { job?.cancel(); state.update { it.copy(query = query, genre = genre, sort = sort) }; load(true) }
    fun load(reset: Boolean = false) {
        if (!reset && (busy.value || !state.value.hasNext)) return
        job = launchAction {
            error.value = null
            val s = state.value; val page = if (reset) 1 else s.page + 1
            val result = sourcesRepository.source(s.source).searchManga(s.query, page, SearchFilters(s.genre.ifBlank { null }, s.sort))
            manga.cache(result.items)
            state.value = s.copy(items = ((if (reset) emptyList() else s.items) + result.items).distinctBy { it.id }, page = page, hasNext = result.hasNext)
        }
    }
    fun catalog(name: String, url: String) = launchAction { sourcesRepository.addCatalog(name, url) }
    fun remove(id: String) = launchAction { sourcesRepository.removeCatalog(id); if (state.value.source == id) select("local") }
    fun import(uri: String, series: String, done: (String) -> Unit) = launchAction { done(manga.importArchive(uri, series)); select("local") }
}
@Composable fun BrowseScreen(onManga: (String) -> Unit, vm: BrowseViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle(); val sources by vm.sources.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }; var genre by remember { mutableStateOf("") }; var sourceOpen by remember { mutableStateOf(false) }; var catalogOpen by remember { mutableStateOf(false) }; var importOpen by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf("") }; var catalogName by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }; var filterOpen by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri.toString(), series, onManga) }
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, state.items.size, state.hasNext) { snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { index -> if (state.items.isNotEmpty() && index >= state.items.size - 4 && error == null) vm.load() } }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Открывайте"), l10n("Следующая история — совсем рядом")) { IconButton(onClick = { sourceOpen = true }) { Icon(Icons.Outlined.Language, l10n("Источники")) } }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { sourceOpen = true }, label = { Text(sources.firstOrNull { it.id == state.source }?.name ?: l10n("Источники")) })
            AssistChip(onClick = { importOpen = true }, label = { Text(l10n("Импорт")) }, leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)) })
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text(if (state.source == "xkcd") l10n("Номер или заголовок") else l10n("Поиск произведений")) }, singleLine = true, trailingIcon = { IconButton(onClick = { vm.search(query, genre, state.sort) }) { Icon(Icons.Outlined.Search, l10n("Найти")) } })
            IconButton(onClick = { filterOpen = true }) { Icon(Icons.Outlined.Tune, l10n("Фильтры")) }
        }
        if (state.source == "xkcd") Text(l10n("CC BY-NC 2.5 · Поиск по заголовку проверяет страницы каталога; по номеру — весь архив."), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(error, { vm.load(state.page == 0) }, vm::dismissError)
        if (state.items.isEmpty() && !busy) EmptyState(l10n("Пока ничего не найдено"), if (state.source == "local") l10n("Импортируйте свои архивы CBZ / ZIP.") else l10n("Измените запрос или просмотрите следующую страницу."))
        LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.weight(1f), state = grid, contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.items, key = { it.id }) { MangaTile(LibraryItem(it, 0, null), onManga) }
            if (state.hasNext) item(span = { GridItemSpan(maxLineSpan) }) { OutlinedButton(onClick = { vm.load() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(l10n("Загрузить ещё")) } }
        }
    }
    if (sourceOpen) AlertDialog(onDismissRequest = { sourceOpen = false }, title = { Text(l10n("Источники")) }, text = { Column { sources.forEach { source ->
        Row { TextButton(onClick = { vm.select(source.id); sourceOpen = false }, modifier = Modifier.weight(1f)) { Text(source.name) }; if (source.id !in listOf("local", "xkcd")) IconButton(onClick = { vm.remove(source.id) }) { Icon(Icons.Outlined.LinkOff, l10n("Отключить ${source.name}", "Disconnect ${source.name}")) } }
    }; Text(l10n("Каталоги подключаются как данные. Сторонний исполняемый код не устанавливается."), style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { sourceOpen = false; catalogOpen = true }) { Text(l10n("Добавить каталог")) } })
    if (catalogOpen) AlertDialog(onDismissRequest = { catalogOpen = false }, title = { Text("Open Catalog") }, text = { Column { Text(l10n("HTTPS-ссылка на каталог издателя с лицензией и изображениями. Формат описан в README проекта.")); OutlinedTextField(catalogName, { catalogName = it }, label = { Text(l10n("Название")) }); OutlinedTextField(url, { url = it }, label = { Text("https://…/catalog.json") }) } }, confirmButton = { TextButton(onClick = { vm.catalog(catalogName, url); catalogOpen = false }, enabled = catalogName.isNotBlank() && url.startsWith("https://")) { Text(l10n("Подключить")) } }, dismissButton = { TextButton(onClick = { catalogOpen = false }) { Text(l10n("Отмена")) } })
    if (importOpen) AlertDialog(onDismissRequest = { importOpen = false }, title = { Text(l10n("Импорт архива")) }, text = { Column { Text(l10n("Архив станет главой. Используйте одно название серии для нескольких глав.")); OutlinedTextField(series, { series = it }, label = { Text(l10n("Название серии")) }) } }, confirmButton = { TextButton(onClick = { importOpen = false; import.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/octet-stream")) }, enabled = series.isNotBlank()) { Text(l10n("Выбрать CBZ / ZIP")) } })
    if (filterOpen) AlertDialog(onDismissRequest = { filterOpen = false }, title = { Text(l10n("Фильтры каталога")) }, text = { Column {
        if (state.source == "xkcd") Text(l10n("xkcd предоставляет последние выпуски. Рейтинги популярности и фильтры жанров в API отсутствуют.")) else { OutlinedTextField(genre, { genre = it }, label = { Text(l10n("Жанр (оставьте пустым для всех)")) }); TextButton(onClick = { vm.search(query, genre, "title"); filterOpen = false }) { Text(l10n("По названию")) } }
    } }, confirmButton = { TextButton(onClick = { vm.search(query, genre, "latest"); filterOpen = false }) { Text(l10n("Последние обновления")) } })
}
