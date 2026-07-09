package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/*
памятка ошибок
SQL-сравнение строковое и регистрозависимое — file:///images/find_me.png ≠ file://\images\find_me.png.
Где 'file://\images\find_me.png' - возвращаемое toString() значение, записываемое в базу данных

Системные пути в файловой системе лучше хранить в переменной типа Path, а не String
 */
class IndexedFilesDaoTest {

    private SQLiteConnectionManager connectionManager;
    private IndexedFilesDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_if_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();
        dao = new IndexedFilesDao(connectionManager);
        dao.initializeTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    private FileMetadata buildMetadata(String filePath, String fileName) {
        return new FileMetadata(
                Paths.get(filePath),
                fileName,
                "png",
                2048L,
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 1, 10, 0),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    // ── Тесты upsert() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert(): вставка новой записи возвращает положительный id")
    void upsert_newRecord_returnsPositiveId() throws SQLException {
        long id = dao.upsert(anchorConnection, buildMetadata("/images/photo.png", "photo.png"));
        assertTrue(id > 0, "Сгенерированный id должен быть положительным, получено: " + id);
    }

    @Test
    @DisplayName("upsert(): повторный вызов с тем же путём возвращает тот же id (ON CONFLICT UPDATE)")
    void upsert_samePathTwice_returnsSameId() throws SQLException {
        FileMetadata meta = buildMetadata("/images/photo.png", "photo.png");
        long id1 = dao.upsert(anchorConnection, meta);
        long id2 = dao.upsert(anchorConnection, meta);
        assertEquals(id1, id2, "При конфликте пути upsert должен вернуть исходный id");
    }

    @Test
    @DisplayName("upsert(): обновляет поля записи при конфликте пути (fileName и fileSize)")
    void upsert_conflictPath_updatesScalarFields() throws SQLException {
        dao.upsert(anchorConnection, buildMetadata("/images/update_me.png", "old_name.png"));

        FileMetadata updated = new FileMetadata(
                Paths.get( Paths.get("/images/update_me.png").toString()),
                "new_name.png",
                "png",
                9999L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                Collections.emptyList(),
                Collections.emptyList()
        );
        dao.upsert(anchorConnection, updated);

        FileMetadata loaded = dao.findByPath(anchorConnection, Paths.get("/images/update_me.png").toString());
        assertNotNull(loaded);
        assertAll(
                () -> assertEquals("new_name.png", loaded.getFileName(),
                        "fileName должен быть обновлён"),
                () -> assertEquals(9999L, loaded.getFileSize(),
                        "fileSize должен быть обновлён")
        );
    }

    // ── Тесты findIdByPath() ────────────────────────────────────────────────

    @Test
    @DisplayName("findIdByPath(): возвращает id вставленной записи")
    void findIdByPath_existingPath_returnsCorrectId() throws SQLException {
        long insertedId = dao.upsert(anchorConnection,
                buildMetadata("/images/find_me.png", "find_me.png"));
        long foundId = dao.findIdByPath(anchorConnection,  Paths.get("/images/find_me.png").toString());
        assertEquals(insertedId, foundId);
    }

    @Test
    @DisplayName("findIdByPath(): несуществующий путь возвращает -1")
    void findIdByPath_notFound_returnsMinusOne() throws SQLException {
        long result = dao.findIdByPath(anchorConnection, "/does/not/exist.png");
        assertEquals(-1, result);
    }

    // ── Тесты deleteById() ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById(): запись физически удаляется, findIdByPath() возвращает -1")
    void deleteById_existingRecord_removesIt() throws SQLException {
        long id = dao.upsert(anchorConnection,
                buildMetadata("/images/delete_me.png", "delete_me.png"));

        dao.deleteById(anchorConnection, id);

        long foundId = dao.findIdByPath(anchorConnection, "/images/delete_me.png");
        assertEquals(-1, foundId, "После удаления findIdByPath() должен вернуть -1");
    }

    @Test
    @DisplayName("deleteById(): вызов с несуществующим id не генерирует исключений")
    void deleteById_nonExistentId_noException() {
        assertDoesNotThrow(() -> dao.deleteById(anchorConnection, 99999L));
    }

    // ── Тесты findByPath() ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByPath(): несуществующий путь возвращает null")
    void findByPath_notFound_returnsNull() throws SQLException {
        FileMetadata result = dao.findByPath(anchorConnection, "/no/such/file.png");
        assertNull(result);
    }

    @Test
    @DisplayName("findByPath(): возвращает FileMetadata с корректным id после upsert()")
    void findByPath_afterUpsert_returnsMetadataWithCorrectId() throws SQLException {
        long insertedId = dao.upsert(anchorConnection,
                buildMetadata("/images/round_trip.png", "round_trip.png"));

        FileMetadata loaded = dao.findByPath(anchorConnection,  Paths.get("/images/round_trip.png").toString());

        assertNotNull(loaded);
        assertEquals(insertedId, loaded.getId(),
                "id в FileMetadata должен совпадать с PK, возвращённым upsert()");
    }

    @Test
    @DisplayName("findByPath(): все скалярные поля сохраняются без искажений (round-trip)")
    void findByPath_preservesAllScalarFields() throws SQLException {
        FileMetadata original = new FileMetadata(
                Paths.get("/images/full_fields.png"),
                "full_fields.png",
                "png",
                4096L,
                LocalDateTime.of(2025, 3, 15, 10, 30),
                LocalDateTime.of(2025, 6, 1, 22, 0),
                Collections.emptyList(),
                Collections.emptyList()
        );
        dao.upsert(anchorConnection, original);

        FileMetadata loaded = dao.findByPath(anchorConnection, Paths.get("/images/full_fields.png").toString());

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals( Paths.get("/images/full_fields.png").toString(),
                        loaded.getFilePath().toString(), "filePath"),
                () -> assertEquals("full_fields.png",
                        loaded.getFileName(), "fileName"),
                () -> assertEquals("png",
                        loaded.getFileExtension(), "fileExtension"),
                () -> assertEquals(4096L,
                        loaded.getFileSize(), "fileSize"),
                () -> assertEquals(LocalDateTime.of(2025, 3, 15, 10, 30),
                        loaded.getCreationDate(), "creationDate"),
                () -> assertEquals(LocalDateTime.of(2025, 6, 1, 22, 0),
                        loaded.getModificationDate(), "modificationDate")
        );
    }

    // ── Тесты convenience-версий (без явного Connection) ────────────────────

    @Test
    @DisplayName("convenience upsert(): работает без внешнего Connection")
    void upsert_convenience_returnsPositiveId() throws SQLException {
        long id = dao.upsert(buildMetadata("/images/convenience.png", "convenience.png"));
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("convenience findIdByPath(): согласован с primary-версией")
    void findIdByPath_convenience_consistent() throws SQLException {
        long idFromPrimary = dao.upsert(anchorConnection,
                buildMetadata("/images/conv_find.png", "conv_find.png"));
        long idFromConvenience = dao.findIdByPath( Paths.get("/images/conv_find.png").toString());
        assertEquals(idFromPrimary, idFromConvenience);
    }

    @Test
    @DisplayName("convenience findByPath(): возвращает ту же запись, что и primary-версия")
    void findByPath_convenience_returnsCorrectRecord() throws SQLException {
        dao.upsert(anchorConnection,
                buildMetadata("/images/conv_path.png", "conv_path.png"));

        FileMetadata loaded = dao.findByPath( Paths.get("/images/conv_path.png").toString());

        assertNotNull(loaded);
        assertEquals("conv_path.png", loaded.getFileName());
    }
}