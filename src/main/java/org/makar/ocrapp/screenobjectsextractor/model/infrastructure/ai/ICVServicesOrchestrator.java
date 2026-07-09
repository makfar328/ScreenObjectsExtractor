package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OrtException;

import java.awt.image.BufferedImage;
import java.io.Closeable;

/**
 * Контракт оркестратора CV-сервисов.
 *
 * <p>Скрывает за интерфейсом управление жизненным циклом
 * {@code ObjectDetectionService} и извлечение ONNX-модели из ресурсов.
 * {@link ImageAnalysisManager} должен зависеть от этого интерфейса,
 * а не от конкретного класса {@link CVServicesOrchestrator}.
 */
public interface ICVServicesOrchestrator extends Closeable {

    /**
     * Выполняет детекцию объектов на переданном изображении.
     *
     * @param image изображение для анализа (может быть {@code null} —
     *              реализация обязана корректно обработать этот случай
     *              и вернуть {@code null} без исключения)
     * @return обнаруженные объекты, либо {@code null} если сервис недоступен
     * @throws OrtException если ONNX Runtime сообщил об ошибке
     */
    DetectedObjects detectObjects(BufferedImage image) throws OrtException;
}