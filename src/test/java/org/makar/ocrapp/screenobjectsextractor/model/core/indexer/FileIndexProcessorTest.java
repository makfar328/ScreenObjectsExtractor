package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для FileIndexingProcessor.
 *
 * Стратегия: мокируем MetadataExtractor, ImageContentAnalyzer и FileIndexRepository,
 * чтобы изолировать логику самого процессора.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class FileIndexingProcessorTest {

    @Mock
    private org.makar.ocrapp.screenobjectsextractor.model.infrastructure
            .persistence.fileIndexDatabase.FileIndexRepository repository;
    @Mock
    private MetadataExtractor    metadataExtractor;
    @Mock
    private ImageContentAnalyzer imageContentAnalyzer;

    private FileIndexingProcessor processor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        processor = new FileIndexingProcessor(repository, metadataExtractor, imageContentAnalyzer);
    }

    // ── успешный сценарий ──────────────────────────────────────────────────

    @Test
    @DisplayName("processFile: при успешном извлечении метаданных — вызов repository.save()")
    void processFile_validFile_saveCalledOnRepository() throws IOException, SQLException {
        Path file = Files.createFile(tempDir.resolve("test.png"));
        FileMetadata stub = buildStub(file, "test", "png", 0, 0);

        when(metadataExtractor.extract(file)).thenReturn(stub);
        when(imageContentAnalyzer.analyzeObjects(any())).thenReturn(List.of());

        processor.processFile(file);

        verify(repository, times(1)).save(stub);
    }

    @Test
    @DisplayName("processFile: возвращает FileMetadata с корректным именем файла")
    void processFile_validFile_returnsMetadataWithCorrectFileName() throws IOException {
        Path file = Files.createFile(tempDir.resolve("capture.png"));
        FileMetadata stub = buildStub(file, "capture", "png", 0, 0);

        when(metadataExtractor.extract(file)).thenReturn(stub);
        when(imageContentAnalyzer.analyzeObjects(any())).thenReturn(List.of());

        FileMetadata result = processor.processFile(file);

        assertNotNull(result);
        assertEquals("capture", result.getFileName());
    }

    // ── падение AI-анализа ─────────────────────────────────────────────────

    @Test
    @DisplayName("processFile: если analyzeObjects бросает — метаданные всё равно сохраняются")
    void processFile_analyzerThrows_metadataStillSaved() throws IOException, SQLException {
        Path file = Files.createFile(tempDir.resolve("crash.png"));
        FileMetadata stub = buildStub(file, "crash", "png", 0, 0);

        when(metadataExtractor.extract(file)).thenReturn(stub);
        when(imageContentAnalyzer.analyzeObjects(any())).thenThrow(new RuntimeException("AI failure"));

        // Не должно пробрасывать наружу
        assertDoesNotThrow(() -> processor.processFile(file));
        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("processFile: если analyzeObjects возвращает null — detectedObjects остаётся пустым")
    void processFile_analyzerReturnsNull_detectedObjectsEmpty() throws IOException {
        Path file = Files.createFile(tempDir.resolve("null_ai.png"));
        FileMetadata stub = buildStub(file, "null_ai", "png", 0, 0);

        when(metadataExtractor.extract(file)).thenReturn(stub);
        when(imageContentAnalyzer.analyzeObjects(any())).thenReturn(null);

        FileMetadata result = processor.processFile(file);

        assertNotNull(result);
        // detectedObjects не должен быть null после вызова
        assertNotNull(result.getDetectedObjects());
    }

    // ── падение извлечения метаданных ──────────────────────────────────────

    @Test
    @DisplayName("processFile: если MetadataExtractor бросает IOException — repository.save() не вызывается")
    void processFile_extractorThrows_repositoryNotCalled() throws IOException, SQLException {
        Path file = Files.createFile(tempDir.resolve("bad.png"));

        when(metadataExtractor.extract(file)).thenThrow(new IOException("disk error"));

        assertDoesNotThrow(() -> processor.processFile(file));
        verify(repository, never()).save(any());
    }

    private FileMetadata buildStub(Path path, String name, String ext,
                                   int imageWidth, int imageHeight) {
        return new FileMetadata(
                path, name, ext, 0L,
                LocalDateTime.now(), LocalDateTime.now(),
                List.of(), List.of(),
                imageWidth, imageHeight
        );
    }
}