package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты репозитория {@link DecomposeSessionRepository}.
 *
 * <p>Стратегия изоляции: каждый тест получает именованную shared-cache
 * in-memory базу данных SQLite с уникальным URL вида
 * {@code jdbc:sqlite:file:repoTestDb_<nanotime>?mode=memory&cache=shared}.
 * «Якорное» соединение открывается первым в {@code setUp()} — оно удерживает
 * базу в памяти на всё время теста, не давая ей уничтожиться после
 * закрытия внутренних соединений DAO при вызове {@code initializeTable()}.
 */
public class DecomposeSessionRepositoryTest {

    private SQLiteConnectionManager connectionManager;
    private DecomposeSessionRepository repository;

    // DAO-экземпляры нужны здесь для инициализации таблиц
    private DecomposeSessionFileEntryDao fileEntryDao;
    private DSDetectedObjectsDao         detectedObjectsDao;
    private DSRecognizedTextDao          recognizedTextDao;

    // «якорное» соединение — держит named in-memory БД живой
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:repoTestDb_" + System.nanoTime()
                + "?mode=memory&cache=shared";

        connectionManager  = new SQLiteConnectionManager(url);

        // Якорное соединение открывается ПЕРВЫМ — база создаётся и не уничтожается
        anchorConnection   = connectionManager.getConnection();

        fileEntryDao       = new DecomposeSessionFileEntryDao(connectionManager);
        detectedObjectsDao = new DSDetectedObjectsDao(connectionManager);
        recognizedTextDao  = new DSRecognizedTextDao(connectionManager);

        // DDL выполняется через внутренние соединения DAO; база остаётся живой
        fileEntryDao.initializeTable();
        detectedObjectsDao.initializeTable();
        recognizedTextDao.initializeTable();

