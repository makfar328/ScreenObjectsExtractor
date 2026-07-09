package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.SelectedObjectClass;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IInitializable;

import java.sql.Connection;
import java.util.List;

/**
 * Бизнес-контракт DAO для таблицы selected_object_classes.
 *
 * Намеренно НЕ наследует IInitializable — DatabaseManager вызывает
 * initializeTable() напрямую через конкретный класс.
 */
public interface ISelectedObjectsClassDao extends IInitializable {

    /**
     * Сохраняет одну запись, привязывая её к существующим критериям поиска.
     */
    void insert(long searchCriteriaId, SelectedObjectClass selectedObjectClass, Connection conn);

    /**
     * Возвращает все классы объектов, связанные с указанными критериями.
     */
    List<SelectedObjectClass> findByCriteriaId(long searchCriteriaId, Connection conn);
}