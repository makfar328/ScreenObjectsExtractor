package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDatabaseManager;

/**
 * Контракт менеджера данных файлового индекса.
 *
 * Предоставляет репозиторий через типизированный геттер.
 * FileIndexService и FileSearchService зависят от этого интерфейса,
 * а не от конкретного FileIndexDatabaseManager.
 */
public interface IFileIndexDatabaseManager extends IDatabaseManager {

    /**
     * Типизированный доступ к репозиторию.
     * Возвращает интерфейс — зависимость от контракта, не от реализации.
     */
    IFileIndexRepository getRepository();
}
