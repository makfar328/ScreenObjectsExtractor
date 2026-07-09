package org.makar.ocrapp.screenobjectsextractor.model.core.search;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.*;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.FileIndexDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.FileIndexRepository;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Сквозные интеграционные тесты FileSearchService.
 *
 * Стратегия изоляции:
 *   - БД: FileIndexRepository подключается к SQLite in-memory (":memory:").
 *   - ФС:  реальные файлы во временной папке (@TempDir), удаляется JUnit после каждого теста.
 *   - OCR / CV: заглушки (не нужны для проверки метаданных пути/имени/расширения).
 *   - Executor: прямой (Runnable::run), делает асинхронный код синхронным.
 */
@DisplayName("FileSearchService — сквозные IT-тесты")
class FileSearchServiceIT {

    // ── Инфраструктура ────────────────────────────────────────────────────

    /**
     * Временная папка, создаётся JUnit перед каждым тестом и удаляется после.
     * Объявлена нестатически → новый экземпляр для каждого теста.
     */
    @TempDir
    Path tempDir;

    private FileIndexDatabaseManager dbManager;

    /** Репозиторий c in-memory БД — изоляция на уровне метода. */
    private IFileIndexRepository repository;

    /** Тестируемый сервис. */
    private FileSearchService service;

    /**
     * Executor, выполняющий задачи прямо в вызывающем потоке.
     * Делает CompletableFuture синхронными и убирает потенциальные
     * race-condition из IT-тестов.
     */
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    // ── Фикстуры ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws IOException {
        // SQLite-файл живёт ВНУТРИ tempDir, не вместо него
        Path dbFile = tempDir.resolve("file_index.db");

        String jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        System.out.println("Database URL: " + jdbcUrl);

        SQLiteConnectionManager connManager = new SQLiteConnectionManager(jdbcUrl);

        dbManager = new FileIndexDatabaseManager(connManager);
        dbManager.initializeSchema();

        repository = dbManager.getRepository();

        FileIndexService fileIndexService = buildFileIndexService();

        service = new FileSearchService(
                fileIndexService,
                repository,
                new DirectExecutorService()
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        repository.truncateAll();
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    /**
     * Собирает FileIndexService с реальными компонентами.
     * MetadataExtractor и FileScanner — реальные (читают ФС).
     * ImageContentAnalyzer — заглушка: возвращает пустые списки,
     *   не запускает Tesseract / DJL — это не входит в область теста.
     */
    private FileIndexService buildFileIndexService() {
        StubImageContentAnalyzer stubAnalyzer = new StubImageContentAnalyzer();
        MetadataExtractor metadataExtractor   = new MetadataExtractor();

        return new FileIndexService(
                repository,
                new FileIndexingProcessor(repository, metadataExtractor, stubAnalyzer),
                new FileScanner(),
                metadataExtractor,
                stubAnalyzer,
                SYNC_EXECUTOR
        );
    }

    /**
     * Создаёт реальный PNG-файл во временной папке.
     * 10×10 пикселей, белый фон — минимально валидный файл для ImageIO.
     */
    private Path createRealPng(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    /** Создаёт реальный JPG-файл во временной папке. */
    private Path createRealJpg(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", file.toFile());
        return file;
    }

    /**
     * Строит SearchCriteria, нацеленный на tempDir без дополнительных фильтров.
     * Глубина 1 — только файлы непосредственно в папке.
     */
    private SearchCriteria criteriaForTempDir() {
        return new SearchCriteria(
                List.of(),                       // fileNames — без фильтра
                List.of(),                       // keywords
                List.of(),                       // entries (object classes)
                null,                            // globalMinProbability
                null,                            // minDate
                null,                            // maxDate
                List.of(new SearchDirectoryConfig(tempDir, 1)),
                List.of("png", "jpg", "jpeg")    // fileTypes
        );
    }

    /**
     * Синхронно получает результат CompletableFuture.
     * Оборачивает checked-исключения в AssertionError, чтобы тест упал
     * с понятным сообщением, а не с обёрнутым ExecutionException.
     */
    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("CompletableFuture завершился с ошибкой", e);
        }
    }

    // ── Тесты searchExistingIndex ─────────────────────────────────────────

