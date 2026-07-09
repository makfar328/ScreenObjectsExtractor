package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Класс для сканирования файловой системы на предмет файлов изображений.
 * Он обходит указанные корневые директории и их поддиректории,
 * фильтруя файлы по заданным расширениям.
 */
public class FileScanner {

    private static final Logger LOGGER = Logger.getLogger(FileScanner.class.getName());
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    public FileScanner() {
        LOGGER.info("FileScanner инициализирован.");
    }


    /**
     * Сканирует указанную корневую директорию и ее поддиректории на наличие файлов изображений.
     * Сканирование выполняется рекурсивно без ограничения глубины.
     *
     * @param rootDirectory
     * @return Список путей к найденным файлам изображений.
     */
    public List<Path> scanDirectory(Path rootDirectory) throws IOException {
        // Вызываем перегруженный метод с максимальной глубиной -1, что означает без ограничения.
        return scanDirectory(rootDirectory, -1);
    }


    /**
     * Сканирует указанную корневую директорию и применяет базовые фильтры файловой системы
     * на основе предоставленных критериев поиска. Этот метод предназначен для первичной фильтрации
     * файлов-кандидатов для индексации.
     *
     * @param rootDirectory Корневая директория для сканирования.
     * @param maxDepth      Максимальная глубина сканирования (-1 для неограниченной глубины).
     *                      (например, по имени файла, типу).
     * @return Список путей к файлам, удовлетворяющим базовым условиям.
     * @throws IOException В случае ошибки при сканировании файловой системы.
     */
    public List<Path> scanDirectory(Path rootDirectory, int maxDepth) throws IOException {
        LOGGER.info("Начато сканирование и базовая фильтрация директорий " + rootDirectory);
        List<Path> fileList = walkDirectoryList(rootDirectory, maxDepth);
        fileList = FileFilterUtils.filterCloudOnlyFiles(fileList);
        fileList = FileFilterUtils.filterByImageExtension(fileList, IMAGE_EXTENSIONS);

        LOGGER.info(String.format("Базовая фильтрация по типу изображения для директории '%s' завершена. Результат будет передан для дальнейшей фильтрации.", rootDirectory));
        return fileList;
    }


    /**
     * Проверка и Files.walk()
     * @param rootDirectory
     * @param maxDepth
     * @return
     * @throws IOException
     */
    private List<Path> walkDirectoryList(Path rootDirectory, int maxDepth) throws IOException {
        if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
            LOGGER.log(Level.WARNING, "Указанная директория не существует или не является директорией: " + rootDirectory);
            return List.of();
        }
        int effectiveMaxDepth = (maxDepth == -1) ? Integer.MAX_VALUE : maxDepth;

        try (Stream<Path> walk = Files.walk(rootDirectory, effectiveMaxDepth)) {
            return walk.filter(Files::isRegularFile).toList();
        } catch (IOException ignored) {
            LOGGER.log(Level.WARNING, ignored.getMessage(), ignored);
            return List.of();
        }
    }

}