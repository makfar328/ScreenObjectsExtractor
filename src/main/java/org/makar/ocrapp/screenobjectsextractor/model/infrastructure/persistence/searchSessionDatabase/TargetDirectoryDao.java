package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class TargetDirectoryDao extends BaseDAO
        implements ITargetDirectoryDao {

    public TargetDirectoryDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, TargetDirectoryDao.class);
    }

    @Override
    public void initializeTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS target_directories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "search_criteria_id INTEGER NOT NULL," +
                    "root_directory TEXT NOT NULL," +
                    "depth INTEGER NOT NULL," +
                    "FOREIGN KEY (search_criteria_id) REFERENCES search_criteria(id) ON DELETE CASCADE" +
                    ")";
            stmt.execute(sql);
            logger.info("Table 'target_directories' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing 'target_directories' table", e);
            throw new RuntimeException("Failed to initialize 'target_directories' table", e);
        }
    }

    @Override
    public void insert(long searchCriteriaId, SearchDirectoryConfig config, Connection conn)
            throws SQLException {
        String sql = "INSERT INTO target_directories (search_criteria_id, root_directory, depth) " +
                "VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, searchCriteriaId);
            pstmt.setString(2, config.getDirectory().toString());
            pstmt.setInt(3, config.getSearchDepth());
            pstmt.executeUpdate();
        }
        // SQLException пробрасывается наверх — репозиторий сделает rollback
    }

    @Override
    public List<SearchDirectoryConfig> findByCriteriaId(long searchCriteriaId, Connection conn)
            throws SQLException {
        List<SearchDirectoryConfig> result = new ArrayList<>();
        String sql = "SELECT root_directory, depth FROM target_directories " +
                "WHERE search_criteria_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, searchCriteriaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new SearchDirectoryConfig(
                            Path.of(rs.getString("root_directory")),
                            rs.getInt("depth")
                    ));
                }
            }
        }
        return result;
    }
}