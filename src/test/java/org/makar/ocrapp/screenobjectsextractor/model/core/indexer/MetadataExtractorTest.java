package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;

import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для MetadataExtractor.
 * Зависимостей нет — тестируем чистую логику извлечения метаданных из Path.
 */
class MetadataExtractorTest {

    private MetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new MetadataExtractor();
    }

    // ── имя файла ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("extract: fileName сохраняется без расширения")
    void extract_regularFile_fileNameWithoutExtension() throws IOException {
        Path file = Files.createFile(tempDir.resolve("screenshot.png"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals("screenshot", metadata.getFileName());
    }

    @Test
    @DisplayName("extract: fileName для файла без расширения возвращает полное имя")
    void extract_fileWithoutExtension_fullNameReturned() throws IOException {
        Path file = Files.createFile(tempDir.resolve("Makefile"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals("Makefile", metadata.getFileName());
    }

    @Test
    @DisplayName("extract: fileName для файла с несколькими точками — только последнее расширение отсекается")
    void extract_multiDotFileName_onlyLastExtensionStripped() throws IOException {
        Path file = Files.createFile(tempDir.resolve("archive.tar.gz"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals("archive.tar", metadata.getFileName());
    }

    // ── расширение ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("extract: расширение возвращается в нижнем регистре")
    void extract_uppercaseExtension_extensionLowercased() throws IOException {
        Path file = Files.createFile(tempDir.resolve("photo.PNG"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals("png", metadata.getFileExtension());
    }

    @Test
    @DisplayName("extract: расширение корректно для jpg-файла")
    void extract_jpgFile_extensionIsJpg() throws IOException {
        Path file = Files.createFile(tempDir.resolve("photo.jpg"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals("jpg", metadata.getFileExtension());
    }

    // ── путь и размер ──────────────────────────────────────────────────────

    @Test
    @DisplayName("extract: filePath совпадает с переданным путём")
    void extract_filePath_matchesInput() throws IOException {
        Path file = Files.createFile(tempDir.resolve("img.png"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals(file, metadata.getFilePath());
    }

    @Test
    @DisplayName("extract: fileSize соответствует реальному размеру файла")
    void extract_nonEmptyFile_fileSizeCorrect() throws IOException {
        Path file = tempDir.resolve("data.png");
        Files.write(file, new byte[256]);

        FileMetadata metadata = extractor.extract(file);

        assertEquals(256L, metadata.getFileSize());
    }

    @Test
    @DisplayName("extract: пустой файл имеет размер 0")
    void extract_emptyFile_fileSizeZero() throws IOException {
        Path file = Files.createFile(tempDir.resolve("createEmpty.png"));

        FileMetadata metadata = extractor.extract(file);

        assertEquals(0L, metadata.getFileSize());
    }

    // ── временны́е метки ───────────────────────────────────────────────────

    @Test
    @DisplayName("extract: creationDate и modificationDate не null")
    void extract_timestamps_notNull() throws IOException {
        Path file = Files.createFile(tempDir.resolve("ts.png"));

        FileMetadata metadata = extractor.extract(file);

        assertNotNull(metadata.getCreationDate(),      "creationDate не должен быть null");
        assertNotNull(metadata.getModificationDate(),  "modificationDate не должен быть null");
    }
}