package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Контракт DAO для таблицы search_criteria.
 *
 * Все методы принимают Connection — управление соединением
 * и транзакциями остаётся на стороне SearchSessionRepository.
 */
public interface ISearchCriteriaDao extends IInitializable {

    /**
     * Вставляет критерии поиска, привязанные к сессии.
     *
     * @return сгенерированный id новой строки
     */
    long insert(long sessionId, SearchCriteria criteria, Connection conn) throws SQLException;

    /**
     * Возвращает критерии по первичному ключу.
     */
    SearchCriteria findBySessionId(long id, Connection conn) throws SQLException;

    /**
     * Удаляет критерии по id сессии.
     * Вызывается каскадно при удалении сессии (или явно из Repository).
     */
    void deleteBySessionId(long sessionId, Connection conn) throws SQLException;
}