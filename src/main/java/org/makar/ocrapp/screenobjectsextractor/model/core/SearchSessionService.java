package org.makar.ocrapp.screenobjectsextractor.model.core;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase.ISearchSessionRepository;

import java.util.List;

// Новый класс — тонкий фасад, не содержит бизнес-логики поиска
public class SearchSessionService {
    private final ISearchSessionRepository repository;

    public SearchSessionService(ISearchSessionRepository repository) {
        this.repository = repository;
    }

    public void save(SearchSession session) {
        repository.saveSession(session);
    }

    public List<SearchSession> findAll() {
        return repository.getAllFullSessions();
    }

    // пусть возвращает результат delete
    public void deleteAll() {
        repository.deleteAll();
    }
}
