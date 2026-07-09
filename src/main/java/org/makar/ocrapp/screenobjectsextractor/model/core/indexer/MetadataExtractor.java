package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject; // Важно, если в FileMetadata нужны списки TextObject

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс для извлечения базовых метаданных из файлов.
 * Отвечает за получение имени файла, расширения, размера, дат создания и модификации.
 */
public class MetadataExtractor {

    private static final Logger LOGGER = Logger.getLogger(MetadataExtractor.class.getName());

    public MetadataExtractor() {
        LOGGER.info("MetadataExtractor инициализирован.");
    }

    /**
     * Извлекает базовые метаданные из указанного файла и возвращает объект FileMetadata.
     *
     * @param filePath Путь к файлу, из которого нужно извлечь метаданные.
     * @return Объект FileMetadata, содержащий извлеченные данные.
     * @throws IOException Если произошла ошибка при доступе к файлу.
     */
    public FileMetadata extract(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String fileExtension = "";
        long fileSize = 0;
        LocalDateTime creationDate = null;
        LocalDateTime modificationDate = null;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(dotIndex + 1).toLowerCase();
            fileName = fileName.substring(0, dotIndex); // Имя файла без расширения
        }

        try {
            BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
            fileSize = attributes.size();
            creationDate = LocalDateTime.ofInstant(attributes.creationTime().toInstant(), ZoneId.systemDefault());
            modificationDate = LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Не удалось прочитать атрибуты файла: " + filePath + ". Сообщение: " + e.getMessage(), e);
            // Продолжаем с частично заполненными метаданными или выбрасываем исключение,
            // в зависимости от того, насколько критичны эти поля.
            // В данном случае, выбрасываем, так как FileIndexService ожидает полноценный объект.
            throw e;
        }

        //recognizedTextContent и detectedObjectClasses инициализируются пустыми списками,
        //их заполнит ImageContentAnalyzer, если файл является изображением.
        List<TextObject> recognizedTextContent = Collections.emptyList();
        List<OCRAppDetectedObject> detectedObjects = Collections.emptyList();


        return new FileMetadata(
                filePath,
                fileName,
                fileExtension,
                fileSize,
                creationDate,
                modificationDate,
                recognizedTextContent,
                detectedObjects
        );
    }
}