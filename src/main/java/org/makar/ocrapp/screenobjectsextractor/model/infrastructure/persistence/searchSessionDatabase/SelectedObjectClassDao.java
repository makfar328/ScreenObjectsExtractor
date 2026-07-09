package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.SelectedObjectClass;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Реализация {@link ISelectedObjectsClassDao} поверх SQLite.
 *
 * Реализует {@link IInitializable} — инициализация таблицы вызывается
 * из {@code SearchSessionDatabaseManager.initializeSchema()}, не из бизнес-кода.
 *
 * Методы бизнес-логики принимают {@link Connection} извне — это намеренно:
 * владелец соединения (обычно SearchSessionRepository) управляет транзакцией
 * и несёт ответственность за commit/rollback/close.
 */
public class SelectedObjectClassDao extends BaseDAO
        implements ISelectedObjectsClassDao, IInitializable {

    public SelectedObjectClassDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SelectedObjectClass.class);
    }

    // ── Инициализация схемы ──────────────────────────────────────────────
    // initializeTable управляет своим соединением самостоятельно:
    // это не часть бизнес-транзакции, а DDL-операция при старте приложения.

    @Override
    public void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS selected_objects_classes (" +
                "id                 INTEGER PRIMARY KEY AUTOINCREMENT," +
                "search_criteria_id INTEGER NOT NULL," +
                "class_name         TEXT," +
                "count              INTEGER," +
                "FOREIGN KEY (search_criteria_id) REFERENCES search_criteria(id) ON DELETE CASCADE" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'selected_objects_classes' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing 'selected_objects_classes' table", e);
            throw new RuntimeException("Failed to initialize 'selected_objects_classes' table", e);
        }
    }

    // ── Бизнес-операции (контракт ISelectedObjectsClassDao) ──────────────
    // Оба метода используют переданный conn, не открывают своё соединение.
    // try-with-resources охватывает только PreparedStatement / ResultSet.

    @Override
    public void insert(long searchCriteriaId,
                       SelectedObjectClass selectedObjectClass,
                       Connection conn) {
        String sql = "INSERT INTO selected_objects_classes " +
                "(search_criteria_id, class_name, count) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, searchCriteriaId);
            pstmt.setString(2, selectedObjectClass.getClassName());
            pstmt.setInt(3, selectedObjectClass.getCount());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error inserting into 'selected_objects_classes'", e);
            throw new RuntimeException("Error inserting into 'selected_objects_classes'", e);
        }
    }

    @Override
    public List<SelectedObjectClass> findByCriteriaId(long searchCriteriaId,
                                                      Connection conn) {
        List<SelectedObjectClass> result = new ArrayList<>();
        String sql = "SELECT class_name, count FROM selected_objects_classes " +
                "WHERE search_criteria_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, searchCriteriaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new SelectedObjectClass(
                            rs.getString("class_name"),
                            rs.getInt("count")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching from 'selected_objects_classes'", e);
            throw new RuntimeException("Error fetching from 'selected_objects_classes'", e);
        }
        return result;
    }
}