package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

/**
 * Контракт инициализации схемы.
 * Реализуется всеми DAO-классами, но НЕ выставляется через бизнес-интерфейсы DAO.
 * DatabaseManager знает конкретные классы DAO → вызывает initializeTable() напрямую.
 */
public interface IInitializable {
    void initializeTable();
}