package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Менеджер данных файлового индекса. Создать DAO, подключить к SQLite, инициализировать схему
 *
 * Единственная ответственность:
 *   - создать DAO
 *   - собрать Repository
 *   - инициализировать схему
 *   - предоставить Repository через интерфейс
 *
 * Инфраструктурный lifecycle (initializeSchema, isSchemaReady) живёт здесь,
 * а не в Repository — Repository говорит на языке домена.
 */
public class FileIndexDatabaseManager implements IFileIndexDatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(FileIndexDatabaseManager.class.getName());

    private final SQLiteConnectionManager  connectionManager;
    private final IFileIndexRepository     repository;

    // Конкретные типы — только для инициализации схемы
    // Repository получает их же, но через интерфейсы
    private final IndexedFilesDao                 filesDao;
    private final IndexedFilesDetectedObjectsDao  objectsDao;
    private final IndexedFilesRecognizedTextDao   textDao;

    public FileIndexDatabaseManager(SQLiteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        this.filesDao   = new IndexedFilesDao(connectionManager);
        this.objectsDao = new IndexedFilesDetectedObjectsDao(connectionManager);
        this.textDao    = new IndexedFilesRecognizedTextDao(connectionManager);

        // Repository получает их через интерфейсы — полиморфизм сохранён
        this.repository = new FileIndexRepository(connectionManager, filesDao, objectsDao, textDao);
        //                          тип: IndexedFilesDao
        //                          параметр: IIndexedFilesDao
        //                          Java выполняет upcasting автоматически — ошибки нет
        /* Upcasting — это неявное приведение ссылки от дочернего типа к родительскому (интерфейсу или суперклассу). */
    }

    // ─── IDatabaseManager ────────────────────────────────────────────────────

    @Override
    public void initializeSchema() {
        // Вызываем напрямую — конкретные типы здесь известны, cast не нужен
        filesDao.initializeTable();    // indexed_files (родительская — первой)
        objectsDao.initializeTable();  // detected_file_objects (FK → indexed_files)
        textDao.initializeTable();     // recognized_text_file  (FK → indexed_files)
        LOGGER.info("FileIndexDatabaseManager: схема инициализирована.");
    }

    @Override
    public boolean isSchemaReady() {
        try (Connection conn = connectionManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return tableExists(meta, "indexed_files")
                    && tableExists(meta, "detected_file_objects")
                    && tableExists(meta, "recognized_text_file");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "isSchemaReady(): ошибка проверки схемы", e);
            return false;
        }
    }

    // ─── IFileIndexDatabaseManager ───────────────────────────────────────────

    @Override
    public IFileIndexRepository getRepository() {
        return repository;
    }

    // ─── Утилиты ─────────────────────────────────────────────────────────────

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}