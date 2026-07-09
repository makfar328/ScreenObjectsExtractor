package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Результат обработки одного файла в рамках сессии декомпозиции.
 *
 * <p>Внутренний объект сервиса — не персистируется самостоятельно.
 * При обработке «распадается» на два независимых потока записи:
 * <ul>
 *   <li>{@code fileIndexRepository.save(entry.getFileMetadata())} — файловый индекс;</li>
 *   <li>статистика сессии пишется в {@code decomposeSessionDatabase} через существующий механизм.</li>
 * </ul>
 *
 * <p>Экземпляры неизменяемы (все поля {@code final}).
 * Для построения используйте {@link DecomposeSessionBuilder}.
 */
public final class DecomposeSessionEntry {

    /**
     * Ссылка на {@code DecomposeSession}, в рамках которой обработан файл.
     */
    private final long sessionId;

    /**
     * Момент завершения обработки этого конкретного файла.
     */
    private final LocalDateTime processedAt;

    private final FileMetadata fileMetadata;

    /**
     * Источник захвата файла.
     * Допустимые значения: {@code "SCREEN_CAPTURE"}, {@code "FILESYSTEM_SCAN"}, {@code null}.
     */
    private final String captureSource;

    private DecomposeSessionEntry(DecomposeSessionBuilder builder) {
        this.sessionId = builder.sessionId;
        this.processedAt = Objects.requireNonNull(builder.processedAt, "processedAt must not be null");
        this.fileMetadata = Objects.requireNonNull(builder.fileMetadata, "fileMetadata must not be null");
        this.captureSource = builder.captureSource;
    }

    public static final class CAPTURE_SOURCE {
        public static final String SCREEN_CAPTURE = "SCREEN_CAPTURE";
        public static final String FILE_SYSTEM = "FILE_SYSTEM";
    }

    // Геттеры

    public long getSessionId() {
        return sessionId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }

    public String getCaptureSource() {
        return captureSource;
    }

    // equals / hashCode / toString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecomposeSessionEntry)) return false;
        DecomposeSessionEntry that = (DecomposeSessionEntry) o;
        return sessionId == that.sessionId
                && Objects.equals(processedAt, that.processedAt)
                && Objects.equals(fileMetadata, that.fileMetadata)
                && Objects.equals(captureSource, that.captureSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, processedAt, fileMetadata, captureSource);
    }

    @Override
    public String toString() {
        return "DecomposeSessionEntry{" +
                "sessionId=" + sessionId +
                ", processedAt=" + processedAt +
                ", filePath='" + (fileMetadata != null ? fileMetadata.getFilePath() : "null") + '\'' +
                ", captureSource='" + captureSource + '\'' +
                '}';
    }

    public static DecomposeSessionBuilder builder() {
        return new DecomposeSessionBuilder();
    }

    public static final class DecomposeSessionBuilder {

        private long          sessionId;
        private LocalDateTime processedAt;
        private FileMetadata fileMetadata;
        private String        captureSource       = null;

        private DecomposeSessionBuilder() {}

        public DecomposeSessionBuilder sessionId(long sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public DecomposeSessionBuilder processedAt(LocalDateTime processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public DecomposeSessionBuilder fileMetadata(FileMetadata fileMetadata) {
            this.fileMetadata = fileMetadata;
            return this;
        }

        public DecomposeSessionBuilder captureSource(String captureSource) {
            this.captureSource = captureSource;
            return this;
        }

        public DecomposeSessionEntry build() {
            if (this.sessionId < 0) throw new IllegalArgumentException("sessionId must be >= 0");
            if (this.processedAt == null) throw new IllegalArgumentException("processedAt must not be null");
            return new DecomposeSessionEntry(this);
        }
    }
}