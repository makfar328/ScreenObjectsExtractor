package org.makar.ocrapp.screenobjectsextractor.model.common;

import java.util.Collections;
import java.util.List;

public class FileSearchResults {
    private final List<FileMetadata> results;

    public FileSearchResults(List<FileMetadata> results) {
        this.results = Collections.unmodifiableList(results != null ? results : Collections.<FileMetadata>emptyList());
    }


    public List<FileMetadata> getResults() {
        return results;
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public int count() {
        return results.size();
    }

    @Override
    public String toString() {
        return "FileSearchResults{" +
                "count=" + results.size() +
                ", results=" + results +
                '}';
    }
}