        repository = new DecomposeSessionRepository(
                connectionManager,
                fileEntryDao,
                detectedObjectsDao,
                recognizedTextDao
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close(); // Освобождение последнего соединения → БД уничтожается
        }
    }

    // ── Вспомогательные строители объектов ─────────────────────────────────

    /**
     * Строит {@link DecomposeSessionEntry} с заданными списками дочерних данных.
     */
    private DecomposeSessionEntry buildEntry(String fileName,
                                             List<OCRAppDetectedObject> objects,
                                             List<TextObject> texts) {
        FileMetadata meta = new FileMetadata(
                0L,
                null,
                fileName,
                "png",
                2048L,
                LocalDateTime.of(2025, 3, 15, 10, 0),
                LocalDateTime.of(2025, 3, 15, 10, 0),
                texts,
                objects,
                1920,
                1080
        );
        return DecomposeSessionEntry.builder()
                .sessionId(1L)
                .processedAt(LocalDateTime.now())
                .captureSource("TEST_SOURCE")
                .fileMetadata(meta)
                .build();
    }

    private OCRAppDetectedObject buildObject(String className, double x, double y,
                                             double w, double h) {
        return new OCRAppDetectedObject(className, 0.9, x, y, w, h);
    }

    private TextObject buildText(String text, int x, int y, int w, int h) {
        return new TextObject(text, x, y, w, h);
    }

    // ── Тесты saveSession() ─────────────────────────────────────────────────

    @Test
    @DisplayName("saveSession() возвращает положительный id для записи без дочерних данных")
    void saveSession_emptyChildren_returnsPositiveId() {
        DecomposeSessionEntry entry = buildEntry(
                "screen_empty.png", Collections.emptyList(), Collections.emptyList());

        long id = repository.saveSession(entry);

        assertTrue(id > 0,
                "saveSession() должен вернуть положительный id, получено: " + id);
    }

    @Test
    @DisplayName("saveSession() атомарно сохраняет запись вместе с детектированными объектами")
    void saveSession_withObjects_persistsObjectsTransactionally() {
        List<OCRAppDetectedObject> objects = List.of(
                buildObject("cat",  0.0, 0.0, 100.0, 80.0),
                buildObject("dog", 50.0, 50.0, 120.0, 90.0)
        );
        DecomposeSessionEntry entry = buildEntry(
                "screen_objects.png", objects, Collections.emptyList());

        long id = repository.saveSession(entry);
        assertTrue(id > 0);

        // Читаем результат через getAllSessionsSummary() — полный round-trip через БД
        List<DecomposeSessionEntry> all = repository.getAllSessionsSummary();
        assertEquals(1, all.size());

        List<OCRAppDetectedObject> loaded = all.get(0).getFileMetadata().getDetectedObjects();
        assertEquals(2, loaded.size(),
                "Должны быть сохранены оба детектированных объекта");
    }

    @Test
    @DisplayName("saveSession() атомарно сохраняет запись вместе с распознанным текстом")
    void saveSession_withTexts_persistsTextsTransactionally() {
        List<TextObject> texts = List.of(
                buildText("Hello world", 10, 20, 200, 30),
                buildText("Second line", 10, 60, 150, 30)
        );
        DecomposeSessionEntry entry = buildEntry(
                "screen_text.png", Collections.emptyList(), texts);

        long id = repository.saveSession(entry);
        assertTrue(id > 0);

        List<DecomposeSessionEntry> all = repository.getAllSessionsSummary();
        assertEquals(1, all.size());

        List<TextObject> loaded = all.get(0).getFileMetadata().getRecognizedTextContent();
        assertEquals(2, loaded.size(),
                "Должны быть сохранены оба текстовых фрагмента");
    }

    @Test
    @DisplayName("saveSession() возвращает уникальные id при последовательных вызовах")
    void saveSession_multipleCalls_returnDistinctIds() {
        long id1 = repository.saveSession(
                buildEntry("s1.png", Collections.emptyList(), Collections.emptyList()));
        long id2 = repository.saveSession(
                buildEntry("s2.png", Collections.emptyList(), Collections.emptyList()));

        assertNotEquals(id1, id2,
                "Два последовательных saveSession() должны порождать разные id");
    }

    // ── Тесты getAllSessionsSummary() ───────────────────────────────────────

    @Test
    @DisplayName("getAllSessionsSummary() возвращает пустой список на незаполненной БД")
    void getAllSessionsSummary_emptyDb_returnsEmptyList() {
        List<DecomposeSessionEntry> result = repository.getAllSessionsSummary();

        assertNotNull(result, "Метод не должен возвращать null");
        assertTrue(result.isEmpty(),
                "На пустой базе список должен быть пустым");
    }

    @Test
    @DisplayName("getAllSessionsSummary() возвращает все сохранённые записи")
    void getAllSessionsSummary_afterThreeInserts_returnsThreeEntries() {
        repository.saveSession(
                buildEntry("a.png", Collections.emptyList(), Collections.emptyList()));
        repository.saveSession(
                buildEntry("b.png", Collections.emptyList(), Collections.emptyList()));
        repository.saveSession(
                buildEntry("c.png", Collections.emptyList(), Collections.emptyList()));

        List<DecomposeSessionEntry> result = repository.getAllSessionsSummary();

        assertEquals(3, result.size(),
                "После трёх сохранений список должен содержать 3 записи");
    }

    @Test
    @DisplayName("getAllSessionsSummary() загружает дочерние данные вместе с записями")
    void getAllSessionsSummary_loadsChildDataEagerly() {
        List<OCRAppDetectedObject> objects = List.of(buildObject("bird", 5, 5, 40, 40));
        List<TextObject> texts             = List.of(buildText("OCR result", 0, 0, 100, 20));

        repository.saveSession(buildEntry("rich.png", objects, texts));

        List<DecomposeSessionEntry> result = repository.getAllSessionsSummary();
        assertEquals(1, result.size());

        FileMetadata loaded = result.get(0).getFileMetadata();
        assertAll(
                () -> assertEquals(1, loaded.getDetectedObjects().size(),
                        "Детектированные объекты должны быть загружены"),
                () -> assertEquals("bird",
                        loaded.getDetectedObjects().get(0).getClassName(),
                        "Класс объекта должен совпадать с сохранённым"),
                () -> assertEquals(1, loaded.getRecognizedTextContent().size(),
                        "Текстовые фрагменты должны быть загружены"),
                () -> assertEquals("OCR result",
                        loaded.getRecognizedTextContent().get(0).getText(),
                        "Текст должен совпадать с сохранённым")
        );
    }

    // ── Тест truncateAll() ──────────────────────────────────────────────────

    @Test
    @DisplayName("truncateAll() физически удаляет все записи из всех трёх таблиц")
    void truncateAll_clearsAllThreeTables() throws SQLException {
        List<OCRAppDetectedObject> objects = List.of(buildObject("car", 0, 0, 50, 50));
        List<TextObject> texts             = List.of(buildText("text", 0, 0, 100, 20));

        repository.saveSession(buildEntry("before_truncate.png", objects, texts));

        // Убеждаемся, что данные есть
        assertEquals(1, repository.getAllSessionsSummary().size());

        repository.truncateAll();

        // После truncateAll все три таблицы должны быть пусты
        List<DecomposeSessionEntry> result = repository.getAllSessionsSummary();
        assertTrue(result.isEmpty(),
                "После truncateAll() список записей должен быть пустым");
    }

    // ── Тест изоляции дочерних данных ──────────────────────────────────────

    @Test
    @DisplayName("Дочерние данные двух разных сессий не смешиваются при загрузке")
    void getAllSessionsSummary_childDataIsolatedBetweenSessions() {
        repository.saveSession(buildEntry(
                "session_a.png",
                List.of(buildObject("dog",  0, 0, 50, 50)),
                List.of(buildText("text A", 0, 0, 100, 20))
        ));
        repository.saveSession(buildEntry(
                "session_b.png",
                List.of(buildObject("cat", 0, 0, 50, 50),
                        buildObject("car", 60, 60, 80, 60)),
                List.of(buildText("text B1", 0, 0, 100, 20),
                        buildText("text B2", 0, 30, 100, 20))
        ));

        List<DecomposeSessionEntry> all = repository.getAllSessionsSummary();
        assertEquals(2, all.size());

        // Ищем нужную сессию по имени файла
        DecomposeSessionEntry sessionA = all.stream()
                .filter(e -> "session_a.png".equals(e.getFileMetadata().getFileName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("session_a.png не найдена"));

        DecomposeSessionEntry sessionB = all.stream()
                .filter(e -> "session_b.png".equals(e.getFileMetadata().getFileName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("session_b.png не найдена"));

        assertAll(
                () -> assertEquals(1, sessionA.getFileMetadata().getDetectedObjects().size(),
                        "Session A должна содержать 1 объект"),
                () -> assertEquals(1, sessionA.getFileMetadata().getRecognizedTextContent().size(),
                        "Session A должна содержать 1 текстовый фрагмент"),
                () -> assertEquals(2, sessionB.getFileMetadata().getDetectedObjects().size(),
                        "Session B должна содержать 2 объекта"),
                () -> assertEquals(2, sessionB.getFileMetadata().getRecognizedTextContent().size(),
                        "Session B должна содержать 2 текстовых фрагмента")
        );
    }
}