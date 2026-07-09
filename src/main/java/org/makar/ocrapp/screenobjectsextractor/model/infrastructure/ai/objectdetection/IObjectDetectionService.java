package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OrtException;

import java.awt.image.BufferedImage;
import java.io.Closeable;

/**
 * Контракт сервиса детекции объектов на одном изображении.
 *
 * <p>Позволяет подменять реальный {@link ObjectDetectionService}
 * в тестах {@code CVServicesOrchestrator} и {@code ImageAnalysisManager}
 * без загрузки ONNX-модели.
 */
public interface IObjectDetectionService extends Closeable {

    /**
     * Обнаруживает объекты на переданном изображении.
     *
     * @param image исходное изображение (не {@code null})
     * @return результат детекции; {@code null} если сервис недоступен
     * @throws OrtException            если ONNX Runtime сообщил об ошибке
     * @throws IllegalArgumentException если {@code image == null}
     */
    DetectedObjects detectObjects(BufferedImage image) throws OrtException;
}