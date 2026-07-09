package org.makar.ocrapp.screenobjectsextractor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.makar.ocrapp.screenobjectsextractor.model.core.LoggingService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.*;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.CVServicesOrchestrator;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.OcrServiceFactory;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase.DecomposeSessionDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase.IDecomposeSessionDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.FileIndexDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase.SearchSessionDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.view.ControllerFactory;
import org.makar.ocrapp.screenobjectsextractor.view.IControllerFactory;
import org.makar.ocrapp.screenobjectsextractor.view.main.MainController;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.SearchDirectoryService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ImageAnalysisManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection.ObjectDetectionService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.*;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.FileSearchService;
import org.makar.ocrapp.screenobjectsextractor.model.core.SearchSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.DecomposeSessionService;
import org.makar.ocrapp.screenobjectsextractor.view.main.ServiceBundle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApplication.class.getName());

    private static SearchSessionDatabaseManager searchSessionDM;
    private static IFileIndexDatabaseManager fileIndexDM;
    private static IDecomposeSessionDatabaseManager decomposeSessionDM;

    private volatile LoggingService loggingService;
    private volatile ObjectDetectionService objectDetectionService;
    private volatile ExecutorService aiExecutorService;
    private volatile ExecutorService ioExecutorService;
    private volatile Path tempYoloModelPath; // путь к временному файлу

    @Override
    public void start(Stage primaryStage) throws IOException {

        Platform.setImplicitExit(false); // если не останется видимых окон, приложение не упадет (с кодом 0)

        /* --------- настройка логирования ----------------*/
        loggingService = new LoggingService();
        /* --------- конец настройки логирования -----------*/

        /* --------- инициализация компонентов базы данных -------------*/
        SQLiteConnectionManager sqLiteConnectionManager = new SQLiteConnectionManager(AppConfig.DB_FILE_PATH);
        sqLiteConnectionManager.initializeDatabaseFile(); // на момент написания - создание файла, если его нет
        sqLiteConnectionManager.checkForeignKeySupport();

        searchSessionDM = new SearchSessionDatabaseManager(sqLiteConnectionManager);
        searchSessionDM.initializeSchema();

        fileIndexDM = new FileIndexDatabaseManager(sqLiteConnectionManager);
        fileIndexDM.initializeSchema(); // возможно стоит проверять, существует ли уже?

        decomposeSessionDM = new DecomposeSessionDatabaseManager(sqLiteConnectionManager);
        decomposeSessionDM.initializeSchema();
        /* --------- конец инициализация компонентов базы данных -------------*/

        /* --------- UI - окно main-view ------------ */
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("fxml/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        MainController controller = fxmlLoader.getController();

        primaryStage.setTitle("ScreenObjectsExtractor");
        primaryStage.setScene(scene);
        primaryStage.show();

        controller.setPrimaryStage(primaryStage);

        controller.onServicesInitializing(); // блокирование элементов Control и наследников (требуют инициализации зависимостей, ждут)

        startServiceInitialization(controller, fileIndexDM.getRepository(), primaryStage);

        primaryStage.setOnCloseRequest(e -> closeResources());

    }

    private void startServiceInitialization(MainController controller,
                                            IFileIndexRepository indexRepository,
                                            Stage primaryStage) {

        Supplier<IOcrService> ocrServiceSupplier = () -> {
            LOGGER.info("Инициализация OcrService...");
            return OcrServiceFactory.create();
        };
        Supplier<ObjectDetectionService> yoloSupplier = () -> {
            LOGGER.info("Инициализация ObjectDetectionService (YOLO)...");
            ObjectDetectionService ODService = null;
            try {
                InputStream modelStream = getClass().getResourceAsStream(AppConfig.YOLO_MODEL_PATH);
                if (modelStream == null) {
                    throw new FileNotFoundException("YOLO-модель не найдена: " + AppConfig.YOLO_MODEL_PATH);
                }
                tempYoloModelPath = Files.createTempFile("yolo_model_", ".onnx");
                Files.copy(modelStream, tempYoloModelPath, StandardCopyOption.REPLACE_EXISTING);
                modelStream.close();

                ODService = new ObjectDetectionService(tempYoloModelPath.toString(), AppConfig.YOLO_OUTPUT_NAME);
                LOGGER.info("ObjectDetectionService готов: " + tempYoloModelPath);

            } catch (FileNotFoundException e) {
                System.out.println("Ошибка инициализации ObjectDetectionService - не получилось получить модель для временного файла" + e + " :: " + e.getMessage());
                LOGGER.info("Ошибка инициализации ObjectDetectionService - не получилось получить модель для временного файла" + " :: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Ошибка инициализации ObjectDetectionService" + e + " :: " + e.getMessage());
                LOGGER.info("Ошибка инициализации ObjectDetectionService" + " :: " + e.getMessage());
                e.printStackTrace();
            }
            return ODService;
        };

        CompletableFuture<IOcrService> ocrFuture = CompletableFuture.supplyAsync(ocrServiceSupplier);
        CompletableFuture<ObjectDetectionService> odsFuture = CompletableFuture.supplyAsync(yoloSupplier);

        CompletableFuture
                .allOf(ocrFuture, odsFuture)
                .thenApply(ignored -> buildServiceGraph(ocrFuture.join(), odsFuture.join(), indexRepository))
                .thenAcceptAsync(bundle -> {
                    controller.setServiceBundle(bundle);

                    IControllerFactory controllerFactory = new ControllerFactory(bundle);
                    controller.setControllerFactory(controllerFactory);
                    LOGGER.info("Все сервисы инициализированы и переданы контроллеру.");
                    controller.onServicesReady();
                }, Platform::runLater)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Критическая ошибка инициализации сервисов", ex);
                    Platform.runLater(() -> showInitError(primaryStage, ex));
                    return null;
                });

    }

    /**
     * Построение графа зависимостей сервисов (выполняется в фоновом потоке)
     */
    private ServiceBundle buildServiceGraph(IOcrService ocrService,
                                            ObjectDetectionService objectDetectionService,
                                            IFileIndexRepository fileIndexRepository) {
        this.objectDetectionService = objectDetectionService;

        int cpuCounts = Runtime.getRuntime().availableProcessors();
        aiExecutorService               = Executors.newFixedThreadPool(cpuCounts);
        ioExecutorService               = Executors.newFixedThreadPool(cpuCounts);
        Executor searchServiceExecutor  = Executors.newFixedThreadPool(cpuCounts);
        Executor indexingServiceExecutor = Executors.newFixedThreadPool(cpuCounts);

        CVServicesOrchestrator aiServicesOrchestrator = new CVServicesOrchestrator(objectDetectionService);
        ImageAnalysisManager imageAnalysisManager   = new ImageAnalysisManager(ocrService, aiServicesOrchestrator, aiExecutorService);
        ImageContentAnalyzer imageContentAnalyzer   = new ImageContentAnalyzer(imageAnalysisManager, ioExecutorService);

        FileScanner fileScanner             = new FileScanner();
        MetadataExtractor metadataExtractor = new MetadataExtractor();

        FileIndexingProcessor indexingProcessor = new FileIndexingProcessor(
                fileIndexRepository, metadataExtractor, imageContentAnalyzer
        );

        FileIndexService fileIndexService = new FileIndexService(
                fileIndexRepository, indexingProcessor, fileScanner, metadataExtractor,
                imageContentAnalyzer, indexingServiceExecutor
        );

        SearchDirectoryService directoryService = new SearchDirectoryService();
        SearchSessionService searchSessionService = new SearchSessionService(searchSessionDM.getRepository());
        DecomposeSessionService decomposeSessionService = new DecomposeSessionService(decomposeSessionDM.getRepository());
        LoggingService loggingService = new LoggingService();

        FileSearchService fileSearchService = new FileSearchService(
                fileIndexService, fileIndexRepository,
                Executors.newFixedThreadPool(cpuCounts));
        return new ServiceBundle(
                fileSearchService,
                fileIndexService,
                directoryService,
                imageAnalysisManager,
                imageContentAnalyzer,
                searchSessionService,
                decomposeSessionService,
                loggingService,
                aiExecutorService
        );
    }

    private void showInitError(Stage owner, Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Ошибка инициализации");
        alert.setHeaderText("не удалось запустить один из ai-сервисов");
        alert.setContentText(cause.getMessage());
        alert.showAndWait();
    }

    private void closeResources() {
        LOGGER.info("Освобождение русерсов...");
        if (ioExecutorService != null) { ioExecutorService.shutdownNow(); }
        if (aiExecutorService != null) { aiExecutorService.shutdownNow(); }
        if (objectDetectionService != null) {
            try {
                objectDetectionService.close();
                LOGGER.info("ObjectDetectionService закрыт.");
            } catch (IOException e) {
                LOGGER.warning("Ошибка закрытия ObjectDetectionService: " + e.getMessage());
            }
            try {
                if (tempYoloModelPath != null) {
                    Files.deleteIfExists(tempYoloModelPath);
                    LOGGER.info("Временный файл модели удалён: " + tempYoloModelPath);
                }
            } catch (IOException e) {
                LOGGER.warning("Не удалось удалить временный файл: " + e.getMessage());
            }
        }
        Platform.exit();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Утилиты
    // ─────────────────────────────────────────────────────────────────────────

    public static void saveSearchSessions(SearchSession session) {
        System.out.println("Сохранение списка сессий поиска...");
        searchSessionDM.getRepository().saveSession(session);
    }


    public static List<SearchSession> getAllSessionsSummary() {
        System.out.println("Получение списка сессий поиска...");
        return searchSessionDM.getRepository().getAllSessionsSummary();
    }

    public static void main(String[] args) {
        launch(args);
    }

    /*
    IOException может поймать например ошибку, возникшую из-за того, что fxml файл не найден
    */

    /*
    Разница между get() и join() :
    Если асинхронная задача выбрасывает исключение, join() оборачивает его в CompletionException (непроверяемое исключение) и выбрасывает его.
    Выбрасывает проверяемые исключения: InterruptedException, ExecutionException (если асинхронная задача завершилась с исключением) и
    TimeoutException (если время ожидания истекло). Эти исключения должны обрабатываться явно или объявляться в сигнатуре метода.

    То есть
    join : выброс исключения не требует явной обработки или объявления в сигнатуре метода, так как CompletionException — непроверяемое исключение.  (например, вне лямбда выражения)
    get : выбрасывает проверяемые исключения, которые должны обрабатываться явно или объявляться в сигнатуре метода. (блокирует поток выполнения, пока не обработаем)
    * get имеет версию выбрасываемого исключения с timeout
    */
}
