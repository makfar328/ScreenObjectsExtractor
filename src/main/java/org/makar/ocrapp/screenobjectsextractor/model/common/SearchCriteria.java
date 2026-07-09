package org.makar.ocrapp.screenobjectsextractor.model.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/* Представляет собой структурированный запрос. Объект, инкапсулирующий все параметры поиска. */
public class SearchCriteria {
    private final long id;
    /* fileName */
    private final List<String> fileNames;
    /* Ключевые слова для поиска в именах файлов или метаданных (может быть и в именах каталогов) */
    private final List<String> keywords;
    /* Ключевые слова для описания содержимого сцены на фотографии (название класса, цвет, поза и т.п.) */
    private final List<SelectedObjectClass> entries;
    /* Глобальная минимальная вероятность соответствия всех классов entries */
    private final Double globalMinProbability;
    /* Минимальная дата */
    private final LocalDate minDate;
    /* Максимальная дата */
    private final LocalDate maxDate;
    /* Директории, где будет производиться поиск */
    private final List<SearchDirectoryConfig> targetDirectories;
    /* типы файлов, хоть это пользователь вряд ли будет указывать. */
    private final List<String> fileTypes;

    public SearchCriteria(
            List<String> fileNames,
            List<String> keywords,
            List<SelectedObjectClass> entries,
            Double globalMinProbability,
            LocalDate minDate,
            LocalDate maxDate,
            List<SearchDirectoryConfig> targetDirectories,
            List<String> fileTypes
            ) {
        this.id = -1;
        this.fileNames = fileNames != null ? Collections.unmodifiableList(fileNames) : new ArrayList<String>();
        // null лучше не оставлять
        this.keywords = keywords != null ? Collections.unmodifiableList(keywords) : new ArrayList<String>();
        this.entries = entries != null ? Collections.unmodifiableList(entries) : new ArrayList<SelectedObjectClass>();
        this.globalMinProbability = globalMinProbability;
        this.minDate = minDate;
        this.maxDate = maxDate;
        this.targetDirectories = targetDirectories != null ? Collections.unmodifiableList(targetDirectories) : new ArrayList<SearchDirectoryConfig>();
        this.fileTypes = fileTypes != null ? Collections.unmodifiableList(fileTypes) : new ArrayList<String>();
    }

    public SearchCriteria(
            long id,
            List<String> fileNames,
            List<String> keywords,
            List<SelectedObjectClass> entries,
            Double globalMinProbability,
            LocalDate minDate,
            LocalDate maxDate,
            List<SearchDirectoryConfig> targetDirectories,
            List<String> fileTypes
    ) {
        this.id = id;
        this.fileNames = fileNames != null ? Collections.unmodifiableList(fileNames) : new ArrayList<String>();
        // null лучше не оставлять
        this.keywords = keywords != null ? Collections.unmodifiableList(keywords) : new ArrayList<String>();
        this.entries = entries != null ? Collections.unmodifiableList(entries) : new ArrayList<SelectedObjectClass>();
        this.globalMinProbability = globalMinProbability;
        this.minDate = minDate;
        this.maxDate = maxDate;
        this.targetDirectories = targetDirectories != null ? Collections.unmodifiableList(targetDirectories) : new ArrayList<SearchDirectoryConfig>();
        this.fileTypes = fileTypes != null ? Collections.unmodifiableList(fileTypes) : new ArrayList<String>();
    }

    public boolean isEmpty() {
        return this.equals(this.createEmpty());
    }

    public static SearchCriteria createEmpty() {
        return new SearchCriteria(
                -1,
                List.of(),
                List.of(),
                List.of(),
                0.0,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    public static Object SearchCriteriaBuilder() {
        return null;
    }

    public long getId() {
        return id;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<SelectedObjectClass> getEntries() {
        return entries;
    }

    public Double getGlobalMinProbability() {
        return globalMinProbability;
    }

    public LocalDate getMinDate() {
        return minDate;
    }

    public LocalDate getMaxDate() {
        return maxDate;
    }

    public List<SearchDirectoryConfig> getTargetDirectories() {
        return targetDirectories;
    }

    public List<String> getFileTypes() {
        return fileTypes;
    }

    public boolean hasFileNames() {
        return !fileNames.isEmpty();
    }

    public boolean hasTextKeywords() {
        return keywords != null && keywords.stream().anyMatch(k -> !k.isEmpty());
    }

    public boolean hasObjectEntries() {
        return entries != null && !entries.isEmpty();
    }

    public boolean hasFileTypes() {
        return fileTypes != null && !fileTypes.isEmpty();
    }

    public boolean hasMinDate() {
        return minDate != null;
    }

    public boolean hasMaxDate() {
        return maxDate != null;
    }

    @Override
    public String toString() {
        return "SearchCriteria [fileNames=" + fileNames +
                ", keywords=" + keywords +
                ", entries=" + entries.toString() +
                ", globalMinProbability=" + globalMinProbability +
                ", minDate=" + minDate +
                ", maxDate=" + maxDate +
                ", targetDirectories=" + targetDirectories +
                ", fileTypes=" + fileTypes + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        SearchCriteria that = (SearchCriteria) o;
        SearchCriteria oo = (SearchCriteria) o;
        boolean isEqual = Objects.equals(fileNames, oo.fileNames) &&
                Objects.equals(keywords, oo.keywords) &&
                Objects.equals(entries, oo.entries) &&
                Objects.equals(globalMinProbability, oo.globalMinProbability) &&
                Objects.equals(minDate, oo.minDate) &&
                Objects.equals(maxDate, oo.maxDate) &&
                Objects.equals(targetDirectories, oo.targetDirectories) &&
                Objects.equals(fileTypes, oo.fileTypes);
        return isEqual;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileNames, keywords, entries, globalMinProbability, minDate, maxDate, targetDirectories, fileTypes);
    }
}
