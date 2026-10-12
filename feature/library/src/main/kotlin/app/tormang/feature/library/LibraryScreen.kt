package app.tormang.feature.library

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import app.tormang.domain.usecase.FilterLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class LibraryViewModel @Inject constructor(private val repository: MangaRepository, private val prefs: PreferencesRepository) : ActionViewModel() {
    val filter = MutableStateFlow(LibraryFilter())
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val categories = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val library = combine(repository.observeLibrary(), filter, preferences) { items, filter, preferences -> FilterLibrary()(items, filter, preferences.sort) }.catch { error.value = it.message }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun query(value: String) { filter.update { it.copy(query = value) } }
    fun category(id: String?) { filter.update { it.copy(categoryId = id) } }
    fun favorite() { filter.update { it.copy(favoritesOnly = !it.favoritesOnly) } }
    fun unread() { filter.update { it.copy(unreadOnly = !it.unreadOnly) } }
    fun grid() = launchAction { prefs.update { it.copy(grid = !it.grid) } }
    fun sort(sort: LibrarySort) = launchAction { prefs.update { it.copy(sort = sort) } }
    fun refresh() = launchAction { repository.refreshLibrary() }
    fun addCategory(name: String) = launchAction { repository.addCategory(name) }
    fun rename(id: String, name: String) = launchAction { repository.renameCategory(id, name) }
    fun removeCategory(id: String) = launchAction { repository.deleteCategory(id); category(null) }
}
@Composable fun LibraryScreen(onManga: (String) -> Unit, onBrowse: () -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val items by vm.library.collectAsStateWithLifecycle(); val prefs by vm.preferences.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle(); val filter by vm.filter.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var editing by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Библиотека"), l10n("Ваше место для хороших историй")) { IconButton(onClick = { vm.refresh() }, enabled = !busy) { Icon(Icons.Outlined.Refresh, l10n("Обновить библиотеку")) }; IconButton(onClick = { vm.grid() }) { Icon(if (prefs.grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, l10n("Изменить вид")) } }
        OutlinedTextField(filter.query, vm::query, Modifier.fillMaxWidth().padding(horizontal = 24.dp), placeholder = { Text(l10n("Найти в библиотеке")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.large)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter.categoryId == null, { vm.category(null) }, { Text(l10n("Все · ${items.size}", "All · ${items.size}")) })
            categories.forEach { c -> FilterChip(filter.categoryId == c.id, { vm.category(c.id) }, { Text(c.name) }) }
            AssistChip(onClick = { manage = true }, label = { Text(l10n("Категории")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)) })
        }
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter.favoritesOnly, vm::favorite, { Text(l10n("Избранное")) }); FilterChip(filter.unreadOnly, vm::unread, { Text(l10n("Непрочитанное")) })
            var sortOpen by remember { mutableStateOf(false) }
            Box { AssistChip(onClick = { sortOpen = true }, label = { Text(l10n("Сортировка")) }); DropdownMenu(sortOpen, { sortOpen = false }) {
                LibrarySort.entries.forEachIndexed { i, sort -> DropdownMenuItem(text = { Text(listOf(l10n("По названию"), l10n("Последнее чтение"), l10n("Последнее обновление"), l10n("Дата добавления"), l10n("Непрочитанные"))[i]) }, onClick = { vm.sort(sort); sortOpen = false }) }
            } }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(error, { vm.refresh() }, vm::dismissError)
        if (items.isEmpty()) EmptyState(l10n("Истории ждут вас"), l10n("Добавьте произведение из каталога или импортируйте свои CBZ / ZIP."), l10n("Открыть обзор"), onBrowse)
        else MangaCollection(items, prefs.grid, onManga, Modifier.weight(1f))
    }
    if (manage) AlertDialog(onDismissRequest = { manage = false }, title = { Text(l10n("Категории")) }, text = { Column {
        categories.forEach { c -> Row { TextButton(onClick = { editing = c.id; name = c.name }, modifier = Modifier.weight(1f)) { Text(c.name) }; IconButton(onClick = { vm.removeCategory(c.id) }) { Icon(Icons.Outlined.DeleteOutline, l10n("Удалить категорию ${c.name}", "Delete category ${c.name}")) } } }
        OutlinedTextField(name, { name = it }, label = { Text(if (editing == null) l10n("Новая категория") else l10n("Название категории")) })
    } }, confirmButton = { TextButton(onClick = { if (editing == null) vm.addCategory(name) else vm.rename(editing!!, name); name = ""; editing = null }, enabled = name.isNotBlank()) { Text(l10n("Сохранить")) } }, dismissButton = { TextButton(onClick = { manage = false }) { Text(l10n("Готово")) } })
}