    @Test
    @DisplayName("searchExistingIndex — пустой индекс возвращает пустой список без исключений")
    void searchExistingIndex_emptyIndex_returnsEmptyList() {
        // Arrange
        SearchCriteria criteria = criteriaForTempDir();

        // Act
        List<FileMetadata> result = await(service.searchExistingIndex(criteria));

        // Assert
        assertNotNull(result,
                "Результат не должен быть null — сервис обязан вернуть список");
        assertTrue(result.isEmpty(),
                "Пустой индекс должен давать пустой список, получено: " + result.size());
    }

    @Test
    @DisplayName("searchExistingIndex — индекс заполнен, критерии совпадают → возвращает совпадающие записи")
    void searchExistingIndex_indexHasMatchingEntries_returnsMatchingMetadata()
            throws IOException, SQLException {

        // Arrange: создаём PNG, индексируем его напрямую через репозиторий,
        // чтобы обойти OCR и проверить только поиск.
        Path pngFile = createRealPng("target.png");
        FileMetadata stub = new FileMetadata(
                pngFile,
                "target",
                "png",
                Files.size(pngFile),
                null,
                null,
                List.of(),
                List.of()
        );
        repository.save(stub);  // прямая вставка в индекс

        SearchCriteria criteria = new SearchCriteria(
                List.of("target"),               // ищем по имени «target»
                List.of(),
                List.of(),
                null, null, null,
                List.of(new SearchDirectoryConfig(tempDir, 1)),
                List.of("png")
        );

        // Act
        List<FileMetadata> result = await(service.searchExistingIndex(criteria));

        // Assert
        assertFalse(result.isEmpty(),
                "Индекс содержит target.png — поиск должен вернуть хотя бы одну запись");
        assertTrue(
                result.stream().anyMatch(m -> "target".equals(m.getFileName())),
                "Среди результатов должен быть файл с именем target"
        );
    }

