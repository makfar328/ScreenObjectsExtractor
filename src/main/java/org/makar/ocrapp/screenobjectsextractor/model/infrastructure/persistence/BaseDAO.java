package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Абстрактный базовый класс для всех объектов доступа к данным (DAO)* в вашем проекте.
 * Он содержит общую инфраструктурную логику для взаимодействия с базой данных SQLite.
 * Возможно стоит переименовать в SQLiteDAO
 */
public class BaseDAO {
    protected final Logger logger;
    protected final SQLiteConnectionManager connectionManager;
    protected static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BaseDAO(SQLiteConnectionManager connectionManager, Class<?> repositoryClass) {
        this.connectionManager = connectionManager;
        this.logger = Logger.getLogger(repositoryClass.getName());
    }

    /**
     * соединение с базой данных
     * @return
     * @throws SQLException
     */
    protected Connection getConnection() throws SQLException {
        return connectionManager.getConnection();
    }

    /**
     * Вспомогательный метод для выполнения SQL-запросов на вставку данных,
     * который возвращает сгенерированный базой данных ключ (например, AUTOINCREMENT ID).
     * @param sql
     * @param params
     * @return
     * @throws SQLException
     */
    protected int executeUpdate(String sql, List<Object> params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(pstmt, params);
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return -1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка, не получилось сгенерировать или получить ключ: " + sql, e);
            throw e;
        }
    }

    /**
     * Устанавливает параметры для PreparedStatement.
     * @param pstmt
     * @param params
     * @throws SQLException
     */
    public void setParameters(PreparedStatement pstmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof LocalDateTime) {
                pstmt.setString(i + 1, ((LocalDateTime) param).format(DATE_TIME_FORMATTER));
            } else {
                pstmt.setObject(i + 1, param);
            }
        }
    }
}
