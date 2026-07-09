package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

import java.sql.SQLException;

/**
 * Общий контракт для всех SQLite-репозиториев проекта.
 *
 * Реализуют:
 *   - FileIndexRepository
 *   - SearchSessionRepository
 *   - (третий будущий репозиторий)
 *
 * Не содержит доменной логики — только инфраструктурные обязанности:
 * инициализация схемы и базовая самодиагностика.
 */
public interface ISQLiteRepository {

    /**
     * Инициализировать все таблицы и индексы, которыми управляет этот репозиторий.
     * Вызывается один раз при старте приложения через DatabaseInitializer
     * или соответствующий DatabaseManager.
     * Реализация должна быть идемпотентной (CREATE TABLE IF NOT EXISTS).
     */
    /*void initializeSchema();*/

    /**
     * Проверить, что все необходимые таблицы существуют и доступны.
     * Используется при старте для диагностики состояния БД.
     *
     * @return true — схема полностью инициализирована.
     */
    /*boolean isSchemaReady();*/

    /**
     * Удалить все данные из таблиц репозитория, сохранив схему.
     * Используется в тестах и при сбросе состояния приложения.
     *
     * @throws SQLException если операция не выполнена.
     */
    void truncateAll() throws SQLException;
}