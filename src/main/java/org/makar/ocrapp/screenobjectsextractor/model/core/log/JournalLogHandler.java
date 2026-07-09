package org.makar.ocrapp.screenobjectsextractor.model.core.log;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.LogEntry;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class JournalLogHandler extends Handler {

    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();

    public JournalLogHandler() {
        // Уровень логов, который будет собирать этот обработчик. ALL = {FINEST и др.}
        setLevel(Level.ALL);
    }

    public ObservableList<LogEntry> getLogEntries() {
        return logEntries;
    }


    /**
     * Очищает список логов.
     */
    public void clear() {
        logEntries.clear();
    }


    /**
     * Этот метод вызывается для каждой записи лога, которая проходит фильтрацию по уровню.
     * Здесь: фильтрую по уровню лога.
     */
    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }

        LogEntry entry = new LogEntry(record.getLevel(), record.getMessage(), record.getMillis(), record.getLoggerName());

        logEntries.add(entry);
    }


    /**
     * Метод flush() используется для записи буферизованных данных.
     * Сейчас записи добавляются в список сразу, поэтому этот метод пустой.
     */
    @Override
    public void flush() {
        // нет буферизации, поэтому вроде ничего не нужно делать
    }


    /**
     * Закрывает обработчик. В данном случае не требуется очистка ресурсов.
     */
    @Override
    public void close() throws SecurityException {
        // файлов нет
    }
}
