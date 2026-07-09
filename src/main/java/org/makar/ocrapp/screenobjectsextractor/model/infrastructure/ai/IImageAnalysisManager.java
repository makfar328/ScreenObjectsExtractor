package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;

import ai.djl.modality.cv.output.DetectedObjects;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * Контракт менеджера анализа изображений.
 *
 * <p>Координирует асинхронный запуск OCR и детекции объектов.
 * Клиентский код (сервисный слой, UI-контроллеры) обязан зависеть
 * от этого интерфейса, что позволяет подменять реализацию в тестах
 * без инициализации реальных AI-сервисов.
 *
 * <p>Оба метода возвращают {@link CompletableFuture}: при любой ошибке
 * внутри задачи фьючер завершается с {@code null} либо с пустым
 * объектом-результатом — конкретное поведение задаётся реализацией.
 */
public interface IImageAnalysisManager {

    /**
     * Асинхронно распознаёт текст на изображении.
     *
     * @param image исходное изображение; может быть {@code null}
     *              (реализация обязана вернуть завершённый фьючер
     *              с пустым {@link TextObjects}, а не выбросить исключение)
     * @return фьючер с результатом OCR; никогда не {@code null} сам по себе
     */
    CompletableFuture<TextObjects> recognizeTextAsync(BufferedImage image);

    /**
     * Асинхронно обнаруживает объекты на изображении.
     *
     * @param image исходное изображение; может быть {@code null}
     *              (реализация обязана вернуть фьючер, а не выбросить исключение)
     * @return фьючер с результатом детекции; значение внутри фьючера
     *         может быть {@code null} при ошибке или недоступности сервиса
     */
    CompletableFuture<DetectedObjects> detectObjectsAsync(BufferedImage image);
}