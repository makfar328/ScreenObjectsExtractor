package org.makar.ocrapp.screenobjectsextractor.model.common;

import java.util.List;

public class SearchSessionResults {
    private final long searchSessionId;
    private List<FileMetadata> results;

    public SearchSessionResults(final long searchSessionId, final List<FileMetadata> results) {
        this.searchSessionId = searchSessionId;
        this.results = results;
    }

    public long getSearchSessionId() {
        return searchSessionId;
    }

    public List<FileMetadata> getResults() {
        return results;
    }

    public void addToResults(FileMetadata fileMetadata) {
        results.add(fileMetadata);
    }

    public void removeFromResults(int i) {
        results.remove(i);
    }

    public FileMetadata index(int i) {
        return results.get(i);
    }
}
