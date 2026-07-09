package org.makar.ocrapp.screenobjectsextractor.view.main;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.SearchDirectoryService;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.FileSearchService;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/*
@TestInstance(TestInstance.Lifecycle.PER_CLASS) — это аннотация в JUnit 5, которая настраивает жизненный цикл
 тестовых экземпляров для указанного класса или интерфейса. Она указывает фреймворку создать один экземпляр
 тестового класса, который будет использоваться для всех тестовых методов в этом классе, а не создавать
 отдельный экземпляр для каждого метода.
 */

@ExtendWith(ApplicationExtension.class)             // (1)
class MainControllerUITest {
    private MainController controller;
    private Stage primaryStage;

    /**
     * (2) @Start — аналог start(Stage) из ApplicationTest, но без extends.
     * FxRobot инжектируется как параметр в каждый @Test — это JUnit 5 стиль TestFX.
     */
    @Start
    void start(Stage stage) throws Exception {
        URL fxmlUrl = MainApplication.class.getResource("fxml/main-view.fxml");
        assertNotNull(fxmlUrl, "FXML not found on classpath");

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        controller = loader.getController();
        this.primaryStage = stage;
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
        stage.toFront();
        controller.setPrimaryStage(stage);
    }


    @Test
    @DisplayName("objectClassTextField: клик открывает выпадающий список")
    void objectClassTextField_onClick_dropdownBecomesVisible(FxRobot robot) throws Exception {

        robot.clickOn("#objectClassTextField");

        // Вариант А — Popup + ListView (кастомная автодополнение-реализация)
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                robot.lookup(".list-view").tryQuery().isPresent()
        );
        assertTrue(
                robot.lookup(".list-view").tryQuery().isPresent(),
                "ListView в Popup должен стать видимым после клика"
        );

