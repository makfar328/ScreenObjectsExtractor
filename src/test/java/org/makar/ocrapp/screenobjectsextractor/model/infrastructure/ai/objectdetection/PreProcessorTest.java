package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для {@link PreProcessor#preprocessImage}.
 *
 * <p>Все тесты детерминированы и не требуют файлов — только ONNX Runtime JNI,
 * который уже присутствует в тестовом classpath через {@code onnxruntime}.
 *
 * <p>Один {@link OrtEnvironment} создаётся на весь класс (singleton-pattern
 * самой библиотеки) и закрывается после всех тестов.
 */
@DisplayName("PreProcessor")
class PreProcessorTest {

    private static OrtEnvironment ENV;

    /** Стандартная форма входа YOLO: [1, 3, 640, 640] */
    private static final long[] SHAPE_STANDARD = {1L, 3L, 640L, 640L};
    /** Минимально возможный вход: [1, 3, 1, 1] */
    private static final long[] SHAPE_MINIMAL  = {1L, 3L, 1L, 1L};

    @BeforeAll
    static void initEnv() {
        // OrtEnvironment.getEnvironment() — потокобезопасный singleton;
        // не требует файлов, только JNI
        ENV = OrtEnvironment.getEnvironment();
    }

    @AfterAll
    static void closeEnv() throws Exception {
        if (ENV != null) ENV.close();
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    /**
     * Создаёт однотонное изображение заданного цвета.
     */
    private static BufferedImage solidImage(int width, int height, int argb) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                img.setRGB(x, y, argb);
        return img;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. Валидация входной формы
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Валидация inputShape")
    class InputShapeValidation {

        @Test
        @DisplayName("Корректная форма [1,3,640,640] -> не выбрасывает исключений")
        void validShape_noException() {
            BufferedImage img = solidImage(100, 100, 0xFFFF0000);
            assertDoesNotThrow(() -> {
                try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                    assertNotNull(t);
                }
            });
        }

        @Test
        @DisplayName("Форма длиной 3 -> IllegalArgumentException")
        void shapeLengthThree_throwsIllegalArgument() {
            BufferedImage img = solidImage(10, 10, 0xFF000000);
            long[] badShape = {3L, 640L, 640L};
            assertThrows(IllegalArgumentException.class,
                    () -> PreProcessor.preprocessImage(img, ENV, badShape),
                    "Форма без batch-измерения должна быть отклонена");
        }

        @Test
        @DisplayName("Форма длиной 5 → IllegalArgumentException")
        void shapeLengthFive_throwsIllegalArgument() {
            BufferedImage img = solidImage(10, 10, 0xFF000000);
            long[] badShape = {1L, 3L, 640L, 640L, 1L};
            assertThrows(IllegalArgumentException.class,
                    () -> PreProcessor.preprocessImage(img, ENV, badShape));
        }

        @Test
        @DisplayName("Batch-размер ≠ 1 -> IllegalArgumentException")
        void batchSizeNotOne_throwsIllegalArgument() {
            BufferedImage img = solidImage(10, 10, 0xFF000000);
            long[] badShape = {2L, 3L, 640L, 640L};
            assertThrows(IllegalArgumentException.class,
                    () -> PreProcessor.preprocessImage(img, ENV, badShape),
                    "Batch-размер 2 должен быть отклонён");
        }

        @Test
        @DisplayName("Минимальная форма [1,3,1,1] -> тензор создаётся")
        void minimalShape_tensorCreated() throws OrtException {
            BufferedImage img = solidImage(10, 10, 0xFFABCDEF);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_MINIMAL)) {
                assertNotNull(t);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Форма и тип возвращаемого тензора
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Форма и тип тензора")
    class TensorShapeAndType {

        @Test
        @DisplayName("Форма тензора совпадает с переданным inputShape")
        void tensorShape_matchesInputShape() throws OrtException {
            BufferedImage img = solidImage(100, 100, 0xFF00FF00);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                assertArrayEquals(SHAPE_STANDARD, t.getInfo().getShape(),
                        "Форма тензора должна точно совпадать с inputShape");
            }
        }

        @Test
        @DisplayName("Тип элементов тензора — FLOAT")
        void tensorElementType_isFloat() throws OrtException {
            BufferedImage img = solidImage(50, 50, 0xFF0000FF);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                assertEquals(ai.onnxruntime.OnnxJavaType.FLOAT,
                        t.getInfo().type,
                        "Тензор должен содержать float-значения");
            }
        }

        @Test
        @DisplayName("Количество элементов тензора = 1 × C × H × W")
        void tensorElementCount_matchesDimensions() throws OrtException {
            long expectedElements = SHAPE_STANDARD[0]
                    * SHAPE_STANDARD[1]
                    * SHAPE_STANDARD[2]
                    * SHAPE_STANDARD[3]; // 1 * 3 * 640 * 640 = 1 228 800

            BufferedImage img = solidImage(320, 240, 0xFFFFFFFF);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                FloatBuffer buf = t.getFloatBuffer();
                assertEquals(expectedElements, buf.remaining(),
                        "Буфер тензора должен содержать ровно C*H*W значений");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Нормализация значений пикселей
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Нормализация пикселей [0..1]")
    class PixelNormalization {

        @Test
        @DisplayName("Полностью чёрное изображение → все значения тензора = 0.0")
        void blackImage_allZeros() throws OrtException {
            // ARGB: 0xFF000000 — чёрный, все каналы = 0
            BufferedImage img = solidImage(10, 10, 0xFF000000);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                FloatBuffer buf = t.getFloatBuffer();
                while (buf.hasRemaining()) {
                    assertEquals(0.0f, buf.get(), 1e-6f,
                            "Все элементы для чёрного изображения должны быть 0.0");
                }
            }
        }

        @Test
        @DisplayName("Полностью белое изображение → все значения тензора ≈ 1.0")
        void whiteImage_allOnes() throws OrtException {
            // ARGB: 0xFFFFFFFF — белый, все каналы = 255
            BufferedImage img = solidImage(10, 10, 0xFFFFFFFF);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                FloatBuffer buf = t.getFloatBuffer();
                while (buf.hasRemaining()) {
                    assertEquals(1.0f, buf.get(), 1e-4f,
                            "Все элементы для белого изображения должны быть ≈ 1.0");
                }
            }
        }

        @Test
        @DisplayName("Все значения тензора в диапазоне [0.0, 1.0]")
        void allValues_withinNormalizedRange() throws OrtException {
            // Случайные цвета через градиент
            BufferedImage img = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 640; y++)
                for (int x = 0; x < 640; x++)
                    img.setRGB(x, y, (0xFF << 24) | ((x * y) & 0xFFFFFF));

            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                FloatBuffer buf = t.getFloatBuffer();
                while (buf.hasRemaining()) {
                    float v = buf.get();
                    assertTrue(v >= 0.0f && v <= 1.0f,
                            () -> "Значение " + v + " выходит за пределы [0.0, 1.0]");
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. BGR-порядок каналов
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BGR-порядок каналов (NCHW)")
    class BgrChannelOrder {

        /**
         * Для чисто-синего пикселя (R=0, G=0, B=255):
         * - Канал 0 (Blue)  → 1.0
         * - Канал 1 (Green) → 0.0
         * - Канал 2 (Red)   → 0.0
         */
        @Test
        @DisplayName("Чисто-синее изображение: Blue-канал=1.0, остальные=0.0")
        void blueImage_blueChannelIsOne() throws OrtException {
            // ARGB: 0xFF0000FF — синий (R=0, G=0, B=255)
            long[] shape1x1 = {1L, 3L, 1L, 1L};
            BufferedImage img = solidImage(1, 1, 0xFF0000FF);

            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, shape1x1)) {
                FloatBuffer buf = t.getFloatBuffer();
                float[] channels = new float[3];
                channels[0] = buf.get(); // Blue  (c=0 в NCHW)
                channels[1] = buf.get(); // Green (c=1)
                channels[2] = buf.get(); // Red   (c=2)

                assertEquals(1.0f, channels[0], 1e-4f,
                        "Blue-канал (c=0) для чисто-синего пикселя должен быть 1.0");
                assertEquals(0.0f, channels[1], 1e-4f,
                        "Green-канал (c=1) для чисто-синего пикселя должен быть 0.0");
                assertEquals(0.0f, channels[2], 1e-4f,
                        "Red-канал (c=2) для чисто-синего пикселя должен быть 0.0");
            }
        }

        /**
         * Для чисто-красного пикселя (R=255, G=0, B=0):
         * - Канал 0 (Blue)  → 0.0
         * - Канал 1 (Green) → 0.0
         * - Канал 2 (Red)   → 1.0
         */
        @Test
        @DisplayName("Чисто-красное изображение: Red-канал=1.0, остальные=0.0")
        void redImage_redChannelIsOne() throws OrtException {
            // ARGB: 0xFFFF0000 — красный
            long[] shape1x1 = {1L, 3L, 1L, 1L};
            BufferedImage img = solidImage(1, 1, 0xFFFF0000);

            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, shape1x1)) {
                FloatBuffer buf = t.getFloatBuffer();
                float blueChannel  = buf.get(); // c=0
                float greenChannel = buf.get(); // c=1
                float redChannel   = buf.get(); // c=2

                assertEquals(0.0f, blueChannel,  1e-4f, "Blue-канал должен быть 0.0");
                assertEquals(0.0f, greenChannel, 1e-4f, "Green-канал должен быть 0.0");
                assertEquals(1.0f, redChannel,   1e-4f, "Red-канал должен быть 1.0");
            }
        }

        @Test
        @DisplayName("Чисто-зелёное изображение: Green-канал=1.0, остальные=0.0")
        void greenImage_greenChannelIsOne() throws OrtException {
            // ARGB: 0xFF00FF00 — зелёный
            long[] shape1x1 = {1L, 3L, 1L, 1L};
            BufferedImage img = solidImage(1, 1, 0xFF00FF00);

            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, shape1x1)) {
                FloatBuffer buf = t.getFloatBuffer();
                float blueChannel  = buf.get(); // c=0
                float greenChannel = buf.get(); // c=1
                float redChannel   = buf.get(); // c=2

                assertEquals(0.0f, blueChannel,  1e-4f, "Blue-канал должен быть 0.0");
                assertEquals(1.0f, greenChannel, 1e-4f, "Green-канал должен быть 1.0");
                assertEquals(0.0f, redChannel,   1e-4f, "Red-канал должен быть 0.0");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. Обработка изображений с нестандартными размерами
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ресайз изображения")
    class ImageResizing {

        @Test
        @DisplayName("Изображение меньше модели (50×50 → 640×640) — тензор правильной формы")
        void smallerImage_resizedCorrectly() throws OrtException {
            BufferedImage img = solidImage(50, 50, 0xFF808080);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                assertArrayEquals(SHAPE_STANDARD, t.getInfo().getShape());
            }
        }

        @Test
        @DisplayName("Изображение крупнее модели (1920×1080 → 640×640) — тензор правильной формы")
        void largerImage_resizedCorrectly() throws OrtException {
            BufferedImage img = solidImage(1920, 1080, 0xFF404040);
            try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                assertArrayEquals(SHAPE_STANDARD, t.getInfo().getShape());
            }
        }

        @Test
        @DisplayName("Квадратное и прямоугольное изображение: нет исключений при ресайзе")
        void rectangularImage_noException() {
            assertDoesNotThrow(() -> {
                BufferedImage img = solidImage(1280, 720, 0xFFCCCCCC);
                try (OnnxTensor t = PreProcessor.preprocessImage(img, ENV, SHAPE_STANDARD)) {
                    assertNotNull(t);
                }
            });
        }
    }
}