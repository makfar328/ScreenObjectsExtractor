package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDatabaseManager;

/**
 * Контракт менеджера данных сессий поиска.
 *
 * Предоставляет репозиторий через типизированный геттер.
 * Контроллеры и сервисы, которым нужна история сессий,
 * зависят от этого интерфейса, а не от конкретного
 * {@code SearchSessionDatabaseManager}.
 */
public interface ISearchSessionDatabaseManager extends IDatabaseManager {

    /**
     * Типизированный доступ к репозиторию сессий.
     * Возвращает интерфейс — зависимость от контракта, не от реализации.
     *
     * @return {@link ISearchSessionRepository} — бизнес-API для работы с сессиями
     */
    ISearchSessionRepository getRepository();
}