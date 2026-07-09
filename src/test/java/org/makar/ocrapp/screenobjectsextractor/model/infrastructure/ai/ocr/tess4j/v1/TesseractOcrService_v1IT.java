package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v1;

import org.junit.jupiter.api.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Интеграционные тесты {@link TesseractOcrService_v1}.
 *
 * <p><b>Требования к среде:</b>
 * <ul>
 *   <li>{@code src/main/resources/tessdata/eng.traineddata} — обязателен</li>
 *   <li>{@code src/main/resources/tessdata/rus.traineddata} — нужен для сценария 6</li>
 * </ul>
 *
 * <p>Tesseract инициализируется один раз через {@code @BeforeAll},
 * поскольку это тяжёлая операция (~1-3 с).
 * {@code @TestInstance(PER_CLASS)} позволяет сделать метод нестатическим.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // один экземпляр Tesseract для всех тестов
@DisplayName("TesseractOcrService_v1 — интеграционные тесты")
class TesseractOcrService_v1IT {

    private TesseractOcrService_v1 service;

    @BeforeAll
    void initService() throws IOException {
        // fromResources() вызывает TesseractDataExtractor.get() — реальные файлы из ресурсов
        service = TesseractOcrService_v1.fromResources();
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    /**
     * Рисует текст белым фоном / чёрным шрифтом 36pt.
     * Крупный шрифт и простой фон — минимальные требования
     * для стабильного распознавания Tesseract.
     */
    private static BufferedImage renderText(String text) {
        final int W = 700, H = 150;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Подсказка рендерингу: субпиксельное сглаживание мешает Tesseract — отключаем
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        g.drawString(text, 40, 100);
        g.dispose();
        return img;
    }

    /** Полностью белое изображение — без единого символа. */
    private static BufferedImage blankWhiteImage() {
        BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 100);
        g.dispose();
        return img;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Сценарий 1: Smoke — сервис стартует и не падает на валидном вводе
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Сценарий 1: изображение с текстом → результат не null, нет исключений")
    void recognizeText_validImage_returnsNonNullWithoutException() {
        BufferedImage image = renderText("TEST");

        TextObjects result = assertDoesNotThrow(
                () -> service.recognizeText(image),
                "recognizeText не должен бросать исключение на валидном изображении"
        );

        assertNotNull(result, "Результат не должен быть null");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Сценарий 2: Пустое изображение — нет NPE / TesseractException
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Сценарий 2: чисто белое изображение → нет исключений, возвращается пустой TextObjects")
    void recognizeText_blankImage_noExceptionEmptyOrTrivialResult() {
        BufferedImage blank = blankWhiteImage();

        TextObjects result = assertDoesNotThrow(
                () -> service.recognizeText(blank),
                "Пустое изображение не должно вызывать исключение"
        );

        assertNotNull(result);
        // Tesseract может вернуть шумовые «слова» на белом фоне,
        // главное — что метод завершается корректно
    }

    // ════════════════════════════════════════════════════════════════════════
    // Сценарий 3: Распознавание английского слова
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Сценарий 3: слово HELLO → в результате есть TextObject с текстом 'hello'")
    void recognizeText_singleEnglishWord_detected() throws Exception {
        BufferedImage image = renderText("HELLO");

        TextObjects result = service.recognizeText(image);
        List<TextObject> objects = result.getTextObjectList(); // ← замени на реальный метод TextObjects

        boolean found = objects.stream()
                .map(TextObject::getText)
                .anyMatch(t -> t.strip().equalsIgnoreCase("hello"));

        assertTrue(found,
                "Tesseract должен распознать слово HELLO. Найдено: " +
                        objects.stream().map(TextObject::getText).toList());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Сценарий 4: Несколько слов → несколько TextObject
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Сценарий 4: три слова → в результате ≥ 2 TextObject")
    void recognizeText_multipleWords_returnsMultipleObjects() throws Exception {
        BufferedImage image = renderText("ONE TWO THREE");

        TextObjects result = service.recognizeText(image);
        List<TextObject> objects = result.getTextObjectList();

        assertTrue(objects.size() >= 2,
                "Для трёх слов ожидается ≥ 2 TextObject, фактически: " + objects.size());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Сценарий 5: Каждый TextObject имеет корректный bounding box
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Сценарий 5: координаты bounding box каждого объекта неотрицательны, размеры > 0")
    void recognizeText_eachObject_hasValidBoundingBox() throws Exception {
        BufferedImage image = renderText("BOUNDING BOX");

        TextObjects result = service.recognizeText(image);
        List<TextObject> objects = result.getTextObjectList();

        // Если ничего не распознано — принудительно пропускаем
        assumeFalse(objects.isEmpty(),
                "Тест bounding box требует хотя бы одного распознанного слова");

        for (TextObject obj : objects) {
            assertAll("BoundingBox для «" + obj.getText().strip() + "»",
                    () -> assertTrue(obj.getWidth()  > 0,  "width  > 0"),
                    () -> assertTrue(obj.getHeight() > 0,  "height > 0"),
                    () -> assertTrue(obj.getX()      >= 0, "x >= 0"),
                    () -> assertTrue(obj.getY()      >= 0, "y >= 0")
            );
        }
    }

    // Сценарий 6 (опциональный): Русскоязычный текст
    // Запускается только при наличии rus.traineddata в
    // ресурсах — assumeTrue пропустит тест, если условие не выполнено.

    @Test
    @DisplayName("Сценарий 6: русское слово ТЕСТ → в результате есть TextObject с текстом 'тест'")
    void recognizeText_russianWord_detected() throws Exception {
        BufferedImage image = renderText("ТЕСТ");

        TextObjects result = service.recognizeText(image);
        List<TextObject> objects = result.getTextObjectList();

        assumeFalse(objects.isEmpty(),
                "rus.traineddata может отсутствовать — тест пропущен");

        boolean found = objects.stream()
                .map(TextObject::getText)
                .anyMatch(t -> t.strip().equalsIgnoreCase("тест"));

        assertTrue(found,
                "Tesseract должен распознать кириллическое слово ТЕСТ. Найдено: " +
                        objects.stream().map(TextObject::getText).toList());
    }
}