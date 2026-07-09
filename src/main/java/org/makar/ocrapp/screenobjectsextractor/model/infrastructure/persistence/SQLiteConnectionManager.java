package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Управляет соединениями с SQLite.
 *
 * ThreadLocal-режим: одно соединение на поток.
 * getConnection() самовосстанавливается — если DAO закрыл соединение через
 * try-with-resources, следующий вызов getConnection() прозрачно переоткроет его.
 *
 * WAL-режим включён для снижения блокировок при параллельных чтениях.
 */
public class SQLiteConnectionManager implements AutoCloseable {

    private static final Logger logger =
            Logger.getLogger(SQLiteConnectionManager.class.getName());

    private final String databaseURL;
    private final ThreadLocal<Connection> threadLocalConnection;

    public SQLiteConnectionManager(String pathOrUrl) {
        this.databaseURL = pathOrUrl.startsWith("jdbc:")
                ? pathOrUrl
                : "jdbc:sqlite:" + pathOrUrl.replace("\\", "/");

        this.threadLocalConnection = ThreadLocal.withInitial(() -> {
            try {
                Connection conn = DriverManager.getConnection(databaseURL);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    stmt.execute("PRAGMA synchronous = NORMAL;");
                    stmt.execute("PRAGMA busy_timeout = 5000;");
                    stmt.execute("PRAGMA cache_size = -8000;");
                }
                logger.info("Новое соединение с БД (поток "
                        + Thread.currentThread().getName() + "): " + databaseURL);
                return conn;
            } catch (SQLException e) {
                throw new RuntimeException(
                        "Не удалось открыть соединение с БД: " + databaseURL, e);
            }
        });
    }

    /**
     * Возвращает живое соединение для текущего потока.
     * Если соединение было закрыто снаружи (например, через try-with-resources
     * в DAO), метод прозрачно переоткрывает его — вызывающий код ничего не замечает.
     */
    public Connection getConnection() {
        try {
            Connection conn = threadLocalConnection.get();
            if (conn.isClosed()) {
                logger.warning("Соединение для потока '"
                        + Thread.currentThread().getName()
                        + "' оказалось закрытым — переоткрываем.");
                threadLocalConnection.remove(); // убираем мёртвую запись
                // withInitial сработает при следующем .get()
            }
        } catch (SQLException e) {
            // isClosed() бросил исключение — соединение точно сломано
            threadLocalConnection.remove();
        }
        return threadLocalConnection.get(); // гарантированно живое соединение
    }

    /**
     * Явно освобождает соединение для текущего потока.
     * Вызывать в finally-блоке по завершении работы в потоке пула —
     * ForkJoinPool-потоки живут долго, без явного закрытия соединения утекут.
     */
    public void releaseConnectionForCurrentThread() {
        Connection conn = threadLocalConnection.get();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                    logger.info("Соединение закрыто для потока: "
                            + Thread.currentThread().getName());
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Ошибка закрытия соединения", e);
            } finally {
                threadLocalConnection.remove(); // обязательно, иначе утечка
            }
        }
    }

    public void initializeDatabaseFile() {
        try (Connection conn = DriverManager.getConnection(databaseURL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            logger.info("Файл БД инициализирован (WAL): " + databaseURL);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Не удалось инициализировать БД: " + databaseURL, e);
        }
    }

    public void checkForeignKeySupport() {
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
            if (rs.next()) {
                logger.info("foreign_keys: " + rs.getInt(1));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка проверки foreign key: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        releaseConnectionForCurrentThread();
    }
}