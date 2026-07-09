package org.makar.ocrapp.screenobjectsextractor.view.main;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.view.IControllerFactory;
import org.makar.ocrapp.screenobjectsextractor.view.LoadedView;
import org.makar.ocrapp.screenobjectsextractor.view.screencapture.CaptureButtonController;
import org.makar.ocrapp.screenobjectsextractor.view.journal.JournalController;
import org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults.ScreenAnalysisResultsController;
import org.makar.ocrapp.screenobjectsextractor.view.selecteddirectories.SelectedDirectoriesController;
import org.makar.ocrapp.screenobjectsextractor.model.common.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSessionBuilder;
import org.makar.ocrapp.screenobjectsextractor.model.core.ScreenCaptureService;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.SearchDirectoryService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.FileIndexService;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.FileSearchService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ImageAnalysisManager;
import org.makar.ocrapp.screenobjectsextractor.view.ScreenOverlay;
import org.makar.ocrapp.screenobjectsextractor.view.UiConfigurator;

import java.awt.AWTException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javafx.event.ActionEvent;
import org.makar.ocrapp.screenobjectsextractor.view.main.components.MainPaneComponents;
import org.makar.ocrapp.screenobjectsextractor.view.main.components.ObjectClassSelector;
import org.makar.ocrapp.screenobjectsextractor.view.main.components.ObjectClassTagFactory;
import org.makar.ocrapp.screenobjectsextractor.view.main.components.TextAreaAutoExpander;
import org.makar.ocrapp.screenobjectsextractor.view.main.managers.FocusManager;

public class MainController {

    @FXML public AnchorPane anchorPane_layer1;
    @FXML public SplitPane splitPane_layer2_prevAnchorPane;
    @FXML public AnchorPane anchorPane1_layer3_prevSplitPane;
    @FXML public AnchorPane anchorPane2_layer3_prevSplitPane;
    @FXML public SplitPane splitPane_layer4_prevAnchorPane;
    @FXML public VBox vBox_layer4_prevAnchorPane;
    @FXML public AnchorPane AnchorPane1_layer5_prevSplitPane;
    @FXML public AnchorPane AnchorPane2_layer5_prevSplitPane;

    @FXML public Button addDocumentButton;
    @FXML public TextArea ProbabilityValueTextArea;
    @FXML public AnchorPane AnchorPane1_layer6_AnchorPane;
    public VBox VBox1_layer6_prevSplitPane;
    @FXML public HBox resultsHBox;
    @FXML public VBox searchResultsPlaceholder;
    @FXML public ScrollPane searchResultsScrollPane;
    @FXML public Slider probabilitySlider;
    private Timeline sliderDebounceTimer; // без изменения таймера по умолчанию лагает
    @FXML public Button GlobalProbabilityButton;

    /* Кнопки левой панели --> */
    @FXML private Button startDecomposeButton;
    @FXML private Button openSettingsButton;
    @FXML private Button openJournalButton;
    /* спейсеры между кнопками по вертикали */
    @FXML public Region topButton1Spacer;
    @FXML public Region button1Button2Spacer;
    @FXML public Region button2Button3Spacer;
    @FXML public Region button3BottomSpacer;

    /* Система поиска по запросу, переменные --> */
    @FXML public HBox searchBarHBox;
    @FXML public Button settingsSearchButton;
    @FXML public Button viewSelectedDirectoriesButton;
    @FXML public Button selectDirectoryButton;
    @FXML private ProgressIndicator searchProgressIndicator;
    @FXML public Button executeSearchButton;
    @FXML public TextArea searchQueryTextArea; /* Под снос */
    @FXML public TextField objectClassTextField;
    @FXML public DatePicker minDatePicker;
    @FXML public DatePicker maxDatePicker;
    @FXML public AnchorPane anchorObjectListBackgroundAnchor;
    @FXML public FlowPane selectedClassTagsContainer;

    /* выпадающий список */
    @FXML private Popup classSelectionPopup;
    @FXML private ListView<String> classListView;
    @FXML private javafx.collections.ObservableList<String> classOptions; /* нужно перенести в AppConfig в виде статического поля */

    /* для хранения выбранных классов объектов.
    * Cейчас хранится в currentSearchSession, поэтому нужно рассмотреть полную замену конкретно этого элемента
    * и почему он @FXML? я уже не помню */
    @FXML private ObservableList<SelectedObjectClass> currentSelectedObjectsClasses = FXCollections.observableArrayList();
    private ObjectClassSelector classSelector;
    private List<FileMetadata> currentSearchResults = new ArrayList<>();

