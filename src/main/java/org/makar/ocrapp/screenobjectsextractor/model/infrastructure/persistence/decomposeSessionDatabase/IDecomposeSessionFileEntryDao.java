package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Контракт DAO для таблицы {@code ds_file_entry}.
 *
 * <p>Каждая запись соответствует одному файлу, обработанному в рамках
 * сессии декомпозиции ({@code DecomposeSession}).
 *
 * <p>Все методы принимают {@link Connection} явно — транзакционная
 * координация находится на стороне репозитория, не DAO.
 */
public interface IDecomposeSessionFileEntryDao extends IInitializable {

    /**
     * Вставить новую запись об обработанном файле.
     *
     * @param entry  данные обработанного файла
     * @param conn   активное соединение с отключённым автокоммитом
     * @return сгенерированный {@code id} вставленной строки
     * @throws SQLException при нарушении ограничения FK или ошибке записи
     */
    long insert(DecomposeSessionEntry entry, Connection conn) throws SQLException;

    /**
     * Найти все записи, принадлежащие сессии.
     *
     * @param sessionId  идентификатор родительской {@code DecomposeSession}
     * @param conn       активное соединение
     * @return список записей; пустой список, если сессия не содержит файлов
     * @throws SQLException при ошибке чтения
     */
    List<DecomposeSessionEntry> findBySessionId(long sessionId, Connection conn) throws SQLException;

    /**
     * Найти запись по первичному ключу.
     *
     * @param id    первичный ключ строки
     * @param conn  активное соединение
     * @return {@code Optional} с записью, или {@code Optional.createEmpty()} если не найдена
     * @throws SQLException при ошибке чтения
     */
    Optional<DecomposeSessionEntry> findById(long id, Connection conn) throws SQLException;

    /**
     * Удалить все записи, принадлежащие сессии.
     * Вызывается каскадно при удалении родительской {@code DecomposeSession}.
     *
     * @param sessionId  идентификатор родительской сессии
     * @param conn       активное соединение с отключённым автокоммитом
     * @throws SQLException при ошибке удаления
     */
    void deleteBySessionId(long sessionId, Connection conn) throws SQLException;

    List<DecomposeSessionEntry> findAll(Connection conn) throws SQLException;
}