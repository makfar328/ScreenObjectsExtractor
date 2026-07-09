package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectDetectionServiceTest {

    private static final String OUTPUT_NAME = "output0";

    // ── моки ──────────────────────────────────────────────────────────────

    @Mock private IModelRunner        mockModelRunner;
    @Mock private YoloPostProcessor   mockPostProcessor;
    @Mock private OrtSession.Result   mockResult;
    @Mock private OnnxTensor          mockTensor;   // concrete class — Mockito 5 справится

    // ── конструктор: валидация ──────────────────────────────────────────────

    @Test
    @DisplayName("конструктор: yoloOutputName == null → IllegalArgumentException")
    void constructor_nullOutputName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                new ObjectDetectionService(mockModelRunner, mockPostProcessor, null));
    }

    @Test
    @DisplayName("конструктор: yoloOutputName пустая строка → IllegalArgumentException")
    void constructor_blankOutputName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                new ObjectDetectionService(mockModelRunner, mockPostProcessor, "  "));
    }

    // ── detectObjects: валидация входа ─────────────────────────────────────

    @Test
    @DisplayName("detectObjects: null изображение → IllegalArgumentException")
    void detectObjects_nullImage_throwsIllegalArgument() throws OrtException {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        assertThrows(IllegalArgumentException.class, () -> service.detectObjects(null));

        verifyNoInteractions(mockModelRunner); // runModel() вообще не вызван
    }

    // ── detectObjects: тензор не найден ────────────────────────────────────

    @Test
    @DisplayName("detectObjects: тензор с нужным именем отсутствует → OrtException")
    void detectObjects_outputTensorNotFound_throwsOrtException() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        // Result итерирует по записям с ДРУГИМ именем тензора
        Map.Entry<String, OnnxValue> wrongEntry = Map.entry("wrong_output", mockTensor);
        when(mockModelRunner.runModel(any(BufferedImage.class))).thenReturn(mockResult);
        when(mockResult.spliterator()).thenReturn(List.<Map.Entry<String, OnnxValue>>of(wrongEntry).spliterator());

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);

        OrtException ex = assertThrows(OrtException.class, () -> service.detectObjects(image));
        assertTrue(ex.getMessage().contains(OUTPUT_NAME),
                "Сообщение должно содержать имя ненайденного тензора");
    }

    // ── detectObjects: OnnxValue не является OnnxTensor ────────────────────

    @Test
    @DisplayName("detectObjects: OnnxValue не OnnxTensor → OrtException")
    void detectObjects_onnxValueNotTensor_throwsOrtException() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        // Возвращаем OnnxValue, который НЕ является OnnxTensor
        OnnxValue nonTensorValue = mock(OnnxValue.class); // не OnnxTensor
        Map.Entry<String, OnnxValue> entry = Map.entry(OUTPUT_NAME, nonTensorValue);

        when(mockModelRunner.runModel(any(BufferedImage.class))).thenReturn(mockResult);
        when(mockResult.spliterator()).thenReturn(List.of(entry).spliterator());

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);

        assertThrows(OrtException.class, () -> service.detectObjects(image));
    }

    // ── detectObjects: happy path ────────────────────────────────────────────

    @Test
    @DisplayName("detectObjects: корректный тензор float[][][] → возвращает DetectedObjects")
    void detectObjects_validTensor_returnsDetectedObjects() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        // Формируем float[][][] — минимальный пустой тензор (1 batch, 84 колонки, 0 боксов)
        float[][][] fakeOutput = new float[1][84][0];

        Map.Entry<String, OnnxValue> entry = Map.entry(OUTPUT_NAME, mockTensor);

        when(mockModelRunner.runModel(any(BufferedImage.class))).thenReturn(mockResult);
        when(mockResult.spliterator()).thenReturn(List.<Map.Entry<String, OnnxValue>>of(entry).spliterator());
        when(mockTensor.getValue()).thenReturn(fakeOutput);

        DetectedObjects fakeDetected = mock(DetectedObjects.class);
        when(mockPostProcessor.postProcess(eq(fakeOutput), anyInt(), anyInt()))
                .thenReturn(fakeDetected);

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        DetectedObjects result = service.detectObjects(image);

        assertSame(fakeDetected, result);
        verify(mockPostProcessor).postProcess(fakeOutput, 640, 640);
    }

    // ── detectObjects: неверный тип данных тензора ─────────────────────────

    @Test
    @DisplayName("detectObjects: getValue() возвращает не float[][][] → OrtException")
    void detectObjects_wrongTensorDataType_throwsOrtException() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        Map.Entry<String, OnnxValue> entry = Map.entry(OUTPUT_NAME, mockTensor);
        when(mockModelRunner.runModel(any(BufferedImage.class))).thenReturn(mockResult);
        when(mockResult.spliterator()).thenReturn(List.<Map.Entry<String, OnnxValue>>of(entry).spliterator());
        when(mockTensor.getValue()).thenReturn("unexpected_string"); // не float[][][]

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);

        assertThrows(OrtException.class, () -> service.detectObjects(image));
    }

    // ── close(): освобождение ресурсов ─────────────────────────────────────

    @Test
    @DisplayName("close: вызывает close() у modelRunner и postProcessor")
    void close_closesModelRunnerAndPostProcessor() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        service.close();

        verify(mockModelRunner).close();
        verify(mockPostProcessor).close();
    }

    @Test
    @DisplayName("close: исключение в modelRunner.close() не мешает вызову postProcessor.close()")
    void close_modelRunnerThrows_postProcessorStillClosed() throws Exception {
        var service = new ObjectDetectionService(mockModelRunner, mockPostProcessor, OUTPUT_NAME);

        doThrow(new IOException("runner error")).when(mockModelRunner).close();

        // finally блок должен гарантировать вызов postProcessor.close()
        assertThrows(IOException.class, service::close);
        verify(mockPostProcessor).close();
    }
}