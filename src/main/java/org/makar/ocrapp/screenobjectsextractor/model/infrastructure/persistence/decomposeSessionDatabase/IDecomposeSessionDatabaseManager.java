package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDatabaseManager;

/**
 * Контракт менеджера базы данных для домена сессий декомпозиции.
 *
 * Отвечает за инициализацию схемы домена
 * таблиц decompose_sessions, ds_file_entry и дочерних таблиц
 * (ds_detected_objects, ds_recognized_text).
 *
 * Пример использования при старте приложения:
 * <pre>{@code
 * IDecomposeSessionDatabaseManager manager = new DecomposeSessionDatabaseManager(connectionManager);
 * manager.initializeSchema();
 * if (!manager.isSchemaReady()) {
 *     throw new IllegalStateException("DecomposeSessionDatabase schema is not ready");
 * }
 * }</pre>
 */
public interface IDecomposeSessionDatabaseManager extends IDatabaseManager {

    /**
     * Возвращает репозиторий домена сессий декомпозиции.
     * Используется для получения репозитория после успешной
     * инициализации схемы без повторного создания экземпляра.
     *
     * @return полностью инициализированный {@link IDecomposeSessionRepository}
     */
    IDecomposeSessionRepository getRepository();
}