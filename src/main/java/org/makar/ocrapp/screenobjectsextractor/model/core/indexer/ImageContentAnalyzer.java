package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import ai.djl.modality.cv.output.DetectedObjects;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ImageAnalysisManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Модуль для анализа содержания изображений
 * например, для извлечения ключевых слов "море", "люди" с использованием сервисных классов, реализующих AI-обработку,
 * включая OCR (распознавание текста) и Object Detection (обнаружение объектов).
 * Обновляет существующий объект FileMetadata. Использует асинхронный подход для выполнения AI-задач.
 */
public class ImageContentAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(ImageContentAnalyzer.class.getName());
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private final ImageAnalysisManager imageAnalysisManager;
    private final ExecutorService ioExecutorService;


    public ImageContentAnalyzer(ImageAnalysisManager imageAnalysisManager, ExecutorService ioExecutorService) {
        this.imageAnalysisManager = imageAnalysisManager;
        this.ioExecutorService = ioExecutorService;
        LOGGER.info("ImageContentAnalyzer инициализирован успешно.");
    }


    /**
     * Вспомогательный метод для загрузки изображения, с проверкой типа файла и асинхронной загрузкой.
     *
     * @param filePath Путь к файлу изображения.
     * @return Загруженное BufferedImage или null, если файл не является изображением или не может быть прочитан.
     * @throws IOException В случае ошибки чтения файла.
     */
    private BufferedImage loadImage(Path filePath) throws IOException {
        System.out.println("::ImageContentAnalyzer.loadImage()");
        String fileExtension = getFileExtension(filePath);

        // надо делать это через FileScanner
        if (fileExtension == null ||
        !(fileExtension.equalsIgnoreCase("png") ||
        fileExtension.equalsIgnoreCase("jpg") ||
        fileExtension.equalsIgnoreCase("jpeg"))) {
            LOGGER.fine("Файл " + filePath + " не является изображением для AI-анализа, пропуск.");
            return null;
        }

        CompletableFuture<BufferedImage> imageLoadingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage image = ImageIO.read(filePath.toFile());
                if (image == null) {
                    LOGGER.log(Level.WARNING, "Не удалось прочитать изображение или файл не является изображением: " + filePath);
                }
                return image;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Ошибка при чтении файла изображения " + filePath + ": " + e.getMessage(), e);
                throw new CompletionException("Failed to read image file", e); // Оборачиваем IOException
            }
        }, ioExecutorService);

        try {
            return imageLoadingFuture.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Unexpected error during image loading: " + e.getMessage(), e);
        }
    }


    private String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return null;
    }


    /**
     * Выполняет оптическое распознавание текста (OCR) для заданного файла изображения.
     *
     * @param filePath Путь к файлу изображения.
     * @return Список распознанных текстовых объектов. Если файл не изображение или OCR не удался, возвращает пустой список.
     * @throws IOException Если произошла ошибка при чтении файла.
     */
    public List<TextObject> recognizeText(Path filePath) throws IOException {
        System.out.println("::ImageContentAnalyzer.recognizeText()");
        BufferedImage image = loadImage(filePath);
        if (image == null) {
            return Collections.emptyList();
        }

        LOGGER.info("Начат OCR для файла: " + filePath);
        //long sessionId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

        try {
            TextObjects recognizedTextObjects = imageAnalysisManager.recognizeTextAsync(image)
                    .exceptionally(ex -> {
                        LOGGER.log(Level.WARNING, "Ошибка при выполнении OCR для файла " + filePath + ": " + ex.getMessage(), ex);
                        return null;
                    }).join();
            if (recognizedTextObjects != null && recognizedTextObjects.getTextObjectList() != null) {
                LOGGER.fine("OCR успешно выполнен для файла: " + filePath + ". Распознано объектов: " + recognizedTextObjects.getTextObjectList().size());
                return recognizedTextObjects.getTextObjectList();
            }
        } catch (CompletionException e) {
            LOGGER.log(Level.WARNING, "Ошибка при выполнении OCR для файла " + filePath + ": " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }


    /**
     * Выполняет обнаружение объектов для заданного файла изображения.
     *
     * @param filePath Путь к файлу изображения.
     * @return Список обнаруженных объектов. Если файл не изображение или обнаружение не удались, возвращает пустой список.
     * @throws IOException Если произошла ошибка при чтении файла.
     */
    public List<OCRAppDetectedObject> analyzeObjects(Path filePath) throws IOException {
        System.out.println("::ImageContentAnalyzer.analyzeObjects()");
        BufferedImage image = loadImage(filePath);
        if (image == null) {
            return Collections.emptyList();
        }

        LOGGER.info("::ImageContentAnalyzer.analyzeObject : Начат анализ объектов для файла: " + filePath);
        //long sessionId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

        try {
            DetectedObjects detectedObjects = imageAnalysisManager.detectObjectsAsync(image)
                    .exceptionally(ex -> {
                        System.out.println("::ImageContentAnalyzer.analyzeObject : Ошибка при выполнении Object Detection для файла " + filePath + ": " + ex.getMessage());
                        LOGGER.log(Level.WARNING, "::ImageContentAnalyzer.analyzeObject : Ошибка при выполнении Object Detection для файла " + filePath + ": " + ex.getMessage(), ex);
                        return null;
                    }).join();

            if (detectedObjects != null) {
                List<OCRAppDetectedObject> ocrAppDetectedObjects = new java.util.ArrayList<>();
                System.out.println("::ImageContentAnalyzer.analyzeObject : Detected objects review: ");
                for (int i = 0; i < detectedObjects.getNumberOfObjects(); i++) {
                    DetectedObjects.DetectedObject item = detectedObjects.item(i);
                    ocrAppDetectedObjects.add(new OCRAppDetectedObject(item));
                }
                LOGGER.fine("Обнаружение объектов успешно выполнено для файла: " + filePath + ". Обнаружено объектов: " + ocrAppDetectedObjects.size());
                return ocrAppDetectedObjects;
            }
        } catch (CompletionException e) {
            LOGGER.log(Level.WARNING, "Ошибка при выполнении Object Detection для файла " + filePath + ": " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }


    /**
     * OCR + Object Detection для уже загруженного BufferedImage.
     * Используется когда изображение получено не с диска (например, захват экрана).
     * Результаты записываются в переданный FileMetadata.
     *
     * @param image    Уже загруженное изображение.
     * @param metadata FileMetadata для записи результатов (filePath может быть null).
     */
    public void analyzeAndUpdateMetadata(BufferedImage image, FileMetadata metadata) {
        if (image == null) {
            LOGGER.warning("analyzeAndUpdateMetadata(BufferedImage): image == null, пропуск.");
            return;
        }

        CompletableFuture<TextObjects> ocrFuture = imageAnalysisManager.recognizeTextAsync(image)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "OCR ошибка (capture): " + ex.getMessage(), ex);
                    return null;
                });

        CompletableFuture<DetectedObjects> detectionFuture = imageAnalysisManager.detectObjectsAsync(image)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Object Detection ошибка (capture): " + ex.getMessage(), ex);
                    return null;
                });

        CompletableFuture.allOf(ocrFuture, detectionFuture).join();

        TextObjects recognizedText = ocrFuture.getNow(null);
        metadata.setRecognizedTextContent(
                recognizedText != null && recognizedText.getTextObjectList() != null
                        ? recognizedText.getTextObjectList()
                        : Collections.emptyList()
        );

        metadata.setImageWidth(image.getWidth());
        metadata.setImageHeight(image.getHeight());

        DetectedObjects detected = detectionFuture.getNow(null);
        if (detected != null) {
            List<OCRAppDetectedObject> mapped = new ArrayList<>();
            for (int i = 0; i < detected.getNumberOfObjects(); i++) {
                DetectedObjects.DetectedObject item = detected.item(i);
                mapped.add(new OCRAppDetectedObject(item));
            }
            metadata.setDetectedObjects(mapped);
        } else {
            metadata.setDetectedObjects(Collections.emptyList());
        }

        LOGGER.info("analyzeAndUpdateMetadata(capture) завершён. " +
                "Текст: " + metadata.getRecognizedTextContent().size() +
                ", Объекты: " + metadata.getDetectedObjects().size());
    }


    /**
     * Выполняет OCR и Object Detection для файла, если он является изображением,
     * и обновляет соответствующие поля в объекте FileMetadata.
     * AI-задачи выполняются асинхронно и параллельно.
     *
     * @param metadata Объект FileMetadata, который будет обновлен.
     * @throws IOException      Если произошла ошибка при чтении файла изображения.
     */
    public void analyzeAndUpdateMetadata(FileMetadata metadata) throws IOException {
        System.out.println("::ImageContentAnalyzer.analyzeAndUpdateMetadata()");
        Path filePath = metadata.getFilePath();
        String fileExtension = metadata.getFileExtension();

        // Проверяем, является ли файл изображением
        if (fileExtension == null ||
                !(fileExtension.equalsIgnoreCase("png") ||
                        fileExtension.equalsIgnoreCase("jpg") ||
                        fileExtension.equalsIgnoreCase("jpeg"))) {
            LOGGER.fine("Файл " + filePath + " не является изображением, пропуск глубокого анализа.");
            return;
        }

        LOGGER.info("Начат глубокий анализ изображения: " + filePath);
        BufferedImage image = null;
        CompletableFuture<BufferedImage> imageLoadingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(filePath.toFile());
                if (img == null) {
                    LOGGER.log(Level.WARNING, "Не удалось прочитать изображение или файл не является изображением: " + filePath);
                }
                return img;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Ошибка при чтении файла изображения " + filePath + ": " + e.getMessage(), e);
                throw new CompletionException("Failed to read image file", e); // Оборачиваем IOException
            }
        }, ioExecutorService);

        // вернуться к теории
        try {
            image = imageLoadingFuture.join();
            if (image == null) {
                return; // Если изображение не удалось прочитать, выходим.
            }
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Unexpected error during image loading: " + e.getMessage(), e);
        }

        //long sessionId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

        // 1. Запускаем OCR и Object Detection, обрабатывая исключения для каждого
        CompletableFuture<TextObjects> ocrFuture = imageAnalysisManager.recognizeTextAsync(image)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Ошибка при выполнении OCR для файла " + filePath + ": " + ex.getMessage(), ex);
                    return null;
                });

        CompletableFuture<DetectedObjects> objectDetectionFuture = imageAnalysisManager.detectObjectsAsync(image)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Ошибка при выполнении Object Detection для файла " + filePath + ": " + ex.getMessage(), ex);
                    return null;
                });

        CompletableFuture.allOf(ocrFuture, objectDetectionFuture).join();

        TextObjects recognizedTextObjects = ocrFuture.getNow(null);
        if (recognizedTextObjects != null && recognizedTextObjects.getTextObjectList() != null) {
            metadata.setRecognizedTextContent(recognizedTextObjects.getTextObjectList());
            LOGGER.fine("OCR успешно выполнен для файла: " + filePath + ". Распознано объектов: " + recognizedTextObjects.getTextObjectList().size());
        } else {
            metadata.setRecognizedTextContent(Collections.emptyList());
        }

        // 3. Обработка результатов Object Detection
        DetectedObjects detectedObjects = objectDetectionFuture.getNow(null);
        if (detectedObjects != null) {
            List<OCRAppDetectedObject> ocrAppDetectedObjects = new java.util.ArrayList<>(); // Упрощено
            for (int i = 0; i < detectedObjects.getNumberOfObjects(); i++) {
                DetectedObjects.DetectedObject item = detectedObjects.item(i);
                ocrAppDetectedObjects.add(new OCRAppDetectedObject(item));
            }
            metadata.setDetectedObjects(ocrAppDetectedObjects);
            LOGGER.fine("Обнаружение объектов успешно выполнено для файла: " + filePath + ". Обнаружено объектов: " + ocrAppDetectedObjects.size());
        } else {
            metadata.setDetectedObjects(Collections.emptyList());
        }


        LOGGER.info("Глубокий анализ изображения завершен: " + filePath);
    }
}
