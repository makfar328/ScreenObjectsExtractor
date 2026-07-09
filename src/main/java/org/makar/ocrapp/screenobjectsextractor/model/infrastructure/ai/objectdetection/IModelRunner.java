package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.Map;

/**
 * Контракт запуска ONNX-модели на одном изображении.
 *
 * <p>Вынесен в интерфейс, чтобы {@link ObjectDetectionService} можно было
 * тестировать с подставным (mock) раннером без реального файла модели.
 */
public interface IModelRunner extends Closeable {

    /**
     * Прогоняет изображение через модель и возвращает «сырой» результат инференса.
     *
     * <p>Вызывающий код несёт ответственность за закрытие возвращённого
     * {@link OrtSession.Result} (он реализует {@link AutoCloseable}).
     *
     * @param image исходное изображение (не {@code null})
     * @return результат работы ONNX-сессии
     * @throws OrtException если ONNX Runtime сообщил об ошибке
     */
    OrtSession.Result runModel(BufferedImage image) throws OrtException;

    /**
     * Возвращает карту метаданных входных узлов модели (имя → {@link NodeInfo}).
     *
     * @return неизменяемая карта входных узлов
     * @throws OrtException если метаданные недоступны
     */
    Map<String, NodeInfo> getInputInfo() throws OrtException;

    /**
     * Ожидаемая ширина входного изображения (px), зафиксированная при загрузке модели.
     */
    int getInputWidth();

    /**
     * Ожидаемая высота входного изображения (px), зафиксированная при загрузке модели.
     */
    int getInputHeight();
}