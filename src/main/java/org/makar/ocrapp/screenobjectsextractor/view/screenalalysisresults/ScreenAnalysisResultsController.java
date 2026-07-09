package org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults;

/*
* Функциональные требования:
* Ф-3.1 Интерактивная валидация объектов
* Ф-3.2 Уточнение границ изображения
*
* Класс ValidationController управляет окном валидации, которое появляется после того, как пользователь
* сделал скриншот выделенной области. Его основная задача — отобразить захваченное изображение, выполнить
* на нем OCR (оптическое распознавание символов) и детекцию объектов, а затем предоставить пользователю
* инструменты для редактирования, экспорта или добавления в документ распознанных элементов.
 */

import ai.djl.modality.cv.output.DetectedObjects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.fxml.FXML;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.core.DecomposeSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.FileIndexService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.ImageContentAnalyzer;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.view.Common; // Убедитесь, что этот класс существует

import javafx.event.ActionEvent;
import javafx.scene.shape.Rectangle;
import org.makar.ocrapp.screenobjectsextractor.view.texteditor.TextEditorController;

import java.awt.image.RasterFormatException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.IOException;

/* инструменты для асинхронного выполнения сервисов обработки изображения и выполнения управления представлением */
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ScreenAnalysisResultsController {

    private final static Logger logger = Logger.getLogger(ScreenAnalysisResultsController.class.getName());

    @FXML public AnchorPane anchorPane_layer1;
    @FXML public MenuBar menuBar;
    @FXML public Menu fileMenu;
    @FXML public MenuItem closeMenuItem;
    @FXML public Menu helpMenu;
    @FXML public MenuItem aboutMenuItem;
    @FXML public SplitPane splitPane_layer3_prevVBox;
    @FXML public AnchorPane leftImageContainer;
    @FXML public AnchorPane imageDisplayAnchorPane;
    @FXML public ImageView capturedImageView;
    @FXML public javafx.scene.shape.Rectangle capturedImageBorder; // Полное имя для Rectangle, чтобы избежать конфликтов
    @FXML public AnchorPane rightControlContainer;
    @FXML public HBox buttonHBox;
    @FXML public Region leftButtonSpacer;
    @FXML public Button recognizedTextButton;
    @FXML public Button detectedObjectsButton;
    @FXML public Button qrButton;
    @FXML public Button tablesButton;
    @FXML public Region rightButtonSpacer;
    @FXML public VBox objectListVBox;
    @FXML public ScrollPane scrollVBoxElements;
    @FXML public HBox HBoxForCenteringImageView;
    /* Выделенный элемент VBox -> выделенная область на изображении */
    @FXML public javafx.scene.shape.Rectangle highlightRectangle;

    /* Сцена и динамические элементы */
    private Stage stage;
    /* Ссылка на выделенный VBox элемент, если нажата кнопка recognizedTextButton */
    private VBox currentlySelectedTextItem;
    /* Ссылка на выделенный VBox элемент, если нажата кнопка detectedObjectsButton */
    private VBox currentlySelectedDetectedObjectItem;


    /* Изображение и результаты обработки */
    private BufferedImage currentCapturedImage; // Хранение захваченного изображения для предварительного просмотра
    private List<TextObject> textObjects; // Хранение текстовых объектов. Нужно рассмотреть вынос в отдельный класс.
    private List<OCRAppDetectedObject> ocrAppDetectedObjects = new ArrayList<>();

    /* Вообще похоже на то, что все эти сервисы нужно инициализировать как можно раньше, например в MainApplication,
    * чтобы фиксировать ошибки как можно раньше. Нужно понять, как это скажется на тестируемости и масштабируемости.
    * Еще одна причина - ведь эти сервисы будут использовать не только в этом контроллере, но и в системе поиска
    * по запросу.
    * Но это увеличит количество передаваемых аргументов -> увеличит сцепление.
    * И как в итоге правильно? */

    /* Логика обработки изображения разными сервисами */
    //private ImageAnalysisManager imageAnalysisManager;
    private ImageContentAnalyzer imageContentAnalyzer; // обертка над imageAnalysisManager

    /* сохранение в базу данных - статистика */
    private DecomposeSessionService decomposeSessionService;

    /* сохранение в базу данных - индекс */
    private FileIndexService fileIndexService;

    /* инструменты асинхронного выполнения AI-сервисов */
    private ExecutorService aiExecutorService;

    @FXML
    public void initialize() {

        anchorPane_layer1.setMinWidth(900);
        anchorPane_layer1.setMinHeight(600);

        AnchorPane.setTopAnchor(rightControlContainer, 0.0);
        AnchorPane.setRightAnchor(rightControlContainer, 0.0);
        AnchorPane.setBottomAnchor(rightControlContainer, 0.0);
        AnchorPane.setLeftAnchor(rightControlContainer, 0.0);

        AnchorPane.setTopAnchor(scrollVBoxElements, 0.0);
        AnchorPane.setRightAnchor(scrollVBoxElements, 0.0);
        AnchorPane.setBottomAnchor(scrollVBoxElements, 0.0);
        AnchorPane.setLeftAnchor(scrollVBoxElements, 0.0);

        AnchorPane.setTopAnchor(HBoxForCenteringImageView, 0.0);
        AnchorPane.setRightAnchor(HBoxForCenteringImageView, 0.0);
        AnchorPane.setBottomAnchor(HBoxForCenteringImageView, 0.0);
        AnchorPane.setLeftAnchor(HBoxForCenteringImageView, 0.0);

        scrollVBoxElements.setFitToWidth(true);

        capturedImageBorder.widthProperty().bind(imageDisplayAnchorPane.widthProperty());
        capturedImageBorder.heightProperty().bind(imageDisplayAnchorPane.heightProperty());
        capturedImageView.setPreserveRatio(true); /* Установлено по умолчанию, но чтобы запомнить, указал явно */
        capturedImageView.fitWidthProperty().bind(imageDisplayAnchorPane.widthProperty());
        capturedImageView.fitHeightProperty().bind(imageDisplayAnchorPane.heightProperty());
        /* <- Привязаны оба свойства, но ImageView автоматически выберет коэффициент масштабирования */

        closeMenuItem.setOnAction(event -> {
            if (stage != null) {
                stage.close();
            }
        });

        selectButton(detectedObjectsButton);
        displayDetectedObjects();


        recognizedTextButton.setOnAction(event -> {
            selectButton(recognizedTextButton);
            displayTextObjects();
            clearHighlight();
        });

        detectedObjectsButton.setOnAction(event -> {
            selectButton(detectedObjectsButton);
            displayDetectedObjects();
            clearHighlight();
        });

        qrButton.setOnAction(event -> {
            selectButton(qrButton);
            // displayQRObjects(); // Реализовать позже
            clearHighlight();
        });

        tablesButton.setOnAction(event -> {
            selectButton(tablesButton);
            // displayTableObjects(); // Реализовать позже
            clearHighlight();
        });

/*
        highlightRectangle = new javafx.scene.shape.Rectangle();
        highlightRectangle.setFill(Color.TRANSPARENT);
        highlightRectangle.setStroke(Color.LIGHTBLUE);
        highlightRectangle.setStrokeWidth(2);
        highlightRectangle.setArcHeight(10);
        highlightRectangle.setArcWidth(10);*/

        //executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        System.out.println("(ScreenAnalysisResultsController) Info: OCRService успешно инициализирован. ");
    }


    public void setImageContentAnalyzer(ImageContentAnalyzer imageContentAnalyzer) {
        this.imageContentAnalyzer = imageContentAnalyzer;
    }

    public void setAiExecutorService(ExecutorService aiExecutorService) {
        this.aiExecutorService = aiExecutorService;
    }

    public void setDecomposeSessionService(DecomposeSessionService decomposeSessionService) {
        this.decomposeSessionService = decomposeSessionService;
    }

    public void setFileIndexService(FileIndexService fileIndexService) {
        this.fileIndexService = fileIndexService;
    }


    private void centerImageViewInAnchorPane() {
        double paneWidth = imageDisplayAnchorPane.getWidth();
        double paneHeight = imageDisplayAnchorPane.getHeight();
        double imageOriginalWidth = capturedImageView.getImage().getWidth();
        double imageOriginalHeight = capturedImageView.getImage().getHeight();

        double imageAspectRatio = imageOriginalWidth / imageOriginalHeight;
        double paneAspectRatio = paneWidth / paneHeight;

        double effectiveImageWidth;
        double effectiveImageHeight;

        // Определяем, какая сторона ограничивает масштабирование
        if (imageAspectRatio > paneAspectRatio) {
            // Изображение шире относительно своей высоты, чем панель
            // Будет подогнано по ширине, высота будет меньше высоты панели
            effectiveImageWidth = paneWidth;
            effectiveImageHeight = paneWidth / imageAspectRatio;
        } else {
            // Изображение выше относительно своей ширины, чем панель, или имеет тот же аспект
            // Будет подогнано по высоте, ширина будет меньше ширины панели
            effectiveImageHeight = paneHeight;
            effectiveImageWidth = paneHeight * imageAspectRatio;
        }

        capturedImageView.setX((paneWidth - effectiveImageWidth) / 2.0);
        System.out.println("\nвычисленный X: " + (paneWidth - effectiveImageWidth) / 2.0 +
                "\nширина AnchorPane: " + paneWidth);
    }


    /**
     * Передача сцены в ScreenAnalysisResultsController извне: из MainController.
     * @param stage .
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        onStageOperations();
    }

    /**
     * Stage operations. Собирающий метод.
     * Здесь выполняются операции над stage в рамках контроллера ScreenAnalysisResultsController.
     * */
    private void onStageOperations() {

        setAspectRatio();

        //centerImageViewInAnchorPane();
        if (currentCapturedImage != null) {
            logger.log(Level.INFO, "(ScreenAnalysisResultsController) Ingo: Начало обработки изображения");
            startImageAnalysis();
            logger.log(Level.INFO, "(ScreenAnalysisResultsController) Ingo: Обработка изображения завершена");
        } else {
            System.out.println("(ScreenAnalysisResultsController) Warning: Нет  изображения, currentCapturedImage is null! Невозможно начать обработку изображения.");
            logger.log(Level.WARNING, "(ScreenAnalysisResultsController) Warning: Нет  изображения, currentCapturedImage is null! Невозможно начать обработку изображения.");
        }
    }


    /**
     * Stage operation.
     * Лиссенеры, следящие за сохранением пропорций окна screen-analysis-results-view.fxml
     * */
    private void setAspectRatio() {
        double aspectRatio;
        if (!stage.isFullScreen()) {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            aspectRatio = screenBounds.getWidth() / screenBounds.getHeight();
        } else {
            aspectRatio = 3.0 / 2.0; // Для полноэкранного режима, если необходимо
        }

        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            Common.adjustStageDimension(stage, newValue.doubleValue(), aspectRatio, true);
        });

        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            Common.adjustStageDimension(stage, newValue.doubleValue(), aspectRatio, false);
        });
    }


    /**
     * Содержит логику вызова методов ImageContentAnalyzer,
     * которые инициализируют потоки обработки изображения разными сервисами через ImageAnalysisManager:
     * TesseractOcrService_v1, ObjectDetectionService (модели yolo).
     */
    private CompletableFuture<FileMetadata> startImageAnalysis() {
        if (imageContentAnalyzer == null) {
            logger.warning("imageContentAnalyzer == null, анализ невозможен.");
            return CompletableFuture.completedFuture(null);
        }
        if (currentCapturedImage == null) {
            logger.warning("currentCapturedImage == null.");
            return CompletableFuture.completedFuture(null);
        }

        String captureName = "capture_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        FileMetadata captureMetadata = new FileMetadata(
                null,                  // filePath — нет файла на диске
                captureName,           // fileName
                "png",                 // fileExtension
                0L,                    // fileSize неизвестен
                LocalDateTime.now(),   // creationDate
                LocalDateTime.now(),   // modificationDate
                Collections.emptyList(),
                Collections.emptyList()
        );

        return CompletableFuture
                .runAsync(() -> imageContentAnalyzer.analyzeAndUpdateMetadata(
                                currentCapturedImage, captureMetadata),
                        aiExecutorService)   // фоновый поток — не UI
                .thenApplyAsync(ignored -> {
                    // Обновляем поля контроллера и UI из результатов metadata
                    this.textObjects = captureMetadata.getRecognizedTextContent();
                    this.ocrAppDetectedObjects = captureMetadata.getDetectedObjects();

                    // Обновляем UI
                    if (recognizedTextButton.getStyleClass().contains("validation-button-selected")) {
                        displayTextObjects();
                    }
                    if (detectedObjectsButton.getStyleClass().contains("validation-button-selected")) {
                        displayDetectedObjects();
                    }

                    saveResults(captureMetadata);

                    return captureMetadata;
                }, Platform::runLater)
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Ошибка анализа изображения: " + ex.getMessage(), ex);
                    Common.showErrorAlert("Ошибка анализа", "Не удалось проанализировать изображение:", ex.getCause().getMessage());
                    return null;
                });
    }

    /*private void startImageAnalysis() {
        if (imageAnalysisManager != null) {

            if (tesseractOcrService != null) {
                imageAnalysisManager.recognizeTextAsync(currentCapturedImage)
                        .thenAcceptAsync(textResults -> {
                            this.textObjects = textResults.getTextObjectList();
                            if (textObjects != null && textObjects.isEmpty()) {
                                System.out.println("(ScreenAnalysisResultsController)Info: UI обновлен, текст не найден. ");
                            } else {
                                System.out.println("(ScreenAnalysisResultsController)Info: UI обновлен, текст распознан. ");
                            }
                            if (recognizedTextButton.getStyleClass().contains("validation-button-selected")) {
                                displayTextObjects();
                            }

                            // Скрываем индикатор загрузки
                            // Например: showLoadingIndicator(false);

                        }, Platform::runLater)
                        .exceptionally(exception -> {
                            System.out.println("(ScreenAnalysisResultsController) Error: Не получилось получить текст на фото: " + exception.getMessage());
                            Common.showErrorAlert("(ScreenAnalysisResultsController) Error", "Не удалось проанализировать изображение: ", exception.getCause().getMessage());
                            // Скрыть индикатор загрузки.
                            return null;
                        });
            }

            if (imageAnalysisManager != null) {
                System.out.println("(ScreenAnalysisResultsController)Info: aiServiceOrchestrator не null, начало обработки");
                imageAnalysisManager.detectObjectsAsync(currentCapturedImage)
                        .thenAcceptAsync(detectedObjectsResult -> {
                            this.currentDetectedObjects = detectedObjectsResult;
                            if (currentDetectedObjects != null && currentDetectedObjects.getNumberOfObjects() != 0) {
                                System.out.println("(ScreenAnalysisResultsController)Info: UI обновлен, система нашла объекты.");
                                for (int i = 0; i < detectedObjectsResult.getNumberOfObjects(); i++) {
                                    DetectedObjects.DetectedObject obj = detectedObjectsResult.item(i);
                                    System.out.printf(
                                            "  Detected object: class='%s', probability=%.3f, box=[x=%.2f, y=%.2f, w=%.2f, h=%.2f]%n",
                                            obj.getClassName(),
                                            obj.getProbability(),
                                            obj.getBoundingBox().getBounds().getX(),
                                            obj.getBoundingBox().getBounds().getY(),
                                            obj.getBoundingBox().getBounds().getWidth(),
                                            obj.getBoundingBox().getBounds().getHeight()
                                    );
                                }
                            } else {
                                System.out.println("(ScreenAnalysisResultsController)Info: UI обновлен, объекты не найдены :( .");
                            }
                            if (detectedObjectsButton.getStyleClass().contains("validation-button-selected")) {
                                displayDetectedObjects();
                            }

                            // Скрываем индикатор загрузки
                            // Например: showLoadingIndicator(false);

                        }, Platform::runLater)
                        .exceptionally(exception -> {
                            System.out.println("(ScreenAnalysisResultsController) Error: Не получилось получить распознать объекты на фото: " + exception.getMessage());
                            // Скрыть индикатор загрузки.
                            return null;
                        });
            }
        } else {
            System.err.println("(ScreenAnalysisResultsController)Warning: при попытке начать обработку изображения imageAnalysisManager = null");
        }
    }*/


    /**
    * Внешнее выделение кнопки из меню выбора класса найденных объектов.
    * @param button Ссылка на кнопку на форме.
     */
    private void selectButton(Button button) {
        // Убираем стиль "selected" со всех кнопок
        recognizedTextButton.getStyleClass().remove("validation-button-selected");
        detectedObjectsButton.getStyleClass().remove("validation-button-selected");
        qrButton.getStyleClass().remove("validation-button-selected");
        tablesButton.getStyleClass().remove("validation-button-selected");

        // Добавляем стиль "selected" к нажатой кнопке
        if (!button.getStyleClass().contains("validation-button-selected")) {
            button.getStyleClass().add("validation-button-selected");
        }
    }


    /* Нужно вынести отсюда логику запуска обработки изображения в более подходящее для этого место.
    Выполняться должно начать сразу после получения изображения. Возможно стоит рассмотреть параллельное
    процесса обработки изображения и отображения окна ValidationImage с изначально пустым списком результатов
    обработки, обновляемым по мере параллельного выполнения обработки.
     */
    public void setCapturedImage(BufferedImage image) {
        this.currentCapturedImage = image; // Сохраняем оригинальное захваченное изображение
        if (image != null) {
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            capturedImageView.setImage(fxImage);
        }
    }


    /**
     * Сохраняет в поле контроллера список найденных слов с их свойствами (расположение).
     * Вызывает метод, размещающий
     * @param recognizedText - список найденных слов
     */
    public void setRecognizedText(List<TextObject> recognizedText) {
        this.textObjects = recognizedText;
        if (recognizedTextButton.getStyleClass().contains("validation-button-selected")) {
            displayTextObjects();
        }
    }

    public void setDetectedObjects(List<OCRAppDetectedObject> ocrAppDetectedObjects) {
        this.ocrAppDetectedObjects = ocrAppDetectedObjects;
        if (detectedObjectsButton.getStyleClass().contains("validation-button-selected")) {
            displayDetectedObjects();
        }
    }


    /**
     * Отображает текстовые объекты в objectListVBox.
     */
    private void displayTextObjects() {
        objectListVBox.getChildren().clear(); // Очищаем предыдущее содержимое
        if (imageDisplayAnchorPane.getChildren().contains(highlightRectangle)) {
            clearHighlight();
        }

        if (currentCapturedImage != null && textObjects != null && !textObjects.isEmpty()) {
            for (TextObject textObject : textObjects) {
                VBox textItem = createTextObjectPreviewItem(textObject);
                objectListVBox.getChildren().add(textItem);
            }

        } else {
            Label noTextLabel = new Label("Текст не найден или не распознан.");
            noTextLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 14px; -fx-padding: 10;");
            objectListVBox.getChildren().add(noTextLabel);
        }
    }


    /**
     * Метод создания элементов для UI - элементы наполнения VBox при активном статусе кнопки Button recognizedTextButton
     * @param textObject
     * @return
     */
    private VBox createTextObjectPreviewItem(TextObject textObject) {
        VBox itemContainer = new VBox(5); // 5px отступ между элементами
        itemContainer.setAlignment(Pos.TOP_LEFT);
        itemContainer.setPadding(new Insets(5));
        itemContainer.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand");
        itemContainer.setPrefWidth(250.0);
        itemContainer.setMaxWidth(250.0);
        itemContainer.setPrefHeight(250.0);
        // itemContainer.setMaxWidth(Double.MAX_VALUE); // Разрешает горизонтальное расширение
        VBox.setMargin(itemContainer, new Insets(0, 0, 10, 0)); // Отступ снизу для каждого элемента

        ImageView previewImageView = new ImageView();
        BufferedImage croppedImage = createCroppedBufferedImageWithTextObject(textObject);

        StackPane imageWrapper = new StackPane(); // Обертка для ImageView; для управления шириной и высотой
        imageWrapper.setStyle("-fx-background-color: black; -fx-border-color: #ccc; -fx-border-width: 1;");
        imageWrapper.setAlignment(Pos.CENTER);

        if (croppedImage != null) {
            Image fxImage = SwingFXUtils.toFXImage(croppedImage, null);
            previewImageView.setImage(fxImage);
            previewImageView.setFitWidth(90);
            previewImageView.setFitHeight(90);
            previewImageView.setPreserveRatio(true);
            // previewImageView.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");
            imageWrapper.getChildren().add(previewImageView);
        } else {
            Label noPreview = new Label("Нет превью изображения");
            noPreview.setStyle("-fx-text-fill: white;"); // Белый текст на черном фоне
            itemContainer.getChildren().add(noPreview);
        }


        // Лейбл для отображения самого текста.
        String displayedText = textObject.getText();
        if (displayedText.length() > 50) { // Ограничиваем длину для отображения в списке
            displayedText = displayedText.substring(0, 47) + "...";
        }
        Label textLabel = new Label(displayedText);
        textLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // Добавляем координаты для информации
        Label coordLabel = new Label(String.format("X: %d, Y: %d, W: %d, H: %d",
                textObject.getX(), textObject.getY(), textObject.getWidth(), textObject.getHeight()));
        coordLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        itemContainer.getChildren().addAll(imageWrapper, textLabel, coordLabel);

        itemContainer.setOnMouseClicked(event -> {
            // Одиночный клик — выделить область на изображении
            highlightTextObject(textObject);
            setSelectedState(itemContainer, currentlySelectedTextItem,
                    "-fx-background-color: #cce8ff; -fx-border-color: #3399ff; " +
                            "-fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;",
                    "-fx-background-color: #f8f8f8; -fx-border-color: #ddd; " +
                            "-fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
            currentlySelectedTextItem = itemContainer;

            // Двойной клик — открыть редактор
            if (event.getClickCount() == 2) {
                openTextEditor(textObject.getText());
            }
        });

        return itemContainer;
    }


    private void displayDetectedObjects() {
        objectListVBox.getChildren().clear();
        clearHighlight();

        if (ocrAppDetectedObjects != null && !ocrAppDetectedObjects.isEmpty()) {
            for (OCRAppDetectedObject obj : ocrAppDetectedObjects) {
                objectListVBox.getChildren().add(createDetectedObjectPreviewItem(obj));
            }
        } else {
            Label noTextLabel = new Label("Ничего не найдено.");
            noTextLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 14px; -fx-padding: 10;");
            objectListVBox.getChildren().add(noTextLabel);
        }
    }


    private VBox createDetectedObjectPreviewItem(OCRAppDetectedObject obj) {
        VBox itemContainer = new VBox(5);
        itemContainer.setAlignment(Pos.TOP_LEFT);
        itemContainer.setPadding(new Insets(5));
        itemContainer.setPrefWidth(250.0);
        itemContainer.setMaxWidth(250.0);
        itemContainer.setPrefHeight(250.0);
        itemContainer.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand");
        VBox.setMargin(itemContainer, new Insets(0, 0, 10, 0));

        StackPane imageWrapper = new StackPane();
        if (currentCapturedImage != null) {
            BufferedImage croppedImage = createCroppedBufferedImageWithDetectedObject(obj);

            imageWrapper.setPrefSize(100, 100);
            imageWrapper.setStyle("-fx-background-color: black; -fx-border-color: #ccc; -fx-border-width: 1;");
            imageWrapper.setAlignment(Pos.CENTER);

            if (croppedImage != null) {
                ImageView previewImageView = new ImageView(SwingFXUtils.toFXImage(croppedImage, null));
                previewImageView.setFitWidth(90);
                previewImageView.setFitHeight(90);
                previewImageView.setPreserveRatio(true);
                previewImageView.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");
                imageWrapper.getChildren().add(previewImageView);
            } else {
                Label noPreview = new Label("Нет превью изображения");
                noPreview.setStyle("-fx-text-fill: white;");
                imageWrapper.getChildren().add(noPreview);
            }
        } else {
            logger.warning("(ScreenAnalysisResults) currentCapturedImage == null при формировании элемента VBox");
        }

        Label classNameLabel = new Label(obj.getClassName());
        classNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label confidenceLevelLabel = new Label(String.format("Confidence: %.2f%%", obj.getProbability() * 100));
        confidenceLevelLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        itemContainer.getChildren().addAll(imageWrapper, classNameLabel, confidenceLevelLabel);

        itemContainer.setOnMouseClicked(event -> {
            highlightDetectedObject(obj);
            setSelectedState(itemContainer, currentlySelectedDetectedObjectItem,
                    "-fx-background-color: #0ef2f7; -fx-border-color: #00c8cc; " +
                            "-fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;",
                    "-fx-background-color: #f8f8f8; -fx-border-color: #ddd; " +
                            "-fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
            currentlySelectedDetectedObjectItem = itemContainer;
        });

        return itemContainer;
    }


    /**
     * Функция вырезает и возвращает кусок фотографии, используя данные из передаваемого объекта TextObject.
     * @param textObject Содержит необходимые метаданные: X, Y, Width, Height.
     * @return javafx.scene.image
     */
    private BufferedImage createCroppedBufferedImageWithTextObject(TextObject textObject) {
        BufferedImage croppedImage = null;
        try {
            int x = textObject.getX();
            int y = textObject.getY();
            int width = textObject.getWidth();
            int height = textObject.getHeight();

            if (x + width > currentCapturedImage.getWidth()) {
                width = currentCapturedImage.getWidth() - x;
            }
            if (y + height > currentCapturedImage.getHeight()) {
                height = currentCapturedImage.getHeight() - y;
            }

            if (x >= 0 && y >= 0
                    && width  > 0 && height > 0
                    && x + width  <= currentCapturedImage.getWidth()
                    && y + height <= currentCapturedImage.getHeight()) {
                croppedImage = currentCapturedImage.getSubimage(x, y, width, height);
            } else {
                System.err.println("Неадекватные свойства x или y или width или height.\n " +
                        "bounding box (x,y,w,h): " + x + "," + y + "," + width + "," + height + "\n" +
                        "Full image size: " + currentCapturedImage.getWidth() + "x" + currentCapturedImage.getHeight());
            }
        } catch (RasterFormatException e) {
            /* The RasterFormatException is thrown if there is invalid layout information in the Raster. */
            System.err.println("Error cropping image for text object : " + e.getMessage());
            System.err.println("(ScreenObjectResults.createCroppedBufferedImageWithTextObject) Warning : " +
                    "RasterFormatException вызван при следующих параметрах:" +
                    " x=" + textObject.getX() + ", y=" + textObject.getY() +
                    ", width=" + textObject.getWidth() + ", height=" + textObject.getHeight());
        }

        return croppedImage;
    }


    private BufferedImage createCroppedBufferedImageWithDetectedObject(OCRAppDetectedObject obj) {
        BufferedImage croppedImage = null;
        try {
            int x = (int)(obj.getBoxX() * currentCapturedImage.getWidth());
            int y = (int)(obj.getBoxY() * currentCapturedImage.getHeight());
            int width = (int)(obj.getBoxWidth() * currentCapturedImage.getWidth());
            int height = (int)(obj.getBoxHeight() * currentCapturedImage.getHeight());

            x = Math.max(x, 0);
            y = Math.max(y, 0);
            width = Math.min(width, currentCapturedImage.getWidth() - x);
            height = Math.min(height, currentCapturedImage.getHeight() - y);

            if (width > 0 && height > 0) {
                croppedImage = currentCapturedImage.getSubimage(x, y, width, height);
            } else {
                System.err.println("Неадекватные bounding box координаты: " +
                        x + "," + y + "," + width + "," + height +
                        " | Image: " + currentCapturedImage.getWidth() + "x" + currentCapturedImage.getHeight());
            }
        } catch (RasterFormatException e) {
            System.err.println("RasterFormatException при кропе объекта: " + e.getMessage());
        }
        return croppedImage;
    }

    // TextObject: координаты абсолютные (int px)
    private void highlightTextObject(TextObject t) {
        highlightObject(t.getX(), t.getY(), t.getWidth(), t.getHeight());
    }

    // OCRAppDetectedObject: координаты нормализованные (0.0–1.0)
    private void highlightDetectedObject(OCRAppDetectedObject obj) {
        double imgW = currentCapturedImage.getWidth();
        double imgH = currentCapturedImage.getHeight();
        highlightObject(
                obj.getBoxX()      * imgW,
                obj.getBoxY()      * imgH,
                obj.getBoxWidth()  * imgW,
                obj.getBoxHeight() * imgH
        );
    }

    /**
     * Отвечает за добавление/обновление 'highlightRectangle' на 'imageDisplayAnchorPane'.
     * Перед добавлением новой рамки, старая (если есть) удаляется из 'imageDisplayAnchorPane',
     * чтобы всегда была видна только одна рамка. (на момент написания не рассматривается
     * функция выделения нескольких объектов через CTRL)
     * @param X Абсцисса нижней левой координаты.
     * @param Y Ордината нижней левой координаты.
     * @param width Ширина выделяемой области.
     * @param height Высоты выделяемой области.
     */
    private void highlightObject(double X, double Y, double width, double height) {
        if (currentCapturedImage == null || capturedImageView.getImage() == null) {
            logger.log(Level.WARNING,
                    "(ScreenAnalysisResultsController) Warning: " +
                            "При выполнении highlightObject обнаружено, " +
                            "currentCapturedImage = null или изображение не загружено!");
            clearHighlight();
            return;
        }
        clearHighlight();

        double imgW = currentCapturedImage.getWidth();
        double imgH = currentCapturedImage.getHeight();
        double ratio = imgW / imgH;

        DoubleBinding effectiveViewWidth = Bindings.createDoubleBinding(() -> {
            double pW = imageDisplayAnchorPane.getWidth();
            double pH = imageDisplayAnchorPane.getHeight();
            if (pH <= 0) return pW;
            return (pW / pH > ratio) ? pH * ratio : pW;
        }, imageDisplayAnchorPane.widthProperty(), imageDisplayAnchorPane.heightProperty());

        DoubleBinding effectiveViewHeight = Bindings.createDoubleBinding(() -> {
            double pW = imageDisplayAnchorPane.getWidth();
            double pH = imageDisplayAnchorPane.getHeight();
            if (pH <= 0) return pH;
            return (pW / pH > ratio) ? pH : pW / ratio;
        }, imageDisplayAnchorPane.widthProperty(), imageDisplayAnchorPane.heightProperty());

        DoubleBinding scaleX  = effectiveViewWidth.divide(imgW);
        DoubleBinding scaleY  = effectiveViewHeight.divide(imgH);
        DoubleBinding offsetX = imageDisplayAnchorPane.widthProperty().subtract(effectiveViewWidth).divide(2.0);
        DoubleBinding offsetY = imageDisplayAnchorPane.heightProperty().subtract(effectiveViewHeight).divide(2.0);

        highlightRectangle.xProperty()     .bind(scaleX.multiply(X).add(offsetX));
        highlightRectangle.yProperty()     .bind(scaleY.multiply(Y).add(offsetY));
        highlightRectangle.widthProperty() .bind(scaleX.multiply(width));
        highlightRectangle.heightProperty().bind(scaleY.multiply(height));

        if (!imageDisplayAnchorPane.getChildren().contains(highlightRectangle)) {
            imageDisplayAnchorPane.getChildren().add(highlightRectangle);
        }
        highlightRectangle.setVisible(true);
        logger.info("--- highlightObject debug end ---");
    }

    /**
     *     Удаляет 'highlightRectangle' из 'imageDisplayAnchorPane' и сбрасывает состояние
     *     выделения элементов в списке. Вызывается при переключении между вкладками ("Текст", "Объекты" и т.д.)
     *     и при загрузке нового изображения. (Стоит рассмотреть вариант точечного удаления, требующего привязку
     *     создаваемого 'highlightRectangle' к объекту соответствующего класса. Объекты с результатами представляют
     *     собой списки, так что для привязки нужны ссылка на результат и идентификатор)
     */
    private void clearHighlight() {
        highlightRectangle.setVisible(false);
        highlightRectangle.xProperty()     .unbind();
        highlightRectangle.yProperty()     .unbind();
        highlightRectangle.widthProperty() .unbind();
        highlightRectangle.heightProperty().unbind();

        if (imageDisplayAnchorPane.getChildren().contains(highlightRectangle)) {
            imageDisplayAnchorPane.getChildren().remove(highlightRectangle);
        }

        if (currentlySelectedDetectedObjectItem != null) {
            currentlySelectedDetectedObjectItem.setStyle("");
            currentlySelectedDetectedObjectItem = null;
        }

        if (currentlySelectedTextItem != null) {
            currentlySelectedTextItem.setStyle("");
            currentlySelectedTextItem = null;
        }
    }


    /**
     * Метод содержит логику смены стиля кнопки:
     * при нажатии на другую кнопку,
     * при нажатии на любую другую часть окна.
     * @param currentSelectedItem
     * @param previousSelectedItem
     * @param selectedStyle
     * @param defaultStyle
     */
    private void setSelectedState(VBox currentSelectedItem,
                                  VBox previousSelectedItem,
                                  String selectedStyle,
                                  String defaultStyle) {
        if (previousSelectedItem != null) {
            previousSelectedItem.setStyle(defaultStyle);
        }
        if (currentSelectedItem != null) {
            currentSelectedItem.setStyle(selectedStyle);
        }
    }


    private void openTextEditor(String textToEdit) {
        try {
            // Корректный путь к FXML-файлу
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/makar/ocrapp/screenobjectsextractor/fxml/text-editor-view.fxml"));
            Parent root = fxmlLoader.load(); // Загружаем FXML и получаем корневой элемент
            TextEditorController controller = fxmlLoader.getController(); // Получаем инстанс контроллера

            // Устанавливаем текст *перед* показом сцены, чтобы он был доступен в initialize() или сразу отобразился
            controller.setCurrentText(textToEdit);

            Stage editorStage = new Stage();
            editorStage.initModality(Modality.APPLICATION_MODAL); // Модальное окно
            editorStage.initOwner(stage); // Владелец - текущая сцена ValidationController
            editorStage.setTitle("Редактирование текста");
            editorStage.initStyle(StageStyle.TRANSPARENT); // Для скругленных углов (если применены через CSS)

            Scene scene = new Scene(root, 300, 500); // Создаем сцену с загруженным корневым элементом
            scene.setFill(Color.TRANSPARENT); // Делаем фон сцены прозрачным для скругленных углов

            editorStage.setScene(scene);
            editorStage.show();

        } catch (IOException e) {
            System.err.println("Error loading text-editor-view.fxml: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    // Метод для создания "черного квадрата с надписью" - пустой шаблон для объектов в VBox
    private StackPane createObjectRepresentation(String className) {
        StackPane stackPane = new StackPane();
        stackPane.setPrefSize(100, 100); // Размер квадрата

        Rectangle rect = new Rectangle(100, 100);
        rect.setFill(Color.BLACK); // Черный квадрат
        rect.setStroke(Color.WHITE); // Белая рамка
        rect.setStrokeWidth(1);

        Label label = new Label(className);
        label.setTextFill(Color.WHITE); // Белый текст

        stackPane.getChildren().addAll(rect, label);
        return stackPane;
    }


    /**
     * Сохраняет результаты анализа в базы данных.
     * Вызывается после успешного завершения startImageAnalysis().
     *
     * @param metadata FileMetadata с заполненными recognizedTextContent и detectedObjects.
     */
    private void saveResults(FileMetadata metadata) {
        if (metadata == null) return;

        if (metadata.getFileName() == null || metadata.getFileName().isBlank()) {
            metadata.setFileName(
                    "capture_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            );
        }

        if (currentCapturedImage != null) {
            metadata.setImageWidth(currentCapturedImage.getWidth());
            metadata.setImageHeight(currentCapturedImage.getHeight());

            // 2. Estimate RAM size if it is not a physical file on disk (so fileSize isn't null/createEmpty)
            if (metadata.getFileSize() == null || metadata.getFileSize() <= 0) {
                try {
                    java.io.ByteArrayOutputStream tmp = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(currentCapturedImage, "png", tmp);
                    metadata.setFileSize((long) tmp.size());
                } catch (Exception e) {
                    metadata.setFileSize((long) (currentCapturedImage.getWidth() * currentCapturedImage.getHeight() * 3));
                }
            }
        }

        // 1. Сохранение в статистику (decompose sessions)
        if (decomposeSessionService != null) {
            try {
                DecomposeSessionEntry entry = DecomposeSessionEntry.builder()
                        .fileMetadata(metadata)
                        .processedAt(LocalDateTime.now())
                        .captureSource(DecomposeSessionEntry.CAPTURE_SOURCE.SCREEN_CAPTURE)
                        .build();
                decomposeSessionService.save(entry);
                logger.info("DecomposeSession сохранён для: " + metadata.getFileName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка сохранения в decompose sessions: " + e.getMessage(), e);
            }
        }

        // 2. Запись в файловый индекс — только если есть реальный файл на диске
        if (fileIndexService != null && metadata.getFilePath() != null) {
            try {
                fileIndexService.saveAnalyzedFile(metadata);
                logger.info("FileIndex обновлён для: " + metadata.getFilePath());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка сохранения в файловый индекс: " + e.getMessage(), e);
            }
        }
    }

}