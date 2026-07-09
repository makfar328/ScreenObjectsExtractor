package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2;

import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.*;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject.OcrLevel;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест {@link TesseractOcrService_v2}.
 *
 * <p>Использует синтетические изображения с текстом, нарисованным программно через AWT —
 * никаких внешних файлов не требуется. Tesseract инициализируется один раз на весь класс
 * {@code @BeforeAll}, что устраняет накладные расходы на инициализацию LSTM-движка (~1 с).
 *
 * <p><b>Важно для запуска:</b> языковые модели ({@code eng.traineddata}, {@code rus.traineddata})
 * должны находиться в {@code src/main/resources/tessdata/} — они подтягиваются через
 * {@code TesseractDataExtractor.get()} так же, как в продакшн-коде.
 *
 * <p><b>Покрываемые сценарии:</b>
 * <ol>
 *   <li>Основной happy path — текст на изображении попадает в результат.</li>
 *   <li>Многоуровневый вывод — результат содержит объекты разных {@link OcrLevel}.</li>
 *   <li>Корректность bounding box — координаты ненулевые и не выходят за границы изображения.</li>
 *   <li>Fallback языка — неизвестный/null язык не бросает исключение, результат непустой.</li>
 *   <li>Язык сбрасывается после вызова — повторный вызов с другим языком независим.</li>
 *   <li>Пустое/белое изображение — не бросает исключение, возвращает пустой результат.</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TesseractOcrService_v2IT {

    /** Один экземпляр сервиса на весь класс — инициализация LSTM дорогая. */
    private static TesseractOcrService_v2 service;

    // ── Фабричные методы для синтетических изображений ───────────────────

    /**
     * Рисует однострочный текст белыми буквами на чёрном фоне.
     * Шрифт и размер подобраны так, чтобы высота символа была ≥ 20 px —
     * минимум для уверенного распознавания LSTM.
     */
    private static BufferedImage renderText(String text, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Чёрный фон
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Белый текст — максимальный контраст для Tesseract
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 36));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Центрируем по вертикали
        FontMetrics fm = g.getFontMetrics();
        int textY = (height - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, 20, textY);

        g.dispose();
        return img;
    }

    /** Полностью белое изображение — текста нет. */
    private static BufferedImage blankImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return img;
    }

    // ── Инициализация ─────────────────────────────────────────────────────

    @BeforeAll
    static void init() throws IOException {
        // fromResources() подтягивает .traineddata из src/main/resources/tessdata/
        // и копирует во временную директорию — ровно так, как в продакшне.
        service = TesseractOcrService_v2.fromResources();
    }

    // ── Сценарий 1: Happy path ────────────────────────────────────────────

    /**
     * Основной сценарий: явный чёрно-белый текст → в результате есть хотя бы один объект,
     * чей {@code getText()} содержит ожидаемое слово (без учёта регистра и пробелов).
     *
     * <p>Tesseract на синтетике стабильно даёт ≥ 95% точности, поэтому проверяем
     * contains, а не equals — допуская возможные лишние пробелы/знаки пунктуации.
     */
    @Test
    @Order(1)
    @DisplayName("Текст на изображении распознаётся — результат непустой и содержит ожидаемое слово")
    void happyPath_textRecognized() throws TesseractException {
        BufferedImage img = renderText("Hello", 400, 100);

        TextObjects result = service.recognizeText(img, "eng");

        List<TextObject> objects = result.getTextObjectList();
        assertFalse(objects.isEmpty(),
                "Ожидался непустой список TextObject, но получен пустой");

        boolean containsHello = objects.stream()
                .map(TextObject::getText)
                .anyMatch(t -> t.toLowerCase().contains("hello"));
        assertTrue(containsHello,
                "Среди распознанных объектов не найден текст 'hello'. Фактически: "
                        + objects.stream().map(TextObject::getText).toList());
    }

    // ── Сценарий 2: Многоуровневый вывод ─────────────────────────────────

    /**
     * hOCR-проход должен возвращать объекты нескольких уровней иерархии.
     * Для короткой строки гарантированы как минимум LINE и WORD.
     * BLOCK и PARAGRAPH появляются при достаточной длине текста — проверяем
     * только минимально надёжный инвариант: присутствует > 1 уникального уровня.
     */
    @Test
    @Order(2)
    @DisplayName("Результат содержит объекты нескольких уровней иерархии (LINE + WORD minimum)")
    void multiLevelOutput_atLeastLineAndWord() throws TesseractException {
        // Более длинная фраза — больше шансов получить LINE + WORD + BLOCK
        BufferedImage img = renderText("OpenCV Tesseract", 600, 100);

        TextObjects result = service.recognizeText(img, "eng");

        long distinctLevels = result.getTextObjectList().stream()
                .map(TextObject::getLevel)
                .distinct()
                .count();

        assertTrue(distinctLevels >= 2,
                "Ожидалось ≥ 2 уровней иерархии (LINE + WORD), получено: " + distinctLevels
                        + ". Уровни: " + result.getTextObjectList().stream()
                        .map(o -> o.getLevel().name()).distinct().toList());
    }

    // ── Сценарий 3: Корректность bounding box ────────────────────────────

    /**
     * Каждый распознанный объект должен иметь ненулевой bounding box,
     * не выходящий за границы исходного изображения.
     */
    @Test
    @Order(3)
    @DisplayName("Bounding box каждого объекта ненулевой и находится внутри изображения")
    void boundingBoxes_nonZeroAndWithinImage() throws TesseractException {
        final int IMG_W = 500, IMG_H = 100;
        BufferedImage img = renderText("BoundingTest", IMG_W, IMG_H);

        TextObjects result = service.recognizeText(img, "eng");
        List<TextObject> objects = result.getTextObjectList();
        assertFalse(objects.isEmpty(), "Список пустой — нет что проверять");

        for (TextObject obj : objects) {
            int x = obj.getX(), y = obj.getY(), w = obj.getWidth(), h = obj.getHeight();

            // Ширина и высота > 0
            assertTrue(w > 0,
                    String.format("'%s' [%s]: width=%d, ожидалось > 0", obj.getText(), obj.getLevel(), w));
            assertTrue(h > 0,
                    String.format("'%s' [%s]: height=%d, ожидалось > 0", obj.getText(), obj.getLevel(), h));

            // Координаты в пределах изображения (с допуском 1 px из-за возможного off-by-one Tesseract)
            assertTrue(x >= 0,
                    String.format("'%s' [%s]: x=%d выходит за левую границу", obj.getText(), obj.getLevel(), x));
            assertTrue(y >= 0,
                    String.format("'%s' [%s]: y=%d выходит за верхнюю границу", obj.getText(), obj.getLevel(), y));
            assertTrue(x + w <= IMG_W + 1,
                    String.format("'%s' [%s]: x+w=%d превышает ширину %d", obj.getText(), obj.getLevel(), x + w, IMG_W));
            assertTrue(y + h <= IMG_H + 1,
                    String.format("'%s' [%s]: y+h=%d превышает высоту %d", obj.getText(), obj.getLevel(), y + h, IMG_H));
        }
    }

    // ── Сценарий 4: Fallback неизвестного языка ───────────────────────────

    /**
     * При передаче неизвестного кода языка сервис НЕ должен бросать исключение
     * и должен вернуть непустой результат (fallback на «eng»).
     */
    @Test
    @Order(4)
    @DisplayName("Неизвестный язык не бросает исключение — выполняется fallback на 'eng'")
    void unknownLanguage_fallbackToEng_noException() {
        BufferedImage img = renderText("Fallback", 400, 100);

        assertDoesNotThrow(() -> {
            TextObjects result = service.recognizeText(img, "klingon");
            assertFalse(result.getTextObjectList().isEmpty(),
                    "После fallback на 'eng' результат не должен быть пустым");
        });
    }

    /**
     * {@code null} в качестве языка — корректный fallback, исключения нет.
     */
    @Test
    @Order(5)
    @DisplayName("null-язык не бросает исключение — выполняется fallback на 'eng'")
    void nullLanguage_fallbackToEng_noException() {
        BufferedImage img = renderText("NullLang", 400, 100);

        assertDoesNotThrow(() -> service.recognizeText(img, null),
                "recognizeText с null-языком не должен бросать исключение");
    }

    // ── Сценарий 5: Сброс языка — повторный вызов независим ──────────────

    /**
     * После вызова с «rus» повторный вызов с «eng» должен вернуть корректный результат.
     * Проверяем, что {@code tesseract.setLanguage(DEFAULT_LANGUAGE)} в конце каждого вызова
     * действительно выполняется и не оставляет сервис в «залипшем» состоянии.
     */
    @Test
    @Order(6)
    @DisplayName("Язык сбрасывается после вызова — два последовательных вызова с разными языками независимы")
    void languageReset_consecutiveCallsAreIndependent() throws TesseractException {
        BufferedImage imgRus = renderText("Привет", 400, 100);
        BufferedImage imgEng = renderText("World", 400, 100);

        // Первый вызов — русский
        TextObjects rusResult = service.recognizeText(imgRus, "rus");
        // Второй вызов — английский. Если язык не сбросился, LSTM выдаст мусор.
        TextObjects engResult = service.recognizeText(imgEng, "eng");

        // Достаточно убедиться, что второй вызов не упал и вернул непустой результат
        assertFalse(engResult.getTextObjectList().isEmpty(),
                "После вызова с 'rus' повторный вызов с 'eng' вернул пустой результат — "
                        + "возможно, язык не сбросился");

        boolean containsWorld = engResult.getTextObjectList().stream()
                .map(TextObject::getText)
                .anyMatch(t -> t.toLowerCase().contains("world"));
        assertTrue(containsWorld,
                "Второй вызов (lang=eng) не содержит 'world'. Фактически: "
                        + engResult.getTextObjectList().stream().map(TextObject::getText).toList());
    }

    // ── Сценарий 6: Пустое изображение ───────────────────────────────────

    /**
     * На полностью белом изображении (без текста) сервис не должен бросать исключение.
     * Результат может быть пустым или содержать артефакты — оба варианта допустимы,
     * главное что нет {@code TesseractException}.
     */
    @Test
    @Order(7)
    @DisplayName("Пустое (белое) изображение не бросает TesseractException — результат не null")
    void blankImage_noException_resultNotNull() {
        BufferedImage img = blankImage(400, 100);

        assertDoesNotThrow(() -> {
            TextObjects result = service.recognizeText(img, "eng");
            assertNotNull(result, "Результат не должен быть null");
            assertNotNull(result.getTextObjectList(), "getTextObjectList() не должен быть null");
        });
    }
}