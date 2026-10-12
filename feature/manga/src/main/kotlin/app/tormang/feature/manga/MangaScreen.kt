package app.tormang.feature.manga

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class MangaViewModel @Inject constructor(handle: SavedStateHandle, private val repository: MangaRepository, private val downloads: DownloadsRepository) : ActionViewModel() {
    val id: String = checkNotNull(handle["mangaId"])
    val manga = repository.observeManga(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val chapters = repository.observeChapters(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloaded = downloads.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { refresh() }
    fun refresh() = launchAction { repository.refresh(id) }
    fun library() = launchAction { repository.setLibrary(id, !(manga.value?.inLibrary ?: false)) }
    fun favorite() = launchAction { repository.setFavorite(id, !(manga.value?.favorite ?: false)) }
    fun category(ids: Set<String>) = launchAction { repository.setCategories(id, ids) }
    fun download(ids: List<String>) = launchAction { downloads.enqueue(ids) }
    fun mark(ids: List<String>, read: Boolean) = launchAction { ids.forEach { repository.markRead(it, read) } }
    fun continueChapter(): Chapter? = chapters.value.filter { it.lastReadAt > 0 && !it.read }.maxByOrNull { it.lastReadAt } ?: chapters.value.firstOrNull { !it.read } ?: chapters.value.firstOrNull()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MangaScreen(onBack: () -> Unit, onRead: (String) -> Unit, onTracking: (String) -> Unit, vm: MangaViewModel = hiltViewModel()) {
    val manga by vm.manga.collectAsStateWithLifecycle(); val chapters by vm.chapters.collectAsStateWithLifecycle(); val categories by vm.categories.collectAsStateWithLifecycle(); val downloads by vm.downloaded.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }; var sort by remember { mutableIntStateOf(0) }; var categoriesOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(l10n("Произведение")) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, l10n("Назад")) } }, actions = { IconButton(onClick = { vm.refresh() }, enabled = !busy) { Icon(Icons.Outlined.Refresh, l10n("Обновить главы")) } })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(error, { vm.refresh() }, vm::dismissError)
        manga?.let { book ->
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Card { Cover(book.cover, book.title, Modifier.size(120.dp, 174.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(book.title, style = MaterialTheme.typography.headlineSmall); Text(book.author); if (book.artist.isNotBlank() && book.artist != book.author) Text(l10n("Художник: ${book.artist}", "Artist: ${book.artist}")); Text(book.status, color = MaterialTheme.colorScheme.primary); Text(book.sourceId, style = MaterialTheme.typography.labelSmall); Text(l10n("Прочитано ${chapters.count { it.read }} из ${chapters.size}", "Read ${chapters.count { it.read }} of ${chapters.size}"), style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.continueChapter()?.let { onRead(it.id) } }, enabled = chapters.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PlayArrow, null); Text(if (chapters.any { it.lastReadAt > 0 }) l10n("Продолжить") else l10n("Читать")) }
                        FilledTonalIconButton(onClick = { vm.library() }) { Icon(if (book.inLibrary) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, if (book.inLibrary) l10n("Убрать из библиотеки") else l10n("В библиотеку")) }
                        FilledTonalIconButton(onClick = { vm.favorite() }) { Icon(if (book.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, l10n("Избранное")) }
                    }
                    Row(Modifier.padding(horizontal = 24.dp)) { TextButton(onClick = { categoriesOpen = true }) { Text(l10n("Категории")) }; TextButton(onClick = { onTracking(book.id) }) { Text(l10n("Трекинг")) } }
                    if (book.alternativeTitles.isNotEmpty()) Text(book.alternativeTitles.joinToString(" · "), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelMedium)
                    Text(book.description, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
                    if (book.genres.isNotEmpty()) Text(book.genres.joinToString("  ·  "), Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.primary)
                    if (book.attribution.isNotBlank()) Text(book.attribution, Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { sort = (sort + 1) % 3 }) { Text(listOf(l10n("Главы ↑"), l10n("Главы ↓"), l10n("По дате публикации"))[sort]) }
                        TextButton(onClick = { selected = if (selected.size == chapters.size) emptySet() else chapters.map { it.id }.toSet() }) { Text(if (selected.isEmpty()) l10n("Выбрать все") else l10n("Сбросить")) }
                    }
                    if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { vm.download(selected.toList()); selected = emptySet() }) { Text(l10n("Скачать (${selected.size})", "Download (${selected.size})")) }
                        IconButton(onClick = { vm.mark(selected.toList(), true); selected = emptySet() }) { Icon(Icons.Outlined.DoneAll, l10n("Отметить прочитанными")) }
                        IconButton(onClick = { vm.mark(selected.toList(), false); selected = emptySet() }) { Icon(Icons.Outlined.RemoveDone, l10n("Отметить непрочитанными")) }
                    }
                    if (chapters.isEmpty() && !busy) EmptyState(l10n("Глав пока нет"), l10n("Обновите произведение или импортируйте ещё один архив."))
                }
                val sorted = when (sort) { 1 -> chapters.sortedByDescending { it.number }; 2 -> chapters.sortedByDescending { it.uploadedAt }; else -> chapters.sortedBy { it.number } }
                items(sorted, key = { it.id }) { chapter ->
                    val status = downloads.firstOrNull { it.chapterId == chapter.id }?.status
                    ListItem(headlineContent = { Text(chapter.title, color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                        supportingContent = { Text((if (chapter.read) l10n("Прочитано") else if (chapter.pageCount > 0) l10n("Страница ${chapter.page + 1} / ${chapter.pageCount}", "Page ${chapter.page + 1} / ${chapter.pageCount}") else l10n("Не прочитано")) + (if (status == DownloadStatus.COMPLETE || book.sourceId == "local") l10n(" · Офлайн") else "")) },
                        leadingContent = { Checkbox(chapter.id in selected, { selected = if (chapter.id in selected) selected - chapter.id else selected + chapter.id }) },
                        trailingContent = { IconButton(onClick = { vm.download(listOf(chapter.id)) }, enabled = book.sourceId != "local" && status != DownloadStatus.COMPLETE) { Icon(if (status == DownloadStatus.COMPLETE) Icons.Outlined.DownloadDone else Icons.Outlined.Download, l10n("Скачать главу")) } },
                        modifier = Modifier.clickable { onRead(chapter.id) })
                }
            }
            if (categoriesOpen) ModalBottomSheet(onDismissRequest = { categoriesOpen = false }) {
                Text(l10n("Категории произведения"), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                if (categories.isEmpty()) Text(l10n("Создайте категории в библиотеке."), Modifier.padding(24.dp))
                categories.forEach { c -> ToggleRow(c.name, c.id in book.categoryIds, { checked -> vm.category(if (checked) book.categoryIds + c.id else book.categoryIds - c.id) }) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
