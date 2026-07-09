package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.ISQLiteRepository;

import java.util.List;

public interface IDecomposeSessionRepository extends ISQLiteRepository {
    long saveSession(DecomposeSessionEntry session);
    List<DecomposeSessionEntry> getAllSessionsSummary();
}