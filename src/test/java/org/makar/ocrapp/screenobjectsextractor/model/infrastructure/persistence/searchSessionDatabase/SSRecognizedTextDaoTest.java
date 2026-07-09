package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SSRecognizedTextDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SSRecognizedTextDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_ssrt_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();
        dao = new SSRecognizedTextDao(connectionManager);

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

    private TextObject text(String content, int x, int y, int w, int h) {
        return new TextObject(content, x, y, w, h);
    }

    // ── Тесты insertBatch() ─────────────────────────────────────────────────

    @Test
    @DisplayName("insertBatch(): пустой список не генерирует исключений и не вставляет строк")
    void insertBatch_emptyList_noRows() throws SQLException {
        long parentId = insertParentRow(1L, "/images/a.png");
        assertDoesNotThrow(() -> dao.insertBatch(parentId, Collections.emptyList(), anchorConnection));

        List<TextObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("insertBatch(): null список не генерирует исключений и не вставляет строк")
    void insertBatch_nullList_noRows() throws SQLException {
        long parentId = insertParentRow(1L, "/images/b.png");
        assertDoesNotThrow(() -> dao.insertBatch(parentId, null, anchorConnection));

        List<TextObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("insertBatch(): вставляет все текстовые объекты пакета")
    void insertBatch_insertsAllTexts() throws SQLException {
        long parentId = insertParentRow(1L, "/images/c.png");
        List<TextObject> batch = List.of(
                text("Строка 1", 0,   0, 300, 25),
                text("Строка 2", 0,  30, 300, 25),
                text("Строка 3", 0,  60, 300, 25)
        );
        dao.insertBatch(parentId, batch, anchorConnection);

        List<TextObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertEquals(3, loaded.size(), "Все три текстовых объекта должны быть сохранены");
    }

    @Test
    @DisplayName("insertBatch(): сохраняет все поля TextObject без искажений (round-trip)")
    void insertBatch_preservesAllFields() throws SQLException {
        long parentId = insertParentRow(1L, "/images/d.png");
        dao.insertBatch(parentId, List.of(text("Hello World", 10, 20, 150, 30)), anchorConnection);

        List<TextObject> loaded = dao.findByForeignKeyId(parentId, anchorConnection);
        assertEquals(1, loaded.size());
        TextObject t = loaded.get(0);
        assertAll(
                () -> assertEquals("Hello World", t.getText(),   "text"),
                () -> assertEquals(10,             t.getX(),     "x"),
                () -> assertEquals(20,             t.getY(),     "y"),
                () -> assertEquals(150,            t.getWidth(), "width"),
                () -> assertEquals(30,             t.getHeight(),"height")
        );
    }

    // ── Тесты deleteByForeignKeyId() ────────────────────────────────────────

    @Test
    @DisplayName("deleteByForeignKeyId(): удаляет только записи указанного родителя")
    void deleteByForeignKeyId_removesOnlyTargetRows() throws SQLException {
        long parent1 = insertParentRow(1L, "/images/e.png");
        long parent2 = insertParentRow(1L, "/images/f.png");

        dao.insertBatch(parent1, List.of(text("text1", 0, 0, 100, 20)), anchorConnection);
        dao.insertBatch(parent2, List.of(text("text2", 0, 0, 100, 20)), anchorConnection);

        dao.deleteByForeignKeyId(parent1, anchorConnection);

        assertTrue(dao.findByForeignKeyId(parent1, anchorConnection).isEmpty(),
                "Тексты parent1 должны быть удалены");
        assertEquals(1, dao.findByForeignKeyId(parent2, anchorConnection).size(),
                "Тексты parent2 должны остаться нетронутыми");
    }

    @Test
    @DisplayName("deleteByForeignKeyId(): повторный вызов не генерирует исключений")
    void deleteByForeignKeyId_idempotent_noException() throws SQLException {
        long parentId = insertParentRow(1L, "/images/g.png");
        dao.insertBatch(parentId, List.of(text("x", 0, 0, 50, 15)), anchorConnection);
        dao.deleteByForeignKeyId(parentId, anchorConnection);

        assertDoesNotThrow(() -> dao.deleteByForeignKeyId(parentId, anchorConnection));
    }

    // ── Тесты findByForeignKeyId() ──────────────────────────────────────────

    @Test
    @DisplayName("findByForeignKeyId(): несуществующий id возвращает пустой список")
    void findByForeignKeyId_notFound_returnsEmpty() throws SQLException {
        List<TextObject> result = dao.findByForeignKeyId(99999L, anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyId(): возвращает только тексты указанного родителя")
    void findByForeignKeyId_returnsCorrectTexts() throws SQLException {
        long parent1 = insertParentRow(1L, "/images/h.png");
        long parent2 = insertParentRow(1L, "/images/i.png");

        dao.insertBatch(parent1, List.of(text("invoice", 0, 0, 100, 20)), anchorConnection);
        dao.insertBatch(parent2, List.of(text("receipt", 0, 0, 100, 20)), anchorConnection);

        List<TextObject> result = dao.findByForeignKeyId(parent1, anchorConnection);

        assertEquals(1, result.size());
        assertEquals("invoice", result.get(0).getText());
    }

    // ── Тесты findByForeignKeyIds() (bulk) ──────────────────────────────────

    @Test
    @DisplayName("findByForeignKeyIds(): пустой список возвращает пустую Map без исключений")
    void findByForeignKeyIds_emptyInput_returnsEmptyMap() throws SQLException {
        Map<Long, List<TextObject>> result =
                dao.findByForeignKeyIds(Collections.emptyList(), anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): null список возвращает пустую Map без исключений")
    void findByForeignKeyIds_nullInput_returnsEmptyMap() throws SQLException {
        Map<Long, List<TextObject>> result =
                dao.findByForeignKeyIds(null, anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): гарантирует присутствие ключа для родителя без текстов")
    void findByForeignKeyIds_guaranteesKeyPresenceForEmptyParent() throws SQLException {
        long p1 = insertParentRow(1L, "/images/j.png");
        long p2 = insertParentRow(1L, "/images/k.png"); // без текстов

        dao.insertBatch(p1, List.of(text("hello", 0, 0, 100, 20)), anchorConnection);

        Map<Long, List<TextObject>> result =
                dao.findByForeignKeyIds(List.of(p1, p2), anchorConnection);

        assertTrue(result.containsKey(p1));
        assertTrue(result.containsKey(p2), "p2 должен присутствовать в Map с пустым списком");
        assertEquals(1, result.get(p1).size());
        assertTrue(result.get(p2).isEmpty());
    }

    @Test
    @DisplayName("findByForeignKeyIds(): bulk-загрузка корректно распределяет тексты по ключам")
    void findByForeignKeyIds_correctlyGroupsByKey() throws SQLException {
        long p1 = insertParentRow(1L, "/images/l.png");
        long p2 = insertParentRow(1L, "/images/m.png");

        dao.insertBatch(p1, List.of(
                text("line1", 0,  0, 200, 20),
                text("line2", 0, 25, 200, 20)
        ), anchorConnection);
        dao.insertBatch(p2, List.of(text("only", 0, 0, 150, 20)), anchorConnection);

        Map<Long, List<TextObject>> result =
                dao.findByForeignKeyIds(List.of(p1, p2), anchorConnection);

        assertEquals(2, result.get(p1).size(), "p1 должен содержать 2 текста");
        assertEquals(1, result.get(p2).size(), "p2 должен содержать 1 текст");
    }
}