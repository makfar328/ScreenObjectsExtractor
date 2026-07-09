package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase;

import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IDetectedObjectsDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.IRecognizedTextDao;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Репозиторий домена декомпозиции. Оркестрирует три DAO:
 * <ul>
 *   <li>{@link IDecomposeSessionFileEntryDao} → таблица {@code ds_file_entry}</li>
 *   <li>{@link IDetectedObjectsDao}           → таблица {@code ds_detected_objects}</li>
 *   <li>{@link IRecognizedTextDao}            → таблица {@code ds_recognized_text}</li>
 * </ul>
 *
 * <p>Единственное место, где управляется транзакция:
 * один {@link Connection} открывается здесь, передаётся во все DAO-методы,
 * после чего коммитится или откатывается.
 */
public class DecomposeSessionRepository implements IDecomposeSessionRepository {

    private static final Logger LOGGER =
            Logger.getLogger(DecomposeSessionRepository.class.getName());

    private final SQLiteConnectionManager        connectionManager;
    private final IDecomposeSessionFileEntryDao  fileEntryDao;
    private final IDetectedObjectsDao            objectsDao;
    private final IRecognizedTextDao             textDao;

    public DecomposeSessionRepository(SQLiteConnectionManager connectionManager,
                                      IDecomposeSessionFileEntryDao fileEntryDao,
                                      IDetectedObjectsDao objectsDao,
                                      IRecognizedTextDao textDao) {
        this.connectionManager = connectionManager;
        this.fileEntryDao      = fileEntryDao;
        this.objectsDao        = objectsDao;
        this.textDao           = textDao;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ISQLiteRepository — инфраструктурный контракт
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void truncateAll() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Сначала дочерние, потом родительская
                stmt.execute("DELETE FROM ds_recognized_text");
                stmt.execute("DELETE FROM ds_detected_objects");
                stmt.execute("DELETE FROM ds_file_entry");
                conn.commit();
                LOGGER.info("DecomposeSessionRepository: все таблицы очищены.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  IDecomposeSessionRepository — доменный контракт
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Атомарное сохранение {@link DecomposeSessionEntry}.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Вставить запись в {@code ds_file_entry} → получить {@code entryId}.</li>
     *   <li>Вставить пакет детектированных объектов в {@code ds_detected_objects}.</li>
     *   <li>Вставить пакет распознанного текста в {@code ds_recognized_text}.</li>
     * </ol>
     *
     * @param entry  данные обработанного файла
     * @return сгенерированный {@code id} строки в {@code ds_file_entry},
     *         или {@code -1L} при ошибке
     */
    @Override
    public long saveSession(DecomposeSessionEntry entry) {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Вставляем родительскую запись
                long entryId = fileEntryDao.insert(entry, conn);
                LOGGER.info("DecomposeSessionRepository.saveSession(): entryId=" + entryId);

                // 2. Вставляем дочерние данные (null-safe: DAO сам проверяет пустоту)
                objectsDao.insertBatch(
                        entryId,
                        entry.getFileMetadata().getDetectedObjects(),
                        conn
                );
                textDao.insertBatch(
                        entryId,
                        entry.getFileMetadata().getRecognizedTextContent(),
                        conn
                );

                conn.commit();
                LOGGER.info("DecomposeSessionRepository.saveSession(): сохранён файл: "
                        + entry.getFileMetadata().getFilePath());
                return entryId;

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE,
                        "DecomposeSessionRepository.saveSession(): откат транзакции для: "
                                + entry.getFileMetadata().getFilePath(), e);
                return -1L;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "DecomposeSessionRepository.saveSession(): не удалось получить соединение", e);
            return -1L;
        }
    }

    /**
     * Загрузить все {@link DecomposeSessionEntry} — сводный список без дочерних данных.
     *
     * <p>Используется для отображения журнала сессий.
     * Дочерние данные (объекты / текст) не загружаются — они дорогостоящи
     * и нужны только при открытии конкретной записи.
     *
     * @return список всех записей; пустой список при отсутствии данных или ошибке
     */
    @Override
    public List<DecomposeSessionEntry> getAllSessionsSummary() {
        try (Connection conn = connectionManager.getConnection()) {
            List<DecomposeSessionEntry> entries = fileEntryDao.findAll(conn);

            for (DecomposeSessionEntry entry : entries) {
                long entryId = entry.getFileMetadata().getId();

                List<TextObject> texts = textDao.findByForeignKeyId(entryId, conn);
                List<OCRAppDetectedObject> objects = objectsDao.findByForeignKeyId(entryId, conn);

                entry.getFileMetadata().setRecognizedTextContent(texts);
                entry.getFileMetadata().setDetectedObjects(objects);
            }

            LOGGER.fine("DecomposeSessionRepository.getAllSessionsSummary(): "
                    + "загружено " + entries.size() + " записей.");
            return entries;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "DecomposeSessionRepository.getAllSessionsSummary(): ошибка чтения", e);
            return Collections.emptyList();
        }
    }
}