package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.logging.Level;

/**
 * DAO для таблицы indexed_files.
 *
 * Каждая SQL-операция имеет две версии:
 *  1. primary(Connection conn, ...)  — для использования в составе внешней транзакции
 *  2. convenience(...)               — открывает соединение самостоятельно, вызывает primary
 *
 * Бизнес-логика и SQL реализованы ТОЛЬКО в primary-версиях.
 * Convenience-версии — тонкие оберртки без дублирования кода.
 */
public class IndexedFilesDao extends BaseDAO
        implements IIndexedFilesDao {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public IndexedFilesDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, IndexedFilesDao.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DDL
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initializeTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS indexed_files (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path         TEXT NOT NULL UNIQUE,
                    file_name         TEXT,
                    file_extension    TEXT,
                    file_size         INTEGER,
                    creation_date     TEXT,
                    modification_date TEXT
                );
                """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'indexed_files' ensured.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize indexed_files", e);
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPSERT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PRIMARY: upsert в рамках внешней транзакции.
     * Соединение НЕ закрывается — управление остаётся за вызывающим кодом.
     *
     * @return сгенерированный id (INSERT) или существующий id (UPDATE при конфликте)
     */
    @Override
    public long upsert(Connection conn, FileMetadata metadata) throws SQLException {
        String sql = """
                INSERT INTO indexed_files
                    (file_path, file_name, file_extension, file_size, creation_date, modification_date)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(file_path) DO UPDATE SET
                    file_name         = excluded.file_name,
                    file_extension    = excluded.file_extension,
                    file_size         = excluded.file_size,
                    creation_date     = excluded.creation_date,
                    modification_date = excluded.modification_date
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindFileMetadata(ps, metadata);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                // INSERT — возвращаем новый id
                if (keys.next()) {
                    long id = keys.getLong(1);
                    if (id > 0) return id;   // ← добавлена проверка id > 0
                }
            }
        }
        // ON CONFLICT DO UPDATE не генерирует ключ — берём существующий id
        return findIdByPath(conn, metadata.getFilePath().toString());
    }

    /** CONVENIENCE: upsert с управлением соединением внутри. */
    @Override
    public long upsert(FileMetadata metadata) throws SQLException {
        try (Connection conn = getConnection()) {
            return upsert(conn, metadata);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FIND ID BY PATH
    // ─────────────────────────────────────────────────────────────────────────

    /** PRIMARY: поиск id по пути в рамках внешней транзакции. */
    @Override
    public long findIdByPath(Connection conn, String filePath) throws SQLException {
        String sql = "SELECT id FROM indexed_files WHERE file_path = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return -1;
    }

    /** CONVENIENCE: поиск id с управлением соединением внутри. */
    @Override
    public long findIdByPath(String filePath) throws SQLException {
        try (Connection conn = getConnection()) {
            return findIdByPath(conn, filePath);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DELETE BY ID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PRIMARY: удаление записи в рамках внешней транзакции.
     * ON DELETE CASCADE в indexed_files удалит связанные строки в дочерних таблицах.
     */
    @Override
    public void deleteById(Connection conn, long fileId) throws SQLException {
        String sql = "DELETE FROM indexed_files WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            int rows = ps.executeUpdate();
            if (rows == 0) logger.warning("deleteById: no file with id=" + fileId);
        }
    }

    /** CONVENIENCE: удаление с управлением соединением внутри. */
    @Override
    public void deleteById(long fileId) throws SQLException {
        try (Connection conn = getConnection()) {
            deleteById(conn, fileId);
        }
    }

    // ───────────────────────────────────────────────────────────────────
    // FIND BY PATH
    // ───────────────────────────────────────────────────────────────────

    /**
     * Primary
     * @param conn
     */
    @Override
    public FileMetadata findByPath(Connection conn, String filePath) throws SQLException {
        String sql = "SELECT id, file_path, file_name, file_extension, file_size, " +
                "       creation_date, modification_date " +
                "FROM indexed_files WHERE file_path = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * convenience
     */
    @Override
    public FileMetadata findByPath(String filePath) throws SQLException {
        try (Connection conn = getConnection()) {
            return findByPath(conn, filePath);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ROW MAPPING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Маппинг ResultSet → FileMetadata.
     * Не принимает Connection — только читает уже открытый курсор.
     * Распознанный текст и объекты не загружаются — за них отвечают другие DAO.
     */
    @Override
    public FileMetadata mapRow(ResultSet rs) throws SQLException {
        String cdStr = rs.getString("creation_date");
        String mdStr = rs.getString("modification_date");

        FileMetadata fm = new FileMetadata(
                java.nio.file.Paths.get(rs.getString("file_path")),
                rs.getString("file_name"),
                rs.getString("file_extension"),
                rs.getLong("file_size"),
                cdStr != null ? LocalDateTime.parse(cdStr, FMT) : null,
                mdStr != null ? LocalDateTime.parse(mdStr, FMT) : null,
                Collections.emptyList(),
                Collections.emptyList()
        );
        fm.setId(rs.getLong("id")); // ← теперь id доступен без второго запроса
        return fm;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Приватные утилиты
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Привязка полей FileMetadata к PreparedStatement.
     * Вынесено сюда, чтобы не дублировать в primary и convenience версиях.
     */
    private void bindFileMetadata(PreparedStatement ps, FileMetadata metadata) throws SQLException {
        ps.setString(1, metadata.getFilePath().toString());
        ps.setString(2, metadata.getFileName());
        ps.setString(3, metadata.getFileExtension());
        ps.setLong  (4, metadata.getFileSize());
        ps.setString(5, metadata.getCreationDate()     != null ? metadata.getCreationDate().format(FMT)     : null);
        ps.setString(6, metadata.getModificationDate() != null ? metadata.getModificationDate().format(FMT) : null);
    }
}