package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSessionBuilder;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты SearchSessionRepository.
 *
 * Каждый тест поднимает изолированную in-memory базу с полной семью-таблиной
 * схемой домена searchSessionDatabase. anchorConnection держит базу живой.
 * Тестируется оркестрация транзакций репозитория и корректность сборки
 * агрегата SearchSession из семи DAO.
 */
class SearchSessionRepositoryTest {

    private SQLiteConnectionManager connectionManager;
    private SearchSessionRepository  repository;

    private SearchSessionDao         sessionDao;
    private SearchCriteriaDao        criteriaDao;
    private TargetDirectoryDao       targetDirDao;
    private SelectedObjectClassDao selectedClassDao;
    private SearchSessionResultsDao  resultsDao;
    private SSDetectedObjectsDao     detectedObjectsDao;
    private SSRecognizedTextDao      recognizedTextDao;

    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_ssr_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();

        sessionDao         = new SearchSessionDao(connectionManager);
        criteriaDao        = new SearchCriteriaDao(connectionManager);
        targetDirDao       = new TargetDirectoryDao(connectionManager);
        selectedClassDao   = new SelectedObjectClassDao(connectionManager);
        resultsDao         = new SearchSessionResultsDao(connectionManager);
        detectedObjectsDao = new SSDetectedObjectsDao(connectionManager);
        recognizedTextDao  = new SSRecognizedTextDao(connectionManager);

        // DDL: создаём все таблицы в правильном порядке (родитель → ребёнок)
        sessionDao.initializeTable();
        criteriaDao.initializeTable();
        targetDirDao.initializeTable();
        selectedClassDao.initializeTable();
        resultsDao.initializeTable();
        detectedObjectsDao.initializeTable();
        recognizedTextDao.initializeTable();

