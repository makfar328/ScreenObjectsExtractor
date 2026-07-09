package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class FileFilterUtils {
    private static final Logger LOGGER = Logger.getLogger(FileFilterUtils.class.getName());

    public List<Path> filterWithCriteria(List<Path> files, SearchCriteria criteria) {
        List<Path> filtered = files;

        if (criteria.hasFileNames()) {
            filtered = filterByFileNames(filtered, criteria.getFileNames());
        }
        if (criteria.hasFileTypes()) {
            filtered = filterByFileTypes(filtered, criteria.getFileTypes());
        }
        if (criteria.hasMinDate()) {
            filtered = filterByMinDate(filtered, criteria.getMinDate());
        }
        if (criteria.hasMaxDate()) {
            filtered = filterByMaxDate(filtered, criteria.getMaxDate());
        }

        LOGGER.info("Фильтрация файлов завершена.");
        return filtered;
    }

    public static List<Path> filterByImageExtension(List<Path> paths, List<String> imageExtensions) {
        return paths.stream()
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                        String extension = fileName.substring(dotIndex + 1).toLowerCase();
                        return imageExtensions.contains(extension);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public static List<Path> filterByFileNames(List<Path> paths, List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return paths;
        return paths.stream()
                .filter(path -> fileNames.contains(path.getFileName().toString()))
                .collect(Collectors.toList());
    }

    public static List<Path> filterByFileTypes(List<Path> paths, List<String> fileTypes) {
        if (fileTypes == null || fileTypes.isEmpty()) return paths;
        List<String> targetExtensions = fileTypes.stream()
                .map(p -> {
                    int dotIndex = p.lastIndexOf('.');
                    return (dotIndex > 0 && dotIndex < p.length() - 1) ? p.substring(dotIndex + 1).toLowerCase() : "";
                })
                .filter(ext -> !ext.isEmpty())
                .collect(Collectors.toList());
        if (targetExtensions.isEmpty()) return paths;
        return paths.stream()
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                        String extension = fileName.substring(dotIndex + 1).toLowerCase();
                        return targetExtensions.contains(extension);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public static List<Path> filterByMinDate(List<Path> paths, LocalDate minDate) {
        if (minDate == null) return paths;
        return paths.stream()
                .filter(path -> {
                    try {
                        LocalDate modificationDate = Files.getLastModifiedTime(path).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return !modificationDate.isBefore(minDate);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Ошибка при получении даты модификации файла " + path + ": " + e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public static List<Path> filterByMaxDate(List<Path> paths, LocalDate maxDate) {
        if (maxDate == null) return paths;
        return paths.stream()
                .filter(path -> {
                    try {
                        LocalDate modificationDate = Files.getLastModifiedTime(path).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return !modificationDate.isAfter(maxDate);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Ошибка при получении даты модификации файла " + path + ": " + e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private static boolean isCloudOnlyFile(Path path) {
        try {
            Map<String, Object> attrs = Files.readAttributes(path, "dos:*");
            Boolean isReparsePoint = (Boolean) attrs.get("reparsePoint");
            return isReparsePoint != null && isReparsePoint;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Ошибка при проверке reparse point для файла " + path + ": " + e.getMessage());
            return true;
        }
    }

    public static List<Path> filterCloudOnlyFiles(List<Path> paths) {
        return paths.stream()
                .filter(path -> !isCloudOnlyFile(path))
                .collect(Collectors.toList());
    }
}