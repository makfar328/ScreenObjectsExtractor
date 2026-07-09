package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;


import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OrtException;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection.ObjectDetectionService;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/* Класс содержит логику вызовов AI-сервисов, получать результаты их работы. Ответственный
* за инициализацию и управление жизненным циклом:
* OCRService,
* ObjectDetectionService,
* а также за безопасное извлечение ONNX модели из ресурсов */
public class CVServicesOrchestrator implements ICVServicesOrchestrator {

    private static final Logger logger = Logger.getLogger(CVServicesOrchestrator.class.getName());
    private Path tempModelPath;

    private ObjectDetectionService objectDetectionService;

    public CVServicesOrchestrator(ObjectDetectionService objectDetectionService) {
        this.objectDetectionService = objectDetectionService;
        logger.info("CVServicesOrchestrator успешно инициализирован с инжектированным ObjectDetectionService.");
    }


    /**
     * Выполняет детекцию объектов на заданном изображении.
     *
     * @param image Изображение для детекции объектов.
     * @return Обнаруженные объекты - DetectedObjects из ai.djl.modality.cv.output.
     * @throws OrtException Если произошла ошибка при работе ONNX Runtime.
     */
    public DetectedObjects detectObjects(BufferedImage image) throws OrtException {
        // сервис мог проинициализироваться с ошибкой, при этом приложение продолжит работать
        if (objectDetectionService == null) {
            logger.log(Level.WARNING, "objectDetectionService не инициализирован. Обнаружение объектов моделью yolo пропущена");
            return null;
        }
        return objectDetectionService.detectObjects(image);
    }


    @Override
    public void close() throws IOException {
        logger.info("Закрытие AI-сервисов");
        if (objectDetectionService != null) {
            objectDetectionService.close();
        }
        // удаление временного файла с моделями
        if (tempModelPath != null && Files.exists(tempModelPath)) {
            try {
                Files.delete(tempModelPath);
                logger.info("Удалены временные файлы с моделями .onnx");
            } catch (IOException ioException) {
                logger.log(Level.WARNING, "Не удалось удалить временный файл модели, хотя он существует и не null: " + ioException);
            }
        }
    }
}
