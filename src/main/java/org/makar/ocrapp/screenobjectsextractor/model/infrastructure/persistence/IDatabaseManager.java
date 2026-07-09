package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

/**
 * Контракт для менеджеров баз данных.
 *
 * DatabaseManager — инфраструктурный объект, отвечающий за:
 *   - инициализацию схемы (создание таблиц и индексов)
 *   - проверку готовности схемы
 *   - предоставление репозиториев через типизированные геттеры
 *
 * Три реализации в проекте:
 *   - FileIndexDatabaseManager
 *   - SearchSessionDatabaseManager
 *   - (ThirdDatabaseManager)
 */
public interface IDatabaseManager {

    /**
     * Инициализировать все таблицы и индексы.
     * Должна быть идемпотентной (CREATE TABLE IF NOT EXISTS).
     * Вызывается один раз при старте приложения.
     */
    void initializeSchema();

    /**
     * Проверить доступность и целостность схемы.
     * Используется при старте для fast-fail диагностики.
     *
     * @return true если все необходимые таблицы существуют
     */
    boolean isSchemaReady();
}