    /* Зависимости */
    private ServiceBundle serviceBundle; /* коробка со всеми сервисами для контроллеров */
    private SearchDirectoryService searchDirectoryService; /* логика работы с объектами класса DirectoryConfig: логика поглощения классов в списке */
    private volatile boolean servicesReady = false;

    /* Список кнопок/узлов, требующих готовых AI-сервисов */
    private final List<Control> serviceAwareControls = new ArrayList<>(); // Заполняется в initialize(), используется в onServicesInitializing() / onServicesReady()

    private SearchSession currentSearchSession;
    @FXML private Stage primaryStage;
    private IControllerFactory controllerFactory;
    Supplier<DirectoryChooser> directoryChooserFactory = DirectoryChooser::new; // вынесен в фабрику для моккирования в тестах
    Supplier<BufferedImage> screenCaptureFactory = () -> {
        try {
            return ScreenCaptureService.captureFullScreen();
        } catch (AWTException e) {
            throw new RuntimeException("captureFullScreen failed", e);
        }
    };


    private final static Logger LOGGER = Logger.getLogger(MainController.class.getName());


    /*** Цепочка диспетчеризации событий (Event Dispatch Chain)
     *      1. Фаза захвата (Capture Phase) : Событие генерируется операционной системой и сначала отправляется от
     * корневого узла сцены к узлу вниз по иерархии узлов (Scene Graph) к потенциальному целевому узлу.
     * Обработчики событий : addEventFilter() - "перехват событий".
     *      2. Фаза цели (Target Phase) : Событие достигает целевого узла, то есть того конкретного компонента UI,
     * на котором произошло действие.
     * Обработчики событий : addEventHandler() или setOn...() - срабатывают на целевом узле.
     *      3. Фаза всплытия (Bubbling Phase) : Если событие не было "потреблено" (consumed) на предыдущих фазах,
     * оно начинает "всплывать" обратно вверх по иерархии Scene Graph от целевого узла к корневому.
     * Обработчики событий : addEventHandler() или setOn...() - срабатывают на каждом узле-предке.
     */

    @FXML
    public void initialize() {

        // Вынесенная настройка UI
        // надо обобщить до массива Pane, если это возможно
        MainPaneComponents components = new MainPaneComponents(
                primaryStage, anchorPane_layer1, splitPane_layer2_prevAnchorPane,
                anchorPane1_layer3_prevSplitPane, anchorPane2_layer3_prevSplitPane,
                splitPane_layer4_prevAnchorPane, vBox_layer4_prevAnchorPane,
                AnchorPane1_layer5_prevSplitPane, AnchorPane2_layer5_prevSplitPane,
                startDecomposeButton, openSettingsButton, openJournalButton,
                topButton1Spacer, button1Button2Spacer, button2Button3Spacer, button3BottomSpacer
        );

        UiConfigurator.configureMainPane(components);

        /* Может стоит поместить в одну внешнюю функцию */
        //UiConfigurator.initializeImageView(startDecomposeIcon, "/org/makar/ocrapp/screenobjectsextractor/icons/photo-decompose.png");
        //UiConfigurator.initializeImageView(settingsIcon, "/org/makar/ocrapp/screenobjectsextractor/icons/photo-side-menu-settings.png");
        //UiConfigurator.initializeImageView(openJournalIcon, "/org/makar/ocrapp/screenobjectsextractor/icons/photo-journal.png");


        /* Система поиска по запросу, находится AnchorPane2_layer5_prevSplitPane -->, сейчас не используется, планируется использование в будущих версиях с прикрученной llm */
        TextAreaAutoExpander.apply(searchQueryTextArea, 5, 1);

        currentSearchSession = new SearchSessionBuilder()
                .buildWithCriteria(new SearchSessionBuilder().build(), new SearchCriteriaBuilder().build());
        ObjectsClassList objectClassesProvider = new ObjectsClassList();
        classOptions = FXCollections.observableArrayList(objectClassesProvider.getClasses());

        /* Поиск в файловой системе, выпадающий список с расширениями классов */
        //extensionListView = new ListView<>(AppConfig.FILE_EXTENSIONS);


        /* Поиск в файловой системе, выпадающий список с названиями классов */
        /* Фильтр. Выпадающий список в Popup наполняется элементами из classOptions, фильтруясь строкой из TextField */
        // ВАЖНО: здесь мы передаем метод контроллера как Consumer
        classSelector = new ObjectClassSelector(
                objectClassTextField,
                classOptions,
                selectedClass -> Platform.runLater(
                        () -> this.addObjectClassTag(new SelectedObjectClass(selectedClass))
                )
        );

        /* обработка кнопок */
        setupEventHandlers();
        // Инициализация placeholder области результатов
        setupSearchResultsPlaceholder();
    }


