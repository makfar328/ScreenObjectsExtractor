package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class TesseractNativeExtractor {

    private static volatile Path extractedDir;

    public static synchronized Path get() throws IOException {
        if (extractedDir != null && Files.exists(extractedDir)) {
            return extractedDir;
        }

        String platformFolder = detectPlatformFolder(); // "win32-x86-64", "linux-x86-64", etc.
        String[] libs = nativeLibsForPlatform();        // {"tesseract50.dll", ...}

        Path tempDir = Files.createTempDirectory("tesseract_native_");
        registerShutdownCleanup(tempDir);

        for (String lib : libs) {
            String resourcePath = "/native/" + platformFolder + "/" + lib;
            try (InputStream is = TesseractNativeExtractor.class.getResourceAsStream(resourcePath)) {
                if (is == null) throw new IOException("Не найдена нативная библиотека: " + resourcePath);
                Files.copy(is, tempDir.resolve(lib), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // JNA найдёт извлечённые библиотеки по этому пути
        System.setProperty("jna.library.path", tempDir.toString());

        extractedDir = tempDir;
        return extractedDir;
    }

    private static String detectPlatformFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String archFolder = arch.contains("aarch64") || arch.contains("arm64")
                ? "aarch64" : "x86-64";
        if (os.contains("win"))   return "win32-x86-64";
        if (os.contains("linux")) return "linux-" + archFolder;
        if (os.contains("mac"))   return "darwin-" + archFolder;
        throw new UnsupportedOperationException("Неподдерживаемая платформа: " + os);
    }

    private static String[] nativeLibsForPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))   return new String[]{"tesseract50.dll", "leptonica-1.82.0.dll",
                "libgcc_s_seh-1.dll"};
        if (os.contains("linux")) return new String[]{"libtesseract.so.5", "libleptonica.so.6"};
        if (os.contains("mac"))   return new String[]{"libtesseract.5.dylib"};
        throw new UnsupportedOperationException("Неподдерживаемая платформа: " + os);
    }

    private static void registerShutdownCleanup(Path tempDir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {}
        }));
    }
}
