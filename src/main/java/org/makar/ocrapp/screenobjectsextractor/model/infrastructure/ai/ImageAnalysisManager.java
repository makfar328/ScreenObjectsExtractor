package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OrtException;
import net.sourceforge.tess4j.TesseractException;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageAnalysisManager implements IImageAnalysisManager {

    private static final Logger logger = Logger.getLogger(ImageAnalysisManager.class.getName());

    private final IOcrService ocrService;
    private final ICVServicesOrchestrator cvServicesOrchestrator;
    private final ExecutorService executorService;

    public ImageAnalysisManager(IOcrService ocrService, ICVServicesOrchestrator cvServicesOrchestrator, ExecutorService aiExecutorService) {
        this.ocrService = ocrService;
        this.cvServicesOrchestrator = cvServicesOrchestrator;
        this.executorService = aiExecutorService;
    }

    /*
    * (!) Рассмотреть 2 варианта в будущем при увеличении количества сервисов:
    * использование разных не полных комбинаций разных сервисов здесь;
    * реализация либо через добавление флагов, либо через написание отдельной функции.
    * (!) Может быть стоит сделать копию функции с набором флагов в качестве аргументов функции,
    * а копия функции без флагов будет всегда выполнять все сервисы.
    * (!) Важно придерживаться правила: если отсутствие какого-либо сервиса не вредит выполнению остальных,
    * тогда не прерывать выполнение процесса анализа изображения.
    *
    * * Инициализация некоторых сервисов, например ExecutorService, обязательна.
     */


    public CompletableFuture<TextObjects> recognizeTextAsync(BufferedImage image) {
        System.out.println("::ImageAnalysisManager.recognizeTextAsync()");
        if (ocrService == null) {
            logger.log(Level.WARNING,"TesseractOcrService_v1 не инициализирован. Распознавание текста не будет выполнено.");
            return CompletableFuture.completedFuture(new TextObjects());
        }
        if (image == null) {
            logger.log(Level.WARNING, "Фотография не найдена, image = null. Распознавание текста не будет выполнено.");
        }

        return CompletableFuture.supplyAsync(() -> {
            TextObjects textObjects = new TextObjects();
            try {
                System.out.println("ImageAnalysisManager - Starting OCR Service...");
                textObjects = ocrService.recognizeText(image);
                System.out.println("ImageAnalysisManager - Finished OCR Service... (no errors)");
                return textObjects;
            } catch (TesseractException tesseractException) {
                logger.log(Level.WARNING, "(ImageAnalysisManager) Warning: ошибка обработки изображения TesseractException: \n" + tesseractException.getMessage());
            }
            return null;
        }, executorService);

    }


    public CompletableFuture<DetectedObjects> detectObjectsAsync(BufferedImage image) {
        System.out.println("::ImageAnalysisManager.detectObjectsAsync()");
        if (cvServicesOrchestrator == null) {
            logger.log(Level.WARNING, "cvServicesOrchestrator не инициализирован. Распознавание объектов не будет выполнено.");
            System.out.println("cvServicesOrchestrator не инициализирован. Распознавание объектов не будет выполнено.");
        }
        if (image == null) {
            logger.log(Level.WARNING, "Фотография не найдена, image = null. Распознавание объектов не будет выполнено.");
            System.out.println("Фотография не найдена, image = null. Распознавание объектов не будет выполнено.");
        }
        return CompletableFuture.supplyAsync(() -> {
            DetectedObjects detectedObjects = null;
            try {
                System.out.println("ImageAnalysisManager - Starting yolo11s.onnx processing...");
                detectedObjects = cvServicesOrchestrator.detectObjects(image);
                if (detectedObjects == null) {
                    return null;
                }
                System.out.println("::ImageAnalysisManager.detectObjectAsync : Detected objects revieww: ");
                if (detectedObjects.getNumberOfObjects() == 0) {
                    System.out.println("::ImageAnalysisManager.detectObjectAsync : detectedObjects.getNumberOfObjects() == 0");
                }
                for (int i = 0; i < detectedObjects.getNumberOfObjects(); i++) {
                    DetectedObjects.DetectedObject item = detectedObjects.item(i);
                    System.out.println(String.format("::ImageAnalysisManager.detectObjectAsync : Object #%d: class=%s, probability=%.2f, boundingBox=%s%n",
                            i, item.getClassName(), item.getProbability(), item.getBoundingBox()));
                }
                System.out.println("ImageAnalysisManager - Finished yolo11s.onnx processing... (no errors)");
                return detectedObjects;
            } catch (OrtException ortException) {
                logger.log(Level.WARNING, "(ImageAnalysisManager) Warning: Error while one of AI-services processing (OrtException): \n " + ortException.getMessage());
                System.out.println("(ImageAnalysisManager) Warning: Error while one of AI-services processing (OrtException): \\n \" + ortException.getMessage()");
            }
            return null;
        }, executorService);
    }
}
