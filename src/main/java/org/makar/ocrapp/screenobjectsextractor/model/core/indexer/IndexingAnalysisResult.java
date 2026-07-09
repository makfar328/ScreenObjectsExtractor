package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

public sealed interface IndexingAnalysisResult
        permits IndexingAnalysisResult.NeverIndexed,
                IndexingAnalysisResult.ReadError,
                IndexingAnalysisResult.UpToDate,
                IndexingAnalysisResult.NeedsUpdate {

    /** Файл ещё ни разу не был проиндексирован. */
    record NeverIndexed() implements IndexingAnalysisResult {}

    /** Ошибка чтения метаданных из БД — состояние неизвестно. */
    record ReadError(Exception cause) implements IndexingAnalysisResult {}

    /** Все данные актуальны — индексация не нужна. */
    record UpToDate() implements IndexingAnalysisResult {}

    /** Данные устарели — нужна частичная или полная переиндексация. */
    record NeedsUpdate(FileIndexingFlags flags) implements IndexingAnalysisResult {}
}
