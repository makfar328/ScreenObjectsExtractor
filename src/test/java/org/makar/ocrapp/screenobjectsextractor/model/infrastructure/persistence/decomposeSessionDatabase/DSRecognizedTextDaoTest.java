package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DSRecognizedTextDaoTest {

    private SQLiteConnectionManager connectionManager;
    // ↓ объявляем через интерфейс
    private IRecognizedTextDao recognizedTextDao;
    private DecomposeSessionFileEntryDao fileEntryDao;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        long id = System.nanoTime();
        String dbUrl = "jdbc:sqlite:file:testdb_" + id + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(dbUrl);

        connection = connectionManager.getConnection(); // якорное соединение первым

        fileEntryDao = new DecomposeSessionFileEntryDao(connectionManager);
        recognizedTextDao = new DSRecognizedTextDao(connectionManager);

        fileEntryDao.initializeTable();
        recognizedTextDao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────

    private long insertParentEntry(String fileName) throws SQLException {
        FileMetadata meta = new FileMetadata(
                0L, null, fileName, "png", 512L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                Collections.emptyList(), Collections.emptyList(),
                1920, 1080
        );
        DecomposeSessionEntry entry = DecomposeSessionEntry.builder()
                .sessionId(1L)
                .processedAt(LocalDateTime.now())
                .captureSource("TEST_SOURCE")
                .fileMetadata(meta)
                .build();
        return fileEntryDao.insert(entry, connection);
    }

    private TextObject buildText(String text, int x, int y, int w, int h) {
        return new TextObject(text, x, y, w, h);
    }

    // ── Тесты ────────────────────────────────────────────────────────────────

    /**
     * Тест 1: пакетная вставка одного текстового блока и выборка по entry_id.
     */
    @Test
    void testInsertBatchAndFindByForeignKeyId() throws SQLException {
        long entryId = insertParentEntry("ocr_001.png");

        recognizedTextDao.insertBatch(entryId,
                List.of(buildText("Hello World", 10, 20, 150, 30)), connection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(entryId, connection);

        assertEquals(1, result.size());
        assertEquals("Hello World", result.get(0).getText());
    }

    /**
     * Тест 2: пакетная вставка нескольких текстовых блоков возвращает полный список.
     */
    @Test
    void testInsertBatch_multipleTexts_allReturned() throws SQLException {
        long entryId = insertParentEntry("ocr_002.png");

        List<TextObject> texts = List.of(
                buildText("Строка первая",  0,  0, 200, 25),
                buildText("Строка вторая",  0, 30, 200, 25),
                buildText("Строка третья",  0, 60, 200, 25)
        );
        recognizedTextDao.insertBatch(entryId, texts, connection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(entryId, connection);

        assertEquals(3, result.size());
    }

    /**
     * Тест 3: findByForeignKeyId на несуществующем entryId возвращает пустой список, не null.
     */
    @Test
    void testFindByForeignKeyId_notFound_returnsEmptyList() throws SQLException {
        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(9999L, connection);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Тест 4: текстовые блоки двух разных записей не пересекаются (изоляция по entry_id).
     */
    @Test
    void testTextsAreIsolatedByEntryId() throws SQLException {
        long entry1 = insertParentEntry("page_A.png");
        long entry2 = insertParentEntry("page_B.png");

        recognizedTextDao.insertBatch(entry1,
                List.of(buildText("Текст страницы A", 0, 0, 100, 20)), connection);
        recognizedTextDao.insertBatch(entry2,
                List.of(buildText("Текст страницы B", 0, 0, 100, 20)), connection);

        List<TextObject> result1 = recognizedTextDao.findByForeignKeyId(entry1, connection);
        List<TextObject> result2 = recognizedTextDao.findByForeignKeyId(entry2, connection);

        assertEquals(1, result1.size());
        assertEquals("Текст страницы A", result1.get(0).getText());

        assertEquals(1, result2.size());
        assertEquals("Текст страницы B", result2.get(0).getText());
    }

    /**
     * Тест 5: все поля TextObject (text, x, y, width, height) сохраняются без искажений.
     */
    @Test
    void testInsertBatch_preservesAllFields() throws SQLException {
        long entryId = insertParentEntry("fields_check.png");

        recognizedTextDao.insertBatch(entryId,
                List.of(buildText("Контроль полей", 15, 25, 320, 48)), connection);

        TextObject found =
                recognizedTextDao.findByForeignKeyId(entryId, connection).get(0);

        assertAll(
                () -> assertEquals("Контроль полей", found.getText()),
                () -> assertEquals(15,  found.getX()),
                () -> assertEquals(25,  found.getY()),
                () -> assertEquals(320, found.getWidth()),
                () -> assertEquals(48,  found.getHeight())
        );
    }

    /**
     * Тест 6: findByForeignKeyIds возвращает корректную карту {entryId → список текстов}.
     */
    @Test
    void testFindByForeignKeyIds_returnsCorrectMap() throws SQLException {
        long entry1 = insertParentEntry("batch_A.png");
        long entry2 = insertParentEntry("batch_B.png");

        recognizedTextDao.insertBatch(entry1,
                List.of(buildText("AA", 0, 0, 50, 10), buildText("BB", 0, 15, 50, 10)),
                connection);
        recognizedTextDao.insertBatch(entry2,
                List.of(buildText("CC", 0, 0, 50, 10)),
                connection);

        Map<Long, List<TextObject>> map =
                recognizedTextDao.findByForeignKeyIds(List.of(entry1, entry2), connection);

        assertEquals(2, map.get(entry1).size());
        assertEquals(1, map.get(entry2).size());
        assertEquals("CC", map.get(entry2).get(0).getText());
    }

    /**
     * Тест 7: deleteByForeignKeyId удаляет все тексты конкретной записи.
     */
    @Test
    void testDeleteByForeignKeyId_removesAllTexts() throws SQLException {
        long entryId = insertParentEntry("to_delete.png");

        recognizedTextDao.insertBatch(entryId,
                List.of(buildText("Удаляемый текст", 0, 0, 100, 20)), connection);
        recognizedTextDao.deleteByForeignKeyId(entryId, connection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(entryId, connection);
        assertTrue(result.isEmpty());
    }

    /**
     * Тест 8: insertBatch с пустым списком не генерирует исключений.
     */
    @Test
    void testInsertBatch_emptyList_noException() throws SQLException {
        long entryId = insertParentEntry("empty_batch.png");

        assertDoesNotThrow(() ->
                recognizedTextDao.insertBatch(entryId, Collections.emptyList(), connection));
    }
}