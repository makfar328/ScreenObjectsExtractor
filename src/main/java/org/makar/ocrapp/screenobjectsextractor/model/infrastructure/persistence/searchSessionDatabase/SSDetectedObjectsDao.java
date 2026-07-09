
package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * DAO для таблицы detected_objects_content (схема searchSession).
 *
 * Управление транзакцией — НА СТОРОНЕ РЕПОЗИТОРИЯ.
 * Все методы принимают Connection снаружи и НЕ делают commit/rollback.
 * Это зеркало IndexedFilesDetectedObjectsDao, но под FK search_session_results_id.
 */
public class SSDetectedObjectsDao extends BaseDAO
        implements IDetectedObjectsDao {

    private static final String TABLE_NAME = "detected_objects_content";
    private static final String FK_COLUMN   = "search_session_results_id";

    public SSDetectedObjectsDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, SSDetectedObjectsDao.class); // ← класс самого DAO, не домена
    }

    // ── DDL ──────────────────────────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id                        INTEGER PRIMARY KEY AUTOINCREMENT, " +
                FK_COLUMN + "              INTEGER NOT NULL, " +
                "class_name                TEXT    NOT NULL, " +
                "probability               REAL    NOT NULL, " +
                "FOREIGN KEY (" + FK_COLUMN + ") " +
                "    REFERENCES search_session_results(id) ON DELETE CASCADE" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt  = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Table '" + TABLE_NAME + "' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing '" + TABLE_NAME + "' table", e);
            throw new RuntimeException("Failed to initialize '" + TABLE_NAME + "' table", e);
        }
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    /**
     * Батчевая вставка объектов для одного результата сессии.
     * Вызывается ВНУТРИ транзакции репозитория — conn уже открыт и управляется снаружи.
     *
     * Перед вставкой вызывающий код обязан вызвать deleteByForeignKeyId()
     * для upsert-семантики, иначе записи накопятся дублями.
     */
    @Override
    public void insertBatch(long foreignKeyId,
                            List<OCRAppDetectedObject> objects,
                            Connection conn) throws SQLException {
        if (objects == null || objects.isEmpty()) return;

        String sql = "INSERT INTO " + TABLE_NAME +
                " (" + FK_COLUMN + ", class_name, probability) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (OCRAppDetectedObject obj : objects) {
                pstmt.setLong(1,   foreignKeyId);
                pstmt.setString(2, obj.getClassName());
                pstmt.setDouble(3, obj.getProbability());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
        // SQLException пробрасывается наверх — репозиторий сделает rollback
    }

    /**
     * Удаляет все объекты для указанного результата сессии.
     * Используется в upsert-паттерне: сначала delete, затем insertBatch.
     */
    @Override
    public void deleteByForeignKeyId(long foreignKeyId,
                                     Connection conn) throws SQLException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE " + FK_COLUMN + " = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, foreignKeyId);
            pstmt.executeUpdate();
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    /**
     * Возвращает все объекты, связанные с одним результатом сессии.
     */
    @Override
    public List<OCRAppDetectedObject> findByForeignKeyId(long foreignKeyId,
                                                         Connection conn) throws SQLException {
        List<OCRAppDetectedObject> result = new ArrayList<>();
        String sql = "SELECT class_name, probability FROM " + TABLE_NAME +
                " WHERE " + FK_COLUMN + " = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, foreignKeyId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new OCRAppDetectedObject(
                            rs.getString("class_name"),
                            rs.getDouble("probability")
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Батчевая загрузка объектов для нескольких результатов сессии за один SQL-запрос.
     * Используется репозиторием при загрузке списка сессий, чтобы избежать N+1.
     *
     * @return Map: resultId → список объектов (отсутствующие ключи имеют пустой список)
     */
    @Override
    public Map<Long, List<OCRAppDetectedObject>> findByForeignKeyIds(List<Long> foreignKeyIds,
                                                                     Connection conn) throws SQLException {
        Map<Long, List<OCRAppDetectedObject>> resultMap = new HashMap<>();
        if (foreignKeyIds == null || foreignKeyIds.isEmpty()) return resultMap;

        // Инициализируем пустыми списками — чтобы результат был предсказуемым
        for (Long id : foreignKeyIds) {
            resultMap.put(id, new ArrayList<>());
        }

        // IN-clause: (?, ?, ?, ...)
        String placeholders = String.join(", ", Collections.nCopies(foreignKeyIds.size(), "?"));
        String sql = "SELECT " + FK_COLUMN + ", class_name, probability " +
                "FROM " + TABLE_NAME +
                " WHERE " + FK_COLUMN + " IN (" + placeholders + ")";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < foreignKeyIds.size(); i++) {
                pstmt.setLong(i + 1, foreignKeyIds.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long fkId = rs.getLong(FK_COLUMN);
                    resultMap.get(fkId).add(new OCRAppDetectedObject(
                            rs.getString("class_name"),
                            rs.getDouble("probability")
                    ));
                }
            }
        }
        return resultMap;
    }
    /*
    ранее:
    public void insert(long searchSessionResultId, OCRAppDetectedObject object)
    public void insertAll(long searchSessionResultId, List<OCRAppDetectedObject> objects)
    public List<OCRAppDetectedObject> findByResultId(long searchSessionResultId)
    */
}