package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * DAO для таблицы search_sessions.
 *
 * Управление соединениями:
 *   - initializeTable(), insert()        — открывают собственное соединение
 *                                          (DDL и «точка входа» репозитория)
 *   - findById(), findAll(),
 *     deleteSearchSessionScore(), update() — используют Connection, переданный
 *                                            репозиторием; в транзакцию не лезут
 */
public class SearchSessionDao extends BaseDAO implements ISearchSessionDao {

    public SearchSessionDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SearchSessionDao.class);
    }

    // ── DDL ──────────────────────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS search_sessions (" +
                "id                      INTEGER PRIMARY KEY AUTOINCREMENT," +
                "started_at              TEXT," +
                "finished_at             TEXT," +
                "files_count             INTEGER," +
                "status                  TEXT," +
                "error_message           TEXT," +
                "fast_search_start       TEXT," +
                "fast_search_end         TEXT," +
                "background_search_start TEXT," +
                "background_search_end   TEXT" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'search_sessions' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing 'search_sessions' table", e);
            throw new RuntimeException("Failed to initialize 'search_sessions' table", e);
        }
    }

    // ── insert ────────────────────────────────────────────────────────

    @Override
    public int insert(SearchSession session, Connection conn) {
        String sql = "INSERT INTO search_sessions " +
                "(started_at, finished_at, files_count, status, error_message, " +
                " fast_search_start, fast_search_end, " +
                " background_search_start, background_search_end) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSessionFields(pstmt, session);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("insert: no rows affected.");
            }
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("insert: no generated key obtained.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error inserting session", e);
            throw new RuntimeException("Failed to insert session", e);
        }
    }

    // ── update: использует conn репозитория ──────────────────────────────

    @Override
    public void update(SearchSession session, Connection conn) throws SQLException {
        String sql = "UPDATE search_sessions SET " +
                "finished_at = ?, files_count = ?, status = ?, error_message = ?, " +
                "fast_search_end = ?, background_search_end = ? " +
                "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, session.getFinishedAt() != null
                    ? session.getFinishedAt().toString() : null);
            pstmt.setInt   (2, session.getFilesCount());
            pstmt.setString(3, session.getStatus());
            pstmt.setString(4, session.getErrorMessage());
            pstmt.setString(5, session.getFastSearchEnd() != null
                    ? session.getFastSearchEnd().toString() : null);
            pstmt.setString(6, session.getBackgroundSearchEnd() != null
                    ? session.getBackgroundSearchEnd().toString() : null);
            pstmt.setLong  (7, session.getSessionId());

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("update: session not found, id=" + session.getSessionId());
            }
        }
        // SQLException пробрасывается наверх — репозиторий делает rollback
    }

    // ── findById: использует conn репозитория ────────────────────────────

    @Override
    public SearchSession findById(long id, Connection conn) throws SQLException {
        String sql = "SELECT * FROM search_sessions WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // ── findAll: использует conn репозитория ─────────────────────────────

    @Override
    public List<SearchSession> findAll(Connection conn) throws SQLException {
        List<SearchSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM search_sessions ORDER BY id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                sessions.add(mapRow(rs));
            }
        }
        return sessions;
    }

    // ── delete: использует conn репозитория ──────────────────────────────

    @Override
    public void deleteSearchSessionScore(long sessionId, Connection conn) throws SQLException {
        String sql = "DELETE FROM search_sessions WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                logger.warning("deleteSearchSessionScore: session not found, id=" + sessionId);
            } else {
                logger.info("deleteSearchSessionScore: deleted session id=" + sessionId);
            }
        }
    }

    @Override
    public int deleteAllSessions(Connection conn) throws SQLException {
        String sql = "DELETE FROM search_sessions";
        int affected = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            affected = pstmt.executeUpdate();
            if (affected == 0) {
                logger.warning("deleteSearchSessionScore: no sessions not found");
            } else {
                logger.info("deleteSearchSessionScore: deleted sessions " + affected);
            }
        }
        return affected;
    }

    // ── Вспомогательные ──────────────────────────────────────────────────

    /**
     * Единственное место, где колонки таблицы отображаются на поля SearchSession.
     * findById и findAll используют один и тот же маппинг.
     */
    private SearchSession mapRow(ResultSet rs) throws SQLException {
        SearchSession session = new SearchSession(
                rs.getLong("id"),
                parseDateTime(rs.getString("started_at")),
                parseDateTime(rs.getString("finished_at")),
                rs.getInt("files_count"),
                null,   // criteria      — заполняет репозиторий
                null,   // searchResults — заполняет репозиторий
                rs.getString("status"),
                rs.getString("error_message"),
                parseDateTime(rs.getString("fast_search_start")),
                parseDateTime(rs.getString("background_search_start"))
        );
        session.setFastSearchEnd(parseDateTime(rs.getString("fast_search_end")));
        session.setBackgroundSearchEnd(parseDateTime(rs.getString("background_search_end")));
        return session;
    }

    /**
     * Привязывает поля сессии к PreparedStatement для INSERT.
     * Выделено в метод — если добавятся новые колонки, меняется в одном месте.
     */
    private void bindSessionFields(PreparedStatement pstmt, SearchSession s)
            throws SQLException {
        pstmt.setString(1, s.getStartedAt()           != null ? s.getStartedAt().toString()           : null);
        pstmt.setString(2, s.getFinishedAt()           != null ? s.getFinishedAt().toString()           : null);
        pstmt.setInt   (3, s.getFilesCount());
        pstmt.setString(4, s.getStatus());
        pstmt.setString(5, s.getErrorMessage());
        pstmt.setString(6, s.getFastSearchStart()      != null ? s.getFastSearchStart().toString()      : null);
        pstmt.setString(7, s.getFastSearchEnd()        != null ? s.getFastSearchEnd().toString()        : null);
        pstmt.setString(8, s.getBackgroundSearchStart() != null ? s.getBackgroundSearchStart().toString() : null);
        pstmt.setString(9, s.getBackgroundSearchEnd()  != null ? s.getBackgroundSearchEnd().toString()  : null);
    }

    /** Nullable LocalDateTime из строки ISO-8601. */
    private LocalDateTime parseDateTime(String value) {
        return value != null ? LocalDateTime.parse(value) : null;
    }

    /**
     * @deprecated Небезопасен в многопоточной среде — id последней строки,
     *             а не конкретной транзакции. Используй возвращаемое значение
     *             {@link #insert(SearchSession,Connection)} вместо этого метода.
     */
    @Deprecated
    public long getLastId() throws SQLException {
        String sql = "SELECT id FROM search_sessions ORDER BY id DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}