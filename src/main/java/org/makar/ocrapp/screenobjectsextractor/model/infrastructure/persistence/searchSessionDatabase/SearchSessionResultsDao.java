package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchSessionResults;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * DAO для таблицы search_session_results в контексте поисковых сессий.
 *
 * Управление соединением и транзакцией остаётся на стороне SearchSessionRepository.
 * Все методы принимают Connection снаружи и НЕ открывают/закрывают его самостоятельно.
 * Исключение: initializeTable() — вызывается только при старте SearchSessionDatabaseManager.
 */
public class SearchSessionResultsDao extends BaseDAO implements ISearchSessionResultsDao {

    public SearchSessionResultsDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SearchSessionResults.class);
    }

    // ── Инициализация схемы ───────────────────────────────────────────────

    @Override
    public void initializeTable() {
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS search_session_results (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "session_id INTEGER NOT NULL," +
                    "file_path TEXT NOT NULL," +
                    "file_name TEXT," +
                    "file_extension TEXT," +
                    "file_size INTEGER," +
                    "creation_date TEXT," +
                    "modification_date TEXT" +
                    ")";
            stmt.execute(sql);
            logger.info("Table 'search_session_results' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing 'search_session_results' table", e);
            throw new RuntimeException("Failed to initialize 'search_session_results' table", e);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * Вставляет строку результата в рамках переданной транзакции.
     * Connection открыт и управляется репозиторием — здесь НЕ закрываем.
     */
    @Override
    public int insert(long searchSessionId, FileMetadata result, Connection conn) throws SQLException {
        String sql = "INSERT INTO search_session_results " +
                "(session_id, file_path, file_name, file_extension, file_size, creation_date, modification_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, searchSessionId);
            pstmt.setString(2, result.getFilePath().toString());
            pstmt.setString(3, result.getFileName());
            pstmt.setString(4, result.getFileExtension());
            pstmt.setLong(5, result.getFileSize());
            pstmt.setString(6, result.getCreationDate() != null
                    ? result.getCreationDate().toString() : null);
            pstmt.setString(7, result.getModificationDate() != null
                    ? result.getModificationDate().toString() : null);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("insert: no rows affected for search_session_results.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
                throw new SQLException("insert: no generated key returned for search_session_results.");
            }
        }
        // SQLException пробрасывается наверх - репозиторий решает, откатить транзакцию или нет
    }

    /**
     * Читает все строки результатов для одной сессии.
     * Контентные поля (recognizedText, detectedObjects) не заполняются —
     * их достраивает SearchSessionRepository через отдельные DAO.
     */
    @Override
    public SearchSessionResults findBySessionId(long sessionId, Connection conn) throws SQLException {
        SearchSessionResults result = new SearchSessionResults(sessionId, new ArrayList<>());
        String sql = "SELECT * FROM search_session_results WHERE session_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.addToResults(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * Читает все результаты по всем сессиям, сгруппированные по session_id.
     */
    @Override
    public List<SearchSessionResults> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM search_session_results ORDER BY session_id DESC, id ASC";
        Map<Long, SearchSessionResults> sessionMap = new LinkedHashMap<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                long sessionId = rs.getLong("session_id");
                sessionMap
                        .computeIfAbsent(sessionId, id -> new SearchSessionResults(id, new ArrayList<>()))
                        .addToResults(mapRow(rs));
            }
        }
        return new ArrayList<>(sessionMap.values());
    }

    /**
     * Удаляет все строки результатов для указанной сессии.
     */
    @Override
    public void deleteBySessionId(long sessionId, Connection conn) throws SQLException {
        String sql = "DELETE FROM search_session_results WHERE session_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            int deleted = pstmt.executeUpdate();
            logger.fine("deleteBySessionId: удалено " + deleted + " строк для session_id=" + sessionId);
        }
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    /**
     * Маппинг строки ResultSet -> FileMetadata.
     * Контентные поля передаются как null — заполняются репозиторием.
     */
    private FileMetadata mapRow(ResultSet rs) throws SQLException {
        String creationDate     = rs.getString("creation_date");
        String modificationDate = rs.getString("modification_date");
        return new FileMetadata(
                rs.getLong("id"),
                Path.of(rs.getString("file_path")),
                rs.getString("file_name"),
                rs.getString("file_extension"),
                rs.getLong("file_size"),
                creationDate     != null ? LocalDateTime.parse(creationDate)     : null,
                modificationDate != null ? LocalDateTime.parse(modificationDate) : null,
                null, // recognizedText  - достраивает репозиторий
                null  // detectedObjects - достраивает репозиторий
        );
    }
}