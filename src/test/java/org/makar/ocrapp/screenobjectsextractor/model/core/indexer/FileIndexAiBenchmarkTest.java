package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.junit.jupiter.api.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.CVServicesOrchestrator;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ImageAnalysisManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection.ObjectDetectionService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2.TesseractOcrService_v2;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.SQLiteConnectionManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.FileIndexDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexDatabaseManager;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase.IFileIndexRepository;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * BM-4b: Инкрементальное индексирование с РЕАЛЬНЫМ AI (YOLO11s + Tesseract).
 *
 * Демонстрирует настоящий выигрыш инкрементальности:
 *   - Первый проход  : scan + hash + SQLite + YOLO+ OCR
 *   - Второй проход  : scan + hash + SQLite SELECT → уже в индексе → пропуск AI
 *
 *
 * ТРЕБУЕТ:   /resources/models/yolo11s.onnx  (уже есть в проекте)
 *            Tesseract tessdata               (уже есть в проекте)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileIndexAiBenchmarkTest {

    // ── Параметры ──────────────────────────────────────────────────────────

    /**
     * Размеры тестовых каталогов. Ограничено 50: 50 × ~10 с = ~8 мин на все прогоны.
     */
    private static final int[] FILE_COUNTS = {5, 10, 20, 30, 50, 75, 100, 150};

    /**
     * 1 прогрев достаточно: основной overhead — загрузка модели в @BeforeAll.
     */
    private static final int WARMUP_RUNS = 1;

    /**
     * Три замера — достаточно для среднего и σ.
     */
    private static final int MEASURE_RUNS = 3;

    private static final String OUTPUT_NAME = "output0";

    private static final String CSV_OUTPUT =
            "C:/Users/Makar/IdeaProjects/java-project/ScreenObjectsExtractor/bm4b_ai_indexing_results.csv";

    // ── Инфраструктура ─────────────────────────────────────────────────────

    // ── Поля класса ────────────────────────────────────────────────────────────
    private static Path                       tempDbPath;
    private static Path                       modelPath;

    private static SQLiteConnectionManager    connectionManager;
    private static FileIndexDatabaseManager   dbManager;
    private static IFileIndexRepository       repository;

    private static FileScanner                fileScanner;
    private static MetadataExtractor          metadataExtractor;

    private static ObjectDetectionService     yoloService;
    private static IOcrService                ocrService;

    private static ExecutorService            indexingExecutorService;  // для FileIndexService
    private static ExecutorService            aiExecutorService;   // для ImageAnalysisManager
    private static ExecutorService            ioExecutorService;   // для ImageContentAnalyzer

    private static ImageAnalysisManager imageAnalysisManager;
    private static ImageContentAnalyzer       realAnalyzer;

    // ── @BeforeAll ─────────────────────────────────────────────────────────────
    @BeforeAll
    static void setUpInfrastructure() throws Exception {

        // 1. SQLite-БД только для бенчмарка
        tempDbPath = Files.createTempFile("bm4b_index_", ".db");
        tempDbPath.toFile().deleteOnExit();

        String jdbcUrl = "jdbc:sqlite:" + tempDbPath.toString().replace("\\", "/");
        connectionManager = new SQLiteConnectionManager(jdbcUrl);
        connectionManager.initializeDatabaseFile();

        dbManager = new FileIndexDatabaseManager(connectionManager);
        dbManager.initializeSchema();
        repository = dbManager.getRepository();

        fileScanner        = new FileScanner();
        metadataExtractor  = new MetadataExtractor();

        // 2. Извлечь ONNX-модель из JAR во временный файл (паттерн из MainApplication)
        try (InputStream is = FileIndexAiBenchmarkTest.class
                .getResourceAsStream("/models/yolo11s.onnx")) {
            if (is == null) {
                throw new IllegalStateException(
                        "yolo11s.onnx не найдена в ресурсах (/models/yolo11s.onnx)");
            }
            modelPath = Files.createTempFile("bm4b_yolo_", ".onnx");
            modelPath.toFile().deleteOnExit();
            Files.copy(is, modelPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 3. Построить граф зависимостей AI-слоя — точная копия buildServiceGraph()
        //    из MainApplication, без JavaFX-окружения.
        int cpuCount    = Runtime.getRuntime().availableProcessors();
        aiExecutorService = Executors.newFixedThreadPool(Math.min(cpuCount, 2));
        ioExecutorService = Executors.newFixedThreadPool(cpuCount);

        yoloService   = new ObjectDetectionService(modelPath.toString(), OUTPUT_NAME);
        ocrService    = TesseractOcrService_v2.fromResources();   // или OcrServiceFactory.create()

        CVServicesOrchestrator aiOrchestrator =
                new CVServicesOrchestrator(yoloService);

        imageAnalysisManager =
                new ImageAnalysisManager(ocrService, aiOrchestrator, aiExecutorService);

        realAnalyzer =
                new ImageContentAnalyzer(imageAnalysisManager, ioExecutorService);

        indexingExecutorService = Executors.newFixedThreadPool(cpuCount);

        System.out.println("[BM-4b] БД: "         + tempDbPath);
        System.out.println("[BM-4b] YOLO модель: " + modelPath);
        System.out.println("[BM-4b] Инфраструктура готова.");
    }

    // ── @AfterAll — обязательное освобождение ресурсов ────────────────────────
    @AfterAll
    static void tearDownInfrastructure() throws Exception {
        if (aiExecutorService != null) { aiExecutorService.shutdownNow(); }
        if (ioExecutorService != null) { ioExecutorService.shutdownNow(); }
        if (yoloService       != null) { yoloService.close();             }
        if (connectionManager != null) { connectionManager.close();       }
        Files.deleteIfExists(modelPath);
        Files.deleteIfExists(tempDbPath);
        System.out.println("[BM-4b] Ресурсы освобождены.");
    }

// ── Helper: N синтетических PNG 640×480 ───────────────────────────────

    /**
     * Каждый файл — уникальный цвет + текст.
     * 640×480 ≈ реальный скриншот UI-элемента — дополнительная нагрузка на OCR.
     */
    private Path createSyntheticImageDir(int count) throws IOException {
        Path dir = Files.createTempDirectory("bm4b_imgs_" + count + "_");
        dir.toFile().deleteOnExit();
        for (int i = 0; i < count; i++) {
            BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color((i * 37) % 256, (i * 71) % 256, (i * 113) % 256));
            g.fillRect(0, 0, 640, 480);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("BM-4b File " + i, 20, 40);
            g.drawString("Synthetic benchmark image", 20, 80);
            g.dispose();

            Path out = dir.resolve("img_" + String.format("%04d", i) + ".png");
            ImageIO.write(img, "png", out.toFile());
        }
        return dir;
    }

// ── Helper: построить FileIndexService с реальным AI ──────────────────

    private FileIndexService buildService() {
        FileIndexingProcessor processor = new FileIndexingProcessor(
                repository, metadataExtractor, realAnalyzer
        );
        return new FileIndexService(
                repository, processor, fileScanner, metadataExtractor,
                realAnalyzer,
                indexingExecutorService
        );
    }

// ══════════════════════════════════════════════════════════════════════
// BM-4b: первичный vs повторный проход с реальным AI
// ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("BM-4b: AI Incremental Indexing — first pass (YOLO+OCR) vs second pass (cache)")
    void bm4b_aiIncrementalIndexingBenchmark() throws Exception {

        PrintWriter csv = new PrintWriter(new FileWriter(CSV_OUTPUT));
        csv.println("file_count,pass_type,run,elapsed_ms");

        System.out.println("[BM-4b] Начало замеров (это займёт несколько минут)");
        System.out.println("file_count,pass_type,run,elapsed_ms");

        StringBuilder summary = new StringBuilder();
        summary.append("\n[BM-4b] === SUMMARY ===\n");
        summary.append("file_count,first_pass_mean_ms,second_pass_mean_ms,speedup_x\n");

        for (int fileCount : FILE_COUNTS) {

            System.out.printf("[BM-4b] → N=%d файлов%n", fileCount);
            Path imgDir = createSyntheticImageDir(fileCount);
            SearchDirectoryConfig dirConfig = new SearchDirectoryConfig(imgDir, -1);

            // ── Адаптивные таймауты ────────────────────────────────────────────
            // Первый проход: YOLO ~136 мс + OCR ~59 мс = ~200 мс/файл на поток.
            // С cpuCount потоками: ceil(N / cpuCount) × 200 мс + буфер × 10.
            int cpuCount = Runtime.getRuntime().availableProcessors();
            long firstPassSec  = Math.max(120L, (long) Math.ceil((double) fileCount / cpuCount) * 10L);
            long secondPassSec = Math.max(30L,  fileCount / 2L);   // второй проход без AI: ~9 мс/файл

            // Количество замеров: для больших N хватит 2, для малых оставляем 3.
            // Позволяет уложиться в разумное время без потери статистической значимости.
            int effectiveMeasureRuns = (fileCount >= 100) ? 2 : MEASURE_RUNS;

            // ── Прогрев ──────────────────────────────────────────────────
            for (int w = 0; w < WARMUP_RUNS; w++) {
                repository.truncateAll();
                FileIndexService warmup = buildService();
                warmup.performFullIncrementalIndexing(dirConfig)
                        .get(firstPassSec, TimeUnit.SECONDS);
                System.out.printf("[BM-4b]   warmup %d/%d для N=%d завершён%n",
                        w + 1, WARMUP_RUNS, fileCount);
            }

            // ── Первый проход ─────────────────────────────────────────────
            long[] firstPassTimes = new long[effectiveMeasureRuns];
            for (int r = 0; r < effectiveMeasureRuns; r++) {
                repository.truncateAll();
                FileIndexService service = buildService();

                long t0 = System.currentTimeMillis();
                service.performFullIncrementalIndexing(dirConfig)
                        .get(firstPassSec, TimeUnit.SECONDS);
                firstPassTimes[r] = System.currentTimeMillis() - t0;

                String row = fileCount + ",first_pass," + (r + 1) + "," + firstPassTimes[r];
                csv.println(row);
                csv.flush();
                System.out.println(row);
            }

            // ── Подготовка к второму проходу ─────────────────────────────
            repository.truncateAll();
            buildService().performFullIncrementalIndexing(dirConfig)
                    .get(firstPassSec, TimeUnit.SECONDS);
            System.out.printf("[BM-4b]   N=%d: индекс заполнен, начинаем замер second_pass%n",
                    fileCount);

            // ── Второй проход ─────────────────────────────────────────────
            long[] secondPassTimes = new long[effectiveMeasureRuns];
            for (int r = 0; r < effectiveMeasureRuns; r++) {
                FileIndexService service = buildService();

                long t0 = System.currentTimeMillis();
                service.performFullIncrementalIndexing(dirConfig)
                        .get(secondPassSec, TimeUnit.SECONDS);
                secondPassTimes[r] = System.currentTimeMillis() - t0;

                String row = fileCount + ",second_pass," + (r + 1) + "," + secondPassTimes[r];
                csv.println(row);
                csv.flush();
                System.out.println(row);
            }

            // ── Статистика ────────────────────────────────────────────────
            double meanFirst  = mean(firstPassTimes);
            double meanSecond = mean(secondPassTimes);
            double speedup    = meanFirst / Math.max(meanSecond, 1);

            summary.append(String.format("%d,%.0f,%.0f,%.2f%n",
                    fileCount, meanFirst, meanSecond, speedup));

            deleteDir(imgDir);

            System.gc();
            Thread.sleep(800);
        }

        csv.close();
        System.out.println(summary);
        System.out.println("[BM-4b] CSV сохранён: " + CSV_OUTPUT);
    }

// ── Статистические утилиты ────────────────────────────────────────────

    private static double mean(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return (double) sum / values.length;
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}