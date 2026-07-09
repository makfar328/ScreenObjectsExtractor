package org.makar.ocrapp.screenobjectsextractor.model.core.indexer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteria;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchCriteriaBuilder;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для FileScanner.
 * Проверяем базовую фильтрацию по расширению и глубине сканирования.
 */
class FileScannerTest {

    private FileScanner scanner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        scanner = new FileScanner();
    }

    @Test
    @DisplayName("scanDirectory: пустая директория — пустой список")
    void scanDirectory_emptyDir_returnsEmptyList() throws IOException {
        List<Path> result = scanner.scanDirectory(tempDir, Integer.MAX_VALUE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanDirectory: возвращает только файлы нужного расширения")
    void scanDirectory_mixedFiles_onlyMatchingExtensionsReturned() throws IOException {
        Files.createFile(tempDir.resolve("a.png"));
        Files.createFile(tempDir.resolve("b.jpg"));
        Files.createFile(tempDir.resolve("c.txt"));
        Files.createFile(tempDir.resolve("d.csv"));

        List<Path> result = scanner.scanDirectory(tempDir, Integer.MAX_VALUE);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> {
            String name = p.getFileName().toString();
            return name.endsWith(".png") || name.endsWith(".jpg");
        }));
    }

    @Test
    @DisplayName("scanDirectory: регистр расширения не имеет значения")
    void scanDirectory_uppercaseExtension_fileIsFound() throws IOException {
        Files.createFile(tempDir.resolve("photo.PNG"));

        List<Path> result = scanner.scanDirectory(tempDir, Integer.MAX_VALUE);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("scanDirectory: вложенные директории не сканируются при depth=1")
    void scanDirectory_depth1_subdirFilesNotIncluded() throws IOException {
        Path sub = Files.createDirectory(tempDir.resolve("sub"));
        Files.createFile(sub.resolve("nested.png"));
        Files.createFile(tempDir.resolve("top.png"));

        SearchCriteria criteria = new SearchCriteriaBuilder()
                .withFileTypes(List.of("png"))
                .withTargetDirectories(List.of(new SearchDirectoryConfig(tempDir, 1)))
                .build();

        List<Path> result = scanner.scanDirectory(
                tempDir,
                criteria.getTargetDirectories().get(0).getSearchDepth()   // = 1
        );

        assertEquals(1, result.size());
        assertEquals("top.png", result.get(0).getFileName().toString());
    }

    @Test
    @DisplayName("scanDirectory: директория не существует — не бросает исключение")
    void scanDirectory_nonExistentDir_returnsEmptyListOrThrowsGracefully() {
        Path nonExistent = tempDir.resolve("ghost");

        assertDoesNotThrow(() -> {
            List<Path> result = scanner.scanDirectory(nonExistent, Integer.MAX_VALUE);
            assertNotNull(result);
        });
    }
}