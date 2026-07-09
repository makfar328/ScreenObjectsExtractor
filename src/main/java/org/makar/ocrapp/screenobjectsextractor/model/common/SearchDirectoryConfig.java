package org.makar.ocrapp.screenobjectsextractor.model.common;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.nio.file.Path;
import java.util.Objects;

public class SearchDirectoryConfig {
    private final Path directory;
    private final IntegerProperty searchDepth = new SimpleIntegerProperty(1); // Глубина поиска по умолчанию: 1

    public SearchDirectoryConfig(Path directory) {
        this.directory = directory;
    }

    public SearchDirectoryConfig(Path directory, int searchDepth) {
        this.directory = directory;
        this.searchDepth.set(searchDepth);
    }

    public Path getDirectory() {
        return directory;
    }

    public int getSearchDepth() {
        return searchDepth.get();
    }

    public IntegerProperty searchDepthProperty() {
        return searchDepth;
    }

    public void setSearchDepth(int searchDepth) {
        if (searchDepth >= 0) { // Глубина поиска не может быть отрицательной
            this.searchDepth.set(searchDepth);
        } else {
            this.searchDepth.set(0); // Если попытка установить <0, сбрасываем до 0
        }
    }

    // оно же final, о чем я думал?
    public void incrementSearchDepth() {
        setSearchDepth(getSearchDepth() + 1);
    }

    public void decrementSearchDepth() {
        if (getSearchDepth() > 0) { // Не уменьшаем ниже 0
            setSearchDepth(getSearchDepth() - 1);
        }
    }


    public String getPath() {
        return directory.toString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchDirectoryConfig that = (SearchDirectoryConfig) o;
        return Objects.equals(directory, that.directory); // Сравнение только по пути
    }

    @Override
    public int hashCode() {
        return Objects.hash(directory);
    }

    @Override
    public String toString() {
        return directory.toString() + " (Depth: " + searchDepth.get() + ")";
    }

}