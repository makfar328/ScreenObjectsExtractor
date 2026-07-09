package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchSessionResults;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Контракт DAO для таблицы search_session_results.
 *
 * Управление соединением и транзакцией остаётся на стороне SearchSessionRepository:
 * все методы, кроме initializeTable(), принимают Connection снаружи
 * и НЕ открывают/закрывают его самостоятельно.
 *
 * SearchSessionDatabaseManager знает конкретный класс и вызывает initializeTable() напрямую.
 */
public interface ISearchSessionResultsDao extends IInitializable {

    /**
     * Вставляет одну строку результата и возвращает сгенерированный id.
     */
    int insert(long searchSessionId, FileMetadata result, Connection conn) throws SQLException;

    /**
     * Возвращает все файлы для одной сессии.
     * Контентные поля (text, objects) не заполняются — их достраивает репозиторий.
     */
    SearchSessionResults findBySessionId(long sessionId, Connection conn) throws SQLException;

    /**
     * Возвращает все результаты по всем сессиям, сгруппированные по session_id.
     */
    List<SearchSessionResults> findAll(Connection conn) throws SQLException;

    /**
     * Удаляет все строки для указанной сессии.
     * Вызывается репозиторием перед повторной записью или при удалении сессии.
     */
    void deleteBySessionId(long sessionId, Connection conn) throws SQLException;
}