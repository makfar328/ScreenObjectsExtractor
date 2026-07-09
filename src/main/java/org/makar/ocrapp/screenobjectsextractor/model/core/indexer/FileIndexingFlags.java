package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

public class FileIndexingFlags {
    private boolean needsBasicIndexing;
    private boolean needsTextProcessing;
    private boolean needsImageProcessing;

    public FileIndexingFlags() {
        needsBasicIndexing = false;
        needsTextProcessing = false;
        needsImageProcessing = false;
    }

    public FileIndexingFlags(boolean needsBasicIndexing, boolean needsTextProcessing, boolean needsImageProcessing) {
        needsBasicIndexing = needsBasicIndexing;
        needsTextProcessing = needsTextProcessing;
        needsImageProcessing = needsImageProcessing;
    }

    public boolean isNeedsBasicIndexing() {
        return needsBasicIndexing;
    }

    public void setNeedsBasicIndexing(boolean needsBasicIndexing) {
        this.needsBasicIndexing = needsBasicIndexing;
    }

    public boolean isNeedsTextProcessing() {
        return needsTextProcessing;
    }

    public void setNeedsTextProcessing(boolean needsTextProcessing) {
        this.needsTextProcessing = needsTextProcessing;
    }

    public boolean isNeedsImageProcessing() {
        return needsImageProcessing;
    }

    public void setNeedsImageProcessing(boolean needsImageProcessing) {
        this.needsImageProcessing = needsImageProcessing;
    }

    public boolean needsAny() {
        return needsBasicIndexing || needsTextProcessing || needsImageProcessing;
    }

    @Override
    public String toString() {
        return "FileIndexingFlags [needsBasicIndexing=" + needsBasicIndexing
                + ", needsTextProcessing=" + needsTextProcessing + ", needsImageProcessing="
                + needsImageProcessing + "]";
    }
}
