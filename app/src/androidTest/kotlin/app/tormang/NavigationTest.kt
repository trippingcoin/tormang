package app.tormang

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun libraryStartsWithUsefulEmptyState() { compose.onNodeWithText(app.tormang.core.designsystem.l10n("Истории ждут вас")).assertIsDisplayed(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Найти в библиотеке")).assertIsDisplayed() }
    @Test fun historyAndUpdatesNavigate() { compose.onNodeWithText(app.tormang.core.designsystem.l10n("История")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Ваша история начнётся здесь")).assertIsDisplayed(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Новинки")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Вы ничего не пропустили")).assertIsDisplayed() }
    @Test fun settingsOpenDownloadsAndBackup() { compose.onNodeWithText(app.tormang.core.designsystem.l10n("Ещё")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Загрузки")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Возьмите истории с собой")).assertIsDisplayed(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Назад")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Резервная копия")).performClick(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Экспорт")).assertIsDisplayed(); compose.onNodeWithText(app.tormang.core.designsystem.l10n("Импорт")).assertIsDisplayed() }
}
