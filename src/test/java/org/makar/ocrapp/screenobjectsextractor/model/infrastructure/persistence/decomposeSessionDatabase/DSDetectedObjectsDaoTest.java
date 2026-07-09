package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DSDetectedObjectsDaoTest {

    private SQLiteConnectionManager connectionManager;
    // ↓ объявляем через интерфейс — тест верифицирует контракт, а не детали реализации
    private IDetectedObjectsDao detectedObjectsDao;
    private DecomposeSessionFileEntryDao fileEntryDao;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        long id = System.nanoTime();
        String dbUrl = "jdbc:sqlite:file:testdb_" + id + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(dbUrl);

        // Якорное соединение открывается ПЕРВЫМ — удерживает named in-memory БД
        connection = connectionManager.getConnection();

        fileEntryDao = new DecomposeSessionFileEntryDao(connectionManager);
        detectedObjectsDao = new DSDetectedObjectsDao(connectionManager);

        // Родительская таблица создаётся раньше дочерней (FOREIGN KEY)
        fileEntryDao.initializeTable();
        detectedObjectsDao.initializeTable();
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

    private OCRAppDetectedObject buildObject(String className, double probability) {
        return new OCRAppDetectedObject(className, probability);
    }

    // ── Тесты ────────────────────────────────────────────────────────────────

    /**
     * Тест 1: пакетная вставка одного объекта и выборка по идентификатору записи-родителя.
     */
    @Test
    void testInsertBatchAndFindByForeignKeyId() throws SQLException {
        long entryId = insertParentEntry("scene_001.png");

        detectedObjectsDao.insertBatch(entryId,
                List.of(buildObject("cat", 0.92)), connection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(entryId, connection);

        assertEquals(1, result.size());
        assertEquals("cat", result.get(0).getClassName());
    }

    /**
     * Тест 2: пакетная вставка нескольких объектов возвращает полный список.
     */
    @Test
    void testInsertBatch_multipleObjects_allReturned() throws SQLException {
        long entryId = insertParentEntry("scene_002.png");

        List<OCRAppDetectedObject> objects = List.of(
                buildObject("dog",  0.91),
                buildObject("car",  0.87),
                buildObject("person", 0.99)
        );
        detectedObjectsDao.insertBatch(entryId, objects, connection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(entryId, connection);

        assertEquals(3, result.size());
    }

    /**
     * Тест 3: findByForeignKeyId на несуществующем entryId возвращает пустой список, не null.
     */
    @Test
    void testFindByForeignKeyId_notFound_returnsEmptyList() throws SQLException {
        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(9999L, connection);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Тест 4: объекты двух разных записей не пересекаются (изоляция по entry_id).
     */
    @Test
    void testObjectsAreIsolatedByEntryId() throws SQLException {
        long entry1 = insertParentEntry("scene_A.png");
        long entry2 = insertParentEntry("scene_B.png");

        detectedObjectsDao.insertBatch(entry1, List.of(buildObject("dog", 0.9)), connection);
        detectedObjectsDao.insertBatch(entry2, List.of(buildObject("cat", 0.8)), connection);

        List<OCRAppDetectedObject> result1 =
                detectedObjectsDao.findByForeignKeyId(entry1, connection);
        List<OCRAppDetectedObject> result2 =
                detectedObjectsDao.findByForeignKeyId(entry2, connection);

        assertEquals(1, result1.size());
        assertEquals("dog", result1.get(0).getClassName());

        assertEquals(1, result2.size());
        assertEquals("cat", result2.get(0).getClassName());
    }

    /**
     * Тест 5: поля className и probability сохраняются без потерь (маппинг round-trip).
     */
    @Test
    void testInsertBatch_preservesClassNameAndProbability() throws SQLException {
        long entryId = insertParentEntry("scene_003.png");

        detectedObjectsDao.insertBatch(entryId,
                List.of(buildObject("bicycle", 0.753)), connection);

        OCRAppDetectedObject found =
                detectedObjectsDao.findByForeignKeyId(entryId, connection).get(0);

        assertEquals("bicycle", found.getClassName());
        assertEquals(0.753, found.getProbability(), 0.0001);
    }

    /**
     * Тест 6: findByForeignKeyIds возвращает корректную карту {entryId → список объектов}.
     */
    @Test
    void testFindByForeignKeyIds_returnsCorrectMap() throws SQLException {
        long entry1 = insertParentEntry("batch_A.png");
        long entry2 = insertParentEntry("batch_B.png");

        detectedObjectsDao.insertBatch(entry1,
                List.of(buildObject("dog", 0.9), buildObject("cat", 0.8)), connection);
        detectedObjectsDao.insertBatch(entry2,
                List.of(buildObject("car", 0.7)), connection);

        Map<Long, List<OCRAppDetectedObject>> map =
                detectedObjectsDao.findByForeignKeyIds(List.of(entry1, entry2), connection);

        assertEquals(2, map.get(entry1).size());
        assertEquals(1, map.get(entry2).size());
        assertEquals("car", map.get(entry2).get(0).getClassName());
    }

    /**
     * Тест 7: deleteByForeignKeyId удаляет все объекты конкретной записи.
     */
    @Test
    void testDeleteByForeignKeyId_removesAllObjects() throws SQLException {
        long entryId = insertParentEntry("to_delete.png");

        detectedObjectsDao.insertBatch(entryId,
                List.of(buildObject("cat", 0.9), buildObject("dog", 0.8)), connection);
        detectedObjectsDao.deleteByForeignKeyId(entryId, connection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(entryId, connection);
        assertTrue(result.isEmpty());
    }

    /**
     * Тест 8: insertBatch с пустым списком не генерирует исключений.
     */
    @Test
    void testInsertBatch_emptyList_noException() throws SQLException {
        long entryId = insertParentEntry("empty_batch.png");

        assertDoesNotThrow(() ->
                detectedObjectsDao.insertBatch(entryId, Collections.emptyList(), connection));
    }
}