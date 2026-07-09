package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class IndexedFilesRecognizedTextDao extends BaseDAO
        implements IRecognizedTextDao {

    public IndexedFilesRecognizedTextDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, IndexedFilesRecognizedTextDao.class);
    }

    // ───────────── Override ──────────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String tableSql = """
                CREATE TABLE IF NOT EXISTS recognized_text_file (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id       INTEGER NOT NULL,
                    text_value    TEXT    NOT NULL,
                    region_x      INTEGER,
                    region_y      INTEGER,
                    region_width  INTEGER,
                    region_height INTEGER,
                    FOREIGN KEY (file_id) REFERENCES indexed_files(id) ON DELETE CASCADE
                );
                """;
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_rtf_file_id ON recognized_text_file(file_id);",
                "CREATE INDEX IF NOT EXISTS idx_rtf_text    ON recognized_text_file(text_value);"
        };
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(tableSql);
            for (String idx : indexes) stmt.execute(idx);
            logger.info("Table 'recognized_text_file' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize recognized_text_file", e);
            throw new RuntimeException(e);
        }
    }

    // ───────────── CRUD ──────────────────────────────────────────────────────

    @Override  // ← добавлено
    public void insertBatch(long foreignKeyId, List<TextObject> texts, Connection conn)
            throws SQLException {
        if (texts == null || texts.isEmpty()) return;

        String sql = """
                INSERT INTO recognized_text_file
                    (file_id, text_value, region_x, region_y, region_width, region_height)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (TextObject t : texts) {
                ps.setLong  (1, foreignKeyId);
                ps.setString(2, t.getText());
                ps.setInt   (3, t.getX());
                ps.setInt   (4, t.getY());
                ps.setInt   (5, t.getWidth());
                ps.setInt   (6, t.getHeight());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override  // ← добавлено
    public void deleteByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM recognized_text_file WHERE file_id = ?")) {
            ps.setLong(1, foreignKeyId);
            ps.executeUpdate();
        }
    }

    @Override  // ← добавлено
    public List<TextObject> findByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException {
        List<TextObject> result = new ArrayList<>();
        String sql = "SELECT text_value, region_x, region_y, region_width, region_height " +
                "FROM recognized_text_file WHERE file_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, foreignKeyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    @Override  // ← добавлено
    public Map<Long, List<TextObject>> findByForeignKeyIds(List<Long> foreignKeyIds, Connection conn)
            throws SQLException {
        Map<Long, List<TextObject>> map = new LinkedHashMap<>();
        if (foreignKeyIds.isEmpty()) return map;

        String placeholders = String.join(",", Collections.nCopies(foreignKeyIds.size(), "?"));
        String sql = "SELECT file_id, text_value, region_x, region_y, region_width, region_height " +
                "FROM recognized_text_file WHERE file_id IN (" + placeholders + ") ORDER BY file_id, id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) ps.setLong(i + 1, foreignKeyIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long fid = rs.getLong("file_id");
                    map.computeIfAbsent(fid, k -> new ArrayList<>()).add(mapRow(rs));
                }
            }
        }
        return map;
    }

    // ───────────── Приватные вспомогательные методы ──────────────────────────

    private static TextObject mapRow(ResultSet rs) throws SQLException {
        return new TextObject(
                rs.getString("text_value"),
                rs.getInt   ("region_x"),
                rs.getInt   ("region_y"),
                rs.getInt   ("region_width"),
                rs.getInt   ("region_height")
        );
    }
}