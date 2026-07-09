package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.makar.ocrapp.screenobjectsextractor.model.common.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
НУЖНО СДЕЛАТЬ, НО ....
repository.getMetadataByPath(String) поменять на repository.getMetadataByPath(Path)
 */
class FileIndexRepositoryTest {

    private SQLiteConnectionManager connectionManager;
    private FileIndexRepository repository;

    private IndexedFilesDao                  filesDao;
    private IndexedFilesDetectedObjectsDao   objectsDao;
    private IndexedFilesRecognizedTextDao    textDao;

    private Connection anchorConnection;

    // ── Инфраструктура теста ────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:sqlite:file:testdb_fir_" + System.nanoTime()
                + "?mode=memory&cache=shared";
        connectionManager = new SQLiteConnectionManager(url);
        anchorConnection  = connectionManager.getConnection();

        filesDao   = new IndexedFilesDao(connectionManager);
        objectsDao = new IndexedFilesDetectedObjectsDao(connectionManager);
        textDao    = new IndexedFilesRecognizedTextDao(connectionManager);

        filesDao.initializeTable();
        objectsDao.initializeTable();
        textDao.initializeTable();

        repository = new FileIndexRepository(
                connectionManager, filesDao, objectsDao, textDao);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────────

    private FileMetadata buildMetadata(String filePath,
                                       List<OCRAppDetectedObject> objects,
                                       List<TextObject> texts) {
        return new FileMetadata(
                Paths.get(filePath),
                Paths.get(filePath).getFileName().toString(),
                "png",
                1024L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                texts,
                objects
        );
    }

    private OCRAppDetectedObject buildObject(String className, double probability) {
        return new OCRAppDetectedObject(className, probability);
    }

    private TextObject buildText(String text, int x, int y, int w, int h) {
        return new TextObject(text, x, y, w, h);
    }

    // ── Тесты save() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("save(): сохранение без дочерних данных не генерирует исключений")
    void save_emptyChildren_noException() {
        FileMetadata meta = buildMetadata(
                "/images/createEmpty.png", Collections.emptyList(), Collections.emptyList());
        assertDoesNotThrow(() -> repository.save(meta));
    }

    @Test
    @DisplayName("save(): запись доступна через getMetadataByPath() после сохранения")
    void save_afterSave_findableByPath() throws SQLException {
        repository.save(buildMetadata(
                "/images/findable.png", Collections.emptyList(), Collections.emptyList()));

        FileMetadata loaded = repository.getMetadataByPath(Paths.get("/images/findable.png").toString());

        assertNotNull(loaded, "getMetadataByPath() не должен вернуть null после save()");
        assertEquals("findable.png", loaded.getFileName());
    }

    @Test
    @DisplayName("save(): атомарно сохраняет детектированные объекты")
    void save_withObjects_persistsObjectsTransactionally() throws SQLException {
        List<OCRAppDetectedObject> objects = List.of(
                buildObject("cat", 0.95),
                buildObject("dog", 0.87)
        );
        repository.save(buildMetadata("/images/objects.png", objects, Collections.emptyList()));

        FileMetadata loaded = repository.getMetadataByPath(Paths.get("/images/objects.png").toString());

        assertNotNull(loaded);
        assertEquals(2, loaded.getDetectedObjects().size(),
                "Оба объекта детекции должны быть сохранены и загружены");
    }

    @Test
    @DisplayName("save(): атомарно сохраняет OCR-тексты")
    void save_withTexts_persistsTextsTransactionally() throws SQLException {
        List<TextObject> texts = List.of(
                buildText("Первая строка",  0,  0, 300, 25),
                buildText("Вторая строка",  0, 30, 300, 25)
        );
        repository.save(buildMetadata("/images/texts.png", Collections.emptyList(), texts));

        FileMetadata loaded = repository.getMetadataByPath(Paths.get("/images/texts.png").toString());

        assertNotNull(loaded);
        assertEquals(2, loaded.getRecognizedTextContent().size(),
                "Оба текстовых фрагмента должны быть сохранены и загружены");
    }

