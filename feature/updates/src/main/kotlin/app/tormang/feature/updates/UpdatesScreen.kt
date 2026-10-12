package app.tormang.feature.updates

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.designsystem.PageHeading
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UpdateItem(val update: UpdateEntry, val manga: Manga, val chapter: Chapter)
@HiltViewModel class UpdatesViewModel @Inject constructor(private val repository: MangaRepository) : ActionViewModel() {
    val updates = repository.observeUpdates().map { list -> list.map { UpdateItem(it, repository.getManga(it.mangaId), repository.getChapter(it.chapterId)) } }.catch { error.value = it.message }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun refresh() = launchAction { repository.refreshLibrary() }
}
@Composable fun UpdatesScreen(onRead: (String) -> Unit, vm: UpdatesViewModel = hiltViewModel()) {
    val items by vm.updates.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Обновления"), l10n("Новые главы ваших историй")) { IconButton(onClick = { vm.refresh() }, enabled = !busy) { Icon(Icons.Outlined.Refresh, l10n("Проверить новые главы")) } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(error, { vm.refresh() }, vm::dismissError)
        if (items.isEmpty()) EmptyState(l10n("Вы ничего не пропустили"), l10n("Новые главы появятся здесь после обновления библиотеки."))
        LazyColumn { items(items, key = { it.chapter.id }) { item -> ListItem(headlineContent = { Text(item.manga.title) }, supportingContent = { Text("${item.chapter.title}\n${formatTime(item.update.detectedAt)}") }, leadingContent = { Cover(item.manga.cover, item.manga.title, Modifier.size(52.dp, 74.dp)) }, modifier = Modifier.clickable { onRead(item.chapter.id) }) } }
    }
}
