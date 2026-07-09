package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OrtException;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageAnalysisManagerTest {

    @Mock
    private IOcrService ocrService;
    @Mock private ICVServicesOrchestrator orchestrator;

    // Выполнение task на вызывающем потоке. Распространенная практика в тестах. "same-thread"
    // Здесь нельзя потому что это ExecutorService (1 + 12 абстрактных методов), а не Executor (только 1 метод executor)
    //private final ExecutorService syncExecutor = Runnable::run; // эквивалентно: command -> command.run()
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private ImageAnalysisManager manager;

    @BeforeEach
    void setUp() {
        manager = new ImageAnalysisManager(ocrService, orchestrator, syncExecutor);
    }

    // ── recognizeTextAsync ─────────────────────────────────────────────────

    @Test
    @DisplayName("recognizeTextAsync: ocrService == null → completedFuture с пустым TextObjects, сервис не вызывается")
    void recognizeTextAsync_nullService_returnsEmptyTextObjectsImmediately() throws Exception {
        var mgr = new ImageAnalysisManager(null, orchestrator, syncExecutor);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        TextObjects result = mgr.recognizeTextAsync(image).get();

        assertNotNull(result);
        verifyNoInteractions(ocrService);
    }

    @Test
    @DisplayName("recognizeTextAsync: успешный вызов — future содержит результат ocrService")
    void recognizeTextAsync_happyPath_returnsOcrResult() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        TextObjects expected = new TextObjects();
        when(ocrService.recognizeText(image)).thenReturn(expected);

        TextObjects result = manager.recognizeTextAsync(image).get();

        assertSame(expected, result);
    }

    @Test
    @DisplayName("recognizeTextAsync: TesseractException → future возвращает null, исключение не пробрасывается")
    void recognizeTextAsync_tesseractException_futureReturnsNull() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        when(ocrService.recognizeText(image)).thenThrow(new TesseractException("fail", null));

        TextObjects result = manager.recognizeTextAsync(image).get();

        assertNull(result);
    }

    // ── detectObjectsAsync ─────────────────────────────────────────────────

    @Test
    @DisplayName("detectObjectsAsync: успешный вызов — future содержит результат оркестратора")
    void detectObjectsAsync_happyPath_returnsDetectedObjects() throws Exception {
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        DetectedObjects expected = mock(DetectedObjects.class); // создание заглушки: мок-объекта DetectedObjects
        when(orchestrator.detectObjects(image)).thenReturn(expected); // настройка заглушки.
        // Мок-объект используется для создания иллюзии работы метода класса, который на самом деле не проинициализирован

        DetectedObjects result = manager.detectObjectsAsync(image).get();

        assertSame(expected, result);
    }

    @Test
    @DisplayName("detectObjectsAsync: оркестратор вернул null → future возвращает null")
    void detectObjectsAsync_orchestratorReturnsNull_futureReturnsNull() throws Exception {
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        when(orchestrator.detectObjects(image)).thenReturn(null);

        DetectedObjects result = manager.detectObjectsAsync(image).get();

        assertNull(result);
    }

    @Test
    @DisplayName("detectObjectsAsync: OrtException → future возвращает null, исключение не пробрасывается")
    void detectObjectsAsync_ortException_futureReturnsNull() throws Exception {
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        when(orchestrator.detectObjects(image)).thenThrow(new OrtException("ONNX error"));

        DetectedObjects result = manager.detectObjectsAsync(image).get();

        assertNull(result);
    }
}
