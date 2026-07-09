package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SSDetectedObjectsDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SSDetectedObjectsDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_ssd_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();
        dao = new SSDetectedObjectsDao(connectionManager);

        // Создаём родительскую таблицу — DAO проверяет FK
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS search_session_results (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id INTEGER NOT NULL,
                        file_path  TEXT    NOT NULL
                    )""");
        }
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    /** Вставляет минимальную запись в родительскую таблицу и возвращает её id. */
    private long insertParentRow(long sessionId, String filePath) throws SQLException {
        String sql = "INSERT INTO search_session_results (session_id, file_path) VALUES (?, ?)";
        try (var ps = anchorConnection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, sessionId);
            ps.setString(2, filePath);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private OCRAppDetectedObject obj(String className, double prob) {
        return new OCRAppDetectedObject(className, prob);
    }

    // ── Тесты insertBatch() ─────────────────────────────────────────────────

    @Test
    @DisplayName("insertBatch(): пустой список не генерирует исключений и не вставляет строк")
    void insertBatch_emptyList_noRows() throws SQLException {
        long parentId = insertParentRow(1L, "/images/a.png");
        assertDoesNotThrow(() -> dao.insertBatch(parentId, Collections.emptyList(), anchorConnection));

        List<OCRAppDetectedObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("insertBatch(): null список не генерирует исключений и не вставляет строк")
    void insertBatch_nullList_noRows() throws SQLException {
        long parentId = insertParentRow(1L, "/images/b.png");
        assertDoesNotThrow(() -> dao.insertBatch(parentId, null, anchorConnection));

        List<OCRAppDetectedObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("insertBatch(): вставляет все объекты пакета")
    void insertBatch_insertsAllObjects() throws SQLException {
        long parentId = insertParentRow(1L, "/images/c.png");
        List<OCRAppDetectedObject> batch = List.of(
                obj("cat",  0.95),
                obj("dog",  0.87),
                obj("bird", 0.72)
        );
        dao.insertBatch(parentId, batch, anchorConnection);

        List<OCRAppDetectedObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertEquals(3, loaded.size(), "Все три объекта должны быть сохранены");
    }

    @Test
    @DisplayName("insertBatch(): сохраняет className и probability без искажений (round-trip)")
    void insertBatch_preservesFieldValues() throws SQLException {
        long parentId = insertParentRow(1L, "/images/d.png");
        dao.insertBatch(parentId, List.of(obj("airplane", 0.9876)), anchorConnection);

        List<OCRAppDetectedObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertEquals(1, loaded.size());
        assertEquals("airplane", loaded.get(0).getClassName());
        assertEquals(0.9876, loaded.get(0).getProbability(), 1e-6);
    }

    // ── Тесты deleteByForeignKeyId() ────────────────────────────────────────

    @Test
    @DisplayName("deleteByForeignKeyId(): удаляет только записи указанного родителя")
    void deleteByForeignKeyId_removesOnlyTargetRows() throws SQLException {
        long parent1 = insertParentRow(1L, "/images/e.png");
        long parent2 = insertParentRow(1L, "/images/f.png");

        dao.insertBatch(parent1, List.of(obj("cat", 0.9)), anchorConnection);
        dao.insertBatch(parent2, List.of(obj("dog", 0.8)), anchorConnection);

        dao.deleteByForeignKeyId(parent1, anchorConnection);

        assertTrue(dao.findByForeignKeyId(parent1, anchorConnection).isEmpty(),
                "Записи parent1 должны быть удалены");
        assertEquals(1, dao.findByForeignKeyId(parent2, anchorConnection).size(),
                "Записи parent2 должны остаться нетронутыми");
    }

    @Test
    @DisplayName("deleteByForeignKeyId(): повторный вызов для уже удалённого id не генерирует исключений")
    void deleteByForeignKeyId_alreadyEmpty_noException() throws SQLException {
        long parentId = insertParentRow(1L, "/images/g.png");
        dao.insertBatch(parentId, List.of(obj("x", 0.5)), anchorConnection);
        dao.deleteByForeignKeyId(parentId, anchorConnection);

        assertDoesNotThrow(() -> dao.deleteByForeignKeyId(parentId, anchorConnection));
    }

    // ── Тесты findByForeignKeyId() ──────────────────────────────────────────

    @Test
    @DisplayName("findByForeignKeyId(): несуществующий id возвращает пустой список")
    void findByForeignKeyId_notFound_returnsEmpty() throws SQLException {
        List<OCRAppDetectedObject> result = dao.findByForeignKeyId(99999L, anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyId(): возвращает только объекты указанного родителя")
    void findByForeignKeyId_returnsCorrectObjects() throws SQLException {
        long parent1 = insertParentRow(1L, "/images/h.png");
        long parent2 = insertParentRow(1L, "/images/i.png");

        dao.insertBatch(parent1, List.of(obj("plane", 0.99)), anchorConnection);
        dao.insertBatch(parent2, List.of(obj("ship",  0.88)), anchorConnection);

        List<OCRAppDetectedObject> result = dao.findByForeignKeyId(parent1, anchorConnection);

        assertEquals(1, result.size());
        assertEquals("plane", result.get(0).getClassName());
    }

    // ── Тесты findByForeignKeyIds() (bulk) ──────────────────────────────────

    @Test
    @DisplayName("findByForeignKeyIds(): пустой список возвращает пустую Map без исключений")
    void findByForeignKeyIds_emptyInput_returnsEmptyMap() throws SQLException {
        Map<Long, List<OCRAppDetectedObject>> result =
                dao.findByForeignKeyIds(Collections.emptyList(), anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): null список возвращает пустую Map без исключений")
    void findByForeignKeyIds_nullInput_returnsEmptyMap() throws SQLException {
        Map<Long, List<OCRAppDetectedObject>> result =
                dao.findByForeignKeyIds(null, anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): каждый ключ присутствует в Map, включая родителей без объектов")
    void findByForeignKeyIds_guaranteesKeyPresenceForAllIds() throws SQLException {
        long p1 = insertParentRow(1L, "/images/j.png");
        long p2 = insertParentRow(1L, "/images/k.png"); // без объектов

        dao.insertBatch(p1, List.of(obj("car", 0.93)), anchorConnection);

        Map<Long, List<OCRAppDetectedObject>> result =
                dao.findByForeignKeyIds(List.of(p1, p2), anchorConnection);

        assertTrue(result.containsKey(p1), "p1 должен присутствовать в Map");
        assertTrue(result.containsKey(p2), "p2 должен присутствовать в Map (пустой список)");
        assertEquals(1, result.get(p1).size());
        assertTrue(result.get(p2).isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): bulk-загрузка корректно распределяет объекты по ключам")
    void findByForeignKeyIds_correctlyGroupsByKey() throws SQLException {
        long p1 = insertParentRow(1L, "/images/l.png");
        long p2 = insertParentRow(1L, "/images/m.png");

        dao.insertBatch(p1, List.of(obj("cat", 0.9), obj("dog", 0.8)), anchorConnection);
        dao.insertBatch(p2, List.of(obj("bird", 0.7)), anchorConnection);

        Map<Long, List<OCRAppDetectedObject>> result =
                dao.findByForeignKeyIds(List.of(p1, p2), anchorConnection);

        assertEquals(2, result.get(p1).size(), "p1 должен содержать 2 объекта");
        assertEquals(1, result.get(p2).size(), "p2 должен содержать 1 объект");
    }
}