        // Вариант Б — ContextMenu (если дропдаун реализован через ContextMenu)
        // WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
        //     robot.lookup(".context-menu").tryQuery().isPresent()
        // );
        // assertTrue(robot.lookup(".context-menu").tryQuery().isPresent());
    }


    @Test
    @DisplayName("Выбор 'person' из списка → тег с Label 'person' появляется в контейнере")
    void selectPerson_fromDropdown_tagWithLabelPersonAppearsInContainer(FxRobot robot)
            throws Exception {

        robot.clickOn("#objectClassTextField");

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                robot.lookup(".list-cell")
                        .queryAll()
                        .stream()
                        .filter(node -> node instanceof ListCell)
                        .map(node -> (ListCell<?>) node)
                        .anyMatch(cell -> "person".equals(cell.getText()))
        );

        robot.clickOn(
                (Node) robot.lookup(".list-cell")
                        .<ListCell<?>>match(cell ->
                                cell instanceof ListCell<?> && "person".equals(((ListCell<?>) cell).getText()))
                        .query()
        );

        WaitForAsyncUtils.waitForFxEvents();

        FlowPane container = robot.lookup("#selectedClassTagsContainer")
                .queryAs(FlowPane.class);

        // Проверка 1: контейнер не пуст
        assertFalse(
                container.getChildren().isEmpty(),
                "selectedClassTagsContainer должен содержать хотя бы один тег после выбора"
        );

        // Проверка 2: первый тег — это HBox, содержащий Label с текстом "person"
        assertInstanceOf(
                HBox.class,
                container.getChildren().get(0),
                "Элемент тега должен быть HBox"
        );

        HBox firstTag = (HBox) container.getChildren().get(0);

        boolean hasPersonLabel = firstTag.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .anyMatch(label -> "person".equals(label.getText()));

        assertTrue(
                hasPersonLabel,
                "Тег должен содержать Label с текстом 'person' — " +
                        "первый child в HBox из ObjectClassTagFactory.createTag()"
        );
    }

    @Test
    @DisplayName("ФП-1: При открытии: placeholder результатов виден, ScrollPane скрыт")
    void onOpen_searchResultsPlaceholderVisible(FxRobot robot) {
        VBox placeholder = robot.lookup("#searchResultsPlaceholder").queryAs(VBox.class);
        ScrollPane scrollPane = robot.lookup("#searchResultsScrollPane").queryAs(ScrollPane.class);

        assertTrue(placeholder.isVisible(), "Placeholder должен быть виден при пустых результатах");
        assertFalse(scrollPane.isVisible(), "ScrollPane должен быть скрыт при пустых результатах");
    }

    @Test
    @DisplayName("ФП-02: selectDirectoryButton → DirectoryChooser.showDialog() вызывается")
    void selectDirectoryButton_click_invokesDirectoryChooser(FxRobot robot) {

        DirectoryChooser mockChooser = Mockito.mock(DirectoryChooser.class);
        Mockito.when(mockChooser.showDialog(Mockito.any())).thenReturn(null);

        // Подменяем фабрику до клика (в JAT, чтобы не было race condition)
        Platform.runLater(() -> controller.directoryChooserFactory = () -> mockChooser);
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#selectDirectoryButton");
        WaitForAsyncUtils.waitForFxEvents();

        Mockito.verify(mockChooser).showDialog(Mockito.any());
    }

    @Test
    @DisplayName("ФП-03: после выбора директории -> viewSelectedDirectoriesButton -> окно содержит путь")
    void viewSelectedDirectoriesButton_afterDirectorySelected_windowContainsPath(FxRobot robot)
            throws Exception {

        String expectedPath = System.getProperty("user.home");
        File selectedDir = new File(expectedPath);

        DirectoryChooser mockChooser = Mockito.mock(DirectoryChooser.class);
        Mockito.when(mockChooser.showDialog(Mockito.any())).thenReturn(selectedDir);

        SearchDirectoryService mockDirectoryService = Mockito.mock(SearchDirectoryService.class);
        Mockito.when(mockDirectoryService.applyAbsorptionLogic(Mockito.any())).thenAnswer(invocationOnMock -> invocationOnMock.getArguments());

        Platform.runLater(() -> {
                controller.directoryChooserFactory = () -> mockChooser;
                controller.injectSearchDirectoryServiceForTest(mockDirectoryService);
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#selectDirectoryButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(
                robot.lookup("#viewSelectedDirectoriesButton").queryButton().isDisabled(),
                "viewSelectedDirectoriesButton должен быть активен после выбора директории"
        );

        int windowsBefore = robot.listWindows().size();
        robot.clickOn("#viewSelectedDirectoriesButton");

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                robot.listWindows().size() > windowsBefore
        );
        WaitForAsyncUtils.waitForFxEvents();

        boolean pathFound = robot.lookup((Node node) -> {
            if (node instanceof Labeled lbl && lbl.getText() != null) {
                return lbl.getText().contains(expectedPath);
            }
            if (node instanceof TextInputControl inp && inp.getText() != null) {
                return inp.getText().contains(expectedPath);
            }
            return false;
        }).tryQuery().isPresent();

        assertTrue(pathFound,
                "Окно должно содержать элемент с текстом: " + expectedPath);
    }

    @Test
    @DisplayName("ФП-04: клик executeSearchButton → FileSearchService.searchExistingIndex() вызывается")
    void executeSearchButton_click_invokesFileSearchService(FxRobot robot) throws Exception {

        // ── Arrange ───────────────────────────────────────────────────────────────
        // Futures, которые мы удерживаем вручную — поиск не завершится сам
        CompletableFuture<List<FileMetadata>> fastFuture = new CompletableFuture<>();
        CompletableFuture<List<FileMetadata>> bgFuture   = new CompletableFuture<>();

        FileSearchService mockFss = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFss.searchExistingIndex(Mockito.any())).thenReturn(fastFuture);
        Mockito.when(mockFss.initiateBackgroundIndexing(Mockito.any())).thenReturn(bgFuture);

        Platform.runLater(() -> controller.setServiceBundle(bundleWith(mockFss)));
        WaitForAsyncUtils.waitForFxEvents();

        // ── Act ───────────────────────────────────────────────────────────────────
        robot.clickOn("#executeSearchButton");
        WaitForAsyncUtils.waitForFxEvents();

        // ── Assert ────────────────────────────────────────────────────────────────
        Mockito.verify(mockFss, Mockito.times(1))
                .searchExistingIndex(Mockito.any());
        Mockito.verify(mockFss, Mockito.times(1))
                .initiateBackgroundIndexing(Mockito.any());

        // cleanup: чтобы futures не висели в памяти
        fastFuture.complete(List.of());
        bgFuture.complete(List.of());
    }


    @Test
    @DisplayName("ФП-05: во время поиска executeSearchButton недоступна; после — снова доступна")
    void executeSearchButton_duringSearch_isDisabled(FxRobot robot) throws Exception {

        // Futures с ручным управлением: поиск «завис» пока мы не вызовем complete()
        CompletableFuture<List<FileMetadata>> fastFuture = new CompletableFuture<>();
        CompletableFuture<List<FileMetadata>> bgFuture = new CompletableFuture<>();

        FileSearchService mockFss = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFss.searchExistingIndex(Mockito.any())).thenReturn(fastFuture);
        Mockito.when(mockFss.initiateBackgroundIndexing(Mockito.any())).thenReturn(bgFuture);

        Platform.runLater(() -> controller.setServiceBundle(bundleWith(mockFss)));
        WaitForAsyncUtils.waitForFxEvents();

        // ── Act: запускаем поиск ──────────────────────────────────────────────────
        robot.clickOn("#executeSearchButton");
        WaitForAsyncUtils.waitForFxEvents();

        // ── Assert 1: кнопка заблокирована ВО ВРЕМЯ поиска ───────────────────────
        assertTrue(
                robot.lookup("#executeSearchButton").queryButton().isDisabled(),
                "executeSearchButton должна быть недоступна во время выполнения поиска"
        );
        fastFuture.complete(List.of());
        bgFuture.complete(List.of());

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                !robot.lookup("#executeSearchButton").queryButton().isDisabled()
        );
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(robot.lookup("#executeSearchButton").queryButton().isDisabled(),
                "executeSearchButton должна стать доступной после завершения поиска"
        );
    }

    @Test
    @DisplayName("ФП-06: Нажатие Enter в области любого элемента scene эквивалентен нажатию executeSearchButton")
    void enterInSearchTextField_firesExecuteSearchButton(FxRobot robot) throws Exception {

        AtomicBoolean buttonFired = new AtomicBoolean(false);

        Button executeButton = robot.lookup("#executeSearchButton").queryButton();
        Platform.runLater(() ->
                executeButton.addEventHandler(ActionEvent.ACTION, e -> buttonFired.set(true))
        );
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#probabilitySlider");
        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(
                buttonFired.get(),
                "Enter области любого элемента scene должен инициировать ACTION executeSearchButton"
        );
    }

    @Test
    @DisplayName("ФП-07: непустой результат поиска → область результатов содержит хотя бы один элемент")
    void executeSearch_withResults_resultsAreaContainsItems(FxRobot robot) throws Exception {

        // ── Arrange: готовим FileMetadata-заглушку ──────────────────────────
        Path fakePath = Path.of(System.getProperty("user.home"), "fake-image.png");
        FileMetadata fakeFile = new FileMetadata(
                fakePath,
                "fake-image.png",
                "png",
                1024L,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(),   // recognizedTextContent
                List.of()    // detectedObjects
        );
        List<FileMetadata> fakeResults = List.of(fakeFile);

        // ── Arrange: мокируем FileSearchService ────────────────────────────
        FileSearchService mockFileSearchService = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFileSearchService.searchExistingIndex(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(fakeResults));
        Mockito.when(mockFileSearchService.initiateBackgroundIndexing(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // ── Arrange: собираем ServiceBundle с реальным record-конструктором ─
        ServiceBundle mockBundle = new ServiceBundle(
                mockFileSearchService,
                null,  // fileIndexService — не нужен в этом тесте
                null,  // searchDirectoryService
                null,  // imageAnalysisManager
                null,  // imageContentAnalyzer
                null,  // searchSessionService
                null,  // decomposeSessionService
                null,  // loggingService
                null   // aiExecutorService
        );

        // ── Arrange: инжектируем в контроллер в JAT ────────────────────────
        Platform.runLater(() -> controller.injectServiceBundleForTest(mockBundle));
        WaitForAsyncUtils.waitForFxEvents();

        // ── Act: запускаем поиск ───────────────────────────────────────────
        robot.clickOn("#executeSearchButton");

        // Ждём, пока resultsHBox наполнится (оба future завершены мгновенно,
        // но thenAcceptAsync → Platform::runLater добавляет один цикл JAT)
        //HBox resultsHBox = robot.lookup("#resultsHBox").queryAs(HBox.class);
        HBox resultsHBox = controller.resultsHBox;
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                !resultsHBox.getChildren().isEmpty()
        );
        WaitForAsyncUtils.waitForFxEvents();

        // ── Assert ─────────────────────────────────────────────────────────
        assertFalse(
                resultsHBox.getChildren().isEmpty(),
                "resultsHBox должен содержать хотя бы один элемент после поиска с результатами"
        );

        // ScrollPane должен стать видимым (binding из setupSearchResultsPlaceholder)
        ScrollPane scrollPane = robot.lookup("#searchResultsScrollPane").queryAs(ScrollPane.class);
        assertTrue(scrollPane.isVisible(), "ScrollPane должен быть виден при наличии результатов");

        // Placeholder должен скрыться
        VBox placeholder = robot.lookup("#searchResultsPlaceholder").queryAs(VBox.class);
        assertFalse(placeholder.isVisible(), "Placeholder должен быть скрыт при наличии результатов");
    }

    @Test
    @DisplayName("ФП-08: пустой результат поиска → отображается placeholder")
    void executeSearch_withEmptyResults_placeholderIsVisible(FxRobot robot) throws Exception {

        // оба future возвращают пустые списки
        FileSearchService mockFileSearchService = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFileSearchService.searchExistingIndex(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        Mockito.when(mockFileSearchService.initiateBackgroundIndexing(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        ServiceBundle mockBundle = new ServiceBundle(
                mockFileSearchService,
                null, null, null, null, null, null, null, null
        );

        Platform.runLater(() -> controller.injectServiceBundleForTest(mockBundle));
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#executeSearchButton");

        // Ждём завершения allDone.thenRunAsync (один лишний цикл JAT после future)
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                !robot.lookup("#executeSearchButton").queryButton().isDisabled()
        );
        WaitForAsyncUtils.waitForFxEvents();

        //HBox resultsHBox = robot.lookup("#resultsHBox").queryAs(HBox.class);
        HBox resultsHBox = controller.resultsHBox;
        assertTrue(
                resultsHBox.getChildren().isEmpty(),
                "resultsHBox должен быть пустым при пустых результатах поиска"
        );

        VBox placeholder = robot.lookup("#searchResultsPlaceholder").queryAs(VBox.class);
        assertTrue(placeholder.isVisible(), "Placeholder должен быть виден при пустых результатах");

        ScrollPane scrollPane = robot.lookup("#searchResultsScrollPane").queryAs(ScrollPane.class);
        assertFalse(scrollPane.isVisible(), "ScrollPane должен быть скрыт при пустых результатах");
    }

    @Test
    @DisplayName("ФП-09: Установка нижней границы даты в minDatePicker ограничивает результаты поиска по дате")
    void minDatePicker_setValue_criteriaContainsMinDate(FxRobot robot) throws Exception {

        // ── Arrange ───────────────────────────────────────────────────────────
        LocalDate expectedMinDate = LocalDate.of(2024, 1, 1);

        CompletableFuture<List<FileMetadata>> fastFuture = new CompletableFuture<>();
        CompletableFuture<List<FileMetadata>> bgFuture   = new CompletableFuture<>();

        FileSearchService mockFss = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFss.searchExistingIndex(Mockito.any())).thenReturn(fastFuture);
        Mockito.when(mockFss.initiateBackgroundIndexing(Mockito.any())).thenReturn(bgFuture);

        // Инжектируем сервис и устанавливаем дату в JAT (один runLater → нет race condition)
        Platform.runLater(() -> {
            controller.setServiceBundle(bundleWith(mockFss));
            controller.minDatePicker.setValue(expectedMinDate);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // ── Act ───────────────────────────────────────────────────────────────
        robot.clickOn("#executeSearchButton");
        WaitForAsyncUtils.waitForFxEvents();

        // ── Assert ────────────────────────────────────────────────────────────
        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        Mockito.verify(mockFss).searchExistingIndex(captor.capture());

        SearchCriteria captured = captor.getValue();
        assertEquals(expectedMinDate, captured.getMinDate(),
                "SearchCriteria.getMinDate() должен соответствовать значению minDatePicker");
        assertTrue(captured.hasMinDate(),
                "SearchCriteria.hasMinDate() должен вернуть true после установки даты");

        // Cleanup: не оставляем зависшие futures в памяти
        fastFuture.complete(List.of());
        bgFuture.complete(List.of());
    }

    @Test
    @DisplayName("ФП-10: Установка верхней границы даты в maxDatePicker ограничивает результаты поиска по дате")
    void maxDatePicker_setValue_criteriaContainsMaxDate(FxRobot robot) throws Exception {

        // ── Arrange ───────────────────────────────────────────────────────────
        LocalDate expectedMaxDate = LocalDate.of(2025, 12, 31);

        CompletableFuture<List<FileMetadata>> fastFuture = new CompletableFuture<>();
        CompletableFuture<List<FileMetadata>> bgFuture   = new CompletableFuture<>();

        FileSearchService mockFss = Mockito.mock(FileSearchService.class);
        Mockito.when(mockFss.searchExistingIndex(Mockito.any())).thenReturn(fastFuture);
        Mockito.when(mockFss.initiateBackgroundIndexing(Mockito.any())).thenReturn(bgFuture);

        Platform.runLater(() -> {
            controller.setServiceBundle(bundleWith(mockFss));
            controller.maxDatePicker.setValue(expectedMaxDate);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // ── Act ───────────────────────────────────────────────────────────────
        robot.clickOn("#executeSearchButton");
        WaitForAsyncUtils.waitForFxEvents();

        // ── Assert ────────────────────────────────────────────────────────────
        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        Mockito.verify(mockFss).searchExistingIndex(captor.capture());

        SearchCriteria captured = captor.getValue();
        assertEquals(expectedMaxDate, captured.getMaxDate(),
                "SearchCriteria.getMaxDate() должен соответствовать значению maxDatePicker");
        assertTrue(captured.hasMaxDate(),
                "SearchCriteria.hasMaxDate() должен вернуть true после установки даты");

        // Cleanup
        fastFuture.complete(List.of());
        bgFuture.complete(List.of());
    }

    @Test
    @DisplayName("АЭ-01: startDecomposeButton скрывает main-view и открывает capture-button-view с активным buttonShape")
    void startDecomposeButton_click_hidesMainViewAndOpensCaptureButtonView(FxRobot robot)
            throws Exception {

        robot.clickOn("#startDecomposeButton");

        // ── Assert 1: floatingButtonStage появился ───────────────────────────────
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                robot.listWindows().stream()
                        .anyMatch(w -> w instanceof Stage && ((Stage) w).isAlwaysOnTop())
        );

        assertFalse(primaryStage.isShowing(),
                "Главное окно (main-view) должно быть скрыто после нажатия startDecomposeButton");

        // ── Assert 3: floatingButtonStage открыт ─────────────────────────────────
        Stage floatingStage = robot.listWindows().stream()
                .filter(w -> w instanceof Stage && ((Stage) w).isAlwaysOnTop())
                .map(w -> (Stage) w)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Плавающее окно (capture-button-view) не найдено"));

        assertTrue(floatingStage.isShowing(),
                "Окно capture-button-view должно быть открыто");

        // ── Assert 4: buttonShape виден в сцене ──────────────────────────────────
        Scene scene = floatingStage.getScene();
        assertNotNull(scene, "capture-button-view должен иметь сцену");

        boolean buttonShapeFound = scene.getRoot().lookupAll("#buttonShape")
                .stream()
                .anyMatch(Node::isVisible);

        assertTrue(buttonShapeFound,
                "buttonShape (Circle) должен быть виден и активен в capture-button-view");
    }

    @Test
    @DisplayName("АЭ-02: нажатие buttonShape скрывает capture-button-view и инициирует захват экрана")
    void buttonShape_click_hidesCaptureViewAndInitiatesScreenCapture(FxRobot robot)
            throws Exception {

        // ── Arrange ──────────────────────────────────────────────────────────────
        BufferedImage fakeCapture = new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        AtomicBoolean captureInitiated = new AtomicBoolean(false);

        Platform.runLater(() ->
                controller.screenCaptureFactory = () -> {
                    captureInitiated.set(true);
                    return fakeCapture;
                }
        );
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#startDecomposeButton");

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                robot.listWindows().stream()
                        .anyMatch(w -> w instanceof Stage && ((Stage) w).isAlwaysOnTop())
        );
        WaitForAsyncUtils.waitForFxEvents();

        // ── Находим FloatingButtonStage ───────────────────────────────────────────
        Stage floatingStage = robot.listWindows().stream()
                .filter(w -> w instanceof Stage && ((Stage) w).isAlwaysOnTop())
                .map(w -> (Stage) w)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Плавающее окно не открылось"));

        // ── Act 2: кликаем на buttonShape ────────────────────────────────────────
        Circle buttonShape = (Circle) floatingStage.getScene().getRoot().lookup("#buttonShape");
        assertNotNull(buttonShape, "buttonShape не найден в capture-button-view");

        robot.clickOn(buttonShape);

        // ── Assert 1: capture-button-view закрылся ────────────────────────────────
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !floatingStage.isShowing());
        assertFalse(floatingStage.isShowing(),
                "capture-button-view должен закрыться после клика на buttonShape");

        // ── Assert 2: фабрика захвата экрана была вызвана ─────────────────────────
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, captureInitiated::get);
        assertTrue(captureInitiated.get(),
                "screenCaptureFactory должна быть вызвана — захват экрана инициирован");
    }


    private ServiceBundle bundleWith(FileSearchService fss) {
        return new ServiceBundle(
                fss,   // fileSearchService
                null,  // fileIndexService
                null,  // searchDirectoryService
                null,  // imageAnalysisManager
                null,  // imageContentAnalyzer
                null,  // searchSessionService
                null,  // decomposeSessionService
                null,  // loggingService
                null   // aiExecutorService
        );
    }
}