        repository = new SearchSessionRepository(
                connectionManager,
                sessionDao,
                criteriaDao,
                targetDirDao,
                selectedClassDao,
                resultsDao,
                detectedObjectsDao,
                recognizedTextDao
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    private static String pp(String posixPath) {
        return Paths.get(posixPath).toString();
    }

    private SearchCriteria buildCriteria() {
        return new SearchCriteriaBuilder()
                .withKeywords(List.of("invoice", "report"))
                .withFileTypes(List.of("png", "jpg"))
                .withEntries(List.of(new SelectedObjectClass("cat", 0.8)))
                .withTargetDirectories(
                        List.of(new SearchDirectoryConfig(Paths.get(pp("/images")))))
                .build();
    }

    private FileMetadata buildFile(String path) {
        return new FileMetadata(
                Paths.get(path),
                Paths.get(path).getFileName().toString(),
                "png",
                2048L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private FileMetadata buildFileWithChildren(String path,
                                               List<OCRAppDetectedObject> objects,
                                               List<TextObject> texts) {
        return new FileMetadata(
                Paths.get(path),
                Paths.get(path).getFileName().toString(),
                "png",
                1024L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                texts,
                objects
        );
    }

    private SearchSession buildSession(SearchCriteria criteria,
                                       List<FileMetadata> results) {
        return new SearchSessionBuilder()
                .withStartedAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                .withFinishedAt(LocalDateTime.of(2025, 6, 1, 10, 30))
                .withFilesCount(results.size())
                .withStatus("DONE")
                .withCriteria(criteria)
                .withSearchResults(results)
                .build();
    }

    // ── Тесты saveSession() ─────────────────────────────────────────────────

    @Test
    @DisplayName("saveSession(): возвращает положительный id")
    void saveSession_returnsPositiveId() {
        long id = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("saveSession(): сохранённая сессия доступна через getSessionById()")
    void saveSession_sessionRetrievableById() {
        long id = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));

        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded, "getSessionById() не должен вернуть null после saveSession()");
        assertEquals(id, loaded.getSessionId());
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет критерии поиска с ключевыми словами")
    void saveSession_criteriaPersistedWithKeywords() {
        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(List.of("alpha", "beta"))
                .withFileTypes(Collections.emptyList())
                .withEntries(Collections.emptyList())
                .withTargetDirectories(Collections.emptyList())
                .build();

        long id = repository.saveSession(buildSession(criteria, Collections.emptyList()));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getCriteria());
        assertAll(
                () -> assertTrue(loaded.getCriteria().getKeywords().contains("alpha")),
                () -> assertTrue(loaded.getCriteria().getKeywords().contains("beta"))
        );
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет целевые директории критериев")
    void saveSession_targetDirectoriesPersisted() {
        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(Collections.emptyList())
                .withFileTypes(Collections.emptyList())
                .withEntries(Collections.emptyList())
                .withTargetDirectories(List.of(
                        new SearchDirectoryConfig(Paths.get(pp("/docs"))),
                        new SearchDirectoryConfig(Paths.get(pp("/images")))
                ))
                .build();

        long id = repository.saveSession(buildSession(criteria, Collections.emptyList()));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getCriteria());
        assertEquals(2, loaded.getCriteria().getTargetDirectories().size());
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет выбранные классы объектов")
    void saveSession_selectedObjectClassesPersisted() {
        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(Collections.emptyList())
                .withFileTypes(Collections.emptyList())
                .withEntries(List.of(
                        new SelectedObjectClass("cat",  0.9),
                        new SelectedObjectClass("dog",  0.7)
                ))
                .withTargetDirectories(Collections.emptyList())
                .build();

        long id = repository.saveSession(buildSession(criteria, Collections.emptyList()));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getCriteria());
        assertEquals(2, loaded.getCriteria().getEntries().size());
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет файлы-результаты поиска")
    void saveSession_searchResultsPersisted() {
        List<FileMetadata> files = List.of(
                buildFile(pp("/images/photo1.png")),
                buildFile(pp("/images/photo2.png"))
        );

        long id = repository.saveSession(buildSession(buildCriteria(), files));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getSearchResults());
        assertEquals(2, loaded.getSearchResults().size());
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет объекты детекции для файлов результатов")
    void saveSession_detectedObjectsInResultsPersisted() {
        List<FileMetadata> files = List.of(
                buildFileWithChildren(
                        pp("/images/cats.png"),
                        List.of(new OCRAppDetectedObject("cat", 0.97)),
                        Collections.emptyList()
                )
        );

        long id = repository.saveSession(buildSession(buildCriteria(), files));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getSearchResults());
        assertEquals(1, loaded.getSearchResults().size());
        assertEquals(1, loaded.getSearchResults().get(0).getDetectedObjects().size());
        assertEquals("cat",
                loaded.getSearchResults().get(0).getDetectedObjects().get(0).getClassName());
    }

    @Test
    @DisplayName("saveSession(): атомарно сохраняет OCR-тексты для файлов результатов")
    void saveSession_recognizedTextInResultsPersisted() {
        List<FileMetadata> files = List.of(
                buildFileWithChildren(
                        pp("/images/doc.png"),
                        Collections.emptyList(),
                        List.of(new TextObject("Invoice #100", 0, 0, 300, 25))
                )
        );

        long id = repository.saveSession(buildSession(buildCriteria(), files));
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded.getSearchResults());
        assertEquals(1, loaded.getSearchResults().get(0).getRecognizedTextContent().size());
        assertEquals("Invoice #100",
                loaded.getSearchResults().get(0).getRecognizedTextContent().get(0).getText());
    }

    // ── Тесты getSessionById() ──────────────────────────────────────────────

    @Test
    @DisplayName("getSessionById(): несуществующий id возвращает null")
    void getSessionById_notFound_returnsNull() {
        SearchSession result = repository.getSessionById(99999L);
        assertNull(result);
    }

