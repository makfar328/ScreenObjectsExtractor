package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Контракт DAO для таблицы detected_file_objects.
 * FileIndexRepository зависит от этого интерфейса, а не от конкретной реализации.
 * Это позволяет подменять реализацию в тестах без использования реальной БД.
 */
public interface IDetectedObjectsDao extends IInitializable {

    // ───────────── CRUD ─────────────────────────────────────────────────────

    /**
     * Пакетная вставка объектов для одного файла в рамках существующей транзакции.
     *
     * <p>Перед вставкой вызывающий код обязан предварительно очистить старые записи
     * через {@link #deleteByForeignKeyId} — этот метод только добавляет, не заменяет.
     */
    void insertBatch(long foreignKeyId, List<OCRAppDetectedObject> objects, Connection conn) throws SQLException;

    /**
     * Удалить все объекты для файла (вызывается перед повторной записью).
     */
    void deleteByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException;

    /**
     * Загрузить все обнаруженные объекты для одного файла.
     */
    List<OCRAppDetectedObject> findByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException;

    /**
     * Bulk-загрузка объектов для набора файлов одним запросом (избегает N+1).
     */
    Map<Long, List<OCRAppDetectedObject>> findByForeignKeyIds(List<Long> fileIds, Connection conn) throws SQLException;

}
