package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Менеджер базы данных для домена сессий декомпозиции.
 *
 * <p>Единственная ответственность:
 * <ul>
 *   <li>создать DAO;</li>
 *   <li>собрать {@link DecomposeSessionRepository};</li>
 *   <li>инициализировать схему (порядок: родительские таблицы первыми);</li>
 *   <li>предоставить {@link IDecomposeSessionRepository} через интерфейс.</li>
 * </ul>
 *
 * <p>Инфраструктурный lifecycle ({@code initializeSchema}, {@code isSchemaReady})
 * живёт здесь, а не в Repository — Repository говорит на языке домена.
 */
public class DecomposeSessionDatabaseManager implements IDecomposeSessionDatabaseManager {

    private static final Logger LOGGER =
            Logger.getLogger(DecomposeSessionDatabaseManager.class.getName());

    private final SQLiteConnectionManager connectionManager;
    private final IDecomposeSessionRepository repository;

    // Конкретные типы — только для initializeSchema()
    // Repository получает их через интерфейсы — upcasting автоматический
    private final DecomposeSessionFileEntryDao fileEntryDao;
    private final DSDetectedObjectsDao         detectedObjectsDao;
    private final DSRecognizedTextDao          recognizedTextDao;

    public DecomposeSessionDatabaseManager(SQLiteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        this.fileEntryDao       = new DecomposeSessionFileEntryDao(connectionManager);
        this.detectedObjectsDao = new DSDetectedObjectsDao(connectionManager);
        this.recognizedTextDao  = new DSRecognizedTextDao(connectionManager);

        this.repository = new DecomposeSessionRepository(
                connectionManager,
                fileEntryDao,
                detectedObjectsDao,
                recognizedTextDao
        );
    }

    // ─── IDatabaseManager ────────────────────────────────────────────────────

    @Override
    public void initializeSchema() {
        // Порядок важен: родительская таблица создаётся первой,
        // дочерние таблицы (FK) — после
        fileEntryDao.initializeTable();        // ds_file_entry (родительская)
        detectedObjectsDao.initializeTable();  // ds_detected_objects (FK → ds_file_entry)
        recognizedTextDao.initializeTable();   // ds_recognized_text  (FK → ds_file_entry)
        LOGGER.info("DecomposeSessionDatabaseManager: схема инициализирована.");
    }

    @Override
    public boolean isSchemaReady() {
        try (Connection conn = connectionManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return tableExists(meta, "ds_file_entry")
                    && tableExists(meta, "ds_detected_objects")
                    && tableExists(meta, "ds_recognized_text");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "isSchemaReady(): ошибка проверки схемы", e);
            return false;
        }
    }

    // ─── IDecomposeSessionDatabaseManager ────────────────────────────────────

    @Override
    public IDecomposeSessionRepository getRepository() {
        return repository;
    }

    // ─── Утилиты ─────────────────────────────────────────────────────────────

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}