    private void setupEventHandlers() {
        // Группа: Кнопки навигации (Decompose, Settings, Journal)
        startDecomposeButton.setOnAction(this::handleStartCaptureButtonAction);
        openSettingsButton.setOnAction(this::handleSearchSettingsButton);
        openJournalButton.setOnAction(this::handleOpenJournalButton);

        // Группа: Поиск и файловая система
        viewSelectedDirectoriesButton.setOnAction(this::handleViewSelectedDirectoriesButton);
        selectDirectoryButton.setOnAction(this::handleSelectDirectoryButton);
        executeSearchButton.setOnAction(this::handleExecuteSearchButton);

        // группа кнопок, которые используют зависимости
        serviceAwareControls.addAll(List.of(
                startDecomposeButton,
                executeSearchButton,
                selectDirectoryButton,
                viewSelectedDirectoriesButton
        ));

        ProbabilityValueTextArea.setText(
                String.format("%.0f%%", probabilitySlider.getValue())
        );

        GlobalProbabilityButton.setOnAction(e -> probabilitySlider.setValue(0));

        probabilitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // 1. Мгновенно обновляем текстовую метку — это дёшево
            ProbabilityValueTextArea.setText(String.format("%.0f%%", newVal.doubleValue()));

            // 2. Отменяем предыдущий таймер (если пользователь ещё двигает слайдер)
            if (sliderDebounceTimer != null) sliderDebounceTimer.stop();

            // 3. Запускаем новый таймер на 150 мс
            sliderDebounceTimer = new Timeline(new KeyFrame(
                    Duration.millis(150),
                    e -> {
                        // 4. Только ЗДЕСЬ — дорогая перерисовка, и только если прошло 150 мс тишины
                        if (!currentSearchResults.isEmpty()) {
                            double threshold = newVal.doubleValue() / 100.0;
                            displaySearchResults(
                                    filterByProbability(currentSearchResults, threshold),
                                    resultsHBox
                            );
                        }
                    }
            ));
            sliderDebounceTimer.play();
        });
    }

    /**
     * Связывает видимость placeholder с состоянием resultsHBox.
     * Placeholder виден когда список результатов пуст; ScrollPane — когда есть хотя бы один элемент.
     */
    private void setupSearchResultsPlaceholder() {
        javafx.beans.binding.BooleanBinding hasResults =
                javafx.beans.binding.Bindings.isNotEmpty(resultsHBox.getChildren());

        // ScrollPane виден только когда есть результаты
        searchResultsScrollPane.visibleProperty().bind(hasResults);
        searchResultsScrollPane.managedProperty().bind(hasResults);

        // Placeholder виден только когда результатов нет
        searchResultsPlaceholder.visibleProperty().bind(hasResults.not());
        searchResultsPlaceholder.managedProperty().bind(hasResults.not()); // layout для соседних элементов будет игнорировать этот элемент
    }

    public void setServiceBundle(ServiceBundle serviceBundle) {
        this.serviceBundle = serviceBundle;
    }

    public void setControllerFactory(IControllerFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }


    /**
     * Создает и добавляет новый "тег" выбранного класса объекта в FlowPane.
     * @param selectedObjectClass
     */
    private void addObjectClassTag(SelectedObjectClass selectedObjectClass) {

        int existingIndex = currentSelectedObjectsClasses.indexOf(selectedObjectClass);

        if (existingIndex >= 0) {
            // ── Объект уже выбран: увеличиваем счётчик ──────────────────────────
            SelectedObjectClass existing = currentSelectedObjectsClasses.get(existingIndex);
            existing.setCount(existing.getCount() + 1);

            // Обновляем визуальный тег в FlowPane
            refreshTagInContainer(selectedClassTagsContainer, existing);

            LOGGER.log(Level.FINER,
                    "Счётчик объекта ''{0}'' увеличен до {1}",
                    new Object[]{existing.getClassName(), existing.getCount()});
            return;
        }

        // ── Новый объект: создаём тег и добавляем ────────────────────────────────
        HBox tagBox = ObjectClassTagFactory.createTag(
                selectedObjectClass,
                box -> removeObjectClassTag(box, selectedObjectClass)
        );

        selectedClassTagsContainer.getChildren().add(tagBox);
        currentSelectedObjectsClasses.add(selectedObjectClass);
    }

    /**
     * Перерисовывает тег для указанного объекта в контейнере.
     * Находит существующий тег по userData и заменяет его обновлённым.
     */
    private void refreshTagInContainer(FlowPane container, SelectedObjectClass objectClass) {
        container.getChildren().stream()
                .filter(node -> objectClass.equals(node.getUserData()))
                .findFirst()
                .ifPresent(oldTag -> {
                    int index = container.getChildren().indexOf(oldTag);
                    HBox newTag = ObjectClassTagFactory.createTag(
                            objectClass,
                            box -> removeObjectClassTag(box, objectClass)
                    );
                    container.getChildren().set(index, newTag);
                });
    }


    /**
     * Удаляет "тег" класса объекта из FlowPane и из внутреннего списка.
     * @param tagBox (контейнер) тег для удаления.
     * @param selectedObjectClass Объект, содержащий имя класса и его count (сколько объектов этого класса искать на фотографии)
     */
    private void removeObjectClassTag(HBox tagBox, SelectedObjectClass selectedObjectClass) {
        selectedClassTagsContainer.getChildren().remove(tagBox);
        currentSelectedObjectsClasses.remove(selectedObjectClass);
        System.out.println("Из выборки удален класс " + selectedObjectClass.getClassName() + "\nВыборка " + currentSelectedObjectsClasses);
    }

    public void setPrimaryStage(Stage stage) {
        System.out.println(">>> DEBUG: setPrimaryStage вызван для stage: " + stage);
        this.primaryStage = stage;

        if (primaryStage.getScene() == null) {
            System.err.println(">>> ERROR: Scene is null при вызове setPrimaryStage!");
            return; // Прекратить выполнение, если сцены нет
        }
        if (primaryStage.getScene().getRoot() == null) {
            System.err.println(">>> ERROR: Scene root is null при вызове setPrimaryStage!");
            return; // Прекратить выполнение, если корня сцены нет
        }
        System.out.println(">>> DEBUG: Scene и Root существуют. Root type: " + primaryStage.getScene().getRoot().getClass().getName());

        primaryStage.setMinHeight(400);
        primaryStage.setMinWidth(600);

        /*  Метод addEventFilter - регистрирует обработчик в фазе захвата (1-я фаза);
        *   Метод setOn...  -  регистрирует обработчик в фазе всплытия (3-я фаза),
        * сопровождается риском быть поглощенным другим зарегистрированным событием */

        FocusManager.setupGlobalFocusHandling(
                stage.getScene().getRoot(),
                searchQueryTextArea,
                objectClassTextField,
                classSelector.getClassSelectionPopup()
        );

        /* горячие клавиши */
        primaryStage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !executeSearchButton.isDisabled()) {
                executeSearchButton.fire();
                event.consume(); // ← важно: останавливает EVENT до TextField,
                //   иначе setOnAction на нём сработает дважды
            }
        }); // Нажатие на Enter делегирует начало поиска по файловой системе


    }


    /* Изначально были сеттеры. Теперь поля для инициализации подаются как Sullplier */
    /**
     * Вызывается сразу после show() — UI уже виден, сервисы ещё не готовы.
     * Блокируем интерактивные элементы и показываем индикатор.
     */
    public void onServicesInitializing() {
        serviceAwareControls.forEach(c -> c.setDisable(true));
        LOGGER.info("UI загружен. Ожидание инициализации сервисов...");
    }


    /**
     * Вызывается из MainApplication через Platform::runLater,
     * когда все CompletableFuture завершились успешно.
     */
    public void onServicesReady() {
        servicesReady = true;

        this.searchDirectoryService = serviceBundle.searchDirectoryService();

        serviceAwareControls.forEach(c -> c.setDisable(false));
        LOGGER.info("Сервисы готовы. UI разблокирован.");
    }


    public void handleStartCaptureButtonAction(ActionEvent event) {
        if (primaryStage != null) {
            // Скрываем основное окно приложения сразу
            primaryStage.hide();

            try {
                // Загружаем FXML для плавающей кнопки
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/makar/ocrapp/screenobjectsextractor/fxml/capture-button-view.fxml"));
                Parent root = fxmlLoader.load();
                CaptureButtonController captureButtonController = fxmlLoader.getController();

                // Создаем новую Stage для плавающей кнопки
                Stage floatingButtonStage = new Stage();
                Scene scene = new Scene(root);
                scene.setFill(Color.TRANSPARENT);
                floatingButtonStage.setScene(scene);

                // Передаем контроллеру колбэк для запуска захвата экрана и ссылку на его Stage
                captureButtonController.setOnCaptureInitiated(this::initiateScreenCaptureProcess);
                captureButtonController.setStage(floatingButtonStage); // Передаем Stage для настройки

                floatingButtonStage.show();

            } catch (IOException e) {
                System.err.println("Error loading capture-button-view.fxml: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(primaryStage::show); // Возвращаем основное окно в случае ошибки
            }
        }
    }


    private void initiateScreenCaptureProcess(Void unused) {
        if (primaryStage == null) return;

        primaryStage.hide();

        // ФИХ: вся тяжёлая работа — на фоновом потоке
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200); // теперь НЕ блокирует JAT
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Thread sleep interrupted", e);
            }

            BufferedImage fullScreenCapture;
            try {
                fullScreenCapture = screenCaptureFactory.get(); // ← инжектируемая фабрика
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Ошибка captureFullScreen: " + exception.getMessage(), exception);
                Platform.runLater(primaryStage::show);
                return;
            }

            final BufferedImage captureForOverlay = fullScreenCapture;

            // ScreenOverlay — JavaFX Stage, создаётся обратно на JAT
            Platform.runLater(() -> {
                ScreenOverlay overlay = new ScreenOverlay(captureForOverlay, (selectedRegion) -> {
                    if (selectedRegion != null
                            && selectedRegion.getWidth() > 0
                            && selectedRegion.getHeight() > 0) {
                        try {
                            BufferedImage screenCapture = ScreenCaptureService.captureScreen(
                                    (int) selectedRegion.getMinX(),
                                    (int) selectedRegion.getMinY(),
                                    (int) selectedRegion.getWidth(),
                                    (int) selectedRegion.getHeight()
                            );
                            if (screenCapture != null) {
                                openScreenAnalysisResultsWindow(screenCapture);
                            } else {
                                System.err.println("CaptureImage равен null.");
                                Platform.runLater(primaryStage::show);
                            }
                        } catch (AWTException exception) {
                            LOGGER.log(Level.SEVERE, "Ошибка captureScreen", exception);
                            Platform.runLater(primaryStage::show);
                        }
                    } else {
                        System.out.println("No valid region selected.");
                        Platform.runLater(primaryStage::show);
                    }
                });
                overlay.show();
            });
        });
    }

    private void handleOpenJournalButton(ActionEvent actionEvent) {
        System.out.println("handleOpenJournalButton");
            LoadedView<JournalController> view = controllerFactory.loadJournalView();

            Stage stage = new Stage();
            stage.setScene(new Scene(view.root(), 700, 500));
            stage.show();

            view.controller().setStage(stage);
    }

    private void handleSearchSettingsButton(ActionEvent actionEvent) {
        System.out.println("handleSearchSettingsButton");
    }


    private void openScreenAnalysisResultsWindow(BufferedImage capturedImage) {
        try {
            LoadedView<ScreenAnalysisResultsController> view = controllerFactory.loadScreenAnalysisView(capturedImage);

            Stage stage = new Stage();
            stage.setScene(new Scene(view.root(), 900, 600));
            stage.setTitle("Screen Analysis Results");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(primaryStage);

            stage.setOnHidden(event -> {
                if (primaryStage != null) {
                    primaryStage.show();
                }
            });

            stage.show();
            view.controller().setStage(stage);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Не удалось открыть окно анализа: " + e.getMessage(), e);
            Platform.runLater(() -> {
                if (primaryStage != null) primaryStage.show();
            });
        }
    }


    private void handleSettingsSearchButton(ActionEvent actionEvent) {
        System.out.println("handleSettingsSearchButtonAction");
    }


    private void handleViewSelectedDirectoriesButton(ActionEvent actionEvent) {
        System.out.println("handleViewSelectedDirectoriesButton");
        try {

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/makar/ocrapp/screenobjectsextractor/fxml/selected-directories-view.fxml"));
            Parent root = fxmlLoader.load();
            SelectedDirectoriesController controller = fxmlLoader.getController();

            controller.setSearchDirectoryService(searchDirectoryService);
            controller.setSelectedSearchDirectories(FXCollections.observableArrayList(currentSearchSession.getCriteria().getTargetDirectories()));

            controller.setOnDirectoryChanged(updatedList -> {
                currentSearchSession = new SearchSessionBuilder().buildWithCriteria(
                        currentSearchSession,
                        new SearchCriteriaBuilder().withTargetDirectories(
                                currentSearchSession.getCriteria(),
                                new ArrayList<>(updatedList)));
            });

            Scene scene = new Scene(root);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Выбранные каталоги");
            stage.initOwner(primaryStage);
            stage.setScene(scene);
            stage.showAndWait();

        } catch (Exception exception) {
            System.out.println("Error loading screen-analysis-results-view.fxml: " + exception.getMessage());
            exception.printStackTrace();
            Platform.runLater(primaryStage::show);
        }

    }


    /**
     * Нажатие кнопки обрабатывается вызовом нативного диалогового окна для выбора каталога/файла
     * @param actionEvent
     */
    private void handleSelectDirectoryButton(ActionEvent actionEvent) {
        /* кроссплатформенная реализация */
        DirectoryChooser directoryChooser = directoryChooserFactory.get();
        /* обеспечивает нативный диалог выбора папок и на Windows, и на Macos, и на большинстве Linux систем*/
        directoryChooser.setTitle("Выберите директорию для поиска");

        File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            Path path = selectedDirectory.toPath();
            SearchDirectoryConfig config = new SearchDirectoryConfig(path);

            List<SearchDirectoryConfig> configs = new ArrayList<>(currentSearchSession.getCriteria().getTargetDirectories());
            configs.add(config);
            if (searchDirectoryService != null) {
                searchDirectoryService.addDirectory(config);
                System.out.println("Добавлен каталог для поиска: " + path);
                currentSearchSession = new SearchSessionBuilder().buildWithCriteria(currentSearchSession,
                        new SearchCriteriaBuilder().withTargetDirectories(
                                currentSearchSession.getCriteria(), configs
                        ));
            } else {
                System.err.println("SearchDirectoryService не инициализирован в MainController.");
            }

            currentSearchSession = new SearchSessionBuilder().buildWithCriteria(
                    currentSearchSession,
                    new SearchCriteriaBuilder().withTargetDirectories(
                            currentSearchSession.getCriteria(), configs
                    ));
        }
    }


    /**
     * Обработчик для кнопки "Начать поиск".
     * Формирует SearchCriteria и передает его сервису поиска.
     * @param actionEvent
     */
    private void handleExecuteSearchButton(ActionEvent actionEvent) {

        System.out.println("handleExecuteSearchButton");
        setSearchInProgress(true);

        LocalDate minDate = minDatePicker.getValue();
        LocalDate maxDate = maxDatePicker.getValue();

        System.out.println("Минимальная дата " + ((minDate != null) ? minDate.toString() : "не указана"));
        System.out.println("Максимальная дата " + ((maxDate != null) ? maxDate.toString() : "не указана"));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(new ArrayList<>())
                .withEntries(currentSelectedObjectsClasses)
                .withMinDate(minDate)
                .withMaxDate(maxDate)
                .withTargetDirectories(searchDirectoryService != null ? new ArrayList<>(searchDirectoryService.getDirectoryConfigs()) : new ArrayList<>())
                .withFileTypes(new ArrayList<>())
                .build();

        System.out.println("Сформированный Criteria: " + criteria.toString());

        LocalDateTime startedAt = LocalDateTime.now();

        if (serviceBundle != null && serviceBundle.fileSearchService() != null) {

            CompletableFuture<List<FileMetadata>> fastSearchResults = serviceBundle.fileSearchService().searchExistingIndex(criteria);
            CompletableFuture<List<FileMetadata>> backgroundIndexing = serviceBundle.fileSearchService().initiateBackgroundIndexing(criteria);

            fastSearchResults.thenAcceptAsync(results -> {
                // Обновить UI первыми результатами
                //displaySearchResults(results, resultsHBox);
                double threshold = probabilitySlider.getValue() / 100.0;
                displaySearchResults(filterByProbability(results, threshold), resultsHBox);
                currentSearchSession = new SearchSessionBuilder().buildWithFastSearchEnd(currentSearchSession, LocalDateTime.now());
                System.out.println("::MainController. Быстрые результаты поиска: " + results.size() + " файлов.");
                System.out.println("::MainController. Результаты fastSearchResults (после применения фильтров criteria): \n");
                for (FileMetadata fileMetadata : results) {
                    System.out.println("name: " + fileMetadata.getFileName());
                }
                }, Platform::runLater);

            backgroundIndexing.thenAcceptAsync(results -> {

                System.out.println("::MainController. Результаты initiateBackgroundIndexing (после применения фильтров criteria): \n");
                for (FileMetadata fileMetadata : results) {
                    System.out.println("name: " + fileMetadata.getFileName());
                }
                currentSearchSession = new SearchSessionBuilder().buildWithBackgroundSearchEnd(currentSearchSession, LocalDateTime.now());
            }, Platform::runLater);

            CompletableFuture<Void> allDone = CompletableFuture.allOf(fastSearchResults, backgroundIndexing);

            allDone.thenRunAsync(() -> {
                try {
                    List<FileMetadata> fastResults = fastSearchResults.get();
                    List<FileMetadata> backgroundResults = backgroundIndexing.get();

                    Set<FileMetadata> mergedSet = new LinkedHashSet<>();
                    mergedSet.addAll(fastResults);
                    mergedSet.addAll(backgroundResults);
                    List<FileMetadata> mergedResults = new ArrayList<>(mergedSet);

                    //displaySearchResults(mergedResults, resultsHBox);
                    currentSearchResults = new ArrayList<>(mergedResults);
                    double threshold = probabilitySlider.getValue() / 100.0;
                    displaySearchResults(filterByProbability(currentSearchResults, threshold), resultsHBox);

                    SearchSession session = new SearchSessionBuilder()
                            .withStartedAt(startedAt)
                            .withFinishedAt(LocalDateTime.now())
                            .withCriteria(criteria)
                            .withSearchResults(mergedResults)
                            .withFilesCount(mergedResults.size())
                            .withStatus("COMPLETED")
                            .build();
                    if (serviceBundle.searchSessionService() != null) {
                        serviceBundle.searchSessionService().save(session);
                    } else {
                        LOGGER.log(Level.WARNING,
                                "SearchSessionService не инициализирован — сессия поиска не сохранена.");
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                }
                setSearchInProgress(false);
            }, Platform::runLater); // JAT

        } else {
            System.err.println("FileSearchService не инициализирован.");
            setSearchInProgress(false);
        }
    }

    private void setSearchInProgress(boolean inProgress) {
        executeSearchButton.setDisable(inProgress);
        searchProgressIndicator.setVisible(inProgress);
        searchProgressIndicator.setManaged(inProgress);
    }


    /* добавление нового элемента в hboxResults, синхронная версия */
    public void displaySearchResults(List<FileMetadata> files, HBox hboxResults) {
        hboxResults.getChildren().clear();
        for (FileMetadata file : files) {
            Node node = createSearchResultNode(file);
            hboxResults.getChildren().add(node);
        }
    }

    /**
     * Возвращает файлы, у которых хотя бы один детектированный объект
     * имеет confidence >= threshold.
     * Файлы без детектированных объектов (только OCR-текст) пропускаются
     * через фильтр без проверки — решение зависит от бизнес-логики.
     */
    private List<FileMetadata> filterByProbability(List<FileMetadata> results, double threshold) {
        if (results == null || results.isEmpty()) return Collections.emptyList();
        if (threshold <= 0.0) return results; // слайдер в 0 — показать всё

        return results.stream()
                .filter(file -> {
                    List<OCRAppDetectedObject> objects = file.getDetectedObjects();
                    if (objects == null || objects.isEmpty()) {
                        // Файл без объектов (только текст) — включать/исключать по логике:
                        return true; // или false, если нужна строгая фильтрация
                    }
                    return objects.stream()
                            .anyMatch(obj -> obj.getProbability() >= threshold);
                })
                .collect(Collectors.toList());
    }


    /* добавление нового элемента в hboxResults, асинхронная версия
    * потенциально стоит добавить прогресс-бар */
    public void displaySearchResultsAsync(List<FileMetadata> files, HBox hboxResults) {
        hboxResults.getChildren().clear();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (FileMetadata file : files) {
                    Node node = createSearchResultNode(file);
                    // Добавляем элемент в UI-поток
                    Platform.runLater(() -> hboxResults.getChildren().add(node));
                    // Можно добавить небольшую задержку для плавности (опционально)
                    // try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
                return null;
            }
        };
        new Thread(task).start();
    }


    /**
     * Создание нового элемента в QueryExecuteResults
     * @param fileMetadata
     */
    public Node createSearchResultNode(FileMetadata fileMetadata) {
        double width = 200;
        double height = 200;
        double overlayHeight = 60;

        // 1. Основной контейнер
        StackPane root = new StackPane();
        root.getStyleClass().add("search-results-container");
        root.setPrefSize(width, height);

        // 2. Фоновый прямоугольник (рамка)
        Rectangle borderRect = new Rectangle(width, height);
        borderRect.setArcWidth(16);
        borderRect.setArcHeight(16);
        borderRect.setFill(Color.web("#222222", 0.7));
        borderRect.setStroke(Color.BLACK);
        borderRect.setStrokeWidth(2);

        // 3. Миниатюра файла (или иконка)
        ImageView imageView;
        Path filePath = fileMetadata.getFilePath();
        if (Files.exists(filePath) && isImageFile(filePath)) {
            imageView = new ImageView(new Image(filePath.toUri().toString(), width, height, true, true));
        } else {
            imageView = new ImageView(new Image(getClass().getResourceAsStream("/org/makar/ocrapp/screenobjectsextractor/icons/photo-decompose.png"), 64, 64, true, true));
        }
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setSmooth(true);

        // 4. Подписи
        Label nameLabel = new Label(fileMetadata.getFileName());
        nameLabel.getStyleClass().add("file-name-label");
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");

        Label pathLabel = new Label(fileMetadata.getFilePath().toString());
        pathLabel.getStyleClass().add("file-path-label");
        pathLabel.setTextFill(Color.LIGHTGRAY);
        pathLabel.setStyle("-fx-font-size: 10;");

        VBox labelBox = new VBox(nameLabel, pathLabel);
        labelBox.setSpacing(2);
        labelBox.setPadding(new Insets(0, 8, 4, 8));
        labelBox.setMouseTransparent(true);
        labelBox.setMaxHeight(Region.USE_PREF_SIZE);

        // 5. Фон подписей (полупрозрачный, снизу)
        Rectangle pathBg = new Rectangle(width, overlayHeight);
        pathBg.setFill(Color.rgb(0, 0, 0, 0.6));
        pathBg.setArcWidth(12);
        pathBg.setArcHeight(12);
        pathBg.setMouseTransparent(true);

        // 6. Overlay — нижний оверлей с подписями
        StackPane overlay = new StackPane(pathBg, labelBox);
        overlay.setMaxWidth(width);
        overlay.setMaxHeight(overlayHeight);
        overlay.setPrefHeight(overlayHeight);
        StackPane.setAlignment(overlay, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(pathBg, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(labelBox, Pos.BOTTOM_LEFT);

        // 7. Сборка слоёв
        root.getChildren().addAll(borderRect, imageView, overlay);

        return root;
    }

    private boolean isImageFile(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".gif");
        // эта функция уже в нескольких местах, нужно сделать ее статической в общем модуле
        // в теории до сюда никак не дойдет "неКартинка", но защита от глупости
    }

    /**
     * Важно: Этот метод должен быть вызван при завершении работы приложения (например, в Application.stop()),
     * чтобы корректно закрыть ресурсы ONNX Runtime.
     */
    public void closeServices() {

        // Здесь также можно закрыть другие сервисы, если они реализуют Closeable
        // ocrService.close(); // Если TesseractOcrService_v1 реализует Closeable
        // databaseService.close(); // Если DatabaseService реализует Closeable
    }

    /** Используется только в тестах (package-private). */
    void injectSearchDirectoryServiceForTest(SearchDirectoryService service) {
        this.searchDirectoryService = service;
    }

    /** Используется только в тестах (package-private). */
    void injectServiceBundleForTest(ServiceBundle bundle) {
        this.serviceBundle = bundle;
    }
}