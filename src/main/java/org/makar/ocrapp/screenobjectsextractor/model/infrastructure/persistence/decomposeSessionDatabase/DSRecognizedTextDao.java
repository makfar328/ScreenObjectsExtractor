package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DSRecognizedTextDao extends BaseDAO
        implements IRecognizedTextDao {

    public DSRecognizedTextDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, DSRecognizedTextDao.class);
    }

    @Override
    public void initializeTable() {
        String tableSql = """
                CREATE TABLE IF NOT EXISTS ds_recognized_text (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_id       INTEGER NOT NULL,
                    text_value    TEXT    NOT NULL,
                    region_x      INTEGER,
                    region_y      INTEGER,
                    region_width  INTEGER,
                    region_height INTEGER,
                    FOREIGN KEY (entry_id) REFERENCES ds_file_entry(id) ON DELETE CASCADE
                );
                """;
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_dsrt_entry_id ON ds_recognized_text(entry_id);",
                "CREATE INDEX IF NOT EXISTS idx_dsrt_text    ON ds_recognized_text(text_value);"
        };
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(tableSql);
            for (String idx : indexes) stmt.execute(idx);
            logger.info("Table 'ds_recognized_text' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize ds_recognized_text", e);
            throw new RuntimeException(e);
        }
    }

    // CRUD

    @Override  // ← добавлено
    public void insertBatch(long foreignKeyId, List<TextObject> texts, Connection conn)
            throws SQLException {
        if (texts == null || texts.isEmpty()) return;

        String sql = """
                INSERT INTO ds_recognized_text
                    (entry_id, text_value, region_x, region_y, region_width, region_height)
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
                "DELETE FROM ds_recognized_text WHERE entry_id = ?")) {
            ps.setLong(1, foreignKeyId);
            ps.executeUpdate();
        }
    }

    @Override  // ← добавлено
    public List<TextObject> findByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException {
        List<TextObject> result = new ArrayList<>();
        String sql = "SELECT text_value, region_x, region_y, region_width, region_height " +
                "FROM ds_recognized_text WHERE entry_id = ? ORDER BY id";
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
        String sql = "SELECT entry_id, text_value, region_x, region_y, region_width, region_height " +
                "FROM ds_recognized_text WHERE entry_id IN (" + placeholders + ") ORDER BY entry_id, id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) ps.setLong(i + 1, foreignKeyIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long fid = rs.getLong("entry_id");
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