    @Test
    @DisplayName("searchExistingIndex — критерии не совпадают → возвращает пустой список")
    void searchExistingIndex_noMatchingEntries_returnsEmptyList()
            throws IOException, SQLException {

        // Arrange: в индексе есть запись, но имя не совпадает с критерием
        Path pngFile = createRealPng("alpha.png");
        FileMetadata stub = new FileMetadata(
                pngFile, "alpha.png", "png",
                Files.size(pngFile),
                null, null, List.of(), List.of()
        );
        repository.save(stub);

        SearchCriteria criteria = new SearchCriteria(
                List.of("nonexistent_xyz"),      // заведомо несуществующее имя
                List.of(),
                List.of(),
                null, null, null,
                List.of(new SearchDirectoryConfig(tempDir, 1)),
                List.of("png")
        );

        // Act
        List<FileMetadata> result = await(service.searchExistingIndex(criteria));

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty(),
                "Ни одна запись не соответствует критерию — список должен быть пустым");
    }

    // ── Тесты initiateBackgroundIndexing ──────────────────────────────────

    @Test
    @DisplayName("initiateBackgroundIndexing — папка с .png/.jpg → файлы проиндексированы, " +
            "FileMetadata содержит путь, имя, расширение")
    void initiateBackgroundIndexing_folderWithImages_filesIndexedWithCorrectMetadata()
            throws IOException {

        // Arrange
        Path png1 = createRealPng("photo1.png");
        Path jpg1 = createRealJpg("photo2.jpg");
        SearchCriteria criteria = criteriaForTempDir();

        // Act
        List<FileMetadata> indexed = await(service.initiateBackgroundIndexing(criteria));

        // Assert — хотя бы два файла проиндексированы
        assertFalse(indexed.isEmpty(),
                "В папке есть изображения — список проиндексированных не должен быть пустым");

        // Проверяем photo1.png
        FileMetadata meta1 = indexed.stream()
                .filter(m -> "photo1.png".equals(m.getFilePath().getFileName().toString()))
                .findFirst()
                .orElse(null);
        assertNotNull(meta1, "photo1.png должен присутствовать в результатах индексации");
        assertAll("Метаданные photo1.png",
                () -> assertNotNull(meta1.getFilePath(),   "filePath не должен быть null"),
                () -> assertFalse(meta1.getFileName().isBlank(),   "fileName не должен быть пустым"),
                () -> assertEquals("png", meta1.getFileExtension().toLowerCase(),
                        "расширение должно быть png"),
                () -> assertEquals(png1.toAbsolutePath(), meta1.getFilePath().toAbsolutePath(),
                        "путь в метаданных должен совпадать с реальным путём файла")
        );

        // Проверяем photo2.jpg
        FileMetadata meta2 = indexed.stream()
                .filter(m -> "photo2.jpg".equals(m.getFilePath().getFileName().toString()))
                .findFirst()
                .orElse(null);
        assertNotNull(meta2, "photo2.jpg должен присутствовать в результатах индексации");
        assertAll("Метаданные photo2.jpg",
                () -> assertNotNull(meta2.getFilePath()),
                () -> assertEquals("jpg", meta2.getFileExtension().toLowerCase()),
                () -> assertEquals(jpg1.toAbsolutePath(), meta2.getFilePath().toAbsolutePath())
        );
    }

    @Test
    @DisplayName("initiateBackgroundIndexing — папка без изображений → пустой список, нет исключений")
    void initiateBackgroundIndexing_folderWithoutImages_returnsEmptyList() throws IOException {
        // Arrange: только текстовые файлы — FileScanner их отфильтрует
        Files.writeString(tempDir.resolve("readme.txt"), "not an image");
        Files.writeString(tempDir.resolve("data.csv"),   "a,b,c");
        SearchCriteria criteria = criteriaForTempDir();

        // Act
        List<FileMetadata> result = await(service.initiateBackgroundIndexing(criteria));

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty(),
                "Нет изображений — должен вернуться пустой список, получено: " + result.size());
    }

    @Test
    @DisplayName("initiateBackgroundIndexing + searchExistingIndex последовательно — " +
            "данные, сохранённые при индексации, находятся при поиске")
    void initiateBackgroundIndexingThenSearch_indexedDataIsFoundBySearch() throws IOException {
        // Arrange
        createRealPng("findme.png");
        SearchCriteria indexCriteria  = criteriaForTempDir();
        SearchCriteria searchCriteria = new SearchCriteria(
                List.of("findme"),
                List.of(),
                List.of(),
                null, null, null,
                List.of(new SearchDirectoryConfig(tempDir, 1)),
                List.of("png")
        );

        // Act — шаг 1: индексация
        List<FileMetadata> indexed = await(service.initiateBackgroundIndexing(indexCriteria));
        assertFalse(indexed.isEmpty(), "Индексация должна вернуть хотя бы один файл");

        // Act — шаг 2: поиск по уже заполненному индексу
        List<FileMetadata> found = await(service.searchExistingIndex(searchCriteria));

        // Assert
        assertFalse(found.isEmpty(),
                "После индексации поиск должен найти findme.png в индексе");
        assertTrue(
                found.stream().anyMatch(m -> "findme.png".equals(m.getFilePath().getFileName().toString())),
                "Среди найденных должен быть файл findme.png"
        );
    }

    // ── Вспомогательные классы ────────────────────────────────────────────

    /**
     * Заглушка ImageContentAnalyzer.
     *
     * IT-тест проверяет путь/имя/расширение файла — OCR и детекция объектов
     * выходят за рамки этого теста и требуют GPU/tessdata/модели.
     * Заглушка возвращает пустые результаты без побочных эффектов.
     */
    private static class StubImageContentAnalyzer extends ImageContentAnalyzer {

        public StubImageContentAnalyzer() {
            super(null, null);
        }

        @Override
        public List<org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject>
        recognizeText(Path imagePath) {
            return List.of();
        }

        @Override
        public List<org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject>
        analyzeObjects(Path imagePath) {
            return List.of();
        }
    }

    /**
     * ExecutorService, выполняющий задачи синхронно в вызывающем потоке.
     *
     * Позволяет писать тесты без {@code Thread.sleep} или {@code CountDownLatch}:
     * все CompletableFuture завершаются к моменту возврата из метода.
     *
     * Реализует только методы, необходимые для работы с CompletableFuture;
     * остальные бросают UnsupportedOperationException.
     */
    private static class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown = false;

        @Override public void execute(Runnable command)   { command.run(); }
        @Override public void shutdown()                  { shutdown = true; }
        @Override public List<Runnable> shutdownNow()     { shutdown = true; return List.of(); }
        @Override public boolean isShutdown()             { return shutdown; }
        @Override public boolean isTerminated()           { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}