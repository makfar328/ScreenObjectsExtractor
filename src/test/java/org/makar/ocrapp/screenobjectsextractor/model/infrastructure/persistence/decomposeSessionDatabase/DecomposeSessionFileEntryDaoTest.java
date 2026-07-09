package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты DAO таблицы ds_file_entry.
 *
 * Используется изолированная in-memory база данных SQLite (jdbc:sqlite::memory:).
 * Каждый тест получает чистую пустую таблицу — побочных эффектов нет.
 */
public class DecomposeSessionFileEntryDaoTest {

    /* Первый тест на JUnit 5! Или нет. */
    /* Инфрастуктура теста. */

    private SQLiteConnectionManager connectionManager;
    private DecomposeSessionFileEntryDao dao;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        String uniqueDbName = "testdb_" + System.nanoTime();
        String url = "jdbc:sqlite:file:" + uniqueDbName + "?mode=memory&cache=shared";
        //connectionManager = new SQLiteConnectionManager("jdbc:sqlite:test.db");
        connectionManager = new SQLiteConnectionManager(url);
        // база данных создается не в файловой системе, а в памяти - временная

        connection = connectionManager.getConnection();

        dao = new DecomposeSessionFileEntryDao(connectionManager);
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close(); // После разрыва соединения in-memory база данных уничтожается
        }
    }


    /**
     * Строит минимально корректный DecomposeSessionEntry для тестов.
     * filePath = null (сценарий SCREEN_CAPTURE).
     */
    private DecomposeSessionEntry buildTestEntry(String fileName) {
        FileMetadata meta = new FileMetadata(
                -1,                             // id: -1 до персистирования
                null,                           // filePath: null для захвата экрана
                fileName,
                "png",
                1024L,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                800,
                600
        );

        return DecomposeSessionEntry.builder()
                .sessionId(0)
                .processedAt(LocalDateTime.of(2026, 6, 1, 22, 0))
                .captureSource(DecomposeSessionEntry.CAPTURE_SOURCE.SCREEN_CAPTURE)
                .fileMetadata(meta)
                .build();
    }


// ── Тесты ────────────────────────────────────────────────────────────────

    /**
     * Тест 1: вставка одной записи возвращает сгенерированный PK > 0.
     */
    @Test
    @DisplayName("insert() возвращает сгенерированный PK > 0")
    void insert_returnedIdIsPositive() throws SQLException {
        DecomposeSessionEntry entry = buildTestEntry("capture_test.png");

        long generatedId = dao.insert(entry, connection);

        assertTrue(generatedId > 0,
                "Сгенерированный PK должен быть положительным, получено: " + generatedId);
    }

    /**
     * Тест 2: после вставки findAll() возвращает ровно одну запись.
     */
    @Test
    @DisplayName("findAll() возвращает одну запись после одного insert()")
    void findAll_afterSingleInsert_returnsOneEntry() throws SQLException {
        dao.insert(buildTestEntry("capture_001.png"), connection);

        List<DecomposeSessionEntry> entries = dao.findAll(connection);

        assertEquals(1, entries.size(),
                "После одного insert() таблица должна содержать ровно 1 запись");
    }

    /**
     * Тест 3: id, прочитанный из БД через findAll(), совпадает с возвращённым insert().
     */
    @Test
    @DisplayName("findAll() возвращает запись с id, совпадающим с результатом insert()")
    void findAll_entryHasCorrectId() throws SQLException {
        long insertedId = dao.insert(buildTestEntry("capture_002.png"), connection);

        List<DecomposeSessionEntry> entries = dao.findAll(connection);
        long readId = entries.get(0).getFileMetadata().getId();

        assertEquals(insertedId, readId,
                "id в FileMetadata должен совпадать с PK, возвращённым insert()");
    }

    /**
     * Тест 4: скалярные поля (fileName, captureSource) сохраняются без искажений.
     */
    @Test
    @DisplayName("findAll() сохраняет fileName и captureSource без искажений")
    void findAll_preservesScalarFields() throws SQLException {
        dao.insert(buildTestEntry("capture_check.png"), connection);

        DecomposeSessionEntry loaded = dao.findAll(connection).get(0);

        assertAll(
                () -> assertEquals("capture_check.png",
                        loaded.getFileMetadata().getFileName(),
                        "fileName должен совпадать с сохранённым"),
                () -> assertEquals(DecomposeSessionEntry.CAPTURE_SOURCE.SCREEN_CAPTURE,
                        loaded.getCaptureSource(),
                        "captureSource должен совпадать с сохранённым"),
                () -> assertEquals(800, loaded.getFileMetadata().getImageWidth(),
                        "imageWidth должен совпадать"),
                () -> assertEquals(600, loaded.getFileMetadata().getImageHeight(),
                        "imageHeight должен совпадать")
        );
    }

    /**
     * Тест 5: findAll() на пустой таблице возвращает пустой список, а не null и не исключение.
     */
    @Test
    @DisplayName("findAll() на пустой таблице возвращает пустой список")
    void findAll_onEmptyTable_returnsEmptyList() throws SQLException {
        List<DecomposeSessionEntry> entries = dao.findAll(connection);

        assertNotNull(entries, "findAll() не должен возвращать null");
        assertTrue(entries.isEmpty(), "Список должен быть пустым на незаполненной таблице");
    }

    /**
     * Тест 6: два последовательных insert() дают уникальные PK.
     */
    @Test
    @DisplayName("Два insert() порождают разные уникальные PK")
    void insert_twoEntries_haveDistinctIds() throws SQLException {
        long id1 = dao.insert(buildTestEntry("capture_a.png"), connection);
        long id2 = dao.insert(buildTestEntry("capture_b.png"), connection);

        assertNotEquals(id1, id2,
                "Два последовательных insert() должны дать разные PK");
    }


}
