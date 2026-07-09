package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.TesseractDataExtractor;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v1.TesseractOcrService_v1;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Реализация {@link IOcrService} на базе Tesseract с LSTM-движком и hOCR-выводом.
 *
 * <p>Ключевые улучшения по сравнению с {@link TesseractOcrService_v1}:
 * <ul>
 *   <li>LSTM engine (OEM 1) — лучше на современных шрифтах.</li>
 *   <li>Предобработка в grayscale — устраняет цветовой шум.</li>
 *   <li>Многоуровневый вывод: BLOCK → PARAGRAPH → LINE → WORD → SYMBOL
 *       через один hOCR-вызов; парсинг делегирован {@link HocrParser}.</li>
 * </ul>
 */
public class TesseractOcrService_v2 implements IOcrService {

    private static final Logger logger = Logger.getLogger(TesseractOcrService_v2.class.getName());

    private static final int    OEM_LSTM_ONLY  = 1;
    private static final int    PSM_AUTO       = 3;
    private static final String DEFAULT_LANGUAGE = "eng";
    private static final List<String> AVAILABLE_LANGUAGES = List.of("eng", "rus");

    private final Tesseract tesseract;
    private final HocrParser hocrParser = new HocrParser();

    // ── Конструкторы ──────────────────────────────────────────────────────

    public TesseractOcrService_v2(Tesseract tesseract, Path tessPath) {
        this.tesseract = tesseract;
        tesseract.setDatapath(tessPath.toString());
        tesseract.setLanguage(DEFAULT_LANGUAGE);
        tesseract.setTessVariable("user_defined_dpi", "300");
        tesseract.setPageSegMode(PSM_AUTO);
        tesseract.setOcrEngineMode(OEM_LSTM_ONLY);
        tesseract.setHocr(true);
    }

    public static TesseractOcrService_v2 fromResources() throws IOException {
        return new TesseractOcrService_v2(new Tesseract(), TesseractDataExtractor.get());
    }

    // ── IOcrService ───────────────────────────────────────────────────────

    @Override
    public TextObjects recognizeText(BufferedImage image, String language) throws TesseractException {
        String resolved = resolveLanguage(language);
        tesseract.setLanguage(resolved);
        logger.log(Level.INFO, "Starting recognition: requested=''{0}'', resolved=''{1}''",
                new Object[]{language, resolved});

        String hocrXml = tesseract.doOCR(preprocess(image));
        TextObjects result = hocrParser.parse(hocrXml);

        tesseract.setLanguage(DEFAULT_LANGUAGE);
        logger.log(Level.INFO, "Recognition complete [lang=''{0}'']: total objects={1}",
                new Object[]{resolved, result.getTextObjectList().size()});
        return result;
    }

    // ── Preprocessing ─────────────────────────────────────────────────────

    /**
     * Переводит изображение в grayscale.
     * Masштабирование целесообразно добавить в будущих версиях
     * при работе с мелким текстом (высота строки < 20 px).
     */
    private static BufferedImage preprocess(BufferedImage src) {
        BufferedImage gray = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    // ── Language ─────────────────────────────────────────────────────────

    private static String resolveLanguage(String language) {
        if (language != null && AVAILABLE_LANGUAGES.contains(language)) return language;
        logger.log(Level.WARNING,
                "Unsupported or null language ''{0}''. Available: {1}. Falling back to ''{2}''.",
                new Object[]{language, AVAILABLE_LANGUAGES, DEFAULT_LANGUAGE});
        return DEFAULT_LANGUAGE;
    }
}