package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс выполняет роль инициализатора слоя данных. Он гарантирует, что все необходимые таблицы в базе данных
 * созданы (`dao.initializeTable()`), и собирает композитный объект `SearchSessionRepository`, объединяющий все DAO.
 */
public class SearchSessionDatabaseManager implements ISearchSessionDatabaseManager {

    private final SQLiteConnectionManager connectionManager;
    private static final Logger LOGGER =
            Logger.getLogger(SearchSessionDatabaseManager.class.getName());

    // Репозиторий — через интерфейс (публичный контракт)
    private final ISearchSessionRepository repository;

    private final SearchSessionDao          searchSessionDao;
    private final SearchCriteriaDao         searchCriteriaDao;
    private final TargetDirectoryDao        targetDirectoryDao;
    private final SelectedObjectClassDao selectedObjectClassDao;   // ← конкретный тип
    private final SearchSessionResultsDao   searchSessionResultsDao;
    private final SSDetectedObjectsDao detectedObjectsContentDao;
    private final SSRecognizedTextDao  recognizedTextContentDao;

    public SearchSessionDatabaseManager(SQLiteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.searchSessionDao          = new SearchSessionDao(connectionManager);
        this.searchCriteriaDao         = new SearchCriteriaDao(connectionManager);
        this.targetDirectoryDao        = new TargetDirectoryDao(connectionManager);
        this.selectedObjectClassDao    = new SelectedObjectClassDao(connectionManager);
        this.searchSessionResultsDao   = new SearchSessionResultsDao(connectionManager);
        this.detectedObjectsContentDao = new SSDetectedObjectsDao(connectionManager);
        this.recognizedTextContentDao  = new SSRecognizedTextDao(connectionManager);

        // Сборка репозитория — зависит от интерфейсов DAOs
        this.repository = new SearchSessionRepository(
                connectionManager,
                searchSessionDao,
                searchCriteriaDao,
                targetDirectoryDao,
                selectedObjectClassDao,
                searchSessionResultsDao,
                detectedObjectsContentDao,
                recognizedTextContentDao
        );
    }

    // ── IDatabaseManager ─────────────────────────────────────────────────

    @Override
    public void initializeSchema() {
        // Порядок важен: родительские таблицы первыми
        searchSessionDao.initializeTable();
        searchCriteriaDao.initializeTable();
        targetDirectoryDao.initializeTable();
        selectedObjectClassDao.initializeTable();
        searchSessionResultsDao.initializeTable();
        detectedObjectsContentDao.initializeTable();
        recognizedTextContentDao.initializeTable();
        LOGGER.info("SearchSessionDatabaseManager: схема инициализирована.");
    }

    @Override
    public boolean isSchemaReady() {
        // Быстрая проверка ключевых таблиц для fast-fail диагностики
        String checkSql =
                "SELECT name FROM sqlite_master " +
                        "WHERE type='table' AND name IN " +
                        "('search_sessions','search_criteria','selected_object_classes')";
        try (Connection conn = this.connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql);
             ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) count++;
            return count == 3;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "isSchemaReady: ошибка проверки схемы", e);
            return false;
        }
    }

    // ── ISearchSessionDatabaseManager ────────────────────────────────────

    @Override
    public ISearchSessionRepository getRepository() {
        return repository;
    }
}