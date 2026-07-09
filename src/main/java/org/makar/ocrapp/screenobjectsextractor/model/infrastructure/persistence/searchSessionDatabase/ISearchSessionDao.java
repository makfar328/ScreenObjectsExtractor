package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Контракт DAO для таблицы search_sessions.
 *
 * Управление транзакциями:
 *   - insert(), findById(), findAll(), deleteSearchSessionScore() —
 *     открывают собственное соединение. Являются точкой входа от репозитория.
 *   - update(session, conn) — принимает Connection снаружи:
 *     вызывается из SearchSessionRepository в рамках единой транзакции
 *     при сохранении сессии вместе с вложенными данными.
 */
public interface ISearchSessionDao extends IInitializable {

    /**
     * Вставляет новую сессию и возвращает сгенерированный id.
     *
     * @param session сессия для вставки
     * @return сгенерированный id (PRIMARY KEY)
     * @throws RuntimeException при ошибке записи
     */
    int insert(SearchSession session,Connection conn);

    /**
     * Обновляет поля существующей сессии (статус, временны́е метки завершения).
     * Принимает Connection снаружи — вызывается из репозитория в рамках транзакции.
     *
     * @param session   сессия с обновлёнными полями; session.getId() — ключ
     * @param conn      открытое соединение (управляется репозиторием)
     * @throws SQLException при ошибке записи
     */
    void update(SearchSession session, Connection conn) throws SQLException;

    /**
     * Возвращает сессию по id без вложенных данных (criteria, results).
     * Их достраивает SearchSessionRepository.
     *
     * @param id идентификатор сессии
     * @return SearchSession или null если не найдена
     * @throws RuntimeException при ошибке чтения
     */
    SearchSession findById(long id, Connection conn) throws SQLException;

    /**
     * Возвращает все сессии без вложенных данных, отсортированные по id DESC.
     * Вложенные данные достраивает SearchSessionRepository.
     *
     * @return список сессий (может быть пустым)
     * @throws RuntimeException при ошибке чтения
     */
    List<SearchSession> findAll(Connection conn) throws SQLException;

    /**
     * Удаляет сессию по id.
     * ON DELETE CASCADE на уровне БД удалит связанные записи в search_criteria,
     * target_directories и search_session_results.
     *
     * @param sessionId id удаляемой сессии
     * @throws RuntimeException при ошибке удаления
     */
    void deleteSearchSessionScore(long sessionId, Connection conn) throws SQLException;

    int deleteAllSessions(Connection conn) throws SQLException;
}