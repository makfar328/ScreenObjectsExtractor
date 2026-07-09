package org.makar.ocrapp.screenobjectsextractor.view.journal;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.model.core.DecomposeSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.LoggingService;
import org.makar.ocrapp.screenobjectsextractor.model.core.SearchSessionService;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class JournalControllerUITest {

    private JournalController controller;
    private Stage stage;
    private SearchSessionService searchSessionService;

    /**
     * Воспроизводит полный жизненный цикл, который выполняет ControllerFactory:
     *   1. загружает FXML (→ initialize())
     *   2. инжектирует сервисы через setXxx()
     *   3. вызывает onReady() — именно он показывает системные логи по умолчанию
     */
    @Start
    void start(Stage stage) throws Exception {
        // ── Mocks ──────────────────────────────────────────────────────────
        LoggingService loggingService               = mock(LoggingService.class);
        searchSessionService                        = mock(SearchSessionService.class);
        DecomposeSessionService decomposeService    = mock(DecomposeSessionService.class);

        // getLogEntries() вызывается сразу в showSystemLogs() — обязательно мокировать
        when(loggingService.getLogEntries())
                .thenReturn(FXCollections.observableArrayList());
        when(searchSessionService.findAll()).thenReturn(List.of());
        when(decomposeService.findAll()).thenReturn(List.of());

        // ── Загрузка FXML ──────────────────────────────────────────────────
        URL fxmlUrl = MainApplication.class.getResource("fxml/journal-view.fxml");
        assertNotNull(fxmlUrl, "FXML not found on classpath");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        controller = loader.getController();

        // ── Инжекция зависимостей (имитация ControllerFactory) ─────────────
        controller.setLoggingService(loggingService);
        controller.setSearchSessionService(searchSessionService);
        controller.setDecomposeSessionService(decomposeService);

        // ── Показываем окно ─────────────────────────────────────────────────
        this.stage = stage;
        stage.setScene(new Scene(root, 700, 500));
        stage.show();
        controller.setStage(stage);

        // ── onReady() → showSystemLogs() — активирует вкладку по умолчанию ─
        Platform.runLater(controller::onReady);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── ЖУ-01 ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("ЖУ-01: journal-view открыт, активной по умолчанию является вкладка systemLogsTabButton")
    void journalView_defaultTab_isSystemLogs(FxRobot robot) {

        // Assert 1: окно журнала видимо (journal-view открыт)
        assertTrue(
                stage.isShowing(),
                "Окно журнала (journal-view) должно быть открыто и отображаться на экране"
        );

        // Assert 2: journalContentPane не пуст после onReady()
        //StackPane contentPane = robot.lookup("#journalContentPane").queryAs(StackPane.class);
        StackPane contentPane = controller.journalContentPane;
        assertFalse(
                contentPane.getChildren().isEmpty(),
                "journalContentPane не должен быть пустым: onReady() обязан был заполнить его"
        );

        // Assert 3: содержимое — ListView, а не TableView
        // showSystemLogs() → добавляет ListView<LogEntry>
        // showSearchSessions() / showDecomposeSessions() → добавляют TableView
        assertInstanceOf(
                ListView.class,
                contentPane.getChildren().get(0),
                "По умолчанию journalContentPane должен содержать ListView системных логов, " +
                        "а не TableView — это означает, что активна вкладка systemLogsTabButton"
        );

        // Assert 4: кнопка systemLogsTabButton присутствует и не скрыта
        Button systemLogsTabButton = robot.lookup("#systemLogsTabButton").queryButton();
        assertNotNull(
                systemLogsTabButton,
                "Кнопка #systemLogsTabButton должна присутствовать в journal-view"
        );
        assertTrue(
                systemLogsTabButton.isVisible(),
                "Кнопка #systemLogsTabButton должна быть видимой"
        );
        assertFalse(
                systemLogsTabButton.isDisabled(),
                "Кнопка #systemLogsTabButton должна быть активной (не задисейблена)"
        );
    }

    // ── ЖУ-02 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ЖУ-02: Нажатие systemLogsTabButton содержимое journalContentPane — ListView, заголовок окна — «Системные логи»")
    void systemLogsTabButton_click_showsListViewAndSetsTitleSystemLogs(FxRobot robot) {
        // Arrange: сначала переключаемся на другую вкладку, чтобы тест был независим от onReady()
        robot.clickOn("#searchSessionsTabButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Act
        robot.clickOn("#systemLogsTabButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Assert 1: заголовок окна
        assertEquals(
                "Системные логи",
                stage.getTitle(),
                "После нажатия systemLogsTabButton заголовок окна должен быть «Системные логи»"
        );

        // Assert 2: journalContentPane содержит ListView (системные логи), а не TableView
        StackPane contentPane = controller.journalContentPane;
        assertFalse(
                contentPane.getChildren().isEmpty(),
                "journalContentPane не должен быть пустым после переключения на вкладку системных логов"
        );
        assertInstanceOf(
                ListView.class,
                contentPane.getChildren().get(0),
                "journalContentPane должен содержать ListView<LogEntry> после нажатия systemLogsTabButton"
        );

        // Assert 3: кнопка присутствует, видима и не задисейблена
        Button btn = robot.lookup("#systemLogsTabButton").queryButton();
        assertNotNull(btn, "Кнопка #systemLogsTabButton должна существовать в журнале");
        assertTrue(btn.isVisible(), "Кнопка #systemLogsTabButton должна быть видимой");
        assertFalse(btn.isDisabled(), "Кнопка #systemLogsTabButton должна быть активной (не задисейблена)");
    }

// ── ЖУ-03 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ЖУ-03: Нажатие searchSessionsTabButton содержимое journalContentPane — TableView, заголовок окна — «Сессии поиска»")
    void searchSessionsTabButton_click_showsTableViewAndSetsTitleSearchSessions(FxRobot robot) {
        // Act
        robot.clickOn("#searchSessionsTabButton");
        //WaitForAsyncUtils.waitForFxEvents();
        await()
                .atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .until(() -> "Сессии поиска".equals(stage.getTitle()));

        // Assert 1: заголовок окна
        assertEquals(
                "Сессии поиска",
                stage.getTitle(),
                "После нажатия searchSessionsTabButton заголовок окна должен быть «Сессии поиска»"
        );

        // Assert 2: в scenegraph присутствует именно таблица сессий поиска
        TableView<?> table = robot.lookup("#searchSessionsTable").queryAs(TableView.class);
        assertNotNull(table, "TableView сессий поиска должна присутствовать в journalContentPane");
        assertTrue(table.isVisible(), "TableView сессий поиска должна быть видимой");

        // Assert 3: кнопка присутствует, видима и не задисейблена
        Button btn = robot.lookup("#searchSessionsTabButton").queryButton();
        assertNotNull(btn, "Кнопка #searchSessionsTabButton должна существовать в журнале");
        assertTrue(btn.isVisible(), "Кнопка #searchSessionsTabButton должна быть видимой");
        assertFalse(btn.isDisabled(), "Кнопка #searchSessionsTabButton должна быть активной (не задисейблена)");
    }

// ── ЖУ-04 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ЖУ-04: Нажатие decomposeSessionsTabButton содержимое journalContentPane — TableView, заголовок окна — «Сессии декомпозиции»")
    void decomposeSessionsTabButton_click_showsTableViewAndSetsTitleDecomposeSessions(FxRobot robot) {
        // Act
        robot.clickOn("#decomposeSessionsTabButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Assert 1: заголовок окна
        assertEquals(
                "Сессии декомпозиции",
                stage.getTitle(),
                "После нажатия decomposeSessionsTabButton заголовок окна должен быть «Сессии декомпозиции»"
        );

        // Assert 2: journalContentPane содержит TableView (сессии декомпозиции), а не ListView
        StackPane contentPane = controller.journalContentPane;
        assertFalse(
                contentPane.getChildren().isEmpty(),
                "journalContentPane не должен быть пустым после переключения на вкладку сессий декомпозиции"
        );
        assertInstanceOf(
                javafx.scene.control.TableView.class,
                contentPane.getChildren().get(0),
                "journalContentPane должен содержать TableView<DecomposeSessionEntry> после нажатия decomposeSessionsTabButton"
        );

        // Assert 3: кнопка присутствует, видима и не задисейблена
        Button btn = robot.lookup("#decomposeSessionsTabButton").queryButton();
        assertNotNull(btn, "Кнопка #decomposeSessionsTabButton должна существовать в журнале");
        assertTrue(btn.isVisible(), "Кнопка #decomposeSessionsTabButton должна быть видимой");
        assertFalse(btn.isDisabled(), "Кнопка #decomposeSessionsTabButton должна быть активной (не задисейблена)");
    }

    // ── ЖУ-05: нажатие кнопки «Очистить журнал» вызывает диалог подтверждения ──

    @Test
    @DisplayName("ЖУ-05: Нажатие кнопки «Очистить журнал» вызывает диалог подтверждения")
    void clearButton_showsConfirmationDialog(FxRobot robot) {
        // Arrange: перейти на вкладку сессий поиска
        robot.clickOn("#searchSessionsTabButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Arrange: инжектировать шпион — фиксирует вызов, возвращает «отмена»
        AtomicBoolean dialogWasShown = new AtomicBoolean(false);
        Platform.runLater(() ->
                controller.setConfirmationDialog((title, header, content) -> {
                    dialogWasShown.set(true);
                    return Optional.empty();   // нажали «Отмена» / закрыли крестиком
                })
        );
        WaitForAsyncUtils.waitForFxEvents();

        // Act
        robot.clickOn("#clearJournalButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Assert 1: диалог был вызван ровно один раз
        assertTrue(
                dialogWasShown.get(),
                "После нажатия #clearJournalButton должен появляться диалог подтверждения"
        );

        // Assert 2: без подтверждения deleteAll() не вызывается
        verify(searchSessionService, never()).deleteAll();
    }

    // ── ЖУ-06: после подтверждения список пуст, placeholder виден ────────────

    @Test
    @DisplayName("ЖУ-06: После подтверждения очистки таблица пуста и отображается placeholder")
    void clearButton_onConfirm_clearsTableAndShowsPlaceholder(FxRobot robot) {
        // Arrange: перейти на вкладку (мок findAll() уже отдаёт List.of() — таблица
        // начинает пустой, но нам важно что deleteAll() вызван)
        robot.clickOn("#searchSessionsTabButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Arrange: диалог сразу возвращает OK
        Platform.runLater(() ->
                controller.setConfirmationDialog((title, header, content) ->
                        Optional.of(ButtonType.OK))
        );
        WaitForAsyncUtils.waitForFxEvents();

        // Act
        robot.clickOn("#clearJournalButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Assert 1: deleteAll() вызван ровно один раз
        verify(searchSessionService, times(1)).deleteAll();

        // Assert 2: таблица пуста
        TableView<?> table = robot.lookup("#searchSessionsTable").queryAs(TableView.class);
        assertTrue(
                table.getItems().isEmpty(),
                "После подтверждения очистки таблица должна быть пустой"
        );

        // Assert 3: placeholder виден (JavaFX показывает его автоматически при пустом items)
        Node placeholder = robot.lookup("#searchSessionsPlaceholder").query();
        assertTrue(
                placeholder.isVisible(),
                "Placeholder «Список сессий поиска пуст» должен быть виден при пустой таблице"
        );
    }
}