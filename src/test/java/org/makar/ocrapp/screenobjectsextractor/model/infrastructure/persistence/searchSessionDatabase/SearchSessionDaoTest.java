package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSessionBuilder;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты DAO для таблицы search_sessions.
 *
 * Паттерн инициализации: anchorConnection держит in-memory базу живой на весь тест.
 * Все primary-методы (findById, findAll, update, delete) принимают Connection снаружи —
 * тесты передают anchorConnection явно, имитируя работу репозитория.
 * Метод insert() открывает Connection самостоятельно (как точка входа репозитория),
 * поэтому для него нужна работающая БД — anchorConnection это обеспечивает.
 */
class SearchSessionDaoTest {

    private SQLiteConnectionManager connectionManager;
    private SearchSessionDao dao;
    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_ssd_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();
        dao = new SearchSessionDao(connectionManager);
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
     * Минимальная сессия — только обязательные поля,
     * все временны́е диапазоны null (допустимо схемой).
     */
    private SearchSession buildSession(String status) {
        return new SearchSessionBuilder()
                .withStartedAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                .withFinishedAt(LocalDateTime.of(2025, 6, 1, 10, 30))
                .withFilesCount(5)
                .withStatus(status)
                .withErrorMessage(null)
                .withSearchResults(Collections.emptyList())
                .build();
    }

    private SearchSession buildSessionWithTimes(String status,
                                                LocalDateTime fastStart,
                                                LocalDateTime fastEnd,
                                                LocalDateTime bgStart,
                                                LocalDateTime bgEnd) {
        SearchSession s = new SearchSessionBuilder()
                .withStartedAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                .withFinishedAt(LocalDateTime.of(2025, 6, 1, 11, 0))
                .withFilesCount(3)
                .withStatus(status)
                .withSearchResults(Collections.emptyList())
                .withFastSearchStart(fastStart)
                .withFastSearchEnd(fastEnd)
                .withBackgroundSearchStart(bgStart)
                .withBackgroundSearchEnd(bgEnd)
                .build();
        return s;
    }

    // ── Тесты insert() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert(): вставка возвращает положительный id")
    void insert_newSession_returnsPositiveId() {
        int id = dao.insert(buildSession("DONE"), anchorConnection);
        assertTrue(id > 0, "Сгенерированный id должен быть положительным, получено: " + id);
    }

    @Test
    @DisplayName("insert(): два последовательных вызова возвращают разные id")
    void insert_twoSessions_returnsDifferentIds() {
        int id1 = dao.insert(buildSession("DONE"),   anchorConnection);
        int id2 = dao.insert(buildSession("FAILED"), anchorConnection);
        assertNotEquals(id1, id2, "Каждая вставка должна порождать уникальный id");
    }

    @Test
    @DisplayName("insert(): временны́е поля fastSearch и backgroundSearch сохраняются без потерь")
    void insert_withSearchTimestamps_roundTrip() throws SQLException {
        LocalDateTime fs  = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime fe  = LocalDateTime.of(2025, 6, 1, 10, 15);
        LocalDateTime bs  = LocalDateTime.of(2025, 6, 1, 10, 15);
        LocalDateTime be  = LocalDateTime.of(2025, 6, 1, 10, 30);

        int id = dao.insert(buildSessionWithTimes("DONE", fs, fe, bs, be), anchorConnection);
        SearchSession loaded = dao.findById(id, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(fs, loaded.getFastSearchStart(),        "fastSearchStart"),
                () -> assertEquals(fe, loaded.getFastSearchEnd(),          "fastSearchEnd"),
                () -> assertEquals(bs, loaded.getBackgroundSearchStart(),  "backgroundSearchStart"),
                () -> assertEquals(be, loaded.getBackgroundSearchEnd(),    "backgroundSearchEnd")
        );
    }

    // ── Тесты findById() ────────────────────────────────────────────────────

    @Test
    @DisplayName("findById(): несуществующий id возвращает null")
    void findById_notFound_returnsNull() throws SQLException {
        SearchSession result = dao.findById(99999L, anchorConnection);
        assertNull(result);
    }

    @Test
    @DisplayName("findById(): возвращает сессию с корректными скалярными полями (round-trip)")
    void findById_afterInsert_preservesScalarFields() throws SQLException {
        SearchSession original = new SearchSessionBuilder()
                .withStartedAt(LocalDateTime.of(2025, 3, 15, 9, 0))
                .withFinishedAt(LocalDateTime.of(2025, 3, 15, 9, 45))
                .withFilesCount(12)
                .withStatus("DONE")
                .withErrorMessage("some error")
                .withSearchResults(Collections.emptyList())
                .build();

        int id = dao.insert(original, anchorConnection);
        SearchSession loaded = dao.findById(id, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(id,                                    loaded.getSessionId(),   "sessionId"),
                () -> assertEquals(LocalDateTime.of(2025, 3, 15, 9, 0),  loaded.getStartedAt(),   "startedAt"),
                () -> assertEquals(LocalDateTime.of(2025, 3, 15, 9, 45), loaded.getFinishedAt(),  "finishedAt"),
                () -> assertEquals(12,                                    loaded.getFilesCount(),  "filesCount"),
                () -> assertEquals("DONE",                                loaded.getStatus(),      "status"),
                () -> assertEquals("some error",                          loaded.getErrorMessage(),"errorMessage")
        );
    }

    @Test
    @DisplayName("findById(): поля criteria и searchResults всегда null — заполняет репозиторий")
    void findById_criteriaAndResultsAreNull() throws SQLException {
        int id = dao.insert(buildSession("DONE"), anchorConnection);
        SearchSession loaded = dao.findById(id, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertNull(loaded.getCriteria(),      "criteria должны быть null"),
                () -> assertNull(loaded.getSearchResults(), "searchResults должны быть null")
        );
    }

