package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import java.util.logging.Level;

public class LogEntry {
    private final Level level;
    private final String message;
    private final long timestamp;
    private final String loggerName;

    public LogEntry(final Level level, final String message, final long timestamp, final String loggerName) {
        this.level = level;
        this.message = message;
        this.timestamp = timestamp;
        this.loggerName = loggerName;
    }

    public Level getLevel() { return level; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public String getLoggerName() { return loggerName; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", level.getName(), loggerName, message);
    }
}
