package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v1;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link TesseractOcrService_v1#recognizeText}.
 *
 * <p>Предусловие: в {@link TesseractOcrService_v1} добавлен инъекционный конструктор
 * {@code TesseractOcrService_v1(Tesseract tesseract)}.
 * Реальный {@code Tesseract} и файловая система не задействуются —
 * все зависимости заменяются Mockito-заглушками.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TesseractOcrService_v1")
class TesseractOcrService_v1Test {

    // ── Инфраструктура ────────────────────────────────────────────────────────

    @Mock
    private Tesseract tesseract;

    private TesseractOcrService_v1 ocrService;

    /** Синтетическое изображение: реальный Tesseract не вызывается, поэтому содержимое не важно. */
    private BufferedImage dummyImage;

    @BeforeEach
    void setUp() {
        Path fakePath = Path.of("/fake/tessdata");

        ocrService  = new TesseractOcrService_v1(tesseract, fakePath); /* Mock.
        Интеграционный тест требует решения проблему с пробрасываемой IOException */
        dummyImage  = new BufferedImage(200, 50, BufferedImage.TYPE_INT_RGB);
    }

    // ── Фабричный метод ───────────────────────────────────────────────────────

    /**
     * Создаёт реальный {@link Word} с заданными параметрами.
     * Предпочтительнее мокирования: проверяется реальный контракт маппинга.
     */
    private Word word(String text, int x, int y, int width, int height) {
        return new Word(text, 99.0f, new Rectangle(x, y, width, height));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Тест 1: Основной happy-path — корректность маппинга полей
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Одно слово → TextObject содержит корректный текст и координаты bounding box")
    void singleWord_allFieldsMappedCorrectly() throws TesseractException {
        when(tesseract.getWords(any(BufferedImage.class), eq(0)))
                .thenReturn(List.of(word("OpenCV", 10, 20, 80, 30)));

        TextObjects result = ocrService.recognizeText(dummyImage);

        assertEquals(1, result.getTextObjectList().size());

        TextObject obj = result.getTextObjectList().get(0);
        assertAll("Все поля TextObject должны точно соответствовать Word",
                () -> assertEquals("OpenCV", obj.getText()),
                () -> assertEquals(10,       obj.getX()),
                () -> assertEquals(20,       obj.getY()),
                () -> assertEquals(80,       obj.getWidth()),
                () -> assertEquals(30,       obj.getHeight())
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // Тест 2: Пустой список слов
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Пустой список слов от Tesseract → возвращается непустой TextObjects без элементов")
    void emptyWordList_returnsEmptyTextObjects() throws TesseractException {
        when(tesseract.getWords(any(BufferedImage.class), eq(0)))
                .thenReturn(Collections.emptyList());

        TextObjects result = ocrService.recognizeText(dummyImage);

        assertNotNull(result,
                "Метод не должен возвращать null при пустом списке слов");
        assertTrue(result.getTextObjectList().isEmpty(),
                "TextObjects не должен содержать записей");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Тест 3: Несколько слов — порядок и количество
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Несколько слов → все слова отображены в TextObjects в исходном порядке")
    void multipleWords_countAndOrderPreserved() throws TesseractException {
        List<Word> words = List.of(
                word("Hello",  0,  0, 50, 20),
                word("World", 60,  0, 50, 20),
                word("Java",   0, 30, 40, 20)
        );
        when(tesseract.getWords(any(BufferedImage.class), eq(0))).thenReturn(words);

        TextObjects result = ocrService.recognizeText(dummyImage);

        List<TextObject> objects = result.getTextObjectList();
        assertAll(
                () -> assertEquals(3,       objects.size(),           "Количество объектов должно совпадать"),
                () -> assertEquals("Hello", objects.get(0).getText(), "Первый объект"),
                () -> assertEquals("World", objects.get(1).getText(), "Второй объект"),
                () -> assertEquals("Java",  objects.get(2).getText(), "Третий объект")
        );
    }

    // Тест 4: Пробрасывание TesseractException

/*    @Test
    @DisplayName("TesseractException от getWords → выбрасывается из recognizeText без перехвата")
    void tesseractException_propagatesUnwrapped() throws TesseractException {
        when(tesseract.getWords(any(BufferedImage.class), eq(0)))
                .thenThrow(new TesseractException("OCR engine failed"));

        assertThrows(TesseractException.class,
                () -> ocrService.recognizeText(dummyImage),
                "Исключение не должно быть проглочено или обёрнуто в сервисе");
    }*/
}