    // ── Тесты findAll() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll(): пустая таблица возвращает пустой список")
    void findAll_emptyTable_returnsEmptyList() throws SQLException {
        List<SearchSession> result = dao.findAll(anchorConnection);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll(): возвращает все вставленные сессии, сортировка по id DESC")
    void findAll_multipleInserts_returnsAllInDescOrder() throws SQLException {
        int id1 = dao.insert(buildSession("DONE"),    anchorConnection);
        int id2 = dao.insert(buildSession("FAILED"),  anchorConnection);
        int id3 = dao.insert(buildSession("RUNNING"), anchorConnection);

        List<SearchSession> all = dao.findAll(anchorConnection);

        assertEquals(3, all.size());
        // ORDER BY id DESC → id3 первый
        assertEquals(id3, all.get(0).getSessionId(), "Первой должна быть сессия с наибольшим id");
        assertEquals(id1, all.get(2).getSessionId(), "Последней должна быть сессия с наименьшим id");
    }

    // ── Тесты update() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update(): обновляет status, filesCount, finishedAt и errorMessage")
    void update_changesScalarFields() throws SQLException {
        int id = dao.insert(buildSession("RUNNING"), anchorConnection);

        SearchSession updated = new SearchSessionBuilder()
                .withSessionId(id)
                .withStartedAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                .withFinishedAt(LocalDateTime.of(2025, 6, 1, 10, 50))
                .withFilesCount(99)
                .withStatus("DONE")
                .withErrorMessage("updated error")
                .withSearchResults(Collections.emptyList())
                .build();

        dao.update(updated, anchorConnection);

        SearchSession loaded = dao.findById(id, anchorConnection);
        assertNotNull(loaded);
        assertAll(
                () -> assertEquals("DONE",          loaded.getStatus(),      "status после update"),
                () -> assertEquals(99,              loaded.getFilesCount(),  "filesCount после update"),
                () -> assertEquals("updated error", loaded.getErrorMessage(),"errorMessage после update"),
                () -> assertEquals(LocalDateTime.of(2025, 6, 1, 10, 50),
                        loaded.getFinishedAt(), "finishedAt после update")
        );
    }

    @Test
    @DisplayName("update(): несуществующий id бросает SQLException")
    void update_nonExistentId_throwsSQLException() {
        SearchSession ghost = new SearchSessionBuilder()
                .withSessionId(99999L)
                .withStatus("DONE")
                .withFilesCount(0)
                .withSearchResults(Collections.emptyList())
                .build();

        assertThrows(SQLException.class, () -> dao.update(ghost, anchorConnection),
                "update() должен выбросить SQLException для несуществующего id");
    }

    // ── Тесты deleteSearchSessionScore() ────────────────────────────────────

    @Test
    @DisplayName("deleteSearchSessionScore(): запись недоступна через findById() после удаления")
    void delete_existingSession_removesIt() throws SQLException {
        int id = dao.insert(buildSession("DONE"), anchorConnection);

        dao.deleteSearchSessionScore(id, anchorConnection);

        SearchSession result = dao.findById(id, anchorConnection);
        assertNull(result, "После удаления findById() должен вернуть null");
    }

    @Test
    @DisplayName("deleteSearchSessionScore(): вызов с несуществующим id не бросает исключений")
    void delete_nonExistentId_noException() {
        assertDoesNotThrow(
                () -> dao.deleteSearchSessionScore(99999L, anchorConnection),
                "Удаление несуществующей сессии не должно генерировать исключений"
        );
    }

    @Test
    @DisplayName("deleteSearchSessionScore(): удаление не влияет на другие записи в таблице")
    void delete_oneSession_doesNotAffectOthers() throws SQLException {
        int id1 = dao.insert(buildSession("DONE"),   anchorConnection);
        int id2 = dao.insert(buildSession("FAILED"), anchorConnection);

        dao.deleteSearchSessionScore(id1, anchorConnection);

        assertNull(dao.findById(id1, anchorConnection), "Удалённая сессия недоступна");
        assertNotNull(dao.findById(id2, anchorConnection), "Соседняя сессия должна остаться");
    }

    // ── Тесты null-значений ─────────────────────────────────────────────────

    @Test
    @DisplayName("insert()/findById(): null-поля временны́х меток сохраняются и возвращаются как null")
    void insert_withNullTimestamps_roundTrip() throws SQLException {
        SearchSession session = new SearchSessionBuilder()
                .withStartedAt(null)
                .withFinishedAt(null)
                .withFilesCount(0)
                .withStatus("RUNNING")
                .withSearchResults(Collections.emptyList())
                .build();

        int id = dao.insert(session, anchorConnection);
        SearchSession loaded = dao.findById(id, anchorConnection);

        assertNotNull(loaded);
        assertAll(
                () -> assertNull(loaded.getStartedAt(),            "startedAt должен быть null"),
                () -> assertNull(loaded.getFinishedAt(),           "finishedAt должен быть null"),
                () -> assertNull(loaded.getFastSearchStart(),      "fastSearchStart должен быть null"),
                () -> assertNull(loaded.getFastSearchEnd(),        "fastSearchEnd должен быть null"),
                () -> assertNull(loaded.getBackgroundSearchStart(),"bgSearchStart должен быть null"),
                () -> assertNull(loaded.getBackgroundSearchEnd(),  "bgSearchEnd должен быть null")
        );
    }
}