    @Test
    @DisplayName("getSessionById(): скалярные поля сессии сохраняются без искажений (round-trip)")
    void getSessionById_preservesScalarFields() {
        SearchSession original = new SearchSessionBuilder()
                .withStartedAt(LocalDateTime.of(2025, 4, 10, 8, 0))
                .withFinishedAt(LocalDateTime.of(2025, 4, 10, 8, 45))
                .withFilesCount(7)
                .withStatus("DONE")
                .withErrorMessage("test error")
                .withCriteria(buildCriteria())
                .withSearchResults(Collections.emptyList())
                .build();

        long id = repository.saveSession(original);
        SearchSession loaded = repository.getSessionById(id);

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(id,                                      loaded.getSessionId(),   "sessionId"),
                () -> assertEquals(LocalDateTime.of(2025, 4, 10, 8, 0),    loaded.getStartedAt(),   "startedAt"),
                () -> assertEquals(LocalDateTime.of(2025, 4, 10, 8, 45),   loaded.getFinishedAt(),  "finishedAt"),
                () -> assertEquals(7,                                       loaded.getFilesCount(),  "filesCount"),
                () -> assertEquals("DONE",                                  loaded.getStatus(),      "status"),
                () -> assertEquals("test error",                            loaded.getErrorMessage(),"errorMessage")
        );
    }

    // ── Тесты getAllSessionsSummary() ────────────────────────────────────────

    @Test
    @DisplayName("getAllSessionsSummary(): пустая БД возвращает пустой список")
    void getAllSessionsSummary_emptyDb_returnsEmptyList() {
        List<SearchSession> result = repository.getAllSessionsSummary();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllSessionsSummary(): возвращает все сохранённые сессии")
    void getAllSessionsSummary_multipleSessions_returnsAll() {
        repository.saveSession(buildSession(buildCriteria(), Collections.emptyList()));
        repository.saveSession(buildSession(buildCriteria(), Collections.emptyList()));
        repository.saveSession(buildSession(buildCriteria(), Collections.emptyList()));

        List<SearchSession> result = repository.getAllSessionsSummary();
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("getAllSessionsSummary(): criteria и searchResults null (краткая форма)")
    void getAllSessionsSummary_doesNotLoadCriteriaAndResults() {
        repository.saveSession(buildSession(buildCriteria(),
                List.of(buildFile(pp("/images/x.png")))));

        List<SearchSession> result = repository.getAllSessionsSummary();

        assertFalse(result.isEmpty());
        SearchSession summary = result.get(0);
        assertAll(
                () -> assertNull(summary.getCriteria(),      "criteria должны быть null в summary"),
                () -> assertNull(summary.getSearchResults(), "searchResults должны быть null в summary")
        );
    }

    // ── Тесты getAllFullSessions() ───────────────────────────────────────────

    @Test
    @DisplayName("getAllFullSessions(): загружает критерии и результаты для каждой сессии")
    void getAllFullSessions_loadsCriteriaAndResults() {
        repository.saveSession(buildSession(buildCriteria(),
                List.of(buildFile(pp("/images/full.png")))));

        List<SearchSession> result = repository.getAllFullSessions();

        assertFalse(result.isEmpty());
        SearchSession full = result.get(0);
        assertAll(
                () -> assertNotNull(full.getCriteria(),      "criteria не должны быть null в full"),
                () -> assertNotNull(full.getSearchResults(), "searchResults не должны быть null в full"),
                () -> assertEquals(1, full.getSearchResults().size(), "Должен быть один файл результата")
        );
    }

    // ── Тесты deleteSession() ───────────────────────────────────────────────

    @Test
    @DisplayName("deleteSession(): сессия недоступна через getSessionById() после удаления")
    void deleteSession_existingSession_removesIt() {
        long id = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));

        assertNotNull(repository.getSessionById(id), "Сессия должна существовать до удаления");

        repository.deleteSession(id);

        assertNull(repository.getSessionById(id),
                "После deleteSession() getSessionById() должен вернуть null");
    }

    @Test
    @DisplayName("deleteSession(): вызов с несуществующим id не бросает исключений")
    void deleteSession_nonExistentId_noException() {
        assertDoesNotThrow(() -> repository.deleteSession(99999L));
    }

    @Test
    @DisplayName("deleteSession(): удаление не влияет на другие сессии")
    void deleteSession_doesNotAffectOtherSessions() {
        long id1 = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));
        long id2 = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));

        repository.deleteSession(id1);

        assertNull(repository.getSessionById(id1),  "Удалённая сессия недоступна");
        assertNotNull(repository.getSessionById(id2), "Соседняя сессия должна остаться");
    }

    @Test
    @DisplayName("deleteSession(): повторное сохранение после удаления возвращает новый id")
    void deleteSession_thenSaveAgain_newIdGenerated() {
        long id1 = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));
        repository.deleteSession(id1);

        long id2 = repository.saveSession(
                buildSession(buildCriteria(), Collections.emptyList()));

        assertNotEquals(id1, id2,
                "После удаления и повторного сохранения id должен быть новым");
        assertNotNull(repository.getSessionById(id2));
    }
}