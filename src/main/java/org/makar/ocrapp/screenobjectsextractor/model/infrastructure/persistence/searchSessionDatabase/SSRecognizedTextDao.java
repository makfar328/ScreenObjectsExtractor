package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * DAO для таблицы recognized_text_content в контексте поисковых сессий.
 *
 * Управление соединением и транзакциями остаётся на стороне SearchSessionRepository —
 * все методы принимают Connection извне и НЕ открывают/закрывают его сами.
 * Исключение: initializeTable() — вызывается только при старте, до любых транзакций.
 */
public class SSRecognizedTextDao extends BaseDAO
        implements IRecognizedTextDao {

    public SSRecognizedTextDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SSRecognizedTextDao.class);
    }

    // ── Инициализация схемы ────────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS recognized_text_content (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "search_session_results_id INTEGER NOT NULL," +
                "text_content TEXT," +
                "x INTEGER," +
                "y INTEGER," +
                "width INTEGER," +
                "height INTEGER," +
                "FOREIGN KEY (search_session_results_id) " +
                "    REFERENCES search_session_results(id) ON DELETE CASCADE" +
                ")";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'recognized_text_content' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing 'recognized_text_content' table", e);
            throw new RuntimeException("Failed to initialize 'recognized_text_content' table", e);
        }
    }

    // ── IRecognizedTextDao — методы принимают Connection извне ────────────

    /**
     * Пакетная вставка текстовых объектов для результата сессии.
     * Использует переданный Connection — вызывающий управляет транзакцией.
     */
    @Override
    public void insertBatch(long foreignKeyId, List<TextObject> texts, Connection conn)
            throws SQLException {
        if (texts == null || texts.isEmpty()) return;

        String sql = "INSERT INTO recognized_text_content " +
                "(search_session_results_id, text_content, x, y, width, height) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (TextObject text : texts) {
                pstmt.setLong(1, foreignKeyId);
                pstmt.setString(2, text.getText());
                pstmt.setInt(3, text.getX());
                pstmt.setInt(4, text.getY());
                pstmt.setInt(5, text.getWidth());
                pstmt.setInt(6, text.getHeight());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Удаляет весь распознанный текст для указанного результата сессии.
     * Вызывается перед повторной записью (upsert-паттерн: delete + insertBatch).
     */
    @Override
    public void deleteByForeignKeyId(long foreignKeyId, Connection conn)
            throws SQLException {
        String sql = "DELETE FROM recognized_text_content WHERE search_session_results_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, foreignKeyId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Загружает все текстовые объекты для одного результата сессии.
     */
    @Override
    public List<TextObject> findByForeignKeyId(long foreignKeyId, Connection conn)
            throws SQLException {
        List<TextObject> result = new ArrayList<>();
        String sql = "SELECT text_content, x, y, width, height " +
                "FROM recognized_text_content " +
                "WHERE search_session_results_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, foreignKeyId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new TextObject(
                            rs.getString("text_content"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("width"),
                            rs.getInt("height")
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Пакетная загрузка текстовых объектов для нескольких результатов сессии за один запрос.
     * Избегает N+1 при загрузке списка сессий.
     *
     * @return Map: foreignKeyId → список TextObject (гарантированно не null для каждого ключа)
     */
    @Override
    public Map<Long, List<TextObject>> findByForeignKeyIds(List<Long> foreignKeyIds, Connection conn)
            throws SQLException {
        Map<Long, List<TextObject>> resultMap = new HashMap<>();
        if (foreignKeyIds == null || foreignKeyIds.isEmpty()) return resultMap;

        // Инициализируем пустыми списками — результат предсказуем даже для записей без текста
        for (Long id : foreignKeyIds) {
            resultMap.put(id, new ArrayList<>());
        }

        // IN-clause: (?, ?, ...)
        String placeholders = String.join(", ", Collections.nCopies(foreignKeyIds.size(), "?"));
        String sql = "SELECT search_session_results_id, text_content, x, y, width, height " +
                "FROM recognized_text_content " +
                "WHERE search_session_results_id IN (" + placeholders + ")";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) {
                pstmt.setLong(i + 1, foreignKeyIds.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long fkId = rs.getLong("search_session_results_id");
                    resultMap.get(fkId).add(new TextObject(
                            rs.getString("text_content"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("width"),
                            rs.getInt("height")
                    ));
                }
            }
        }
        return resultMap;
    }
}