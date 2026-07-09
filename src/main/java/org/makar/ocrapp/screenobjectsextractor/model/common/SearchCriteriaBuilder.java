package org.makar.ocrapp.screenobjectsextractor.model.common;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Вложенный статический класс Builder для удобного и безопасного создания экземпляров SearchCriteria,
 * особенно когда многие поля опциональны.
 */
public class SearchCriteriaBuilder {
    private long id = -1;
    private List<String> fileNames = null;
    private List<String> keywords = Collections.emptyList();
    private List<SelectedObjectClass> entries = Collections.emptyList();
    private Double globalMinProbability = null;
    private LocalDate minDate = null;
    private LocalDate maxDate = null;
    private List<SearchDirectoryConfig> targetDirectories = Collections.emptyList();
    private List<String> fileTypes = Collections.emptyList();

    public SearchCriteriaBuilder withId(long id) {
        this.id = id;
        return this;
    }

    public SearchCriteriaBuilder withFileNames(List<String> fileNames) {
        this.fileNames = fileNames;
        return this;
    }

    public SearchCriteriaBuilder withKeywords(List<String> keywords) {
        this.keywords = keywords;
        return this;
    }

    public SearchCriteriaBuilder withEntries(List<SelectedObjectClass> entries) {
        this.entries = entries;
        return this;
    }

    public SearchCriteriaBuilder withGlobalMinProbability(Double globalMinProbability) {
        this.globalMinProbability = globalMinProbability;
        return this;
    }

    public SearchCriteriaBuilder withMinDate(LocalDate minDate) {
        this.minDate = minDate;
        return this;
    }

    public SearchCriteriaBuilder withMaxDate(LocalDate maxDate) {
        this.maxDate = maxDate;
        return this;
    }


    public SearchCriteriaBuilder withTargetDirectories(List<SearchDirectoryConfig> targetDirectories) {
        this.targetDirectories = targetDirectories;
        return this;
    }

    public SearchCriteriaBuilder withFileTypes(List<String> fileTypes) {
        this.fileTypes = fileTypes;
        return this;
    }

    public SearchCriteria build() {
        return new SearchCriteria(
                id,
                fileNames,
                keywords,
                entries,
                globalMinProbability,
                minDate,
                maxDate,
                targetDirectories,
                fileTypes
        );
    }


    public SearchCriteria withTargetDirectories(SearchCriteria oldCriteria, List<SearchDirectoryConfig> newConfigs) {
        return new SearchCriteriaBuilder()
                .withId(oldCriteria.getId())
                .withFileNames(oldCriteria.getFileNames())
                .withKeywords(oldCriteria.getKeywords())
                .withEntries(oldCriteria.getEntries())
                .withGlobalMinProbability(oldCriteria.getGlobalMinProbability())
                .withMinDate(oldCriteria.getMinDate())
                .withMaxDate(oldCriteria.getMaxDate())
                .withTargetDirectories(newConfigs)
                .withFileTypes(oldCriteria.getFileTypes())
                .build();
    }

    public SearchCriteria initEmpty() {
        return new SearchCriteriaBuilder()
                .withId(-1)
                .withFileNames(List.of())
                .withKeywords(List.of())
                .withEntries(List.of())
                .withGlobalMinProbability(0.0)
                .withMinDate(null)
                .withMaxDate(null)
                .withTargetDirectories(List.of())
                .withFileTypes(List.of())
                .build();
    }

}
