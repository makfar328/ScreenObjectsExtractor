package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.BaseDAO;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Реализация {@link IDecomposeSessionFileEntryDao} для SQLite.
 *
 * <p>Таблица {@code ds_file_entry} хранит скалярные поля одного
 * обработанного файла в рамках сессии декомпозиции. Дочерние коллекции
 * (распознанный текст, детектированные объекты) хранятся в отдельных таблицах
 * и связаны с этой таблицей через {@code entry_id}.
 *
 * <p>Конструктор принимает {@link SQLiteConnectionManager} и передаёт его
 * в {@code super()} — это стандартный паттерн всех DAO в проекте.
 * Метод {@link #initializeTable()} вызывается менеджером базы данных при старте.
 */
public class DecomposeSessionFileEntryDao extends BaseDAO implements IDecomposeSessionFileEntryDao {

    private static final String TABLE_NAME = "ds_file_entry";

    // SQL

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id                 INTEGER  PRIMARY KEY AUTOINCREMENT, " +
                    //"session_id         INTEGER  NOT NULL, " +
                    "processed_at       TEXT     NOT NULL, " +
                    "capture_source     TEXT, " +
                    "file_path          TEXT     NOT NULL, " +
                    "file_name          TEXT, " +
                    "file_extension     TEXT, " +
                    "file_size          INTEGER, " +
                    "creation_date      TEXT, " +
                    "modification_date  TEXT, " +
                    "image_width INTEGER, " +
                    "image_height INTEGER, " +
                    "UNIQUE(id, file_path)" +
                    ")";

/*    private static final String SQL_CREATE_IDX_SESSION_ID =
            "CREATE INDEX IF NOT EXISTS idx_ds_file_entry_id " +
                    "ON " + TABLE_NAME + " (id)";*/

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE_NAME + " " +
                    "(processed_at, capture_source, file_path, " +
                    " file_name, file_extension, file_size, creation_date, modification_date, image_width, image_height) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

/*    private static final String SQL_FIND_BY_SESSION_ID =
            "SELECT id, processed_at, capture_source, " +
                    "file_path, file_name, file_extension, file_size, " +
                    "creation_date, modification_date, image_width, image_height " +
                    "FROM " + TABLE_NAME + " WHERE id = ?";*/

    private static final String SQL_FIND_BY_ID =
            "SELECT id, processed_at, capture_source, " +
                    "file_path, file_name, file_extension, file_size, " +
                    "creation_date, modification_date, image_width, image_height " +
                    "FROM " + TABLE_NAME + " WHERE id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT id, processed_at, capture_source, " +
                    "file_path, file_name, file_extension, file_size, " +
                    "creation_date, modification_date, image_width, image_height " +
                    "FROM " + TABLE_NAME;

    private static final String SQL_DELETE_BY_SESSION_ID =
            "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    // END SQL

    public DecomposeSessionFileEntryDao(SQLiteConnectionManager connectionManager) {
        super(connectionManager, DecomposeSessionFileEntryDao.class);
    }

    // IInitializable

    /**
     * Создаёт таблицу и индексы, если они ещё не существуют.
     * Идемпотентно. Вызывается {@code DecomposeSessionDatabaseManager}.
     */
    @Override
    public void initializeTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(SQL_CREATE_TABLE);
/*            stmt.executeUpdate(SQL_CREATE_IDX_SESSION_ID);*/
            logger.info("Table '" + TABLE_NAME + "' initialized successfully.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize table '" + TABLE_NAME + "'", e);
            throw new RuntimeException("Failed to initialize table " + TABLE_NAME, e);
        }
    }

    // ── IDecomposeSessionFileEntryDao ────────────────────────────────────────

    /**
     * Вставляет запись об обработанном файле и возвращает сгенерированный PK.
     */
    @Override
    public long insert(DecomposeSessionEntry entry, Connection conn) throws SQLException {
        FileMetadata meta = entry.getFileMetadata();

        try (PreparedStatement ps = conn.prepareStatement(
                SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, formatDateTime(entry.getProcessedAt()));
            ps.setString(2, entry.getCaptureSource());                    // nullable
            //ps.setString(4, meta.getFilePath().toString());

            String filePath = meta.getFilePath() != null
                    ? meta.getFilePath().toString()
                    : "SCREEN_CAPTURE:" + (meta.getFileName() != null ? meta.getFileName() : "unknown");
            ps.setString(3, filePath);

            ps.setString(4, meta.getFileName());                          // nullable
            ps.setString(5, meta.getFileExtension());                     // nullable
            setNullableLong(ps, 6, meta.getFileSize());                   // nullable
            ps.setString(7, formatDateTime(meta.getCreationDate()));      // nullable
            ps.setString(8, formatDateTime(meta.getModificationDate())); // nullable
            ps.setInt(9, meta.getImageWidth());
            ps.setInt(10, meta.getImageHeight());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException(
                        "INSERT into " + TABLE_NAME + " succeeded but no generated key returned.");
            }
        }
    }

    @Override
    public List<DecomposeSessionEntry> findBySessionId(long sessionId, Connection conn)
            throws SQLException {
        List<DecomposeSessionEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    @Override
    public Optional<DecomposeSessionEntry> findById(long id, Connection conn)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DecomposeSessionEntry> findAll(Connection conn) throws SQLException {
        List<DecomposeSessionEntry> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_FIND_ALL)) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    @Override
    public void deleteBySessionId(long sessionId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_SESSION_ID)) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        }
    }


    // Вспомогательные методы

    /**
     * Собирает {@link DecomposeSessionEntry} из текущей строки {@link ResultSet}.
     * Дочерние коллекции (TextObject, OCRAppDetectedObject) в этот метод
     * не входят — они загружаются отдельными DAO и «подшиваются» репозиторием.
     */
    private DecomposeSessionEntry mapRow(ResultSet rs) throws SQLException {
        String rawPath = rs.getString("file_path");

        java.nio.file.Path resolvedPath = null;
        if (rawPath != null && !rawPath.startsWith("SCREEN_CAPTURE:")) {
            resolvedPath = Paths.get(rawPath);
        }

        long size = rs.getLong("file_size");
        Long fileSize = rs.wasNull() ? null : size;

        FileMetadata meta = new FileMetadata(
                //Paths.get(rs.getString("file_path")),
                rs.getLong("id"),
                resolvedPath,
                rs.getString("file_name"),
                rs.getString("file_extension"),
                fileSize, //getNullableLong(rs, "file_size"),
                parseDateTime(rs.getString("creation_date")),
                parseDateTime(rs.getString("modification_date")),
                Collections.emptyList(),
                Collections.emptyList(),
                rs.getInt("image_width"),
                rs.getInt("image_height")
                );

        return DecomposeSessionEntry.builder()
                .sessionId(rs.getLong("id"))
                .processedAt(parseDateTime(rs.getString("processed_at")))
                .captureSource(rs.getString("capture_source"))
                .fileMetadata(meta)
                .build();
    }

    /**
     * устанавливает long или null, если в Long null
     */
    private void setNullableLong(PreparedStatement ps, int index, Long value)
            throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    /**
     * (long or null) -> Long
     */
    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * LocalDateTime -> String
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? DATE_TIME_FORMATTER.format(dateTime) : null;
    }

    /**
     * String -> LocalDateTime
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty()) return null;
        return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    }
}