package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexingNeedsAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(IndexingNeedsAnalyzer.class.getName());

    /**
     * Анализирует потребность в (пере)индексации для конкретного файла.
     *
     * Возвращает sealed IndexingAnalysisResult — явно различает:
     *   - NeverIndexed: файл отсутствует в индексе
     *   - ReadError:    чтение из БД завершилось ошибкой (не силентим!)
     *   - UpToDate:     данные актуальны
     *   - NeedsUpdate:  данные устарели, флаги описывают что именно обновить
     *
     * @param filePath         абсолютный путь к файлу на диске
     * @param existingMetadata результат чтения из репозитория; null означает отсутствие в индексе
     */
    public IndexingAnalysisResult analyzeFileIndexingNeeds(Path filePath, FileMetadata existingMetadata) {

        // ── 1. Файл не найден в индексе ──────────────────────────────────────
        if (existingMetadata == null) {
            LOGGER.fine("analyzeFileIndexingNeeds: файл не в индексе -> NeverIndexed: " + filePath);
            return new IndexingAnalysisResult.NeverIndexed();
        }

        // ── 2. Данные есть, но неполные (ошибка предыдущей индексации) ───────
        boolean missingObjects = existingMetadata.getDetectedObjects() == null
                || existingMetadata.getDetectedObjects().isEmpty();
        boolean missingText    = existingMetadata.getRecognizedTextContent() == null
                || existingMetadata.getRecognizedTextContent().isEmpty();

        if (missingObjects || missingText) {
            LOGGER.fine("analyzeFileIndexingNeeds: неполные данные -> NeedsUpdate(full): " + filePath);
            return new IndexingAnalysisResult.NeedsUpdate(
                    new FileIndexingFlags(true, missingText, missingObjects)
            );
        }

        // ── 3. Проверка актуальности по дате модификации ──────────────────────
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            Instant diskModified = attrs.lastModifiedTime().toInstant();
            Instant indexedAt   = existingMetadata.getModificationDate()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();

            if (diskModified.isAfter(indexedAt)) {
                LOGGER.fine("analyzeFileIndexingNeeds: файл изменён -> NeedsUpdate(full): " + filePath);
                return new IndexingAnalysisResult.NeedsUpdate(new FileIndexingFlags(true, true, true));
            }
        } catch (IOException e) {
            // Файл есть в индексе, но не читается с диска — это ошибка, не "всё ок"
            LOGGER.log(Level.WARNING,
                    "analyzeFileIndexingNeeds: не удалось прочитать атрибуты файла: " + filePath, e);
            return new IndexingAnalysisResult.ReadError(e);
        }

        // -- 4. Данные актуальны --------------------------------------------
        LOGGER.fine("analyzeFileIndexingNeeds: данные актуальны -> UpToDate: " + filePath);
        return new IndexingAnalysisResult.UpToDate();
    }
}
