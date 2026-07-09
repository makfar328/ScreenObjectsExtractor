package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.SelectedObjectClass;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelectedObjectClassDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SelectedObjectClassDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_soc_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();

        // Цепочка FK: search_sessions → search_criteria → selected_objects_classes
        // Создаём все три уровня схемы
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS search_sessions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "status TEXT" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS search_criteria (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "session_id INTEGER NOT NULL," +
                            "file_names TEXT," +
                            "keywords TEXT," +
                            "min_date TEXT," +
                            "max_date TEXT," +
                            "file_extensions TEXT," +
                            "FOREIGN KEY (session_id) REFERENCES search_sessions(id) ON DELETE CASCADE" +
                            ")"
            );
        }

        dao = new SelectedObjectClassDao(connectionManager);
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    /** Вставляет строку в search_sessions, возвращает её id. */
    private long insertSession() throws SQLException {
        String sql = "INSERT INTO search_sessions (status) VALUES ('CREATED')";
        try (PreparedStatement ps = anchorConnection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    /** Вставляет строку в search_criteria, возвращает её id. */
    private long insertCriteria(long sessionId) throws SQLException {
        String sql = "INSERT INTO search_criteria " +
                "(session_id, file_names, keywords, file_extensions) " +
                "VALUES (?, '[]', '[]', '[]')";
        try (PreparedStatement ps = anchorConnection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private SelectedObjectClass buildEntry(String className, int count) {
        SelectedObjectClass entry = new SelectedObjectClass(className);
        entry.setCount(count);
        return entry;
    }

    // ── Тесты insert() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("insert(): одиночная запись сохраняется без исключений")
    void insert_singleEntry_noException() throws SQLException {
        long criteriaId = insertCriteria(insertSession());

        assertDoesNotThrow(() ->
                dao.insert(criteriaId, buildEntry("cat", 2), anchorConnection)
        );
    }

    @Test
    @DisplayName("insert(): запись доступна через findByCriteriaId() после вставки")
    void insert_afterInsert_findableByByCriteriaId() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildEntry("dog", 1), anchorConnection);

        List<SelectedObjectClass> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals("dog", result.get(0).getClassName());
    }

    @Test
    @DisplayName("insert(): несколько записей с разными классами для одного criteriaId")
    void insert_multipleEntries_allPersisted() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildEntry("car",    3), anchorConnection);
        dao.insert(criteriaId, buildEntry("person", 1), anchorConnection);
        dao.insert(criteriaId, buildEntry("bike",   2), anchorConnection);

        List<SelectedObjectClass> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("insert(): поле count сохраняется корректно")
    void insert_countField_persistedCorrectly() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildEntry("plane", 5), anchorConnection);

        List<SelectedObjectClass> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getCount(), "count должен совпасть с исходным значением");
    }

    // ── Тесты findByCriteriaId() ───────────────────────────────────────────

    @Test
    @DisplayName("findByCriteriaId(): несуществующий criteriaId возвращает пустой список")
    void findByCriteriaId_notFound_returnsEmptyList() throws SQLException {
        List<SelectedObjectClass> result = dao.findByCriteriaId(99999L, anchorConnection);

        assertNotNull(result, "Результат не должен быть null");
        assertTrue(result.isEmpty(), "Список должен быть пустым для несуществующего id");
    }

    @Test
    @DisplayName("findByCriteriaId(): возвращает только записи для указанного criteriaId")
    void findByCriteriaId_returnsOnlyOwnEntries() throws SQLException {
        long sessionId    = insertSession();
        long criteriaId1  = insertCriteria(sessionId);
        long criteriaId2  = insertCriteria(sessionId);

        dao.insert(criteriaId1, buildEntry("cat", 1), anchorConnection);
        dao.insert(criteriaId2, buildEntry("dog", 1), anchorConnection);

        List<SelectedObjectClass> result1 = dao.findByCriteriaId(criteriaId1, anchorConnection);
        List<SelectedObjectClass> result2 = dao.findByCriteriaId(criteriaId2, anchorConnection);

        assertAll(
                () -> assertEquals(1, result1.size()),
                () -> assertEquals("cat", result1.get(0).getClassName(),
                        "criteriaId1 должен вернуть только 'cat'"),
                () -> assertEquals(1, result2.size()),
                () -> assertEquals("dog", result2.get(0).getClassName(),
                        "criteriaId2 должен вернуть только 'dog'")
        );
    }

    @Test
    @DisplayName("findByCriteriaId(): className сохраняется без искажений (round-trip)")
    void findByCriteriaId_classNameRoundTrip() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        String originalClassName = "traffic light";
        dao.insert(criteriaId, buildEntry(originalClassName, 1), anchorConnection);

        List<SelectedObjectClass> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals(originalClassName, result.get(0).getClassName());
    }
}