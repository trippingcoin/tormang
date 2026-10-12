package app.tormang.feature.history

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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

data class HistoryItem(val history: HistoryEntry, val manga: Manga, val chapter: Chapter)
@HiltViewModel class HistoryViewModel @Inject constructor(private val repository: MangaRepository) : ActionViewModel() {
    val history = repository.observeHistory().map { list -> list.map { HistoryItem(it, repository.getManga(it.mangaId), repository.getChapter(it.chapterId)) } }.catch { error.value = it.message }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun remove(id: String?) = launchAction { repository.removeHistory(id) }
}
@Composable fun HistoryScreen(onRead: (String) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val items by vm.history.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); var clear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("История"), l10n("Продолжайте с того места, где остановились")) { IconButton(onClick = { clear = true }) { Icon(Icons.Outlined.DeleteSweep, l10n("Очистить историю")) } }
        ErrorBanner(error, dismiss = vm::dismissError)
        if (items.isEmpty()) EmptyState(l10n("Ваша история начнётся здесь"), l10n("Откройте главу — позиция чтения сохранится автоматически."))
        LazyColumn { items(items, key = { it.history.chapterId }) { item ->
            ListItem(headlineContent = { Text(item.manga.title) }, supportingContent = { Text(l10n("${item.chapter.title} · стр. ${item.history.page + 1}\n${formatTime(item.history.lastReadAt)}", "${item.chapter.title} · p. ${item.history.page + 1}\n${formatTime(item.history.lastReadAt)}")) }, leadingContent = { Cover(item.manga.cover, item.manga.title, Modifier.size(52.dp, 74.dp)) }, trailingContent = { IconButton(onClick = { vm.remove(item.history.chapterId) }) { Icon(Icons.Outlined.Close, l10n("Удалить запись")) } }, modifier = Modifier.clickable { onRead(item.history.chapterId) })
        } }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(l10n("Очистить историю?")) }, text = { Text(l10n("Прогресс в главах сохранится.")) }, confirmButton = { TextButton(onClick = { vm.remove(null); clear = false }) { Text(l10n("Очистить")) } }, dismissButton = { TextButton(onClick = { clear = false }) { Text(l10n("Отмена")) } })
}
