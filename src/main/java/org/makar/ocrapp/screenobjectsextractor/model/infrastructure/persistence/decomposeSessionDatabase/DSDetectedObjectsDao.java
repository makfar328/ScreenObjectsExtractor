package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DSDetectedObjectsDao extends BaseDAO
        implements IDetectedObjectsDao {

    public DSDetectedObjectsDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, DSDetectedObjectsDao.class);
    }

    // ─── Override: IInitializable ────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String tableSql = """
            CREATE TABLE IF NOT EXISTS ds_detected_objects (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id     INTEGER NOT NULL,
                class_name   TEXT    NOT NULL,
                probability  REAL    NOT NULL,
                FOREIGN KEY (entry_id) REFERENCES ds_file_entry(id) ON DELETE CASCADE
            );
            """;
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_dsdo_entry_id   ON ds_detected_objects(entry_id);",
                "CREATE INDEX IF NOT EXISTS idx_dsdo_class_name ON ds_detected_objects(class_name);",
                "CREATE INDEX IF NOT EXISTS idx_dsdo_prob       ON ds_detected_objects(probability);"
        };
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(tableSql);
            for (String idx : indexes) stmt.execute(idx);
            logger.info("Table 'ds_detected_objects' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize ds_detected_objects", e);
            throw new RuntimeException(e);
        }
    }

    // ─── Override: IDetectedObjectsDao ───────────────────────────────────────

    @Override
    public void insertBatch(long foreignKeyId, List<OCRAppDetectedObject> objects, Connection conn)
            throws SQLException {
        if (objects == null || objects.isEmpty()) return;
        String sql = "INSERT INTO ds_detected_objects (entry_id, class_name, probability) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OCRAppDetectedObject obj : objects) {
                ps.setLong  (1, foreignKeyId);
                ps.setString(2, obj.getClassName());
                ps.setDouble(3, obj.getProbability());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void deleteByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM ds_detected_objects WHERE entry_id = ?")) {
            ps.setLong(1, foreignKeyId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<OCRAppDetectedObject> findByForeignKeyId(long foreignKeyId, Connection conn)
            throws SQLException {
        List<OCRAppDetectedObject> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT class_name, probability FROM ds_detected_objects WHERE entry_id = ?")) {
            ps.setLong(1, foreignKeyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new OCRAppDetectedObject(
                            rs.getString("class_name"),
                            rs.getDouble("probability")));
                }
            }
        }
        return result;
    }

    @Override
    public Map<Long, List<OCRAppDetectedObject>> findByForeignKeyIds(List<Long> foreignKeyIds,
                                                                     Connection conn)
            throws SQLException {
        Map<Long, List<OCRAppDetectedObject>> map = new LinkedHashMap<>();
        if (foreignKeyIds.isEmpty()) return map;

        String placeholders = String.join(",", Collections.nCopies(foreignKeyIds.size(), "?"));
        String sql = "SELECT entry_id, class_name, probability FROM ds_detected_objects " +
                "WHERE entry_id IN (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) ps.setLong(i + 1, foreignKeyIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long fid = rs.getLong("entry_id");
                    map.computeIfAbsent(fid, k -> new ArrayList<>())
                            .add(new OCRAppDetectedObject(
                                    rs.getString("class_name"),
                                    rs.getDouble("probability")));
                }
            }
        }
        return map;
    }

    // Standalone-обёртки

    public List<OCRAppDetectedObject> findByForeignKeyId(long foreignKeyId) throws SQLException {
        try (Connection conn = getConnection()) {
            return findByForeignKeyId(foreignKeyId, conn);
        }
    }

    public Map<Long, List<OCRAppDetectedObject>> findByForeignKeyIds(List<Long> foreignKeyIds)
            throws SQLException {
        try (Connection conn = getConnection()) {
            return findByForeignKeyIds(foreignKeyIds, conn);
        }
    }

}
