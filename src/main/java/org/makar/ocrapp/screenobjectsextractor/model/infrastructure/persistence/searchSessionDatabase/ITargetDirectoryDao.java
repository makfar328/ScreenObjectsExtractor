// ITargetDirectoryDao.java
package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Контракт DAO для таблицы target_directories.
 *
 * Все методы принимают Connection — управление транзакцией
 * остаётся на стороне SearchSessionRepository.
 */
public interface ITargetDirectoryDao extends IInitializable {

    /**
     * Вставляет одну целевую директорию, привязанную к критериям поиска.
     */
    void insert(long searchCriteriaId, SearchDirectoryConfig config, Connection conn)
            throws SQLException;

    /**
     * Возвращает все директории, привязанные к указанным критериям поиска.
     */
    List<SearchDirectoryConfig> findByCriteriaId(long searchCriteriaId, Connection conn)
            throws SQLException;
}