    @Test
    @DisplayName("save(): повторный вызов с тем же путём обновляет дочерние данные (upsert)")
    void save_samePath_replacesChildData() throws SQLException {
        repository.save(buildMetadata("/images/upsert.png",
                List.of(buildObject("old_class", 0.5)),
                Collections.emptyList()));

        // Перезапись с другими дочерними данными
        repository.save(buildMetadata("/images/upsert.png",
                List.of(buildObject("new_class_1", 0.9),
                        buildObject("new_class_2", 0.8)),
                Collections.emptyList()));

        FileMetadata loaded = repository.getMetadataByPath(Paths.get("/images/upsert.png").toString());

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(2, loaded.getDetectedObjects().size(),
                        "Дочерние данные должны быть заменены, а не дополнены"),
                () -> assertEquals("new_class_1",
                        loaded.getDetectedObjects().get(0).getClassName())
        );
    }

    // ── Тесты getMetadataByPath() ────────────────────────────────────────────

    @Test
    @DisplayName("getMetadataByPath(): несуществующий путь возвращает null")
    void getMetadataByPath_notFound_returnsNull() throws SQLException {
        FileMetadata result = repository.getMetadataByPath(Paths.get("/no/such/file.png").toString());
        assertNull(result);
    }

    @Test
    @DisplayName("getMetadataByPath(): загружает дочерние данные обоих типов")
    void getMetadataByPath_loadsChildDataEagerly() throws SQLException {
        repository.save(buildMetadata("/images/rich.png",
                List.of(buildObject("bird", 0.91)),
                List.of(buildText("OCR text", 0, 0, 100, 20))));

        FileMetadata loaded = repository.getMetadataByPath(Paths.get("/images/rich.png").toString());

        assertNotNull(loaded);
        assertAll(
                () -> assertEquals(1, loaded.getDetectedObjects().size()),
                () -> assertEquals("bird",
                        loaded.getDetectedObjects().get(0).getClassName()),
                () -> assertEquals(1, loaded.getRecognizedTextContent().size()),
                () -> assertEquals("OCR text",
                        loaded.getRecognizedTextContent().get(0).getText())
        );
    }

    // ── Тесты getDetectedObjectsByFileId() / getRecognizedTextByFileId() ─────

    @Test
    @DisplayName("getDetectedObjectsByFileId(): возвращает объекты для корректного fileId")
    void getDetectedObjectsByFileId_returnsCorrectObjects() throws SQLException {
        repository.save(buildMetadata("/images/by_id.png",
                List.of(buildObject("plane", 0.99)), Collections.emptyList()));

        long fileId = filesDao.findIdByPath(Paths.get("/images/by_id.png").toString());
        List<OCRAppDetectedObject> result =
                repository.getDetectedObjectsByFileId(fileId);

        assertEquals(1, result.size());
        assertEquals("plane", result.get(0).getClassName());
    }

    @Test
    @DisplayName("getRecognizedTextByFileId(): возвращает тексты для корректного fileId")
    void getRecognizedTextByFileId_returnsCorrectTexts() throws SQLException {
        repository.save(buildMetadata("/images/text_by_id.png",
                Collections.emptyList(), List.of(buildText("Hello", 0, 0, 100, 20))));

        long fileId = filesDao.findIdByPath(Paths.get("/images/text_by_id.png").toString());
        List<TextObject> result = repository.getRecognizedTextByFileId(fileId);

        assertEquals(1, result.size());
        assertEquals("Hello", result.get(0).getText());
    }

    // ── Тесты delete() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("delete(): запись недоступна через getMetadataByPath() после удаления")
    void delete_existingFile_removesFromIndex() throws SQLException {
        repository.save(buildMetadata(
                "/images/to_delete.png", Collections.emptyList(), Collections.emptyList()));
        long fileId = filesDao.findIdByPath(Paths.get("/images/to_delete.png").toString());

        repository.delete(fileId);

        FileMetadata result = repository.getMetadataByPath(Paths.get("/images/to_delete.png").toString());
        assertNull(result, "После delete() getMetadataByPath() должен вернуть null");
    }

    // ── Тесты truncateAll() ───────────────────────────────────────────────────

    @Test
    @DisplayName("truncateAll(): очищает все три таблицы; поиск по удалённому пути возвращает null")
    void truncateAll_clearsAllTables() throws SQLException {
        repository.save(buildMetadata("/images/before_truncate.png",
                List.of(buildObject("car", 0.8)),
                List.of(buildText("text", 0, 0, 100, 20))));

        assertNotNull(repository.getMetadataByPath(Paths.get("/images/before_truncate.png").toString()));

        repository.truncateAll();

        assertNull(repository.getMetadataByPath(Paths.get("/images/before_truncate.png").toString()),
                "После truncateAll() запись не должна быть доступна");
    }

    // ── Тесты search() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("search(): фильтр по расширению файла возвращает только совпадающие записи")
    void search_byFileType_returnsMatchingFiles() throws SQLException {
        // Сохраняем файл PNG и файл JPG через прямой upsert для упрощения
        FileMetadata png = new FileMetadata(
                Paths.get("/docs/image.png"), "image.png", "png", 512L,
                LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 1, 1, 0, 0),
                Collections.emptyList(), Collections.emptyList());
        FileMetadata jpg = new FileMetadata(
                Paths.get("/docs/photo.jpg"), "photo.jpg", "jpg", 512L,
                LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 1, 1, 0, 0),
                Collections.emptyList(), Collections.emptyList());

        repository.save(png);
        repository.save(jpg);

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withFileTypes(List.of("png"))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertEquals("png", result.get(0).getFileExtension());
    }

    @Test
    @DisplayName("search(): фильтр по классу детектированного объекта возвращает только совпадающие файлы")
    void search_byObjectClass_returnsMatchingFiles() throws SQLException {
        repository.save(buildMetadata("/images/has_cat.png",
                List.of(buildObject("cat", 0.95)), Collections.emptyList()));
        repository.save(buildMetadata("/images/has_dog.png",
                List.of(buildObject("dog", 0.88)), Collections.emptyList()));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withEntries(List.of(new SelectedObjectClass("cat", 0.0)))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertEquals("has_cat.png", result.get(0).getFileName());
    }

    @Test
    @DisplayName("search(): фильтр по ключевому слову в имени файла возвращает совпадение")
    void search_byKeyword_matchesFileName() throws SQLException {
        repository.save(buildMetadata("/images/invoice_2025.png",
                Collections.emptyList(), Collections.emptyList()));
        repository.save(buildMetadata("/images/photo_holiday.png",
                Collections.emptyList(), Collections.emptyList()));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(List.of("invoice"))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getFileName().contains("invoice"));
    }

    @Test
    @DisplayName("search(): фильтр по ключевому слову в OCR-тексте возвращает совпадение")
    void search_byKeyword_matchesOcrText() throws SQLException {
        repository.save(buildMetadata("/images/doc_with_text.png",
                Collections.emptyList(),
                List.of(buildText("Итоговая сумма: 5000 рублей", 0, 0, 300, 25))));
        repository.save(buildMetadata("/images/empty_doc.png",
                Collections.emptyList(), Collections.emptyList()));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(List.of("Итоговая"))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertEquals("doc_with_text.png", result.get(0).getFileName());
    }

    @Test
    @DisplayName("search(): search() загружает дочерние данные для найденных файлов (bulk-загрузка)")
    void search_resultsContainChildData() throws SQLException {
        repository.save(buildMetadata("/images/search_with_children.png",
                List.of(buildObject("bird", 0.77)),
                List.of(buildText("Search text", 0, 0, 100, 20))));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(List.of("search_with_children"))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertAll(
                () -> assertEquals(1, result.get(0).getDetectedObjects().size(),
                        "Дочерние объекты должны быть загружены bulk-запросом"),
                () -> assertEquals(1, result.get(0).getRecognizedTextContent().size(),
                        "Дочерние тексты должны быть загружены bulk-запросом")
        );
    }

    @Test
    @DisplayName("search(): пустой результат не вызывает исключений")
    void search_noMatches_returnsEmptyList() throws SQLException {
        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withKeywords(List.of("no_such_keyword_xyz"))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("search(): минимальный порог вероятности фильтрует объекты ниже порога")
    void search_byObjectClassWithMinProbability_filtersLowConfidence() throws SQLException {
        repository.save(buildMetadata("/images/high_conf.png",
                List.of(buildObject("cat", 0.95)), Collections.emptyList()));
        repository.save(buildMetadata("/images/low_conf.png",
                List.of(buildObject("cat", 0.40)), Collections.emptyList()));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withEntries(List.of(new SelectedObjectClass("cat", 0.80)))
                .build();

        List<FileMetadata> result = repository.search(criteria);

        assertEquals(1, result.size());
        assertEquals("high_conf.png", result.get(0).getFileName());
    }
}