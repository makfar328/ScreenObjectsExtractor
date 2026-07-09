package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchCriteriaDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SearchCriteriaDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_sc_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();

        // Создаём родительскую таблицу search_sessions вручную —
        // SearchCriteriaDao не должен знать о ней
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS search_sessions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "started_at TEXT," +
                            "finished_at TEXT," +
                            "files_count INTEGER," +
                            "status TEXT," +
                            "error_message TEXT," +
                            "fast_search_start TEXT," +
                            "fast_search_end TEXT," +
                            "background_search_start TEXT," +
                            "background_search_end TEXT" +
                            ")"
            );
        }

        dao = new SearchCriteriaDao(connectionManager);
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    /**
     * Вставляет минимальную запись в search_sessions и возвращает сгенерированный id.
     * Нужен как родитель для FK в search_criteria.
     */
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

    private SearchCriteria buildCriteria(List<String> fileNames,
                                         List<String> keywords,
                                         List<String> fileTypes,
                                         LocalDate minDate,
                                         LocalDate maxDate) {
        return new SearchCriteria(
                fileNames,
                keywords,
                Collections.emptyList(),
                null,
                minDate,
                maxDate,
                Collections.emptyList(),
                fileTypes
        );
    }

    // ── Тесты insert() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("insert(): вставка минимальных критериев возвращает положительный id")
    void insert_minimalCriteria_returnsPositiveId() throws SQLException {
        long sessionId = insertSession();
        SearchCriteria criteria = buildCriteria(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null);

        long id = dao.insert(sessionId, criteria, anchorConnection);

        assertTrue(id > 0, "Сгенерированный id должен быть положительным");
    }

    @Test
    @DisplayName("insert(): каждый вызов генерирует уникальный id")
    void insert_twoCalls_generateDistinctIds() throws SQLException {
        long sessionId1 = insertSession();
        long sessionId2 = insertSession();
        SearchCriteria c = buildCriteria(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null);

        long id1 = dao.insert(sessionId1, c, anchorConnection);
        long id2 = dao.insert(sessionId2, c, anchorConnection);

        assertNotEquals(id1, id2, "Два insert() должны вернуть разные id");
    }

    @Test
    @DisplayName("insert(): сохраняет fileNames, keywords и fileTypes как JSON-массивы")
    void insert_withAllListFields_persistsCorrectly() throws SQLException {
        long sessionId = insertSession();
        SearchCriteria criteria = buildCriteria(
                List.of("photo.png", "scan.jpg"),
                List.of("invoice", "contract"),
                List.of("png", "jpg"),
                null, null
        );

        dao.insert(sessionId, criteria, anchorConnection);
        SearchCriteria loaded = dao.findBySessionId(sessionId, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(List.of("photo.png", "scan.jpg"),
                        loaded.getFileNames(), "fileNames должны совпасть"),
                () -> assertEquals(List.of("invoice", "contract"),
                        loaded.getKeywords(), "keywords должны совпасть"),
                () -> assertEquals(List.of("png", "jpg"),
                        loaded.getFileTypes(), "fileTypes должны совпасть")
        );
    }

    @Test
    @DisplayName("insert(): сохраняет minDate и maxDate")
    void insert_withDates_persistsDates() throws SQLException {
        long sessionId = insertSession();
        LocalDate minDate = LocalDate.of(2024, 1, 1);
        LocalDate maxDate = LocalDate.of(2025, 12, 31);
        SearchCriteria criteria = buildCriteria(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), minDate, maxDate
        );

        dao.insert(sessionId, criteria, anchorConnection);
        SearchCriteria loaded = dao.findBySessionId(sessionId, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(minDate, loaded.getMinDate(), "minDate должна совпасть"),
                () -> assertEquals(maxDate, loaded.getMaxDate(), "maxDate должна совпасть")
        );
    }

    @Test
    @DisplayName("insert(): null-даты сохраняются как NULL в БД и возвращаются null")
    void insert_nullDates_persistsAsNull() throws SQLException {
        long sessionId = insertSession();
        SearchCriteria criteria = buildCriteria(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null
        );

        dao.insert(sessionId, criteria, anchorConnection);
        SearchCriteria loaded = dao.findBySessionId(sessionId, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertNull(loaded.getMinDate(), "minDate должна быть null"),
                () -> assertNull(loaded.getMaxDate(), "maxDate должна быть null")
        );
    }

    // ── Тесты findBySessionId() ────────────────────────────────────────────

    @Test
    @DisplayName("findBySessionId(): несуществующий sessionId возвращает null")
    void findBySessionId_notFound_returnsNull() throws SQLException {
        SearchCriteria result = dao.findBySessionId(99999L, anchorConnection);
        assertNull(result);
    }

    @Test
    @DisplayName("findBySessionId(): возвращает запись, ранее вставленную insert()")
    void findBySessionId_afterInsert_returnsRecord() throws SQLException {
        long sessionId = insertSession();
        SearchCriteria criteria = buildCriteria(
                List.of("file.png"),
                List.of("keyword"),
                List.of("png"),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 6, 30)
        );

        dao.insert(sessionId, criteria, anchorConnection);
        SearchCriteria loaded = dao.findBySessionId(sessionId, anchorConnection);

        assertNotNull(loaded, "Запись должна быть найдена");
    }

    @Test
    @DisplayName("findBySessionId(): возвращает запись именно для указанного sessionId, а не для другого")
    void findBySessionId_returnsCorrectSession() throws SQLException {
        long sessionId1 = insertSession();
        long sessionId2 = insertSession();
        SearchCriteria criteria1 = buildCriteria(
                List.of("alpha.png"), Collections.emptyList(),
                Collections.emptyList(), null, null
        );
        SearchCriteria criteria2 = buildCriteria(
                List.of("beta.png"), Collections.emptyList(),
                Collections.emptyList(), null, null
        );

        dao.insert(sessionId1, criteria1, anchorConnection);
        dao.insert(sessionId2, criteria2, anchorConnection);

        SearchCriteria loaded1 = dao.findBySessionId(sessionId1, anchorConnection);
        SearchCriteria loaded2 = dao.findBySessionId(sessionId2, anchorConnection);

        assertAll(
                () -> assertNotNull(loaded1),
                () -> assertNotNull(loaded2),
                () -> assertTrue(loaded1.getFileNames().contains("alpha.png"),
                        "Для сессии 1 должен вернуться критерий с alpha.png"),
                () -> assertTrue(loaded2.getFileNames().contains("beta.png"),
                        "Для сессии 2 должен вернуться критерий с beta.png")
        );
    }

    // ── Тесты deleteBySessionId() ──────────────────────────────────────────

    @Test
    @DisplayName("deleteBySessionId(): после удаления findBySessionId() возвращает null")
    void deleteBySessionId_existingRecord_removesIt() throws SQLException {
        long sessionId = insertSession();
        dao.insert(sessionId,
                buildCriteria(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), null, null),
                anchorConnection);

        dao.deleteBySessionId(sessionId, anchorConnection);

        assertNull(dao.findBySessionId(sessionId, anchorConnection),
                "После удаления findBySessionId() должен вернуть null");
    }

    @Test
    @DisplayName("deleteBySessionId(): вызов с несуществующим id не генерирует исключений")
    void deleteBySessionId_nonExistent_noException() {
        assertDoesNotThrow(() -> dao.deleteBySessionId(99999L, anchorConnection));
    }
}