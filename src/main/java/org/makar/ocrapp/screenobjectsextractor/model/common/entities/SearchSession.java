package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;

import java.time.LocalDateTime;
import java.util.List;

public class SearchSession {
    private final long sessionId;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final int filesCount;
    private final SearchCriteria criteria;
    private final List<FileMetadata> searchResults;
    private final String status;
    private final String errorMessage;

    private final LocalDateTime fastSearchStart;
    private LocalDateTime fastSearchEnd;
    private final LocalDateTime backgroundSearchStart;
    private LocalDateTime backgroundSearchEnd;

    public SearchSession(long sessionId,
                         LocalDateTime startedAt,
                         LocalDateTime finishedAt,
                         int filesCount,
                         SearchCriteria criteria,
                         List<FileMetadata> searchResults,
                         String status,
                         String errorMessage,
                         LocalDateTime fastSearchStart,
                         LocalDateTime backgroundSearchStart) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.filesCount = filesCount;
        this.searchResults = searchResults;
        this.criteria = criteria;
        //this.fastSearchStart = fastSearchStart;
        //this.backgroundSearchStart = backgroundSearchStart;
        this.status = status;
        this.errorMessage = errorMessage;
        this.fastSearchStart = fastSearchStart;
        this.backgroundSearchStart = backgroundSearchStart;
    }

    public long getSessionId() {
        return sessionId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public int getFilesCount() {
        return filesCount;
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    public List<FileMetadata> getSearchResults() {
        return searchResults;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getFastSearchStart() {
        return fastSearchStart;
    }

    public LocalDateTime getFastSearchEnd() {
        return fastSearchEnd;
    }

    public LocalDateTime getBackgroundSearchStart() {
        return backgroundSearchStart;
    }

    public LocalDateTime getBackgroundSearchEnd() {
        return backgroundSearchEnd;
    }

    public void setFastSearchEnd(LocalDateTime fastSearchEnd) {
        this.fastSearchEnd = fastSearchEnd;
    }

    public void setBackgroundSearchEnd(LocalDateTime backgroundSearchEnd) {
        this.backgroundSearchEnd = backgroundSearchEnd;
    }

    public static enum SessionField {
        SESSION_ID,
        STARTED_AT,
        FINISHED_AT,
        FILES_COUNT,
        CRITERIA,
        SEARCH_RESULTS,
        STATUS,
        ERROR_MESSAGE
    }

}
