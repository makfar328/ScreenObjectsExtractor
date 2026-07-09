package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v1;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.TesseractDataExtractor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TesseractOcrService_v1 implements IOcrService {
    private static final Logger logger = Logger.getLogger(TesseractOcrService_v1.class.getName());

    /**
     * Уровни итерации Tesseract: 0 = RIL_BLOCK, 1 = RIL_PARA, 2 = RIL_TEXTLINE, 3 = RIL_WORD, 4 = RIL_SYMBOL
     */
    private static final int PAGE_ITERATOR_LEVEL_WORD = 2;
    private final Tesseract tesseract;
    /** Токены ниже этого значения считаются шумом и отфильтровываются. */
    private static final float MIN_CONFIDENCE = 30.0f;
    /** Язык по умолчанию — используется при отсутствии совпадения в реестре. */
    private static final String DEFAULT_LANGUAGE = "eng";
    /** Неизменяемый список тэгов доступных языковых словарей (в будущем планируется рассмотрение вынесения в Config) */
    private static final List<String> AVAILABLE_LANGUAGES = new ArrayList<>(List.of("eng", "rus"));

    public TesseractOcrService_v1(Tesseract tesseract, Path tessPath) {
        this.tesseract = tesseract;
        tesseract.setDatapath(tessPath.toString());
        tesseract.setLanguage("eng");
        // Явно задаём DPI: устраняет предупреждение «Invalid resolution 1 dpi»
        // и стабилизирует сегментацию текста на BufferedImage без метаданных.
        tesseract.setTessVariable("user_defined_dpi", "300");
        // PSM_AUTO_OSD (1) или PSM_AUTO (3) — позволяет Tesseract самостоятельно
        // определить ориентацию и тип разметки страницы.
        tesseract.setPageSegMode(3); // PSM_AUTO
    }

    // Редьютант
    public TesseractOcrService_v1() throws IOException {
        this(new Tesseract(), TesseractDataExtractor.get());
    }

    /* Заменяет перегрузку конструктора TesseractOcrService_v1().
    Это нужно для удовлетворения 'принципа наименьшего удивления', ведь I/O операции не являются
    зоной ответственности конструктора.
     */
    public static TesseractOcrService_v1 fromResources() throws IOException {
        return new TesseractOcrService_v1(new Tesseract(), TesseractDataExtractor.get());
    }

    /*public String recognizeText(BufferedImage image) throws TesseractException {

        return tesseract.doOCR(image);
    }*/


    /**
     * Распознаёт текст на изображении и возвращает коллекцию отдельных слов
     * с их ограничивающими прямоугольниками.
     *
     * @param image входное изображение
     * @return {@link TextObjects} — список распознанных слов
     * @throws TesseractException при ошибке движка Tesseract
     */
    @Override
    public TextObjects recognizeText(BufferedImage image, String language) throws TesseractException {

        // язык
        String resolvedLanguage;
        if (language != null && AVAILABLE_LANGUAGES.contains(language)) {
            resolvedLanguage = language;
        } else {
            logger.log(Level.WARNING, String.format(
                    "Unsupported or null language '%s'. Available: %s. Falling back to '%s'.",
                    language, AVAILABLE_LANGUAGES, DEFAULT_LANGUAGE));
            resolvedLanguage = DEFAULT_LANGUAGE;
        }

        tesseract.setLanguage(resolvedLanguage);

        logger.log(Level.INFO, String.format(
                "Starting recognition: requested='%s', resolved='%s'", language, resolvedLanguage));

        // ── Извлечение слов ───────────────────────────────────────────────────
        TextObjects textObjects = new TextObjects();
        List<Word> words = tesseract.getWords(image, PAGE_ITERATOR_LEVEL_WORD);

        int accepted = 0;
        int skippedBlank = 0;
        int skippedLowConf = 0;

        for (Word word : words) {
            String text = word.getText();

            // Фильтр 1: пустые и пробельные токены — неизбежный шум Tesseract
            if (text == null || text.isBlank()) {
                skippedBlank++;
                logger.log(Level.FINE, "Skipping blank token");
                continue;
            }

            // Фильтр 2: низкоуверенные токены (галлюцинации на фоне)
            if (word.getConfidence() < MIN_CONFIDENCE) {
                skippedLowConf++;
                logger.log(Level.FINE, String.format(
                        "Skipping low-confidence token '%s' (%.1f%% < %.1f%%)",
                        text, word.getConfidence(), MIN_CONFIDENCE));
                continue;
            }

            Rectangle boundingBox = word.getBoundingBox();
            textObjects.addTextObject(new TextObject(
                    text,
                    boundingBox.x,
                    boundingBox.y,
                    boundingBox.width,
                    boundingBox.height
            ));

            logger.log(Level.FINE, String.format(
                    "Word #%d: '%s' conf=%.1f%%", ++accepted, text, word.getConfidence()));
        }

        // ── Итоговый отчёт ────────────────────────────────────────────────────
        logger.log(Level.INFO, String.format(
                "Recognition complete [lang='%s']: accepted=%d, skipped_blank=%d, skipped_low_conf=%d",
                resolvedLanguage, accepted, skippedBlank, skippedLowConf));

        tesseract.setLanguage("eng");
        return textObjects;
    }

    public TextObjects recognizeText(BufferedImage image) throws TesseractException {
        return this.recognizeText(image, this.DEFAULT_LANGUAGE);
    }

}
