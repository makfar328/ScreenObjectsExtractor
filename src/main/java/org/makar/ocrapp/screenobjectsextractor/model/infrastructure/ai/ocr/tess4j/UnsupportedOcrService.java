package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j;

import net.sourceforge.tess4j.TesseractException;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.IOcrService;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/**
 * Заглушка OCR-сервиса для платформ, на которых Tesseract не поддерживается
 * в текущей версии приложения. Возвращает пустой результат без исключений,
 * чтобы остальные функции приложения оставались работоспособными.
 */
public class UnsupportedOcrService implements IOcrService {

    private static final Logger LOGGER = Logger.getLogger(UnsupportedOcrService.class.getName());

    private final String platformMessage;

    public UnsupportedOcrService(String platformMessage) {
        this.platformMessage = platformMessage;
        LOGGER.warning("[OCR] Распознавание текста недоступно: " + platformMessage);
    }

    /**
     * Всегда возвращает пустой {@link TextObjects} независимо от переданного языка.
     * Параметр {@code language} принимается, но игнорируется — движок недоступен.
     */
    @Override
    public TextObjects recognizeText(BufferedImage image, String language) {
        LOGGER.info(String.format(
                "[OCR] Вызов recognizeText(language='%s') проигнорирован: %s",
                language, platformMessage));
        return new TextObjects(); // не null — защита от NullPointerException выше по стеку
    }
}