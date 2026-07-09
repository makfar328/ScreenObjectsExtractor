package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.*;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Репозиторий поисковых сессий.
 *
 * Управляет транзакциями самостоятельно — получает Connection из SQLiteConnectionManager
 * и передаёт его в каждый DAO-вызов. DAO не открывают и не закрывают соединения.
 *
 * Поля объявлены через интерфейсы — конкретные реализации скрыты.
 */
public class SearchSessionRepository implements ISearchSessionRepository {

    private static final Logger LOGGER =
            Logger.getLogger(SearchSessionRepository.class.getName());

    private final SQLiteConnectionManager connectionManager; // ← нужен для открытия транзакций

    private final ISearchSessionDao          searchSessionDao;
    private final ISearchSessionResultsDao   sessionResultsDao;
    private final IRecognizedTextDao recognizedTextDao;
    private final IDetectedObjectsDao detectedObjectsDao;
    private final ISearchCriteriaDao         searchCriteriaDao;
    private final ISelectedObjectsClassDao   selectedObjectsClassDao;
    private final ITargetDirectoryDao        targetDirectoryDao;

    // ── Конструктор принимает интерфейсы, а не конкретные классы ──────────
    public SearchSessionRepository(
            SQLiteConnectionManager connectionManager,
            ISearchSessionDao          searchSessionDao,
            ISearchCriteriaDao          searchCriteriaDao,
            ITargetDirectoryDao         targetDirectoryDao,
            ISelectedObjectsClassDao   selectedObjectsClassDao,
            ISearchSessionResultsDao   sessionResultsDao,
            IDetectedObjectsDao      detectedObjectsDao,
            IRecognizedTextDao       recognizedTextDao
    ) {
        this.connectionManager      = connectionManager;
        this.searchSessionDao       = searchSessionDao;
        this.sessionResultsDao      = sessionResultsDao;
        this.recognizedTextDao      = recognizedTextDao;
        this.detectedObjectsDao     = detectedObjectsDao;
        this.searchCriteriaDao      = searchCriteriaDao;
        this.selectedObjectsClassDao = selectedObjectsClassDao;
        this.targetDirectoryDao     = targetDirectoryDao;
    }


