package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetDirectoryDaoTest {

    private SQLiteConnectionManager connectionManager;
    private TargetDirectoryDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_td_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();

        // Создаём цепочку родительских таблиц для соблюдения FK
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

        dao = new TargetDirectoryDao(connectionManager);
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

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

    private SearchDirectoryConfig buildConfig(String path, int depth) {
        return new SearchDirectoryConfig(Paths.get(path), depth);
    }

    // ── Тесты insert() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("insert(): одиночная запись сохраняется без исключений")
    void insert_singleConfig_noException() throws SQLException {
        long criteriaId = insertCriteria(insertSession());

        assertDoesNotThrow(() ->
                dao.insert(criteriaId, buildConfig("C:\\images", 2), anchorConnection)
        );
    }

    @Test
    @DisplayName("insert(): запись доступна через findByCriteriaId() после вставки")
    void insert_afterInsert_findableByCriteriaId() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildConfig("C:\\photos", 1), anchorConnection);

        List<SearchDirectoryConfig> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("insert(): несколько директорий для одного criteriaId сохраняются все")
    void insert_multipleConfigs_allPersisted() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildConfig("C:\\photos",    1), anchorConnection);
        dao.insert(criteriaId, buildConfig("C:\\documents", 3), anchorConnection);
        dao.insert(criteriaId, buildConfig("D:\\archive",   2), anchorConnection);

        List<SearchDirectoryConfig> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(3, result.size(), "Все три директории должны быть сохранены");
    }

    // ── Тесты findByCriteriaId() ───────────────────────────────────────────

    @Test
    @DisplayName("findByCriteriaId(): несуществующий criteriaId возвращает пустой список")
    void findByCriteriaId_notFound_returnsEmptyList() throws SQLException {
        List<SearchDirectoryConfig> result = dao.findByCriteriaId(99999L, anchorConnection);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByCriteriaId(): path сохраняется без искажений (round-trip)")
    void findByCriteriaId_pathRoundTrip() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        String originalPath = Paths.get("C:\\images\\2025").toString();
        dao.insert(criteriaId,
                new SearchDirectoryConfig(Paths.get("C:\\images\\2025"), 1),
                anchorConnection);

        List<SearchDirectoryConfig> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals(originalPath,
                result.get(0).getDirectory().toString(),
                "Путь должен совпасть с исходным после round-trip через БД");
    }

    @Test
    @DisplayName("findByCriteriaId(): depth сохраняется корректно")
    void findByCriteriaId_depthRoundTrip() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildConfig("C:\\data", 5), anchorConnection);

        List<SearchDirectoryConfig> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getSearchDepth(),
                "depth должен совпасть с исходным значением");
    }

    @Test
    @DisplayName("findByCriteriaId(): возвращает только записи для указанного criteriaId")
    void findByCriteriaId_returnsOnlyOwnEntries() throws SQLException {
        long sessionId   = insertSession();
        long criteriaId1 = insertCriteria(sessionId);
        long criteriaId2 = insertCriteria(sessionId);

        dao.insert(criteriaId1, buildConfig("C:\\dir_a", 1), anchorConnection);
        dao.insert(criteriaId2, buildConfig("C:\\dir_b", 2), anchorConnection);

        List<SearchDirectoryConfig> result1 = dao.findByCriteriaId(criteriaId1, anchorConnection);
        List<SearchDirectoryConfig> result2 = dao.findByCriteriaId(criteriaId2, anchorConnection);

        assertAll(
                () -> assertEquals(1, result1.size(),
                        "criteriaId1 должен вернуть ровно одну запись"),
                () -> assertEquals(1, result2.size(),
                        "criteriaId2 должен вернуть ровно одну запись"),
                () -> assertEquals(Paths.get("C:\\dir_a").toString(),
                        result1.get(0).getDirectory().toString(),
                        "criteriaId1 должен вернуть C:\\dir_a"),
                () -> assertEquals(Paths.get("C:\\dir_b").toString(),
                        result2.get(0).getDirectory().toString(),
                        "criteriaId2 должен вернуть C:\\dir_b")
        );
    }

    @Test
    @DisplayName("findByCriteriaId(): depth = 0 сохраняется и возвращается корректно")
    void findByCriteriaId_zeroDepth_persistedCorrectly() throws SQLException {
        long criteriaId = insertCriteria(insertSession());
        dao.insert(criteriaId, buildConfig("C:\\flat", 0), anchorConnection);

        List<SearchDirectoryConfig> result = dao.findByCriteriaId(criteriaId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getSearchDepth(),
                "depth = 0 должен быть сохранён и возвращён без изменений");
    }
}