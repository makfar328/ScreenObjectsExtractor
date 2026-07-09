package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FileIndexService {
    private static final Logger LOGGER = Logger.getLogger(FileIndexService.class.getName());

    private final IFileIndexRepository fileIndexRepository;
    private final FileIndexingProcessor processor;
    private final FileScanner fileScanner;
    private final MetadataExtractor metadataExtractor;
    private final ImageContentAnalyzer imageContentAnalyzer;
    private final Executor indexingServiceExecutor; // Dedicated executor for indexing


    public FileIndexService(IFileIndexRepository fileIndexRepository,
                            FileIndexingProcessor processor,
                            FileScanner fileScanner,
                            MetadataExtractor metadataExtractor,
                            ImageContentAnalyzer imageContentAnalyzer,
                            Executor indexingServiceExecutor) {
        this.fileIndexRepository = fileIndexRepository;
        this.processor = processor;
        this.fileScanner = fileScanner;
        this.metadataExtractor = metadataExtractor;
        this.imageContentAnalyzer = imageContentAnalyzer;
        this.indexingServiceExecutor = indexingServiceExecutor;

        LOGGER.info("FileIndexService инициализирован успешно.");
    }


    /**
     * не используется
     * Определяет статус индексации указанной директории.
     * Этот метод должен быть реализован для проверки, проиндексирована ли директория,
     * и нужно ли ее переиндексировать.
     *
     * @param directory Путь к директории.
     * @param depth Глубина поиска для директории.
     * @return Текущий статус индексации директории.
     */
    public IndexingStatus getIndexingStatus(Path directory, int depth) {
        // TODO: Реализовать логику проверки статуса индексации.

        LOGGER.fine("Проверка статуса индексации для директории: " + directory);

        return IndexingStatus.NOT_INDEXED;
    }


    /**
     * Перечисление, определяющее возможные статусы индексации директории.
     */
    public enum IndexingStatus {
        FULLY_INDEXED,   // Директория полностью проиндексирована и актуальна
        NEEDS_REINDEX,   // Директория была проиндексирована, но нуждается в обновлении
        NOT_INDEXED,     // Директория никогда не была проиндексирована
        IN_PROGRESS      // Индексация директории в данный момент выполняется
    }


    /**
     * Выполняет инкрементальную индексацию файлов в указанной директории
     * на основе предоставленных критериев поиска.
     *
     * @param directoryConfig Конфигурация директории для индексации.
     * @param criteria Критерии поиска для приоритизации и определения необходимой обработки.
     * @return CompletableFuture, содержащий список FileMetadata, которые были только что проиндексированы или обновлены
     *         и теперь соответствуют критериям поиска.
     */
    public CompletableFuture<List<FileMetadata>> performIncrementalIndexing(SearchDirectoryConfig directoryConfig, SearchCriteria criteria) {
        System.out.println("::FileIndexService.performIncrementalIndexing()");
        return CompletableFuture.supplyAsync(() -> {
            List<FileMetadata> updatedMetadataList = new ArrayList<>();
            try {
                List<Path> imageFiles = fileScanner.scanDirectory(directoryConfig.getDirectory(), directoryConfig.getSearchDepth());

                System.out.println("imageFiles: " + imageFiles);

                updatedMetadataList = imageFiles.stream()
                        .map(filePath -> processor.processFileWithFlags(filePath, criteria))
                        .filter(Objects::nonNull)
                        .filter(file -> FileMetadataFilter.matchesCriteria(file, criteria))
                        .collect(Collectors.toList());

                /*for (Path filePath : imageFiles) {
                    FileMetadata updated = processFileWithFlags(filePath, criteria); // проблема тут, возвращается null
                    if (updated != null) {
                        System.out.println("::FileIndexService.performIncrementalIndexing() : processFileWithFlags вернул не null");
                        updatedMetadataList.add(updated);
                    } else {
                        System.out.println("::FileIndexService.performIncrementalIndexing() : processFileWithFlags вернул null");
                    }
                }*/

                if (updatedMetadataList.isEmpty()) System.out.println("::FileIndexService.performIncrementalIndexing() : updatedMetadataList.size() == 0");
                for (int i = 0; i < updatedMetadataList.size(); i++) {
                    System.out.println("----- " + i + " ----->");
                    updatedMetadataList.get(i).showObjects();
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Ошибка при инкрементальной доиндексации директории " + directoryConfig.getDirectory(), e);
            }
            return updatedMetadataList;
        }, indexingServiceExecutor);
    }


    // Аналог performIncrementalIndexing, но без фильтрации по criteria
    public CompletableFuture<List<FileMetadata>> performFullIncrementalIndexing(SearchDirectoryConfig directoryConfig) {
        return CompletableFuture.supplyAsync(() -> {
            List<FileMetadata> updatedMetadataList = new ArrayList<>();
            try {
                List<Path> allFiles = fileScanner.scanDirectory(directoryConfig.getDirectory());
                for (Path filePath : allFiles) {
                    FileMetadata updated = processor.processFileWithFlags(filePath, null);
                    if (updated != null) {
                        updatedMetadataList.add(updated);
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("::FileIndexService.performFullIncrementalIndexing : Ошибка при полной доиндексации директории " + directoryConfig.getDirectory() + ": " + e.getMessage());
            }
            return updatedMetadataList;
        }, indexingServiceExecutor);
    }


    /* DecomposeSession над файлом из файловой системы */
    public void saveAnalyzedFile(FileMetadata fileMetadata) {
        if (fileMetadata == null) {
            LOGGER.warning("saveAnalyzedFile: fileMetadata == null, пропускаем.");
            return;
        }
        try {
            fileIndexRepository.save(fileMetadata);
            LOGGER.info("saveAnalyzedFile: сохранён в индекс: " + fileMetadata.getFileName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "saveAnalyzedFile: ошибка сохранения в индекс: " + e.getMessage(), e);
        }
    }

    /*
    fileScanner.scanDirectory(...) возвращает Stream<Path>, который, скорее всего, основан на DirectoryStream или Files.walk()/Files.list().
            - Такие стримы используют внутренний итератор файловой системы (FileTreeIterator), который автоматически закрывается после первой итерации или при ошибке.
            Попытка воспользоваться (map) таким stream 2й раз приведет к IllegalStateException
     */

}