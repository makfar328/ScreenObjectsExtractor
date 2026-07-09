package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SelectedObjectClass;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;

import java.util.List;
import java.util.logging.Logger;

public class FileMetadataFilter {
    private static final Logger logger = Logger.getLogger(FileMetadataFilter.class.getName());

    public static boolean matchesCriteria(FileMetadata file, SearchCriteria criteria) {
        System.out.println("Перед фильтрацией: detectedObjects=" + file.getDetectedObjects());
        if (file.getDetectedObjects() != null) {
            System.out.println("Размер списка: " + file.getDetectedObjects().size());
        }
        return matchesFileName(file, criteria)
                && matchesFileType(file, criteria)
                && matchesMinDate(file, criteria)
                && matchesMaxDate(file, criteria)
                && matchesTextContent(file, criteria)
                && matchesDetectedObjects(file, criteria);
    }

    // --- Фильтры по полям ---
    private static boolean matchesFileName(FileMetadata file, SearchCriteria criteria) {
        if (criteria.getFileNames() == null || criteria.getFileNames().isEmpty()) return true;
        return criteria.getFileNames().contains(file.getFileName());
    }

    private static boolean matchesFileType(FileMetadata file, SearchCriteria criteria) {
        if (criteria.getFileTypes() == null || criteria.getFileTypes().isEmpty()) return true;
        return criteria.getFileTypes().contains(file.getFileExtension().toLowerCase());
    }

    private static boolean matchesMinDate(FileMetadata file, SearchCriteria criteria) {
        if (criteria.getMinDate() == null) return true;
        return !file.getModificationDate().toLocalDate().isBefore(criteria.getMinDate());
    }

    private static boolean matchesMaxDate(FileMetadata file, SearchCriteria criteria) {
        if (criteria.getMaxDate() == null) return true;
        return !file.getModificationDate().toLocalDate().isAfter(criteria.getMaxDate());
    }

    // фильтр: по содержимому текста (OCR)
    private static boolean matchesTextContent(FileMetadata file, SearchCriteria criteria) {
        List<String> keywords = criteria.getKeywords();
        if (keywords == null || keywords.isEmpty()) return true;
        List<TextObject> textObjects = file.getRecognizedTextContent();
        if (textObjects == null || textObjects.isEmpty()) return false;

        // Проверяем, что хотя бы одно ключевое слово встречается в любом из распознанных текстов
        for (TextObject textObj : textObjects) {
            String text = textObj.getText().toLowerCase();
            for (String keyword : keywords) {
                if (text.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    // Новый фильтр: по обнаруженным объектам
    private static boolean matchesDetectedObjects(FileMetadata file, SearchCriteria criteria) {
        System.out.println("matchesDetectedObjects called");
        List<SelectedObjectClass> entries = criteria.getEntries();
        if (entries == null || entries.isEmpty()) { System.out.println("entries == null or entries.isEmpty()"); return true; }
        List<OCRAppDetectedObject> detectedObjects = file.getDetectedObjects();
        if (detectedObjects == null || detectedObjects.isEmpty()) { System.out.println("detectedObjects == null || detectedObjects.isEmpty()"); return false; }

        // Для каждого искомого класса ищем совпадение среди обнаруженных объектов
        for (SelectedObjectClass entry : entries) {
            for (OCRAppDetectedObject detectedObject : detectedObjects) {
                logger.info("{+" + entry + "} - {" + detectedObject.getClassName() +"}");
                System.out.println("{+" + entry + "} - {" + detectedObject.getClassName() +"}");
            }
            boolean found = detectedObjects.stream().anyMatch(obj ->
                    obj.getClassName().equalsIgnoreCase(entry.getClassName())
            );
            if (!found) return false; // Если хотя бы один класс не найден — файл не подходит
        }
        return true;
    }
}