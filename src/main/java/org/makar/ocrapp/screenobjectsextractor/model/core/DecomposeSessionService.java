package org.makar.ocrapp.screenobjectsextractor.model.core;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase.IDecomposeSessionRepository;

import java.util.List;

public class DecomposeSessionService {
    private final IDecomposeSessionRepository repository;

    public DecomposeSessionService(IDecomposeSessionRepository repository) {
        this.repository = repository;
    }

    public void save(DecomposeSessionEntry entry) {
        repository.saveSession(entry);
    }

    public List<DecomposeSessionEntry> findAll() {
        return repository.getAllSessionsSummary();
    }
}
