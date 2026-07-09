package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IndexedFilesRecognizedTextDaoTest {

    private SQLiteConnectionManager connectionManager;
    // Объявляем через интерфейс — верифицирует корректность реализации контракта
    private IRecognizedTextDao recognizedTextDao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_rtf_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);

        anchorConnection = connectionManager.getConnection();

        // Минимальная родительская таблица для соблюдения FK-ограничения
        try (Statement stmt = anchorConnection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS indexed_files (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_path TEXT NOT NULL
                    )
                    """);
        }

        recognizedTextDao = new IndexedFilesRecognizedTextDao(connectionManager);
        recognizedTextDao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

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

    private TextObject buildText(String text, int x, int y, int w, int h) {
        return new TextObject(text, x, y, w, h);
    }

    // ── Тесты ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("insertBatch() + findByForeignKeyId(): базовый цикл вставки и выборки")
    void testInsertAndFindByFileId() throws SQLException {
        long fileId = insertParentFile("/docs/page1.png");

        recognizedTextDao.insertBatch(
                fileId, List.of(buildText("Hello OCR", 10, 20, 200, 30)), anchorConnection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(fileId, anchorConnection);

        assertEquals(1, result.size());
        assertEquals("Hello OCR", result.get(0).getText());
    }

    @Test
    @DisplayName("insertBatch(): несколько текстовых блоков возвращаются полностью")
    void testInsertMultipleTexts_allReturned() throws SQLException {
        long fileId = insertParentFile("/docs/page2.png");

        recognizedTextDao.insertBatch(fileId, List.of(
                buildText("Строка 1", 0,  0,  300, 25),
                buildText("Строка 2", 0, 30,  300, 25),
                buildText("Строка 3", 0, 60,  300, 25)
        ), anchorConnection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(fileId, anchorConnection);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("findByForeignKeyId(): несуществующий fileId — пустой ненулевой список")
    void testFindByFileId_notFound_returnsEmptyList() throws SQLException {
        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(9999L, anchorConnection);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Тексты разных файлов не пересекаются при выборке по fileId")
    void testTextsAreIsolatedByFileId() throws SQLException {
        long file1 = insertParentFile("/docs/a.png");
        long file2 = insertParentFile("/docs/b.png");

        recognizedTextDao.insertBatch(file1,
                List.of(buildText("Текст файла A", 0, 0, 100, 20)), anchorConnection);
        recognizedTextDao.insertBatch(file2,
                List.of(buildText("Текст файла B", 0, 0, 100, 20)), anchorConnection);

        List<TextObject> result1 =
                recognizedTextDao.findByForeignKeyId(file1, anchorConnection);
        List<TextObject> result2 =
                recognizedTextDao.findByForeignKeyId(file2, anchorConnection);

        assertAll(
                () -> assertEquals(1, result1.size()),
                () -> assertEquals("Текст файла A", result1.get(0).getText()),
                () -> assertEquals(1, result2.size()),
                () -> assertEquals("Текст файла B", result2.get(0).getText())
        );
    }

    @Test
    @DisplayName("insertBatch(): все поля TextObject сохраняются без искажений")
    void testInsertPreservesAllFields() throws SQLException {
        long fileId = insertParentFile("/docs/fields.png");

        recognizedTextDao.insertBatch(fileId,
                List.of(buildText("Контроль полей", 12, 34, 256, 48)), anchorConnection);

        TextObject loaded =
                recognizedTextDao.findByForeignKeyId(fileId, anchorConnection).get(0);

        assertAll(
                () -> assertEquals("Контроль полей", loaded.getText()),
                () -> assertEquals(12,  loaded.getX()),
                () -> assertEquals(34,  loaded.getY()),
                () -> assertEquals(256, loaded.getWidth()),
                () -> assertEquals(48,  loaded.getHeight())
        );
    }

    @Test
    @DisplayName("findByForeignKeyIds(): возвращает корректную карту {fileId → список текстов}")
    void testFindByForeignKeyIds_returnsCorrectMap() throws SQLException {
        long file1 = insertParentFile("/docs/map_a.png");
        long file2 = insertParentFile("/docs/map_b.png");

        recognizedTextDao.insertBatch(file1, List.of(
                buildText("AA", 0,  0, 50, 10),
                buildText("BB", 0, 15, 50, 10)
        ), anchorConnection);
        recognizedTextDao.insertBatch(file2, List.of(
                buildText("CC", 0, 0, 50, 10)
        ), anchorConnection);

        Map<Long, List<TextObject>> map =
                recognizedTextDao.findByForeignKeyIds(List.of(file1, file2), anchorConnection);

        assertAll(
                () -> assertEquals(2, map.get(file1).size()),
                () -> assertEquals(1, map.get(file2).size()),
                () -> assertEquals("CC", map.get(file2).get(0).getText())
        );
    }

    @Test
    @DisplayName("deleteByForeignKeyId(): удаляет все тексты конкретного файла")
    void testDeleteByFileId_removesAllTexts() throws SQLException {
        long fileId = insertParentFile("/docs/delete_me.png");

        recognizedTextDao.insertBatch(fileId,
                List.of(buildText("Удаляемый текст", 0, 0, 100, 20)), anchorConnection);
        recognizedTextDao.deleteByForeignKeyId(fileId, anchorConnection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(fileId, anchorConnection);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("insertBatch() с пустым списком не генерирует исключений")
    void testInsertBatch_emptyList_noException() throws SQLException {
        long fileId = insertParentFile("/docs/createEmpty.png");

        assertDoesNotThrow(() ->
                recognizedTextDao.insertBatch(
                        fileId, Collections.emptyList(), anchorConnection));
    }

    @Test
    @DisplayName("findByForeignKeyId(): порядок возврата соответствует порядку вставки (ORDER BY id)")
    void testFindByFileId_preservesInsertionOrder() throws SQLException {
        long fileId = insertParentFile("/docs/order.png");

        List<String> texts = List.of("Первый", "Второй", "Третий");
        recognizedTextDao.insertBatch(fileId, List.of(
                buildText(texts.get(0), 0,  0, 100, 20),
                buildText(texts.get(1), 0, 25, 100, 20),
                buildText(texts.get(2), 0, 50, 100, 20)
        ), anchorConnection);

        List<TextObject> result =
                recognizedTextDao.findByForeignKeyId(fileId, anchorConnection);

        for (int i = 0; i < texts.size(); i++) {
            final int idx = i;
            assertEquals(texts.get(idx), result.get(idx).getText(),
                    "Порядок элемента " + idx + " нарушен");
        }
    }
}