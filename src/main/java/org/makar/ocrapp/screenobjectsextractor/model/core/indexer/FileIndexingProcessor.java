package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileIndexingProcessor {

    private final IFileIndexRepository fileIndexRepository;
    private final MetadataExtractor metadataExtractor;
    private final ImageContentAnalyzer imageContentAnalyzer;
    private final IndexingNeedsAnalyzer doesNeedsIndexing;

    private static final Logger LOGGER = Logger.getLogger(FileIndexingProcessor.class.getName());

    public FileIndexingProcessor(IFileIndexRepository repository, MetadataExtractor metadataExtractor, ImageContentAnalyzer imageContentAnalyzer) {
        this.fileIndexRepository = repository;
        this.metadataExtractor = metadataExtractor;
        this.imageContentAnalyzer = imageContentAnalyzer;
        this.doesNeedsIndexing = new IndexingNeedsAnalyzer();
    }

    FileMetadata processFileWithFlags(Path filePath, SearchCriteria criteria) {

        // ── 1. Чтение существующих данных из индекса ──────────────────────────
        FileMetadata existingMetadata = null;
        try {
            existingMetadata = fileIndexRepository.getMetadataByPath(filePath.toString());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                    "processFileWithFlags: ошибка чтения из индекса, файл пропущен: " + filePath, e);
            return null;
        }

        // ── 2. Анализ потребности в индексации ────────────────────────────────
        IndexingAnalysisResult analysis = doesNeedsIndexing.analyzeFileIndexingNeeds(filePath, existingMetadata);

        // ── 3. Диспетчеризация — компилятор требует покрыть все 4 случая ──────
        return switch(analysis) {
            case IndexingAnalysisResult.UpToDate ignored -> {
                LOGGER.fine("processFileWithFlags: данные актуальны, пропускаем: " + filePath);
                yield existingMetadata;
            }

            case IndexingAnalysisResult.ReadError err -> {
                // Файл есть в индексе, но атрибуты на диске не читаются.
                // Переиндексировать "вслепую" не будем — пропускаем.
                LOGGER.log(Level.WARNING,
                        "processFileWithFlags: ошибка чтения атрибутов файла, пропускаем: " + filePath,
                        err.cause());
                yield null;
            }

            case IndexingAnalysisResult.NeverIndexed ignored -> {
                LOGGER.fine("processFileWithFlags: файл не в индексе → полная индексация: " + filePath);
                try {
                    // existingMetadata здесь всегда null — передаём явно
                    yield processFileIncrementally(filePath, null,
                            true, true, true, criteria);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "processFileWithFlags: ошибка при первичной индексации: " + filePath, e);
                    yield null;
                }
            }

            case IndexingAnalysisResult.NeedsUpdate u -> {
                LOGGER.fine("processFileWithFlags: данные неполные/устаревшие → обновление: " + filePath);
                FileIndexingFlags flags = u.flags();
                try {
                    yield processFileIncrementally(filePath, existingMetadata,
                            flags.isNeedsBasicIndexing(),
                            flags.isNeedsTextProcessing(),
                            flags.isNeedsImageProcessing(),
                            criteria);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "processFileWithFlags: ошибка при обновлении индекса: " + filePath, e);
                    yield null;
                }
            }

        };

    }

    /**
     * Индексирует один файл без контекста критериев поиска.

     * @param filePath путь к файлу
     * @return FileMetadata после индексации, или null если файл недоступен
     */
    public FileMetadata processFile(Path filePath) {
        return processFileWithFlags(filePath, SearchCriteria.createEmpty());
    }


    /**
     * Выполняет инкрементальную обработку файла, сохраняя прогресс в индексе.
     * Соответствует вашему "потоку 2_1".
     *
     * @param filePath Путь к файлу для обработки.
     * @param existingMetadata Существующие метаданные файла из индекса (может быть null для нового файла).
     * @param needsBasicIndexing Нужно ли извлекать базовые метаданные.
     * @param needsTextProcessing Нужно ли распознавать текст.
     * @param needsImageProcessing Нужно ли анализировать изображение.
     * @param criteria Текущие критерии поиска для приоритизации.
     * @throws SQLException Если возникла ошибка при работе с базой данных.
     */
    private FileMetadata processFileIncrementally(Path filePath, FileMetadata existingMetadata,
                                                  boolean needsBasicIndexing, boolean needsTextProcessing,
                                                  boolean needsImageProcessing, SearchCriteria criteria) throws SQLException, IOException {
        System.out.println("::FileIndexService.processFileIncrementally()");
        FileMetadata metadataToSave = existingMetadata;
        boolean changed = false;

        // 1. Извлечение/обновление базовых метаданных
        if (needsBasicIndexing || existingMetadata == null) {
            FileMetadata basicMetadata = metadataExtractor.extract(filePath);
            if (basicMetadata == null) {
                LOGGER.warning("::FileIndexService.processFileIncrementally : Не удалось извлечь базовые метаданные для файла: " + filePath + ". Пропускаем.");
                return null;
            }

            if (metadataToSave == null) { // Новый файл
                metadataToSave = basicMetadata;
                changed = true;
            } else { // Обновление существующего файла (после модификации на ФС)
                metadataToSave.setFilePath(basicMetadata.getFilePath());
                metadataToSave.setFileName(basicMetadata.getFileName());
                metadataToSave.setFileExtension(basicMetadata.getFileExtension());
                metadataToSave.setFileSize(basicMetadata.getFileSize());
                metadataToSave.setCreationDate(basicMetadata.getCreationDate());
                metadataToSave.setModificationDate(basicMetadata.getModificationDate());
                // Очищаем контент-поля, так как файл мог измениться.
                metadataToSave.setRecognizedTextContent(new ArrayList<>()); needsTextProcessing = true;
                metadataToSave.setDetectedObjects(new ArrayList<>()); needsImageProcessing = true;
                changed = true;
            }
            //fileIndexRepository.save(metadataToSave);
            LOGGER.info("::FileIndexService.processFileIncrementally : Базовые метаданные для " + filePath + " проиндексированы/обновлены.");
        }

        // 2. Распознавание текста (OCR)
        if (needsTextProcessing && metadataToSave != null) {
            try {
                List<TextObject> recognizedText = imageContentAnalyzer.recognizeText(filePath);
                if (!Objects.equals(metadataToSave.getRecognizedTextContent(), recognizedText)) {
                    metadataToSave.setRecognizedTextContent(recognizedText);
                    //fileIndexRepository.save(metadataToSave);
                    LOGGER.info("::FileIndexService.processFileIncrementally : Текст распознан и проиндексирован для " + filePath);
                    changed = true;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "::FileIndexService.processFileIncrementally : Не удалось выполнить OCR для " + filePath + ": " + e.getMessage());
                // Можно сохранить пустой список или флаг ошибки, чтобы не пытаться снова в ближайшее время. Важно что-то записать.
                if (metadataToSave.getRecognizedTextContent() == null || !metadataToSave.getRecognizedTextContent().isEmpty()) {
                    metadataToSave.setRecognizedTextContent(new ArrayList<>());
                    //fileIndexRepository.save(metadataToSave);
                    changed = true;
                }
            }
        }

        // 3. Анализ содержимого изображения (AI)
        if (needsImageProcessing && metadataToSave != null) {
            try {
                List<OCRAppDetectedObject> detectedObjects = imageContentAnalyzer.analyzeObjects(filePath);
                metadataToSave.setDetectedObjects(detectedObjects);
                //fileIndexRepository.save(metadataToSave);
                LOGGER.info("::FileIndexService.processFileIncrementally : Содержимое изображения проанализировано и проиндексировано для " + filePath);
                metadataToSave.showObjects();
                changed = true;
            } catch (Exception e) {
                LOGGER.warning("::FileIndexService.processFileIncrementally : Не удалось выполнить AI-анализ для " + filePath + ": " + e.getMessage());
                e.printStackTrace();
                if (metadataToSave.getDetectedObjects() == null || !metadataToSave.getDetectedObjects().isEmpty()) {
                    metadataToSave.setDetectedObjects(new ArrayList<>());
                    //fileIndexRepository.save(metadataToSave);
                    changed = true;
                }
            }
        }
        if (changed) {
            System.out.println("::FileIndexService.processFileIncrementally : информация о файле была дополнена");
        }
        if (metadataToSave != null) {
            System.out.println("::FileIndexService.processFileIncrementally : отдаёт не null");
            System.out.println(metadataToSave.toString());
            fileIndexRepository.save(metadataToSave);
        }
        System.out.println("::FileIndexService.processFileIncrementally : отдаёт null");
        return metadataToSave;
    }
}
