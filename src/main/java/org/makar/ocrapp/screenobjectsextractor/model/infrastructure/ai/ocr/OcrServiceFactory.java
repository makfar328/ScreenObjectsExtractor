package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2.TesseractOcrService_v2;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.UnsupportedOcrService;

import java.io.IOException;
import java.util.logging.Logger;

public final class OcrServiceFactory {

    private static final Logger LOGGER = Logger.getLogger(OcrServiceFactory.class.getName());

    private OcrServiceFactory() {}

    public static IOcrService create() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        LOGGER.info("OcrServiceFactory: определена платформа — " + System.getProperty("os.name"));

        if (osName.contains("windows")) {
            try {
                IOcrService service = TesseractOcrService_v2.fromResources();
                LOGGER.info("OcrServiceFactory: TesseractOcrService_v1 успешно инициализирован.");
                return service;
            } catch (IOException e) {
                LOGGER.severe("OcrServiceFactory: ошибка инициализации Tesseract — " + e.getMessage());
                return new UnsupportedOcrService(
                        "Не удалось извлечь языковые модели Tesseract: " + e.getMessage()
                );
            }
        }

        // Linux, macOS и прочие — функциональность запланирована в следующих версиях
        String stub = String.format(
                "OCR-компонент на базе Tess4J/Tesseract не поддерживается на платформе '%s' " +
                        "в данной версии приложения. Поддержка Linux и macOS запланирована " +
                        "в следующих версиях. Остальные функции приложения доступны в полном объёме.",
                System.getProperty("os.name")
        );
        return new UnsupportedOcrService(stub);
    }
}