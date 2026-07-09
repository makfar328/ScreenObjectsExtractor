package org.makar.ocrapp.screenobjectsextractor.view.main;

import org.makar.ocrapp.screenobjectsextractor.model.core.DecomposeSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.SearchSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.LoggingService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.FileIndexService;
import org.makar.ocrapp.screenobjectsextractor.model.core.indexer.ImageContentAnalyzer;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.FileSearchService;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.SearchDirectoryService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ImageAnalysisManager;

import java.util.concurrent.ExecutorService;

/**
 * record-контейнер для передачи результата.
 * Неизменяемый контейнер для передачи всего графа сервисов
 * из фонового потока инициализации в JavaFX-поток.
 * Record гарантирует: финальность полей, equals/hashCode/toString бесплатно.
 */
public record ServiceBundle (
        FileSearchService fileSearchService,
        FileIndexService fileIndexService,
        SearchDirectoryService searchDirectoryService,
        ImageAnalysisManager imageAnalysisManager,
        ImageContentAnalyzer imageContentAnalyzer,
        SearchSessionService searchSessionService,
        DecomposeSessionService decomposeSessionService,
        LoggingService loggingService,
        ExecutorService aiExecutorService
) {}
