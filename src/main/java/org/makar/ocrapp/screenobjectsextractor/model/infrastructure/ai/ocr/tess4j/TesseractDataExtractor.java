package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Извлекает языковые модели Tesseract из ресурсов JAR во временную директорию.
 * Singleton — директория создаётся один раз за время жизни JVM.
 */
public final class TesseractDataExtractor {

    private static final String[] LANGUAGES = {"eng", "rus"};
    private static volatile Path extractedDir;

    private TesseractDataExtractor() {}

    /**
     * Возвращает путь к директории с извлечёнными .traineddata-файлами.
     * При повторных вызовах возвращает тот же путь без повторного извлечения.
     *
     * @throws IOException если ресурс не найден или извлечение невозможно
     */
    public static synchronized Path get() throws IOException {
        if (extractedDir != null && Files.exists(extractedDir)) {
            return extractedDir;
        }

        Path tempDir = Files.createTempDirectory("tessdata_");
        registerShutdownCleanup(tempDir);

        for (String lang : LANGUAGES) {
            String resourcePath = "/tessdata/" + lang + ".traineddata";
            try (InputStream is = TesseractDataExtractor.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException(
                            "Языковая модель Tesseract не найдена в ресурсах: src/main/resources/tessdata/ : " + resourcePath
                    );
                }
                Files.copy(is, tempDir.resolve(lang + ".traineddata"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        extractedDir = tempDir;
        return extractedDir;
    }

    private static void registerShutdownCleanup(Path dir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {}
        }));
    }
}