    /**
     * Сохраняет новую сессию поиска со всеми вложенными данными атомарно.
     * При ошибке на любом шаге вся транзакция откатывается.
     *
     * @param session сессия для сохранения
     * @return сгенерированный id сессии
     */
    @Override
    public long saveSession(SearchSession session) {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Сохраняем сессию
                long sessionId = searchSessionDao.insert(session, conn);

                // 2. Сохраняем критерии поиска
                long criteriaId = searchCriteriaDao.insert(sessionId, session.getCriteria(), conn);

                // 3. Сохраняем выбранные классы объектов
                for (SelectedObjectClass cls : session.getCriteria().getEntries()) {
                    selectedObjectsClassDao.insert(criteriaId, cls, conn);
                }

                // 4. Сохраняем директории поиска
                for (SearchDirectoryConfig dir : session.getCriteria().getTargetDirectories()) {
                    targetDirectoryDao.insert(criteriaId, dir, conn);
                }

                // 5. Сохраняем результаты поиска (файлы)
                for (FileMetadata file : session.getSearchResults()) {
                    int fileId = sessionResultsDao.insert(sessionId, file, conn);

                    // 6. Сохраняем распознанный текст
                    if (file.getRecognizedTextContent() != null
                            && !file.getRecognizedTextContent().isEmpty()) {
                        recognizedTextDao.insertBatch(fileId, file.getRecognizedTextContent(), conn);
                    }

                    // 7. Сохраняем обнаруженные объекты
                    if (file.getDetectedObjects() != null
                            && !file.getDetectedObjects().isEmpty()) {
                        detectedObjectsDao.insertBatch(fileId, file.getDetectedObjects(), conn);
                    }
                }

                conn.commit();
                LOGGER.info("saveSession: сессия #" + sessionId + " сохранена успешно.");
                return sessionId;

            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "saveSession: транзакция откатана из-за ошибки.", e);
                throw new RuntimeException("Ошибка сохранения сессии", e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "saveSession: не удалось открыть соединение.", e);
            throw new RuntimeException("Ошибка соединения при сохранении сессии", e);
        }
    }


    /**
     * Возвращает все сессии с полными данными (критерии + результаты).
     * Операция тяжёлая — использовать только при необходимости полной выгрузки.
     */
    @Override
    public List<SearchSession> getAllFullSessions() {
        EnumSet<SearchSession.SessionField> fields = EnumSet.allOf(SearchSession.SessionField.class);
        return getSessionsWithFields(fields);
    }


    /**
     * Возвращает краткие данные о всех сессиях (без критериев и результатов).
     * Используется для отображения журнала.
     */
    @Override
    public List<SearchSession> getAllSessionsSummary() {
        EnumSet<SearchSession.SessionField> fields = EnumSet.of(
                SearchSession.SessionField.SESSION_ID,
                SearchSession.SessionField.STARTED_AT,
                SearchSession.SessionField.FINISHED_AT,
                SearchSession.SessionField.FILES_COUNT,
                SearchSession.SessionField.STATUS,
                SearchSession.SessionField.ERROR_MESSAGE
        );
        return getSessionsWithFields(fields);
    }


    /**
     * Загружает полную сессию по id (со всеми файлами, текстами и объектами).
     *
     * @param sessionId идентификатор сессии
     * @return полная {@link SearchSession} или null, если не найдена
     */
    @Override
    public SearchSession getSessionById(long sessionId) {
        try (Connection conn = connectionManager.getConnection()) {

            // 1. Основные поля сессии
            SearchSession session = searchSessionDao.findById(sessionId, conn);
            if (session == null) {
                LOGGER.warning("getSessionById: сессия #" + sessionId + " не найдена.");
                return null;
            }

            // 2. Критерии поиска
            SearchCriteria criteria = searchCriteriaDao.findBySessionId(sessionId, conn);
            if (criteria == null) {
                LOGGER.warning("getSessionById: критерии для сессии #" + sessionId + " не найдены.");
            } else {
                // 3. Классы объектов и директории — привязаны к criteriaId, не к sessionId
                List<SelectedObjectClass> selectedClasses =
                        selectedObjectsClassDao.findByCriteriaId(criteria.getId(), conn);
                List<SearchDirectoryConfig> targetDirs =
                        targetDirectoryDao.findByCriteriaId(criteria.getId(), conn);

                criteria = new SearchCriteriaBuilder()
                        .withId(criteria.getId()) // ← criteriaId, не sessionId!
                        .withFileNames(criteria.getFileNames())
                        .withKeywords(criteria.getKeywords())
                        .withEntries(selectedClasses)
                        .withGlobalMinProbability(criteria.getGlobalMinProbability())
                        .withMinDate(criteria.getMinDate())
                        .withMaxDate(criteria.getMaxDate())
                        .withTargetDirectories(targetDirs)
                        .withFileTypes(criteria.getFileTypes())
                        .build();
            }

            // 4. Результаты поиска (файлы)
            SearchSessionResults sessionResults =
                    sessionResultsDao.findBySessionId(sessionId, conn);
            for (FileMetadata file : sessionResults.getResults()) {
                long fileId = file.getId();
                file.setRecognizedTextContent(recognizedTextDao.findByForeignKeyId(fileId, conn));
                file.setDetectedObjects(detectedObjectsDao.findByForeignKeyId(fileId, conn));
            }

            // 5. Собираем агрегированный объект
            return new SearchSessionBuilder()
                    .withSessionId(sessionId)
                    .withStartedAt(session.getStartedAt())
                    .withFinishedAt(session.getFinishedAt())
                    .withFilesCount(session.getFilesCount())
                    .withCriteria(criteria)
                    .withSearchResults(sessionResults.getResults())
                    .withStatus(session.getStatus())
                    .withErrorMessage(session.getErrorMessage())
                    .withFastSearchStart(session.getFastSearchStart())
                    .withFastSearchEnd(session.getFastSearchEnd())
                    .withBackgroundSearchStart(session.getBackgroundSearchStart())
                    .withBackgroundSearchEnd(session.getBackgroundSearchEnd())
                    .build();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getSessionById: ошибка при загрузке сессии #" + sessionId, e);
            throw new RuntimeException("Ошибка загрузки сессии #" + sessionId, e);
        }
    }


    /**
     * Удаляет сессию и все связанные данные (через ON DELETE CASCADE в схеме БД).
     *
     * @param sessionId идентификатор сессии для удаления
     */
    @Override
    public void deleteSession(long sessionId) {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                searchSessionDao.deleteSearchSessionScore(sessionId, conn);
                // ON DELETE CASCADE автоматически удалит связанные записи
                // в search_criteria, search_session_results и т.д.
                conn.commit();
                LOGGER.info("deleteSession: сессия #" + sessionId + " удалена.");
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "deleteSession: откат транзакции для сессии #" + sessionId, e);
                throw new RuntimeException("Ошибка удаления сессии #" + sessionId, e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteSession: не удалось открыть соединение.", e);
            throw new RuntimeException("Ошибка соединения при удалении сессии", e);
        }
    }

    @Override
    public void deleteAll() {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            int affected = 0;
            try {
                affected = searchSessionDao.deleteAllSessions(conn);
                LOGGER.log(Level.INFO, "SearchSessions : из базы удалено " + affected + " строк.");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "deleteAll: откат транзакции", e);
                throw new RuntimeException("Ошибка удаления всех сессий", e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteAll: не удалось открыть соединение.", e);
            throw new RuntimeException("Ошибка соединения при удалении сессии", e);
        }
    }


    // ── Вспомогательный метод — конструктор запросов ──────────────────────

    /**
     * Загружает список сессий, заполняя только поля из {@code fields}.
     * Позволяет разделить лёгкий (summary) и тяжёлый (full) запрос.
     */
    private List<SearchSession> getSessionsWithFields(
            EnumSet<SearchSession.SessionField> fields) {

        try (Connection conn = connectionManager.getConnection()) {

            List<SearchSession> sessions = searchSessionDao.findAll(conn);
            List<SearchSession> result = new ArrayList<>();

            for (SearchSession session : sessions) {
                long   sessionId    = fields.contains(SearchSession.SessionField.SESSION_ID)    ? session.getSessionId()    : 0;
                LocalDateTime startedAt   = fields.contains(SearchSession.SessionField.STARTED_AT)   ? session.getStartedAt()    : null;
                LocalDateTime finishedAt  = fields.contains(SearchSession.SessionField.FINISHED_AT)  ? session.getFinishedAt()   : null;
                int    filesCount   = fields.contains(SearchSession.SessionField.FILES_COUNT)   ? session.getFilesCount()   : 0;
                String status       = fields.contains(SearchSession.SessionField.STATUS)        ? session.getStatus()       : null;
                String errorMessage = fields.contains(SearchSession.SessionField.ERROR_MESSAGE) ? session.getErrorMessage() : null;

                SearchCriteria      criteria      = null;
                List<FileMetadata>  searchResults = null;

                if (fields.contains(SearchSession.SessionField.CRITERIA)) {
                    criteria = searchCriteriaDao.findBySessionId(session.getSessionId(), conn);
                    if (criteria != null) {
                        List<SelectedObjectClass> selectedClasses =
                                selectedObjectsClassDao.findByCriteriaId(criteria.getId(), conn);
                        List<SearchDirectoryConfig> targetDirs =
                                targetDirectoryDao.findByCriteriaId(criteria.getId(), conn);
                        criteria = new SearchCriteriaBuilder()
                                .withId(criteria.getId()) // ← criteriaId, не sessionId!
                                .withFileNames(criteria.getFileNames())
                                .withKeywords(criteria.getKeywords())
                                .withEntries(selectedClasses)
                                .withGlobalMinProbability(criteria.getGlobalMinProbability())
                                .withMinDate(criteria.getMinDate())
                                .withMaxDate(criteria.getMaxDate())
                                .withTargetDirectories(targetDirs)
                                .withFileTypes(criteria.getFileTypes())
                                .build();
                    }
                }

                if (fields.contains(SearchSession.SessionField.SEARCH_RESULTS)) {
                    SearchSessionResults sessionResults =
                            sessionResultsDao.findBySessionId(session.getSessionId(), conn);
                    for (FileMetadata file : sessionResults.getResults()) {
                        file.setRecognizedTextContent(
                                recognizedTextDao.findByForeignKeyId(file.getId(), conn));
                        file.setDetectedObjects(
                                detectedObjectsDao.findByForeignKeyId(file.getId(), conn));
                    }
                    searchResults = sessionResults.getResults();
                }

                result.add(new SearchSessionBuilder()
                        .withSessionId(sessionId)
                        .withStartedAt(startedAt)
                        .withFinishedAt(finishedAt)
                        .withFilesCount(filesCount)
                        .withCriteria(criteria)
                        .withSearchResults(searchResults)
                        .withStatus(status)
                        .withErrorMessage(errorMessage)
                        .build());
            }

            return result;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getSessionsWithFields: ошибка при загрузке сессий.", e);
            throw new RuntimeException("Ошибка загрузки списка сессий", e);
        }
    }
}