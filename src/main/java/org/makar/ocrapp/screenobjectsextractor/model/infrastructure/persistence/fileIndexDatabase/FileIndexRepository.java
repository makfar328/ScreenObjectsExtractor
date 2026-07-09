package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Репозиторий файлового индекса. CRUD + транзакции над тремя таблицами
 *
 * Оркестрирует три DAO:
 *   - IIndexedFilesDao    → таблица indexed_files
 *   - IDetectedObjectsDao → таблица detected_file_objects
 *   - IRecognizedTextDao  → таблица recognized_text_file
 */
public class FileIndexRepository implements IFileIndexRepository {

    private static final Logger LOGGER = Logger.getLogger(FileIndexRepository.class.getName());

    private final SQLiteConnectionManager connectionManager;
    private final IIndexedFilesDao        filesDao;
    private final IDetectedObjectsDao objectsDao;
    private final IRecognizedTextDao textDao;

    public FileIndexRepository(SQLiteConnectionManager connectionManager,
                               IIndexedFilesDao filesDao,
                               IDetectedObjectsDao objectsDao,
                               IRecognizedTextDao textDao) {
        this.connectionManager = connectionManager;
        this.filesDao          = filesDao;
        this.objectsDao        = objectsDao;
        this.textDao           = textDao;

    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ISQLiteRepository — инфраструктурный контракт
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Package-private — вызывается только из FileIndexDatabaseManager.
     * Не часть публичного API репозитория.
     */
    /*void initializeDaos() {
        filesDao.initializeTable();
        objectsDao.initializeTable();
        textDao.initializeTable();
    }*/


    @Override
    public void truncateAll() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Сначала дочерние, потом родительская (ON DELETE CASCADE тоже сработает,
                // но явный порядок делает намерение понятным)
                stmt.execute("DELETE FROM recognized_text_file");
                stmt.execute("DELETE FROM detected_file_objects");
                stmt.execute("DELETE FROM indexed_files");
                conn.commit();
                LOGGER.info("FileIndexRepository: все таблицы очищены.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  IFileIndexRepository — доменный контракт
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Атомарное сохранение FileMetadata.
     * Один Connection — одна транзакция на три таблицы.
     */
    @Override
    public void save(FileMetadata metadata) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Upsert основной записи → получаем fileId
                long fileId = filesDao.upsert(conn, metadata);
                LOGGER.info("fileId after upsert: " + fileId);
                System.out.println("fileId after upsert: " + fileId);

                // 2. Очищаем старые дочерние записи перед перезаписью
                objectsDao.deleteByForeignKeyId(fileId, conn);     // IDetectedObjectsDao.deleteById
                textDao.deleteByForeignKeyId(fileId, conn);    // IRecognizedTextDao.deleteByFileId

                // 3. Вставляем новые дочерние записи
                objectsDao.insertBatch(fileId, metadata.getDetectedObjects(), conn);
                textDao.insertBatch(fileId, metadata.getRecognizedTextContent(), conn);

                conn.commit();
                LOGGER.info("FileIndexRepository.save(): сохранён файл: " + metadata.getFilePath());

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE,
                        "FileIndexRepository.save(): откат транзакции для: " + metadata.getFilePath(), e);
                throw e;
            }
        }
    }

    /**
     * Удаление файла из индекса.
     * ON DELETE CASCADE в indexed_files автоматически удалит записи в дочерних таблицах.
     */
    @Override
    public void delete(long fileId) throws SQLException {
        // convenience-версия — сама управляет Connection
        filesDao.deleteById(fileId);
    }

    /**
     * Поиск файлов по SearchCriteria.
     *
     * Стратегия:
     *   1. Динамический SQL с EXISTS-подзапросами к дочерним таблицам.
     *   2. Основной запрос → Map<fileId, FileMetadata>.
     *   3. Bulk-загрузка дочерних данных за 2 запроса (не N+1).
     *   4. Сборка полных FileMetadata.
     */
    @Override
    public List<FileMetadata> search(SearchCriteria criteria) throws SQLException {
        if (criteria.isEmpty()) {
            return findAll();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, file_path, file_name, file_extension, file_size, " +
                        "       creation_date, modification_date " +
                        "FROM indexed_files WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // ── 1. Ключевые слова ─────────────────────────────────────────────
        if (criteria.getKeywords() != null && !criteria.getKeywords().isEmpty()) {
            sql.append(" AND (");
            StringJoiner kw = new StringJoiner(" OR ");
            for (String keyword : criteria.getKeywords()) {
                kw.add("file_name LIKE ? OR EXISTS (" +
                        "SELECT 1 FROM recognized_text_file " +
                        "WHERE file_id = indexed_files.id AND text_value LIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.append(kw).append(")");
        }

        // ── 2. Имена файлов ──────────────────────────────────────────────
        if (criteria.getFileNames() != null && !criteria.getFileNames().isEmpty()) {
            String placeholders = String.join(",",
                    Collections.nCopies(criteria.getFileNames().size(), "?"));
            sql.append(" AND file_name IN (").append(placeholders).append(")");
            params.addAll(criteria.getFileNames());
        }

        // ── 3. Дата создания ──────────────────────────────────────────────
        if (criteria.getMinDate() != null) {
            sql.append(" AND creation_date >= ?");
            params.add(criteria.getMinDate().atStartOfDay().toString());
        }
        if (criteria.getMaxDate() != null) {
            sql.append(" AND creation_date <= ?");
            params.add(criteria.getMaxDate().atTime(23, 59, 59).toString());
        }

        // ── 4. Целевые директории ─────────────────────────────────────────
        if (criteria.getTargetDirectories() != null && !criteria.getTargetDirectories().isEmpty()) {
            sql.append(" AND (");
            StringJoiner dirs = new StringJoiner(" OR ");
            for (SearchDirectoryConfig dir : criteria.getTargetDirectories()) {
                dirs.add("file_path LIKE ?");
                params.add(dir.getPath().toString() + "%");
            }
            sql.append(dirs).append(")");
        }

        // ── 5. Типы файлов ────────────────────────────────────────────────
        if (criteria.getFileTypes() != null && !criteria.getFileTypes().isEmpty()) {
            String placeholders = String.join(",",
                    Collections.nCopies(criteria.getFileTypes().size(), "?"));
            sql.append(" AND file_extension IN (").append(placeholders).append(")");
            params.addAll(criteria.getFileTypes());
        }

        // ── 6. Классы объектов ────────────────────────────────────────────
        if (criteria.getEntries() != null && !criteria.getEntries().isEmpty()) {
            sql.append(" AND (");
            StringJoiner entries = new StringJoiner(" OR ");
            for (SelectedObjectClass entry : criteria.getEntries()) {
                StringBuilder sub = new StringBuilder(
                        "EXISTS (SELECT 1 FROM detected_file_objects " +
                                "WHERE file_id = indexed_files.id AND class_name = ?");
                params.add(entry.getClassName());
                if (entry.getMinProbability() > 0) {
                    sub.append(" AND probability >= ?");
                    params.add(entry.getMinProbability());
                }
                sub.append(")");
                entries.add(sub.toString());
            }
            sql.append(entries).append(")");
        }

        // ── 7. Глобальная минимальная вероятность ────────────────────────
        if (criteria.getGlobalMinProbability() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM detected_file_objects " +
                    "WHERE file_id = indexed_files.id AND probability >= ?)");
            params.add(criteria.getGlobalMinProbability());
        }

        // ── Выполнение основного запроса ──────────────────────────────────
        Map<Long, FileMetadata> resultMap = new LinkedHashMap<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    // filesDao.mapRow — instance-метод, не static
                    resultMap.put(id, filesDao.mapRow(rs));
                }
            }
        }

        if (resultMap.isEmpty()) {
            return Collections.emptyList();
        }

        // ── Bulk-загрузка дочерних данных (2 запроса вместо N*2) ─────────
        List<Long> ids = new ArrayList<>(resultMap.keySet());

        try (Connection conn = connectionManager.getConnection()) {
            Map<Long, List<OCRAppDetectedObject>> objectsMap =
                    objectsDao.findByForeignKeyIds(ids, conn);
            Map<Long, List<TextObject>> textMap =
                    textDao.findByForeignKeyIds(ids, conn);

            List<FileMetadata> result = new ArrayList<>(resultMap.size());
            for (Map.Entry<Long, FileMetadata> entry : resultMap.entrySet()) {
                long id = entry.getKey();
                FileMetadata fm = entry.getValue();
                fm.setDetectedObjects(objectsMap.getOrDefault(id, Collections.emptyList()));
                fm.setRecognizedTextContent(textMap.getOrDefault(id, Collections.emptyList()));
                result.add(fm);
            }

            LOGGER.fine("FileIndexRepository.search(): найдено " + result.size() + " файлов.");
            return result;
        }
    }

    /**
     * Вернуть все проиндексированные файлы без фильтрации.
     * Используется когда criteria.isEmpty() == true, а также
     * напрямую из тестов и отладочного кода.
     *
     * Реализация идентична search(), но без WHERE-условий:
     *   1. SELECT * FROM indexed_files
     *   2. Bulk-загрузка дочерних данных за 2 запроса.
     *   3. Сборка полных FileMetadata.
     */
    @Override
    public List<FileMetadata> findAll() throws SQLException {
        String sql = "SELECT id, file_path, file_name, file_extension, file_size, " +
                "       creation_date, modification_date " +
                "FROM indexed_files";

        Map<Long, FileMetadata> resultMap = new LinkedHashMap<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                resultMap.put(id, filesDao.mapRow(rs));
            }
        }

        if (resultMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = new ArrayList<>(resultMap.keySet());

        try (Connection conn = connectionManager.getConnection()) {
            Map<Long, List<OCRAppDetectedObject>> objectsMap =
                    objectsDao.findByForeignKeyIds(ids, conn);
            Map<Long, List<TextObject>> textMap =
                    textDao.findByForeignKeyIds(ids, conn);

            List<FileMetadata> result = new ArrayList<>(resultMap.size());
            for (Map.Entry<Long, FileMetadata> entry : resultMap.entrySet()) {
                long id = entry.getKey();
                FileMetadata fm = entry.getValue();
                fm.setDetectedObjects(objectsMap.getOrDefault(id, Collections.emptyList()));
                fm.setRecognizedTextContent(textMap.getOrDefault(id, Collections.emptyList()));
                result.add(fm);
            }

            LOGGER.fine("FileIndexRepository.findAll(): возвращено " + result.size() + " файлов.");
            return result;
        }
    }

    /**
     * Загрузить FileMetadata по абсолютному пути.
     *
     * Два запроса на одном Connection:
     *   1. findByPath  → FileMetadata (без дочерних данных)
     *   2. findIdByPath → id для загрузки дочерних данных
     *
     * Причина: mapRow не возвращает id в составе FileMetadata,
     * поэтому id получаем отдельным запросом на том же connection.
     */
    @Override
    public FileMetadata getMetadataByPath(String filePath) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            FileMetadata fm = filesDao.findByPath(conn, filePath);
            if (fm == null) return null;

            long id = filesDao.findIdByPath(conn, filePath);

            fm.setDetectedObjects(objectsDao.findByForeignKeyId(id, conn));
            fm.setRecognizedTextContent(textDao.findByForeignKeyId(id, conn));
            return fm;
        }
    }

    @Override
    public List<OCRAppDetectedObject> getDetectedObjectsByFileId(long fileId) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            return objectsDao.findByForeignKeyId(fileId, conn);
        }
    }

    @Override
    public List<TextObject> getRecognizedTextByFileId(long fileId) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            return textDao.findByForeignKeyId(fileId, conn);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Приватные утилиты
    // ═══════════════════════════════════════════════════════════════════════

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Привязка параметров к PreparedStatement по типу.
     * Выделено в утилиту, чтобы не дублировать в методе search.
     */
    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String s) ps.setString(i + 1, s);
            else if (p instanceof Long l) ps.setLong(i + 1, l);
            else if (p instanceof Integer n) ps.setInt(i + 1, n);
            else if (p instanceof Double d) ps.setDouble(i + 1, d);
            else ps.setObject(i + 1, p);
        }
    }
}