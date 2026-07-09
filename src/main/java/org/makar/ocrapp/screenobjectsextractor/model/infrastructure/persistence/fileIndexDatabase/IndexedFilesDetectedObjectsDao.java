package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class IndexedFilesDetectedObjectsDao extends BaseDAO
        implements IDetectedObjectsDao {

    public IndexedFilesDetectedObjectsDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, IndexedFilesDetectedObjectsDao.class);
    }

    // ─── Override: IInitializable ────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String tableSql = """
                CREATE TABLE IF NOT EXISTS detected_file_objects (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id      INTEGER NOT NULL,
                    class_name   TEXT    NOT NULL,
                    probability  REAL    NOT NULL,
                    FOREIGN KEY (file_id) REFERENCES indexed_files(id) ON DELETE CASCADE
                );
                """;
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_dfo_file_id     ON detected_file_objects(file_id);",
                "CREATE INDEX IF NOT EXISTS idx_dfo_class_name  ON detected_file_objects(class_name);",
                "CREATE INDEX IF NOT EXISTS idx_dfo_probability ON detected_file_objects(probability);"
        };
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(tableSql);
            for (String idx : indexes) stmt.execute(idx);
            logger.info("Table 'detected_file_objects' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize detected_file_objects", e);
            throw new RuntimeException(e);
        }
    }

    // ─── Override: IDetectedObjectsDao ───────────────────────────────────────

    @Override
    public void insertBatch(long foreignKeyId, List<OCRAppDetectedObject> objects, Connection conn)
            throws SQLException {
        if (objects == null || objects.isEmpty()) return;
        String sql = "INSERT INTO detected_file_objects (file_id, class_name, probability) VALUES (?, ?, ?)";
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
                "DELETE FROM detected_file_objects WHERE file_id = ?")) {
            ps.setLong(1, foreignKeyId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<OCRAppDetectedObject> findByForeignKeyId(long foreignKeyId, Connection conn)
            throws SQLException {
        List<OCRAppDetectedObject> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT class_name, probability FROM detected_file_objects WHERE file_id = ?")) {
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
        String sql = "SELECT file_id, class_name, probability FROM detected_file_objects " +
                "WHERE file_id IN (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) ps.setLong(i + 1, foreignKeyIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long fid = rs.getLong("file_id");
                    map.computeIfAbsent(fid, k -> new ArrayList<>())
                            .add(new OCRAppDetectedObject(
                                    rs.getString("class_name"),
                                    rs.getDouble("probability")));
                }
            }
        }
        return map;
    }

    // ─── Standalone-обёртки (не из интерфейса — без @Override) ──────────────

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