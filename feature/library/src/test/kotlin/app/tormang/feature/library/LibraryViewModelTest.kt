package app.tormang.feature.library

import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun filterUpdatesVisibleLibraryAndPreferences() = runTest(dispatcher) {
        val prefs = object : PreferencesRepository { val state = MutableStateFlow(Preferences()); override val preferences = state; override suspend fun update(transform: (Preferences) -> Preferences) { state.value = transform(state.value) } }
        val items = listOf(LibraryItem(Manga("a", "s", "a", "Alpha"), 1, null), LibraryItem(Manga("b", "s", "b", "Beta"), 0, null))
        val repository = Proxy.newProxyInstance(MangaRepository::class.java.classLoader, arrayOf(MangaRepository::class.java)) { _, method, _ -> when (method.name) { "observeLibrary" -> flowOf(items); "observeCategories" -> flowOf(emptyList<Category>()); else -> error("Unexpected call: ${method.name}") } } as MangaRepository
        val vm = LibraryViewModel(repository, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.library.collect() }
        runCurrent(); assertEquals(2, vm.library.value.size)
        vm.query("alpha"); runCurrent(); assertEquals("a", vm.library.value.single().manga.id)
        vm.grid(); runCurrent(); assertFalse(prefs.state.value.grid)
    }
}
