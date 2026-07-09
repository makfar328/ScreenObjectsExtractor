package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;

import java.time.LocalDateTime;
import java.util.List;

public class SearchSessionBuilder {
    private long sessionId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int filesCount;
    private SearchCriteria criteria;
    private List<FileMetadata> searchResults;
    private String status;
    private String errorMessage;
    private LocalDateTime fastSearchStart;
    private LocalDateTime fastSearchEnd;
    private LocalDateTime backgroundSearchStart;
    private LocalDateTime backgroundSearchEnd;

    public SearchSessionBuilder withSessionId(long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public SearchSessionBuilder withStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public SearchSessionBuilder withFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public SearchSessionBuilder withFilesCount(int filesCount) {
        this.filesCount = filesCount;
        return this;
    }

    public SearchSessionBuilder withCriteria(SearchCriteria criteria) {
        this.criteria = criteria;
        return this;
    }

    public SearchSessionBuilder withSearchResults(List<FileMetadata> searchResults) {
        this.searchResults = searchResults;
        return this;
    }

    public SearchSessionBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public SearchSessionBuilder withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public SearchSessionBuilder withFastSearchStart(LocalDateTime fastSearchStart) {
        this.fastSearchStart = fastSearchStart;
        return this;
    }

    public SearchSessionBuilder withFastSearchEnd(LocalDateTime fastSearchEnd) {
        this.fastSearchEnd = fastSearchEnd;
        return this;
    }

    public SearchSessionBuilder withBackgroundSearchStart(LocalDateTime backgroundSearchStart) {
        this.backgroundSearchStart = backgroundSearchStart;
        return this;
    }

    public SearchSessionBuilder withBackgroundSearchEnd(LocalDateTime backgroundSearchEnd) {
        this.backgroundSearchEnd = backgroundSearchEnd;
        return this;
    }

    public SearchSession build() {
        SearchSession session = new SearchSession(
                sessionId,
                startedAt,
                finishedAt,
                filesCount,
                criteria,
                searchResults,
                status,
                errorMessage,
                fastSearchStart,
                backgroundSearchStart
        );

        // Установим end-поля через reflection или через package-private сеттеры, если нужно
        // (или расширьте конструктор SearchSession)
        // Например, если добавить package-private методы setFastSearchEnd и setBackgroundSearchEnd:
        if (fastSearchEnd != null) {
            session.setFastSearchEnd(fastSearchEnd);
        }
        if (backgroundSearchEnd != null) {
            session.setBackgroundSearchEnd(backgroundSearchEnd);
        }
        return session;
    }

    public SearchSession buildWithFastSearchEnd(SearchSession currentSession, LocalDateTime fastSearchEnd) {
        return new SearchSessionBuilder()
                .withSessionId(currentSession.getSessionId())
                .withStartedAt(currentSession.getStartedAt())
                .withFinishedAt(currentSession.getFinishedAt())
                .withFilesCount(currentSession.getFilesCount())
                .withCriteria(currentSession.getCriteria())
                .withSearchResults(currentSession.getSearchResults())
                .withStatus(currentSession.getStatus())
                .withFastSearchStart(currentSession.getFastSearchStart())
                .withFastSearchEnd(fastSearchEnd)
                .withBackgroundSearchStart(currentSession.getBackgroundSearchStart())
                .withBackgroundSearchEnd(currentSession.getBackgroundSearchEnd())
                .build();
    }

    public SearchSession buildWithBackgroundSearchEnd(SearchSession currentSession, LocalDateTime backgroundSearchEnd) {
        return new SearchSessionBuilder()
                .withSessionId(currentSession.getSessionId())
                .withStartedAt(currentSession.getStartedAt())
                .withFinishedAt(currentSession.getFinishedAt())
                .withFilesCount(currentSession.getFilesCount())
                .withCriteria(currentSession.getCriteria())
                .withSearchResults(currentSession.getSearchResults())
                .withStatus(currentSession.getStatus())
                .withFastSearchStart(currentSession.getFastSearchStart())
                .withFastSearchEnd(currentSession.getFastSearchEnd())
                .withBackgroundSearchStart(currentSession.getBackgroundSearchStart())
                .withBackgroundSearchEnd(backgroundSearchEnd)
                .build();
    }

    public SearchSession buildWithCriteria(SearchSession currentSession, SearchCriteria newCriteria) {
        return new SearchSessionBuilder()
                .withSessionId(currentSession.getSessionId())
                .withStartedAt(currentSession.getStartedAt())
                .withFinishedAt(currentSession.getFinishedAt())
                .withFilesCount(currentSession.getFilesCount())
                .withCriteria(newCriteria)
                .withSearchResults(currentSession.getSearchResults())
                .withStatus(currentSession.getStatus())
                .withFastSearchStart(currentSession.getFastSearchStart())
                .withFastSearchEnd(currentSession.getFastSearchEnd())
                .withBackgroundSearchStart(currentSession.getBackgroundSearchStart())
                .withBackgroundSearchEnd(currentSession.getBackgroundSearchEnd())
                .build();
    }
}