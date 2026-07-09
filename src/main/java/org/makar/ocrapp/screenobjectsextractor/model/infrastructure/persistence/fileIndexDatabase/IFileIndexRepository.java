package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.ISQLiteRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Доменный контракт репозитория файлового индекса.
 *
 * Позволяет подменять реализацию (например, in-memory заглушка в тестах)
 * без изменений в FileIndexService или FileSearchService.
 *
 * Принцип : зависимость от интерфейса, а не от конкретного класса.
 */
public interface IFileIndexRepository extends ISQLiteRepository {

    // ───── Запись ──────────────────────────────────────────────────────────

    /**
     * Атомарно сохранить или обновить метаданные файла.
     * При повторном вызове для того же пути — заменить дочерние записи
     * (detected objects, recognized text).
     *
     * @param metadata полный объект FileMetadata включая детектированные объекты и текст.
     * @throws SQLException при ошибке БД.
     */
    void save(FileMetadata metadata) throws SQLException;

    /**
     * Удалить файл из индекса по его id.
     * ON DELETE CASCADE удаляет связанные объекты и текст.
     *
     * @param fileId внутренний id записи в indexed_files.
     * @throws SQLException при ошибке БД.
     */
    void delete(long fileId) throws SQLException;

    // ───── Чтение ──────────────────────────────────────────────────────────

    /**
     * Найти файлы по критериям поиска.
     * Строит динамический SQL с JOIN/EXISTS-подзапросами.
     *
     * @param criteria параметры поиска (ключевые слова, даты, директории, классы объектов).
     * @return список найденных FileMetadata с заполненными detectedObjects и recognizedText.
     * @throws SQLException при ошибке БД.
     */
    List<FileMetadata> search(SearchCriteria criteria) throws SQLException;

    /**
     * Найти все файлы.
     * Строит динамический SQL с JOIN-подзапросами.
     *
     * @return список найденных FileMetadata с заполненными detectedObjects и recognizedText.
     * @throws SQLException при ошибке БД.
     */
    List<FileMetadata> findAll() throws SQLException;

    /**
     * Получить FileMetadata по абсолютному пути файла.
     * Включает ленивую загрузку detectedObjects и recognizedText.
     *
     * @param filePath абсолютный путь к файлу.
     * @return объект FileMetadata или null если файл не проиндексирован.
     * @throws SQLException при ошибке БД.
     */
    FileMetadata getMetadataByPath(String filePath) throws SQLException;

    // ───── Вспомогательные доменные запросы ────────────────────────────────

    /**
     * Получить все обнаруженные объекты для конкретного файла.
     * Используется для ленивой загрузки в контексте просмотра деталей.
     *
     * @param fileId внутренний id записи в indexed_files.
     * @return список OCRAppDetectedObject.
     * @throws SQLException при ошибке БД.
     */
    List<OCRAppDetectedObject> getDetectedObjectsByFileId(long fileId) throws SQLException;

    /**
     * Получить распознанный текст для конкретного файла.
     *
     * @param fileId внутренний id записи в indexed_files.
     * @return список TextObject.
     * @throws SQLException при ошибке БД.
     */
    List<TextObject> getRecognizedTextByFileId(long fileId) throws SQLException;
}
