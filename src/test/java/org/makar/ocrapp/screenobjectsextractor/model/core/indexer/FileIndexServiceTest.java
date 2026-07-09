package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteriaBuilder;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.*;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест
 */
class FileIndexServiceTest {

    @TempDir
    Path tempDir;

    private FileIndexService     service;
    private IFileIndexRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("idx.db");
        SQLiteConnectionManager connectionManager = new SQLiteConnectionManager(dbUrl);
        IFileIndexDatabaseManager dbManager = new FileIndexDatabaseManager(connectionManager);
        dbManager.initializeSchema();
        repository = dbManager.getRepository();

        MetadataExtractor    extractor = new MetadataExtractor();

        ImageContentAnalyzer analyzer  = new StubImageContentAnalyzer();
        FileScanner          scanner   = new FileScanner();
        FileIndexingProcessor processor = new FileIndexingProcessor(repository, extractor, analyzer);
        ExecutorService      executor  = Executors.newSingleThreadExecutor();

        service = new FileIndexService(repository, processor, scanner, extractor, analyzer, executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.truncateAll();
    }

    // ── performIncrementalIndexing ─────────────────────────────────────────

    @Test
    @DisplayName("performIncrementalIndexing: png-файл индексируется, fileName без расширения")
    void incrementalIndexing_pngFile_indexedWithNameWithoutExtension() throws Exception {
        Files.write(tempDir.resolve("report.png"), new byte[70]);

        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("png"));

        assertFalse(indexed.isEmpty(), "Должен быть проиндексирован хотя бы один файл");
        assertTrue(indexed.stream().anyMatch(m -> "report".equals(m.getFileName())));
    }

    @Test
    @DisplayName("performIncrementalIndexing: txt-файл игнорируется (не входит в fileTypes)")
    void incrementalIndexing_txtFile_notIndexed() throws Exception {
        Files.write(tempDir.resolve("notes.txt"), "hello".getBytes());

        // FileScanner фильтрует по IMAGE_EXTENSIONS — txt туда не входит,
        // поэтому результат будет пустым независимо от criteria.fileTypes
        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("png"));

        assertTrue(indexed.isEmpty(), "txt-файл не должен попасть в индекс");
    }

    @Test
    @DisplayName("performIncrementalIndexing: пустая директория — индекс остаётся пустым")
    void incrementalIndexing_emptyDir_nothingIndexed() throws Exception {
        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("png"));

        assertTrue(indexed.isEmpty());
    }

    @Test
    @DisplayName("performIncrementalIndexing: png и jpg — оба файла попадают в индекс")
    void incrementalIndexing_pngAndJpg_bothIndexed() throws Exception {
        Files.write(tempDir.resolve("a.png"), new byte[70]);
        Files.write(tempDir.resolve("b.jpg"), new byte[100]);

        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("png", "jpg", "jpeg"));

        assertEquals(2, indexed.size());
    }

    @Test
    @DisplayName("performIncrementalIndexing: повторный вызов (upsert) не дублирует запись")
    void incrementalIndexing_calledTwice_noDuplicates() throws Exception {
        Files.write(tempDir.resolve("dup.png"), new byte[70]);

        invokeIndexing(tempDir, 1, List.of("png"));
        invokeIndexing(tempDir, 1, List.of("png")); // второй вызов — upsert

        // findAll нет в интерфейсе — используем search с пустыми критериями
        List<FileMetadata> all = repository.search(SearchCriteria.createEmpty());
        long count = all.stream()
                .filter(m -> "dup".equals(m.getFileName()))
                .count();
        assertEquals(1, count, "Одна запись, не две");
    }

    @Test
    @DisplayName("performIncrementalIndexing: fileExtension сохраняется корректно")
    void incrementalIndexing_extension_storedCorrectly() throws Exception {
        Files.write(tempDir.resolve("img.jpg"), new byte[100]);

        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("jpg"));

        assertTrue(indexed.stream().anyMatch(m -> "jpg".equals(m.getFileExtension())));
    }

    @Test
    @DisplayName("performIncrementalIndexing: вложенный файл при depth=1 не индексируется")
    void incrementalIndexing_depth1_subdirFilesNotReturned() throws Exception {
        Path sub = Files.createDirectory(tempDir.resolve("sub"));
        Files.write(sub.resolve("nested.png"), new byte[70]);
        Files.write(tempDir.resolve("top.png"), new byte[70]);

        List<FileMetadata> indexed = invokeIndexing(tempDir, 1, List.of("png"));

        // depth=1 означает только верхний уровень; nested.png из sub не должен попасть
        assertEquals(1, indexed.size());
        assertEquals("top", indexed.get(0).getFileName());
    }

    // ── saveAnalyzedFile ────────────────────────────────────────────────────

    @Test
    @DisplayName("saveAnalyzedFile: null-аргумент не бросает исключение")
    void saveAnalyzedFile_nullArg_doesNotThrow() {
        assertDoesNotThrow(() -> service.saveAnalyzedFile(null));
    }

    @Test
    @DisplayName("saveAnalyzedFile: корректный FileMetadata сохраняется в репозиторий")
    void saveAnalyzedFile_validMetadata_savedInRepository() throws Exception {
        Path file = Files.write(tempDir.resolve("manual.png"), new byte[70]);

        // Получаем метаданные через MetadataExtractor напрямую, чтобы
        // создать валидный FileMetadata без запуска всей цепочки индексации
        MetadataExtractor extractor = new MetadataExtractor();
        FileMetadata metadata = extractor.extract(file);

        service.saveAnalyzedFile(metadata);

        List<FileMetadata> found = repository.search(SearchCriteria.createEmpty());
        assertFalse(found.isEmpty());
        assertTrue(found.stream().anyMatch(m -> "manual".equals(m.getFileName())));
    }

    // ── processor.processFile (публичный API) ───────────────────────────────

    @Test
    @DisplayName("processFile: файл сохраняется в репозиторий после вызова")
    void processFile_singleFile_savedInRepository() throws Exception {
        Path file = Files.write(tempDir.resolve("single.png"), new byte[70]);

        MetadataExtractor    extractor = new MetadataExtractor();
        ImageContentAnalyzer analyzer  = new StubImageContentAnalyzer();
        FileIndexingProcessor proc     = new FileIndexingProcessor(repository, extractor, analyzer);

        proc.processFile(file);

        List<FileMetadata> all = repository.search(SearchCriteria.createEmpty());
        assertTrue(all.stream().anyMatch(m -> "single".equals(m.getFileName())));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Синхронно выполняет performIncrementalIndexing и возвращает результат.
     */
    private List<FileMetadata> invokeIndexing(Path dir, int depth, List<String> fileTypes)
            throws Exception {
        SearchDirectoryConfig dirConfig = new SearchDirectoryConfig(dir, depth);
        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withTargetDirectories(List.of(dirConfig))
                .withFileTypes(fileTypes)
                .build();

        return service
                .performIncrementalIndexing(dirConfig, criteria)
                .get(15, TimeUnit.SECONDS);
    }

    /**
     * Заглушка AI-анализа. Переопределяет оба метода, чтобы обойти
     * инициализацию ONNX-рантайма и зависимость от .ort-файла.
     */
    private static final class StubImageContentAnalyzer extends ImageContentAnalyzer {

        StubImageContentAnalyzer() {
            super(null, null); // поля не используются — оба метода переопределены
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
}