package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;


import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Контракт DAO для таблицы indexed_files.
 * FileIndexRepository зависит от этого интерфейса, а не от конкретной реализации.
 * Это позволяет подменять реализацию в тестах без реального SQLite-подключения.!!
 */
public interface IIndexedFilesDao extends IInitializable {

    // ── Транзакционные (первичные) ──────────────────────────────────────────

    /** Upsert в рамках внешней транзакции. */
    long upsert(Connection conn, FileMetadata metadata) throws SQLException;

    /** Найти id в рамках внешней транзакции. */
    long findIdByPath(Connection conn, String filePath) throws SQLException;

    /** Удалить запись в рамках внешней транзакции. */
    void deleteById(Connection conn, long fileId) throws SQLException;

    /**
     * Найти файл по абсолютному пути (один SELECT вместо findIdByPath + findById).
     * Primary: соединение управляется снаружи.
     */
    FileMetadata findByPath(Connection conn, String filePath) throws SQLException;

    // ── Автономные (удобные оверлоады) ─────────────────────────────────────

    /**
     * Upsert записи о файле.
     * @param metadata
     * @return id строки (новый при вставке, существующий при конфликте)
     * @throws SQLException
     */
    long upsert(FileMetadata metadata) throws SQLException;

    /**
     * Найти id файла по его пути.
     * @param filePath
     * @return id или -1, если файл не найден
     * @throws SQLException
     */
    long findIdByPath(String filePath) throws SQLException;

    /**
     * Удалить файл из индекса по id.
     * ON DELETE CASCADE присутствует.
     * @param fileId - id файла.
     * @throws SQLException
     */
    void deleteById(long fileId) throws SQLException;

    /** Convenience-обёртка. */
    FileMetadata findByPath(String filePath) throws SQLException;

    // ── Маппинг ─────────────────────────────────────────────────────────────

    /**
     * Смаппить текущую строку ResultSet в FileMetadata.
     * Не загружает detectedObjects и recognizedText — это зона других DAO.
     */
    FileMetadata mapRow(ResultSet rs) throws SQLException;
}
