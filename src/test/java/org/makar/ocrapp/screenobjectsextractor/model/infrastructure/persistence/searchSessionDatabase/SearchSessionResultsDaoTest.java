package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchSessionResults;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchSessionResultsDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SearchSessionResultsDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_ssrd_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();
        dao = new SearchSessionResultsDao(connectionManager);

        // Создаём родительскую таблицу search_sessions для FK (если нужна)
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS search_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT
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

    /**
     * Создаёт FileMetadata с минимальным набором полей.
     * Путь нормализован через Paths.get().toString() для кроссплатформенности.
     */
    private FileMetadata buildMetadata(String posixPath, String fileName) {
        return new FileMetadata(
                Paths.get(posixPath),
                fileName,
                "png",
                1024L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                null,  // recognizedText  — не хранится в этой таблице
                null   // detectedObjects — не хранится в этой таблице
        );
    }

    // ── Тесты insert() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert(): возвращает положительный сгенерированный id")
    void insert_newRecord_returnsPositiveId() throws SQLException {
        int id = dao.insert(1L, buildMetadata("/images/photo.png", "photo.png"), anchorConnection);
        assertTrue(id > 0, "Сгенерированный id должен быть положительным, получено: " + id);
    }

    @Test
    @DisplayName("insert(): два вызова для одной сессии возвращают разные id")
    void insert_twoRecords_sameSession_differentIds() throws SQLException {
        int id1 = dao.insert(1L, buildMetadata("/images/a.png", "a.png"), anchorConnection);
        int id2 = dao.insert(1L, buildMetadata("/images/b.png", "b.png"), anchorConnection);
        assertNotEquals(id1, id2, "Два последовательных insert() должны возвращать разные id");
    }

    @Test
    @DisplayName("insert(): записи для разных сессий изолированы — разные session_id")
    void insert_differentSessions_areIsolated() throws SQLException {
        dao.insert(1L, buildMetadata("/images/sess1.png", "sess1.png"), anchorConnection);
        dao.insert(2L, buildMetadata("/images/sess2.png", "sess2.png"), anchorConnection);

        SearchSessionResults session1 = dao.findBySessionId(1L, anchorConnection);
        SearchSessionResults session2 = dao.findBySessionId(2L, anchorConnection);

        assertEquals(1, session1.getResults().size(), "Сессия 1 должна содержать 1 запись");
        assertEquals(1, session2.getResults().size(), "Сессия 2 должна содержать 1 запись");
    }

    // ── Тесты findBySessionId() ─────────────────────────────────────────────

    @Test
    @DisplayName("findBySessionId(): несуществующая сессия возвращает пустой SearchSessionResults")
    void findBySessionId_notFound_returnsEmptyResults() throws SQLException {
        SearchSessionResults result = dao.findBySessionId(99999L, anchorConnection);
        assertNotNull(result);
        assertEquals(99999L, result.getSearchSessionId());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    @DisplayName("findBySessionId(): возвращает все записи указанной сессии")
    void findBySessionId_returnsAllResultsForSession() throws SQLException {
        long sessionId = 5L;
        dao.insert(sessionId, buildMetadata("/images/x.png", "x.png"), anchorConnection);
        dao.insert(sessionId, buildMetadata("/images/y.png", "y.png"), anchorConnection);
        dao.insert(sessionId, buildMetadata("/images/z.png", "z.png"), anchorConnection);

        SearchSessionResults result = dao.findBySessionId(sessionId, anchorConnection);

        assertEquals(3, result.getResults().size(),
                "Должны быть возвращены все три записи сессии");
    }

    @Test
    @DisplayName("findBySessionId(): возвращает только записи указанной сессии, не чужие")
    void findBySessionId_doesNotLeakOtherSessionData() throws SQLException {
        dao.insert(10L, buildMetadata("/images/own.png",   "own.png"),   anchorConnection);
        dao.insert(20L, buildMetadata("/images/other.png", "other.png"), anchorConnection);

        SearchSessionResults result = dao.findBySessionId(10L, anchorConnection);

        assertEquals(1, result.getResults().size());
        assertEquals(Paths.get("/images/own.png").toString(),
                result.getResults().get(0).getFilePath().toString());
    }

    @Test
    @DisplayName("findBySessionId(): скалярные поля FileMetadata сохраняются без искажений (round-trip)")
    void findBySessionId_preservesScalarFields() throws SQLException {
        long sessionId = 7L;
        FileMetadata original = new FileMetadata(
                Paths.get("/images/roundtrip.png"),
                "roundtrip.png",
                "png",
                4096L,
                LocalDateTime.of(2025, 3, 15, 10, 30),
                LocalDateTime.of(2025, 6,  1, 22,  0),
                null, null
        );
        dao.insert(sessionId, original, anchorConnection);

        SearchSessionResults loaded = dao.findBySessionId(sessionId, anchorConnection);
        FileMetadata fm = loaded.getResults().get(0);

        assertAll(
                () -> assertEquals(Paths.get("/images/roundtrip.png").toString(),
                        fm.getFilePath().toString(),  "filePath"),
                () -> assertEquals("roundtrip.png",    fm.getFileName(),      "fileName"),
                () -> assertEquals("png",              fm.getFileExtension(), "fileExtension"),
                () -> assertEquals(4096L,              fm.getFileSize(),      "fileSize"),
                () -> assertEquals(LocalDateTime.of(2025, 3, 15, 10, 30),
                        fm.getCreationDate(),     "creationDate"),
                () -> assertEquals(LocalDateTime.of(2025, 6,  1, 22,  0),
                        fm.getModificationDate(), "modificationDate")
        );
    }

    @Test
    @DisplayName("findBySessionId(): возвращённый FileMetadata имеет положительный id из PK таблицы")
    void findBySessionId_fileMetadataHasPositiveId() throws SQLException {
        long sessionId = 3L;
        int insertedKey = dao.insert(sessionId,
                buildMetadata("/images/id_check.png", "id_check.png"), anchorConnection);

        SearchSessionResults result = dao.findBySessionId(sessionId, anchorConnection);

        assertEquals(1, result.getResults().size());
        assertEquals(insertedKey, result.getResults().get(0).getId(),
                "getId() должен вернуть PK строки из search_session_results");
    }

    // ── Тесты findAll() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll(): пустая таблица возвращает пустой список без исключений")
    void findAll_emptyTable_returnsEmptyList() throws SQLException {
        List<SearchSessionResults> result = dao.findAll(anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll(): возвращает по одному SearchSessionResults на каждый уникальный session_id")
    void findAll_groupsBySessionId() throws SQLException {
        dao.insert(1L, buildMetadata("/images/s1a.png", "s1a.png"), anchorConnection);
        dao.insert(1L, buildMetadata("/images/s1b.png", "s1b.png"), anchorConnection);
        dao.insert(2L, buildMetadata("/images/s2a.png", "s2a.png"), anchorConnection);

        List<SearchSessionResults> all = dao.findAll(anchorConnection);

        assertEquals(2, all.size(),
                "Должно быть 2 группы — по одной на каждый session_id");

        int totalFiles = all.stream().mapToInt(r -> r.getResults().size()).sum();
        assertEquals(3, totalFiles, "Суммарно должны быть возвращены все 3 файла");
    }

    @Test
    @DisplayName("findAll(): каждая группа содержит только записи своей сессии")
    void findAll_eachGroupContainsOnlyOwnData() throws SQLException {
        dao.insert(100L, buildMetadata("/images/hundred.png", "hundred.png"), anchorConnection);
        dao.insert(200L, buildMetadata("/images/twohundred.png", "twohundred.png"), anchorConnection);

        List<SearchSessionResults> all = dao.findAll(anchorConnection);

        for (SearchSessionResults group : all) {
            for (FileMetadata fm : group.getResults()) {
                // session_id не хранится внутри FileMetadata — проверяем через группировку
                // каждая группа имеет ровно одну запись
                assertEquals(1, group.getResults().size(),
                        "Каждая группа должна содержать ровно одну запись");
            }
        }
    }

    // ── Тесты deleteBySessionId() ────────────────────────────────────────────

    @Test
    @DisplayName("deleteBySessionId(): удаляет все записи сессии; findBySessionId() возвращает пустой результат")
    void deleteBySessionId_removesAllResultsForSession() throws SQLException {
        long sessionId = 9L;
        dao.insert(sessionId, buildMetadata("/images/del1.png", "del1.png"), anchorConnection);
        dao.insert(sessionId, buildMetadata("/images/del2.png", "del2.png"), anchorConnection);

        dao.deleteBySessionId(sessionId, anchorConnection);

        SearchSessionResults after = dao.findBySessionId(sessionId, anchorConnection);
        assertTrue(after.getResults().isEmpty(),
                "После deleteBySessionId() список результатов должен быть пустым");
    }

    @Test
    @DisplayName("deleteBySessionId(): не затрагивает записи других сессий")
    void deleteBySessionId_doesNotAffectOtherSessions() throws SQLException {
        dao.insert(11L, buildMetadata("/images/keep1.png", "keep1.png"), anchorConnection);
        dao.insert(12L, buildMetadata("/images/keep2.png", "keep2.png"), anchorConnection);

        dao.deleteBySessionId(11L, anchorConnection);

        SearchSessionResults session12 = dao.findBySessionId(12L, anchorConnection);
        assertEquals(1, session12.getResults().size(),
                "Сессия 12 не должна пострадать при удалении сессии 11");
    }

    @Test
    @DisplayName("deleteBySessionId(): повторный вызов для уже удалённой сессии не генерирует исключений")
    void deleteBySessionId_idempotent_noException() throws SQLException {
        long sessionId = 13L;
        dao.insert(sessionId, buildMetadata("/images/idem.png", "idem.png"), anchorConnection);
        dao.deleteBySessionId(sessionId, anchorConnection);

        assertDoesNotThrow(() -> dao.deleteBySessionId(sessionId, anchorConnection));
    }
}