package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr;

import net.sourceforge.tess4j.TesseractException;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

import java.awt.image.BufferedImage;
import java.util.List;

public interface IOcrService {

    /**
     * Распознаёт текст с явным указанием языка.
     */
    TextObjects recognizeText(BufferedImage image, String language) throws TesseractException;

    /**
     * Распознаёт текст с языком по умолчанию («eng»).
     * Делегирует в {@link #recognizeText(BufferedImage, String)}.
     */
    default TextObjects recognizeText(BufferedImage image) throws TesseractException {
        return recognizeText(image, "eng");
    }
}