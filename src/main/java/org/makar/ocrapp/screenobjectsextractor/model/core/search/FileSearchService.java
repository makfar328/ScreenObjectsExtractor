package org.makar.ocrapp.screenobjectsextractor.model.core.search;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.FileIndexService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.FileIndexRepository;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Основной сервис для поиска по файловой системе.
 *
 * Два режима работы:
 *   - searchExistingIndex  — быстрый путь: прямой запрос к репозиторию, без индексации.
 *   - initiateBackgroundIndexing — медленный путь: инициирует индексацию через FileIndexService.
 *
 * Зависит от IFileIndexRepository (интерфейс), а не от FileIndexRepository (класс),
 * что позволяет подменять реализацию в тестах без изменений здесь.
 */
public class FileSearchService {

    private static final Logger LOGGER = Logger.getLogger(FileSearchService.class.getName());
    private final FileIndexService fileIndexService;
    private final IFileIndexRepository fileIndexRepository;
    private final ExecutorService searchServiceExecutor;


    public FileSearchService(FileIndexService fileIndexService,
                             IFileIndexRepository fileIndexRepository,
                             ExecutorService searchServiceExecutor) {
        this.fileIndexService = fileIndexService;
        this.fileIndexRepository = fileIndexRepository;
        this.searchServiceExecutor = searchServiceExecutor;
        LOGGER.info("FileSearchService: инициализирован успешно.");
    }


    /**
     * Выполняет быстрый поиск по существующему индексу базы данных, используя предоставленные критерии.
     * Этот метод должен быть максимально быстрым и не зависеть от фоновой индексации.
     *
     * @param criteria Критерии поиска.
     * @return CompletableFuture, содержащий список найденных FileMetadata.
     */
    public CompletableFuture<List<FileMetadata>> searchExistingIndex(SearchCriteria criteria) {
        LOGGER.info("Запущен быстрый поиск по существующему индексу с критериями: " + criteria.toString());
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Прямой запрос к репозиторию
                return fileIndexRepository.search(criteria);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Ошибка при выполнении быстрого поиска по индексу: " + e.getMessage(), e); // Добавил полный стек-трейс
                return new ArrayList<>();
            }
        }, searchServiceExecutor);
    }


    /**
     * Инициирует фоновую инкрементальную индексацию для директорий, указанных в SearchCriteria.
     * Этот метод запускает асинхронные задачи обработки файлов и возвращает
     * список FileMetadata, которые были обновлены или вновь проиндексированы
     * в процессе и соответствуют текущим критериям поиска.
     *
     * @param criteria Критерии, содержащие целевые директории для потенциальной индексации.
     * @return CompletableFuture, который завершится, когда все необходимые задачи индексации будут выполнены,
     *         и будет содержать список обновленных/новых FileMetadata.
     */
    public CompletableFuture<List<FileMetadata>> initiateBackgroundIndexing(SearchCriteria criteria) {
        LOGGER.info("Запущено инициирование фоновой инкрементальной индексации для критериев: " + criteria.toString());

        List<CompletableFuture<List<FileMetadata>>> indexingTasks = new ArrayList<>();

        for (SearchDirectoryConfig directoryConfig : criteria.getTargetDirectories()) {
            // Вызываем метод FileIndexService для выполнения инкрементальной индексации
            // и сбора обновленных/новых метаданных.
            indexingTasks.add(fileIndexService.performIncrementalIndexing(directoryConfig, criteria));
        }

        // Собираем результаты всех CompletableFuture в один список.
        return CompletableFuture.allOf(indexingTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> indexingTasks.stream()
                        .flatMap(future -> future.join().stream()) // Объединяем списки из всех задач
                        .collect(Collectors.toList()))
                .exceptionally(ex -> {
                    LOGGER.severe("Одна или несколько задач фоновой индексации завершились с ошибкой: " + ex.getMessage());
                    return new ArrayList<>(); // В случае ошибки возвращаем пустой список
                });
    }



    /*public FileSearchResults search(SearchCriteria criteria) {

        System.out.println("FileSearchResults.search started.");

        // 1. Проверка и, при необходимости, инициирование индексации директорий.
        // Этот шаг может быть длительным и, возможно, должен выполняться асинхронно
        // или инициироваться пользователем до начала поиска.
        // Для демонстрации предполагаем, что FileIndexService имеет метод 'ensureIndexed'.
        for (SearchDirectoryConfig directoryConfig: criteria.getTargetDirectories()) {
            try {
                // TODO: Реализовать логику ensureIndexed в FileIndexService.
                // Этот метод будет запускать FileScanner, MetadataExtractor, ImageContentAnalyzer
                // если директория не проиндексирована или индекс устарел.
                fileIndexService.ensureIndexed(directoryConfig);
                System.out.println("FileSearchResults.search directoryConfig: " + directoryConfig);
            } catch (Exception exception) {
                System.err.println("FileSearchResults.search directoryConfig: " + directoryConfig);
                continue;
            }
        }

        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM indexed_files WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!criteria.getKeywords().isEmpty()) {
            sqlBuilder.append(" AND (");
            for (int i = 0; i < criteria.getKeywords().size(); i++) {
                if (i > 0) { sqlBuilder.append(" OR "); }
                sqlBuilder.append("file_name LIKE ? OR recognized_text_content LIKE ?");
                params.add("%" + criteria.getKeywords().get(i) + "%");
                params.add("%" + criteria.getKeywords().get(i) + "%");
            }
            sqlBuilder.append(")");
        }

        if (criteria.getEntries().isEmpty()) {

        }

        if (criteria.getMinDate() != null) {

        }
        if (criteria.getMaxDate() != null) {

        }

        if (criteria.getTargetDirectories().isEmpty()) {

        }

        if (criteria.getFileTypes().isEmpty()) {

        }


        List<FileMetadata> rawDbResults;
        try {
            rawDbResults = fileIndexService.search(sqlBuilder.toString(), params);
            System.out.println("Database query executed. Found " + rawDbResults.size() + " raw results.");
        } catch (SQLException sqlException) {
            System.err.println("Database query executed failed.");
            return new FileSearchResults(new ArrayList<>());
        }

        // Потом здесь отработает класс сервиса ранжирования результатов. Хотя лучше вынести вызов этой логики отсюда

        return new FileSearchResults(rawDbResults);
    }*/

}


