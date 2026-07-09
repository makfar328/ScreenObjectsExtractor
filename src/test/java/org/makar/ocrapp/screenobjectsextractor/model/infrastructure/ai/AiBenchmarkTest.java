package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;

import ai.djl.modality.cv.output.DetectedObjects;
import org.junit.jupiter.api.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection.ObjectDetectionService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2.TesseractOcrService_v2;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Бенчмарк AI-слоя — три замера для раздела 4 ВКР:
 *
 *   BM-1  Скорость инференса YOLO: yolo11s vs yolo11x (CPU, ONNX Runtime)
 *   BM-2  Скорость OCR в зависимости от площади изображения (Tesseract v2)
 *   BM-3  Последовательный vs параллельный запуск YOLO + OCR (CompletableFuture)
 *
 * Запуск: Run as JUnit test (один раз вручную, не в CI).
 * Вывод:  CSV-строки в stdout — копируйте в Excel / matplotlib.
 *
 * ВАЖНО: тесты НЕ используют @BeforeEach/@AfterEach совместно,
 * потому что модели создаются/закрываются внутри каждого теста явно
 * (иначе тяжёлые ресурсы висели бы весь класс).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AiBenchmarkTest {

    // ── Константы ──────────────────────────────────────────────────────────

    /** Количество «прогревочных» прогонов (не попадают в статистику). */
    private static final int WARMUP_RUNS = 3;

    /** Количество измерительных прогонов. */
    private static final int MEASURE_RUNS = 10;

    /** Имя выходного тензора модели — совпадает с AppConfig.YOLO_OUTPUT_NAME. */
    private static final String OUTPUT_NAME = "output0";

    // ── Вспомогательные методы ─────────────────────────────────────────────

    /**
     * Извлекает ONNX-модель из ресурсов JAR во временный файл на диске.
     * Повторяет логику MainApplication.startServiceInitialization().
     */
    private static Path extractModel(String resourcePath) throws Exception {
        InputStream modelStream = AiBenchmarkTest.class.getResourceAsStream(resourcePath);
        if (modelStream == null) {
            throw new IllegalStateException("Модель не найдена в ресурсах: " + resourcePath);
        }
        Path tmp = Files.createTempFile("bm_yolo_", ".onnx");
        tmp.toFile().deleteOnExit();
        Files.copy(modelStream, tmp, StandardCopyOption.REPLACE_EXISTING);
        modelStream.close();
        return tmp;
    }

    /**
     * Синтетическое изображение заданного размера, залитое случайным цветом.
     * Достаточно для прогрева ONNX Runtime и Tesseract без реального контента.
     */
    private static BufferedImage makeImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Светлый фон + несколько тёмных прямоугольников имитируют текстовый документ
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < height; i += 30) {
            g.fillRect(10, i + 5, (int) (width * 0.8), 14);
        }
        g.dispose();
        return img;
    }

    /** Вычисляет среднее значение массива long[]. */
    private static double mean(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return (double) sum / values.length;
    }

    /** Вычисляет стандартное отклонение. */
    private static double stdDev(long[] values, double mean) {
        double variance = 0;
        for (long v : values) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / values.length);
    }

    // ══════════════════════════════════════════════════════════════════════
    // BM-1 : Скорость инференса YOLO — yolo11s vs yolo11x
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Для каждой модели выполняет WARMUP_RUNS прогревочных + MEASURE_RUNS
     * измерительных прогонов на изображении 640×640 px.
     *
     * <p>Вывод (stdout):
     * <pre>
     * === BM-1: YOLO Inference Speed ===
     * model,run,latency_ms
     * yolo11s,1,45
     * yolo11s,2,43
     * ...
     * yolo11x,1,198
     * ...
     * === BM-1 Summary ===
     * model,mean_ms,std_ms,min_ms,max_ms
     * yolo11s,44.3,1.2,42,47
     * yolo11x,201.5,4.7,196,212
     * </pre>
     */
    @Test
    @Order(1)
    @DisplayName("BM-1: YOLO inference speed — yolo11s vs yolo11x")
    void bm1_yoloInferenceSpeed() throws Exception {

        // Модели, доступные в ресурсах проекта
        record ModelEntry(String label, String resourcePath) {}
        List<ModelEntry> models = List.of(
                new ModelEntry("yolo11s", "/models/yolo11s.onnx"),
                new ModelEntry("yolo11x", "/models/yolo11x.onnx")
        );

        BufferedImage testImage = makeImage(640, 640);

        System.out.println("\n=== BM-1: YOLO Inference Speed ===");
        System.out.println("model,run,latency_ms");

        System.out.println("\n=== BM-1 Summary ===");
        System.out.println("model,mean_ms,std_ms,min_ms,max_ms");

        for (ModelEntry entry : models) {
            Path modelPath = extractModel(entry.resourcePath());

            try (ObjectDetectionService service =
                         new ObjectDetectionService(modelPath.toString(), OUTPUT_NAME)) {

                // Прогрев — не замеряем
                for (int i = 0; i < WARMUP_RUNS; i++) {
                    service.detectObjects(testImage);
                }

                // Замеры
                long[] latencies = new long[MEASURE_RUNS];
                for (int i = 0; i < MEASURE_RUNS; i++) {
                    long t0 = System.currentTimeMillis();
                    DetectedObjects result = service.detectObjects(testImage);
                    latencies[i] = System.currentTimeMillis() - t0;

                    System.out.printf("%s,%d,%d%n", entry.label(), i + 1, latencies[i]);

                    // Защита от null при нулевом результате
                    Assertions.assertNotNull(result,
                            "detectObjects вернул null для модели " + entry.label());
                }

                // Итоги
                double avg = mean(latencies);
                double sd  = stdDev(latencies, avg);
                long   min = Arrays.stream(latencies).min().orElse(0);
                long   max = Arrays.stream(latencies).max().orElse(0);

                System.out.printf("# %s,%.1f,%.1f,%d,%d%n",
                        entry.label(), avg, sd, min, max);

            } finally {
                Files.deleteIfExists(modelPath);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BM-2 : Скорость OCR в зависимости от площади изображения
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Прогоняет Tesseract на изображениях разных размеров.
     * Площади выбраны так, чтобы охватить диапазон от скриншота кнопки
     * до полноэкранного захвата (FullHD).
     *
     * <p>Вывод (stdout):
     * <pre>
     * === BM-2: OCR Speed vs Image Area ===
     * width,height,area_px2,run,latency_ms
     * 200,100,20000,1,87
     * ...
     * === BM-2 Summary ===
     * width,height,area_px2,mean_ms,std_ms
     * 200,100,20000,85.3,3.1
     * ...
     * </pre>
     */
    @Test
    @Order(2)
    @DisplayName("BM-2: OCR speed vs image area (Tesseract v2)")
    void bm2_ocrSpeedVsArea() throws Exception {

        // Набор размеров: от маленького виджета до Full HD
        record SizeEntry(int w, int h) {
            long area() { return (long) w * h; }
        }
        List<SizeEntry> sizes = List.of(
                new SizeEntry(200,  100),   //   20 000 px²  — небольшой элемент UI
                new SizeEntry(400,  300),   //  120 000 px²  — средний блок
                new SizeEntry(800,  600),   //  480 000 px²  — стандартное окно
                new SizeEntry(1280, 720),   //  921 600 px²  — HD
                new SizeEntry(1920, 1080)   // 2 073 600 px² — Full HD
        );

        TesseractOcrService_v2 ocr = TesseractOcrService_v2.fromResources();

        System.out.println("\n=== BM-2: OCR Speed vs Image Area ===");
        System.out.println("width,height,area_px2,run,latency_ms");

        System.out.println("\n=== BM-2 Summary ===");
        System.out.println("width,height,area_px2,mean_ms,std_ms");

        for (SizeEntry sz : sizes) {
            BufferedImage img = makeImage(sz.w(), sz.h());

            // Прогрев
            for (int i = 0; i < WARMUP_RUNS; i++) {
                ocr.recognizeText(img);
            }

            // Замеры
            long[] latencies = new long[MEASURE_RUNS];
            for (int i = 0; i < MEASURE_RUNS; i++) {
                long t0 = System.currentTimeMillis();
                TextObjects result = ocr.recognizeText(img);
                latencies[i] = System.currentTimeMillis() - t0;

                System.out.printf("%d,%d,%d,%d,%d%n",
                        sz.w(), sz.h(), sz.area(), i + 1, latencies[i]);

                Assertions.assertNotNull(result,
                        "recognizeText вернул null для размера " + sz.w() + "x" + sz.h());
            }

            double avg = mean(latencies);
            double sd  = stdDev(latencies, avg);
            System.out.printf("# %d,%d,%d,%.1f,%.1f%n",
                    sz.w(), sz.h(), sz.area(), avg, sd);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BM-3 : Последовательный vs параллельный запуск YOLO + OCR
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Сравнивает суммарное время двух режимов на изображении 800×600:
     * <ul>
     *   <li><b>Sequential</b> — сначала YOLO, затем OCR в том же потоке.</li>
     *   <li><b>Parallel</b>   — оба CompletableFuture запускаются одновременно
     *       через {@link ExecutorService}, результаты объединяются через
     *       {@link CompletableFuture#allOf}.</li>
     * </ul>
     *
     * <p>Вывод (stdout):
     * <pre>
     * === BM-3: Sequential vs Parallel (YOLO + OCR) ===
     * mode,run,latency_ms
     * sequential,1,312
     * ...
     * parallel,1,198
     * ...
     * === BM-3 Summary ===
     * mode,mean_ms,std_ms,speedup_x
     * sequential,308.4,6.2,1.00
     * parallel,199.1,4.5,1.55
     * </pre>
     */
    @Test
    @Order(3)
    @DisplayName("BM-3: Sequential vs Parallel (YOLO + OCR)")
    void bm3_sequentialVsParallel() throws Exception {

        final int W = 800, H = 600;
        BufferedImage testImage = makeImage(W, H);

        // Извлекаем лёгкую модель (s) — достаточно для сравнения режимов
        Path modelPath = extractModel("/models/yolo11s.onnx");
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try (ObjectDetectionService yolo =
                     new ObjectDetectionService(modelPath.toString(), OUTPUT_NAME)) {

            TesseractOcrService_v2 ocr = TesseractOcrService_v2.fromResources();

            // ── Прогрев: оба сервиса ───────────────────────────────────────
            for (int i = 0; i < WARMUP_RUNS; i++) {
                yolo.detectObjects(testImage);
                ocr.recognizeText(testImage);
            }

            System.out.println("\n=== BM-3: Sequential vs Parallel (YOLO + OCR) ===");
            System.out.println("mode,run,latency_ms");

            // ── Последовательный режим ─────────────────────────────────────
            long[] seqTimes = new long[MEASURE_RUNS];
            for (int i = 0; i < MEASURE_RUNS; i++) {
                long t0 = System.currentTimeMillis();

                DetectedObjects detectedObjects = yolo.detectObjects(testImage);  // шаг 1
                TextObjects     textObjects     = ocr.recognizeText(testImage);   // шаг 2

                seqTimes[i] = System.currentTimeMillis() - t0;
                System.out.printf("sequential,%d,%d%n", i + 1, seqTimes[i]);

                Assertions.assertNotNull(detectedObjects, "YOLO вернул null (sequential)");
                Assertions.assertNotNull(textObjects,     "OCR вернул null (sequential)");
            }

            // ── Параллельный режим ─────────────────────────────────────────
            long[] parTimes = new long[MEASURE_RUNS];
            for (int i = 0; i < MEASURE_RUNS; i++) {
                long t0 = System.currentTimeMillis();

                // Точная копия паттерна из ImageAnalysisManager
                CompletableFuture<DetectedObjects> yoloFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return yolo.detectObjects(testImage);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, pool);

                CompletableFuture<TextObjects> ocrFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return ocr.recognizeText(testImage);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, pool);

                // Ждём завершения обоих
                CompletableFuture.allOf(yoloFuture, ocrFuture).join();

                parTimes[i] = System.currentTimeMillis() - t0;
                System.out.printf("parallel,%d,%d%n", i + 1, parTimes[i]);

                Assertions.assertNotNull(yoloFuture.get(), "YOLO вернул null (parallel)");
                Assertions.assertNotNull(ocrFuture.get(),  "OCR вернул null (parallel)");
            }

            // ── Итоговая сводка ────────────────────────────────────────────
            double seqMean = mean(seqTimes);
            double parMean = mean(parTimes);
            double seqSd   = stdDev(seqTimes, seqMean);
            double parSd   = stdDev(parTimes, parMean);
            double speedup = seqMean / parMean;

            System.out.println("\n=== BM-3 Summary ===");
            System.out.println("mode,mean_ms,std_ms,speedup_x");
            System.out.printf("sequential,%.1f,%.1f,1.00%n", seqMean, seqSd);
            System.out.printf("parallel,%.1f,%.1f,%.2f%n",   parMean, parSd, speedup);

            // Параллельный режим должен быть быстрее с высокой вероятностью
            Assertions.assertTrue(parMean < seqMean,
                    String.format(
                            "Ожидается: parallel (%.0f мс) < sequential (%.0f мс). " +
                                    "Возможно, машина однопоточная или один из сервисов уже использует все ядра.",
                            parMean, seqMean));

        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            Files.deleteIfExists(modelPath);
        }
    }
}