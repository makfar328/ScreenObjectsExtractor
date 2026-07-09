package org.makar.ocrapp.screenobjectsextractor;

/**
 * Использую: MainApplication, тесты.
 */
public final class AppConfig {

    // ── YOLO ──────────────────────────────────────────────────────────────
    public static final String YOLO_MODEL_PATH = "/models/yolo11x.onnx";
    public static final String YOLO_OUTPUT_NAME = "output0";

    // ── OCR ───────────────────────────────────────────────────────────────
    public static final String TESSERACT_DATA_PATH = "C:\\Users\\Makar\\IdeaProjects\\java-project\\ScreenObjectsExtractor\\src\\main\\resources\\tessTraineddata";

    // ── DB ─────────────────────────────────────────────────────────────────
    public static final String DB_FILE_PATH = "jdbc:sqlite:file_index.db";

    private AppConfig() {} // утилитный класс, не инстанциируется
}
