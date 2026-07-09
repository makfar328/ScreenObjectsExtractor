package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Контракт DAO для таблицы recognized_text_file.
 * Все методы принимают Connection — управление соединением и транзакциями
 * остаётся на стороне FileIndexRepository.
 */
public interface IRecognizedTextDao extends IInitializable{

    // ───────────── CRUD ─────────────────────────────────────────────────────

    /**
     * Пакетная вставка текстовых объектов для файла.
     */
    void insertBatch(long foreignKeyId, List<TextObject> texts, Connection conn) throws SQLException;

    /**
     * Удалить весь распознанный текст для файла (вызывается перед повторной записью).
     */
    void deleteByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException;

    /**
     * Загрузить все фрагменты текста для одного файла.
     */
    List<TextObject> findByForeignKeyId(long foreignKeyId, Connection conn) throws SQLException;

    /**
     * Bulk-загрузка текста для набора файлов одним запросом (избегает N+1).
     */
    Map<Long, List<TextObject>> findByForeignKeyIds(List<Long> foreignKeyIds, Connection conn) throws SQLException;

}
