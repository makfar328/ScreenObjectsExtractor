package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;

/**
 * DAO для таблицы search_criteria.
 *
 * Не управляет соединением — Connection передаётся извне (из SearchSessionRepository).
 * Это позволяет включать вставку критериев в общую транзакцию сессии.
 */
public class SearchCriteriaDao extends BaseDAO
        implements ISearchCriteriaDao {

    public SearchCriteriaDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SearchCriteriaDao.class); // ← только connectionManager, без Class<?>
    }

    // ── Инициализация схемы ───────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS search_criteria ("
                + "id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "session_id  INTEGER NOT NULL,"
                + "file_names  TEXT,"
                + "keywords    TEXT,"
                + "min_date    TEXT,"
                + "max_date    TEXT,"
                + "file_extensions TEXT,"
                + "FOREIGN KEY (session_id) REFERENCES search_sessions(id) ON DELETE CASCADE"
                + ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("SearchCriteriaDao: таблица 'search_criteria' инициализирована.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SearchCriteriaDao: ошибка инициализации таблицы 'search_criteria'", e);
            throw new RuntimeException("Failed to initialize 'search_criteria' table", e);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * Вставляет критерии в рамках переданного соединения (транзакции).
     *
     * @return сгенерированный id новой строки
     */
    @Override
    public long insert(long sessionId, SearchCriteria criteria, Connection conn) throws SQLException {
        String sql = "INSERT INTO search_criteria "
                + "(session_id, file_names, keywords, min_date, max_date, file_extensions) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        Gson gson = new Gson();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong  (1, sessionId);
            pstmt.setString(2, gson.toJson(criteria.getFileNames()));
            pstmt.setString(3, gson.toJson(criteria.getKeywords()));
            pstmt.setString(4, criteria.getMinDate() != null ? criteria.getMinDate().toString() : null);
            pstmt.setString(5, criteria.getMaxDate() != null ? criteria.getMaxDate().toString() : null);
            pstmt.setString(6, gson.toJson(criteria.getFileTypes()));

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("SearchCriteriaDao.insert: строка не была вставлена.");
            }
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                } else {
                    throw new SQLException("SearchCriteriaDao.insert: не удалось получить сгенерированный id.");
                }
            }
        }
        // SQLException пробрасывается наверх — Repository решает,
        // откатывать транзакцию или нет
    }


    @Override
    public SearchCriteria findBySessionId(long sessionId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM search_criteria WHERE session_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public void deleteBySessionId(long sessionId, Connection conn) throws SQLException {
        String sql = "DELETE FROM search_criteria WHERE session_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    // ── Маппинг ResultSet → SearchCriteria ────────────────────────────────

    /**
     * Выделен в отдельный метод — единственное место, где знаем,
     * как колонки таблицы отображаются на поля SearchCriteria.
     * findById и findBySessionId используют один и тот же маппинг.
     */
    private SearchCriteria mapRow(ResultSet rs) throws SQLException {
        Gson gson = new Gson();
        long id = rs.getLong("id");

        List<String> fileNames  = gson.fromJson(rs.getString("file_names"),
                new TypeToken<List<String>>(){}.getType());
        List<String> keywords   = gson.fromJson(rs.getString("keywords"),
                new TypeToken<List<String>>(){}.getType());
        List<String> fileTypes  = gson.fromJson(rs.getString("file_extensions"),
                new TypeToken<List<String>>(){}.getType());

        String minDateStr = rs.getString("min_date");
        String maxDateStr = rs.getString("max_date");
        LocalDate minDate = minDateStr != null ? LocalDate.parse(minDateStr) : null;
        LocalDate maxDate = maxDateStr != null ? LocalDate.parse(maxDateStr) : null;

        return new SearchCriteria(
                id,
                fileNames,
                keywords,
                null,   // targetDirectories — не хранятся в этой таблице
                0.0,    // similarityThreshold
                minDate,
                maxDate,
                null,   // другой опциональный параметр
                fileTypes
        );
    }
}