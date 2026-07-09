package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;

import java.util.List;

/**
 * Бизнес-контракт репозитория сессий поиска.
 *
 * Контроллеры работают только с этим интерфейсом —
 * конкретная {@code SearchSessionRepository} скрыта за ним.
 */
public interface ISearchSessionRepository {

    /**
     * Сохраняет новую сессию со всеми вложенными данными.
     *
     * @param session сессия для сохранения
     * @return сгенерированный id сессии
     */
    long saveSession(SearchSession session);

    /**
     * Возвращает все сессии с полными данными.
     * Используй только когда нужна полная выгрузка — операция тяжёлая.
     *
     * @return список всех {@link SearchSession}
     */
    List<SearchSession> getAllFullSessions();

    /**
     * Возвращает краткие данные о всех сессиях (без вложенных объектов).
     * Используется для отображения журнала.
     *
     * @return список {@link SearchSession} с заполненными только сводными полями
     */
    List<SearchSession> getAllSessionsSummary();

    /**
     * Возвращает сессию с полными данными по идентификатору.
     *
     * @param sessionId идентификатор сессии
     * @return {@link SearchSession} или null, если не найдена
     */
    SearchSession getSessionById(long sessionId);

    /**
     * Удаляет сессию и все связанные с ней данные (CASCADE).
     *
     * @param sessionId идентификатор сессии для удаления
     */
    void deleteSession(long sessionId);

    void deleteAll();
}