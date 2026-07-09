package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IndexedFilesDetectedObjectsDaoTest {

    private SQLiteConnectionManager connectionManager;
    // Объявляем через интерфейс — верифицирует корректность реализации контракта
    private IDetectedObjectsDao detectedObjectsDao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_dfo_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);

        // Якорное соединение — удерживает named in-memory БД живой
        anchorConnection = connectionManager.getConnection();

        // Минимальная родительская таблица, на которую ссылается FK
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS indexed_files (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_path TEXT NOT NULL
                    )
                    """);
        }

        detectedObjectsDao = new IndexedFilesDetectedObjectsDao(connectionManager);
        detectedObjectsDao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    /**
     * Вставляет минимальную запись в indexed_files.
     * Возвращает сгенерированный PK, необходимый как FK для дочерних таблиц.
     */
    private long insertParentFile(String filePath) throws SQLException {
        try (PreparedStatement ps = anchorConnection.prepareStatement(
                "INSERT INTO indexed_files (file_path) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next(), "Генерация PK для indexed_files завершилась неудачей");
                return rs.getLong(1);
            }
        }
    }

    /**
     * В данном домене OCRAppDetectedObject хранит только className и probability —
     * координаты bounding box в таблице detected_file_objects отсутствуют.
     */
    private OCRAppDetectedObject buildObject(String className, double probability) {
        return new OCRAppDetectedObject(className, probability);
    }

    // ── Тесты ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("insertBatch() + findByForeignKeyId(): базовый цикл вставки и выборки")
    void testInsertAndFindByFileId() throws SQLException {
        long fileId = insertParentFile("/images/photo.png");

        detectedObjectsDao.insertBatch(
                fileId, List.of(buildObject("cat", 0.95)), anchorConnection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(fileId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals("cat", result.get(0).getClassName());
    }

    @Test
    @DisplayName("insertBatch(): несколько объектов одного файла возвращаются полностью")
    void testInsertMultipleObjects_allReturned() throws SQLException {
        long fileId = insertParentFile("/images/multi.png");

        detectedObjectsDao.insertBatch(fileId, List.of(
                buildObject("car",  0.91),
                buildObject("dog",  0.87),
                buildObject("tree", 0.75)
        ), anchorConnection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(fileId, anchorConnection);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("findByForeignKeyId(): несуществующий fileId — пустой ненулевой список")
    void testFindByFileId_notFound_returnsEmptyList() throws SQLException {
        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(9999L, anchorConnection);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Объекты разных файлов не пересекаются при выборке по fileId")
    void testObjectsAreIsolatedByFileId() throws SQLException {
        long file1 = insertParentFile("/images/a.png");
        long file2 = insertParentFile("/images/b.png");

        detectedObjectsDao.insertBatch(file1,
                List.of(buildObject("bird", 0.90)), anchorConnection);
        detectedObjectsDao.insertBatch(file2,
                List.of(buildObject("fish", 0.80)), anchorConnection);

        List<OCRAppDetectedObject> result1 =
                detectedObjectsDao.findByForeignKeyId(file1, anchorConnection);
        List<OCRAppDetectedObject> result2 =
                detectedObjectsDao.findByForeignKeyId(file2, anchorConnection);

        assertAll(
                () -> assertEquals(1, result1.size()),
                () -> assertEquals("bird", result1.get(0).getClassName()),
                () -> assertEquals(1, result2.size()),
                () -> assertEquals("fish", result2.get(0).getClassName())
        );
    }

    @Test
    @DisplayName("insertBatch(): поля className и probability сохраняются без искажений")
    void testInsertPreservesClassNameAndProbability() throws SQLException {
        long fileId = insertParentFile("/images/precision.png");

        detectedObjectsDao.insertBatch(fileId,
                List.of(buildObject("helicopter", 0.876543)), anchorConnection);

        OCRAppDetectedObject loaded =
                detectedObjectsDao.findByForeignKeyId(fileId, anchorConnection).get(0);

        assertAll(
                () -> assertEquals("helicopter", loaded.getClassName()),
                () -> assertEquals(0.876543, loaded.getProbability(), 1e-6,
                        "Точность хранения вещественного числа probability")
        );
    }

    @Test
    @DisplayName("deleteByForeignKeyId(): удаляет все объекты конкретного файла")
    void testDeleteByFileId_removesAllObjects() throws SQLException {
        long fileId = insertParentFile("/images/delete_me.png");

        detectedObjectsDao.insertBatch(fileId,
                List.of(buildObject("plane", 0.99)), anchorConnection);
        detectedObjectsDao.deleteByForeignKeyId(fileId, anchorConnection);

        List<OCRAppDetectedObject> result =
                detectedObjectsDao.findByForeignKeyId(fileId, anchorConnection);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("insertBatch() с пустым списком не генерирует исключений")
    void testInsertBatch_emptyList_noException() throws SQLException {
        long fileId = insertParentFile("/images/createEmpty.png");

        assertDoesNotThrow(() ->
                detectedObjectsDao.insertBatch(
                        fileId, Collections.emptyList(), anchorConnection));
    }

    @Test
    @DisplayName("findByForeignKeyIds(): возвращает корректную карту {fileId → список объектов}")
    void testFindByForeignKeyIds_returnsCorrectMap() throws SQLException {
        long file1 = insertParentFile("/images/map_a.png");
        long file2 = insertParentFile("/images/map_b.png");

        detectedObjectsDao.insertBatch(file1, List.of(
                buildObject("cat", 0.90),
                buildObject("dog", 0.85)
        ), anchorConnection);
        detectedObjectsDao.insertBatch(file2, List.of(
                buildObject("car", 0.70)
        ), anchorConnection);

        Map<Long, List<OCRAppDetectedObject>> map =
                detectedObjectsDao.findByForeignKeyIds(
                        List.of(file1, file2), anchorConnection);

        assertAll(
                () -> assertEquals(2, map.get(file1).size()),
                () -> assertEquals(1, map.get(file2).size()),
                () -> assertEquals("car", map.get(file2).get(0).getClassName())
        );
    }
}